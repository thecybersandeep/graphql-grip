package com.grip.graphql.schema.regex;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GripRegexStore {

    public static class MatchResult {
        private final String field;
        private final String type;
        private final List<String> suggestions;
        private final String errorType;

        public MatchResult(String field, String type, List<String> suggestions, String errorType) {
            this.field = field;
            this.type = type;
            this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
            this.errorType = errorType;
        }

        public String getField() { return field; }
        public String getType() { return type; }
        public List<String> getSuggestions() { return suggestions; }
        public String getErrorType() { return errorType; }
    }

    private static final Pattern FIELD_NOT_FOUND = Pattern.compile(
        "Cannot query field [\"']([\\w]+)[\"'] on type [\"']([\\w]+)[\"']"
    );

    private static final Pattern DID_YOU_MEAN = Pattern.compile(
        "Did you mean [\"']?([\\w]+)[\"']?(?:,\\s*[\"']?([\\w]+)[\"']?)*(?:\\s*(?:,\\s*)?or [\"']?([\\w]+)[\"']?)?"
    );

    private static final Pattern MUST_HAVE_SELECTION = Pattern.compile(
        "Field [\"']([\\w]+)[\"'] of type [\"']([\\w]+)[\"'] must have a selection"
    );

    private static final Pattern MUST_NOT_HAVE_SELECTION = Pattern.compile(
        "Field [\"']([\\w]+)[\"'] must not have a selection since type [\"']([\\w!\\[\\]]+)[\"']"
    );

    private static final Pattern UNKNOWN_ARGUMENT = Pattern.compile(
        "Unknown argument [\"']([\\w]+)[\"'] on field [\"']([\\w]+)\\.([\\w]+)[\"']"
    );

    private static final Pattern ARGUMENT_TYPE = Pattern.compile(
        "Argument [\"']([\\w]+)[\"'].*(?:of type|expected) [\"']([\\w!\\[\\]]+)[\"']"
    );

    private static final Pattern REQUIRED_ARGUMENT = Pattern.compile(
        "Field [\"']([\\w]+)[\"'] argument [\"']([\\w]+)[\"'] of type [\"']([\\w!\\[\\]]+)[\"'] is required"
    );

    private static final Pattern NOT_INPUT_TYPE = Pattern.compile(
        "[\"']([\\w]+)[\"'] is not an input type"
    );

    private static final Pattern ABSTRACT_TYPE = Pattern.compile(
        "Abstract type [\"']([\\w]+)[\"'] must resolve to"
    );

    private static final Pattern ENUM_INVALID_VALUE = Pattern.compile(
        "Enum [\"']([\\w]+)[\"'] cannot represent.*value:?\\s*[\"']?([\\w]+)[\"']?"
    );

    private static final Pattern EXPECTED_TYPE = Pattern.compile(
        "Expected type [\"']?([\\w!\\[\\]]+)[\"']?,? found"
    );

    private static final Pattern HOTCHOCOLATE_FIELD = Pattern.compile(
        "The field [\"']?([\\w]+)[\"']? does not exist on [\"']?([\\w]+)[\"']?"
    );

    private static final Pattern SANGRIA_FIELD = Pattern.compile(
        "Field [\"']?([\\w]+)[\"']? is not defined"
    );

    private static final Pattern GRAPHENE_FIELD = Pattern.compile(
        "Cannot resolve field [\"']?([\\w]+)[\"']?"
    );

    private static final Pattern JUNIPER_FIELD = Pattern.compile(
        "Unknown field [\"']?([\\w]+)[\"']?"
    );

    private static final Pattern APPSYNC_FIELD = Pattern.compile(
        "FieldUndefined:.*field [\"']?([\\w]+)[\"']?"
    );

    private static final Pattern POSTGRAPHILE_FIELD = Pattern.compile(
        "(?:Cannot|Unable to) query field [\"']?([\\w]+)[\"']?"
    );

    private static final Pattern RELAY_FIELD = Pattern.compile(
        "Unknown field [\"']?([\\w]+)[\"']? on type [\"']?([\\w]+)[\"']?"
    );

    private static final Pattern DGS_FIELD = Pattern.compile(
        "(?:Field|Property) [\"']?([\\w]+)[\"']? (?:not found|does not exist)"
    );

    public static MatchResult extractFieldInfo(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return null;
        }

        Matcher matcher = FIELD_NOT_FOUND.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            String type = matcher.group(2);
            List<String> suggestions = extractSuggestions(errorMessage);
            return new MatchResult(field, type, suggestions, "FIELD_NOT_FOUND");
        }

        matcher = MUST_HAVE_SELECTION.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            String type = matcher.group(2);
            return new MatchResult(field, type, null, "OBJECT_TYPE");
        }

        matcher = MUST_NOT_HAVE_SELECTION.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            String type = normalizeTypeName(matcher.group(2));
            return new MatchResult(field, type, null, "SCALAR_TYPE");
        }

        matcher = HOTCHOCOLATE_FIELD.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            String type = matcher.group(2);
            List<String> suggestions = extractSuggestions(errorMessage);
            return new MatchResult(field, type, suggestions, "HOTCHOCOLATE_FIELD_NOT_FOUND");
        }

        matcher = SANGRIA_FIELD.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            List<String> suggestions = extractSuggestions(errorMessage);
            return new MatchResult(field, null, suggestions, "SANGRIA_FIELD_NOT_DEFINED");
        }

        matcher = GRAPHENE_FIELD.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            return new MatchResult(field, null, null, "GRAPHENE_CANNOT_RESOLVE");
        }

        matcher = JUNIPER_FIELD.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            return new MatchResult(field, null, null, "JUNIPER_UNKNOWN_FIELD");
        }

        matcher = APPSYNC_FIELD.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            return new MatchResult(field, null, null, "APPSYNC_FIELD_UNDEFINED");
        }

        matcher = POSTGRAPHILE_FIELD.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            List<String> suggestions = extractSuggestions(errorMessage);
            return new MatchResult(field, null, suggestions, "POSTGRAPHILE_UNKNOWN_FIELD");
        }

        matcher = RELAY_FIELD.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            String type = matcher.group(2);
            return new MatchResult(field, type, null, "RELAY_UNKNOWN_FIELD");
        }

        matcher = DGS_FIELD.matcher(errorMessage);
        if (matcher.find()) {
            String field = matcher.group(1);
            return new MatchResult(field, null, null, "DGS_FIELD_NOT_FOUND");
        }

        return null;
    }

    public static MatchResult extractArgumentInfo(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return null;
        }

        Matcher matcher = UNKNOWN_ARGUMENT.matcher(errorMessage);
        if (matcher.find()) {
            String argument = matcher.group(1);
            String typeName = matcher.group(2);
            String fieldName = matcher.group(3);
            List<String> suggestions = extractSuggestions(errorMessage);
            return new MatchResult(argument, typeName + "." + fieldName, suggestions, "UNKNOWN_ARGUMENT");
        }

        matcher = REQUIRED_ARGUMENT.matcher(errorMessage);
        if (matcher.find()) {
            String parentField = matcher.group(1);
            String argName = matcher.group(2);
            String argType = normalizeTypeName(matcher.group(3));

            return new MatchResult(argName, parentField + ":" + argType, null, "REQUIRED_ARGUMENT");
        }

        matcher = ARGUMENT_TYPE.matcher(errorMessage);
        if (matcher.find()) {
            String argName = matcher.group(1);
            String argType = normalizeTypeName(matcher.group(2));
            return new MatchResult(argName, argType, null, "ARGUMENT_TYPE");
        }

        return null;
    }

    public static MatchResult extractTypeInfo(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return null;
        }

        Matcher matcher = NOT_INPUT_TYPE.matcher(errorMessage);
        if (matcher.find()) {
            String typeName = matcher.group(1);
            return new MatchResult(null, typeName, null, "NOT_INPUT_TYPE");
        }

        matcher = ABSTRACT_TYPE.matcher(errorMessage);
        if (matcher.find()) {
            String typeName = matcher.group(1);
            return new MatchResult(null, typeName, null, "ABSTRACT_TYPE");
        }

        matcher = EXPECTED_TYPE.matcher(errorMessage);
        if (matcher.find()) {
            String typeName = normalizeTypeName(matcher.group(1));
            return new MatchResult(null, typeName, null, "EXPECTED_TYPE");
        }

        matcher = ENUM_INVALID_VALUE.matcher(errorMessage);
        if (matcher.find()) {
            String enumName = matcher.group(1);
            String invalidValue = matcher.group(2);

            return new MatchResult(invalidValue, enumName, null, "ENUM_TYPE");
        }

        return null;
    }

    public static List<String> extractSuggestions(String errorMessage) {
        List<String> suggestions = new ArrayList<>();
        if (errorMessage == null) return suggestions;

        Matcher matcher = DID_YOU_MEAN.matcher(errorMessage);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null && !group.isEmpty()) {
                    suggestions.add(group);
                }
            }
        }

        if (suggestions.isEmpty() && errorMessage.toLowerCase().contains("did you mean")) {

            int idx = errorMessage.toLowerCase().indexOf("did you mean");
            if (idx >= 0) {
                String afterMean = errorMessage.substring(idx);
                Pattern quotedWord = Pattern.compile("[\"']([\\w]+)[\"']");
                matcher = quotedWord.matcher(afterMean);
                while (matcher.find()) {
                    String word = matcher.group(1);
                    if (!suggestions.contains(word)) {
                        suggestions.add(word);
                    }
                }
            }
        }

        return suggestions;
    }

    public static boolean indicatesValidField(String errorMessage) {
        if (errorMessage == null) return false;

        return errorMessage.contains("must have a selection") ||
               errorMessage.contains("must not have a selection") ||
               errorMessage.contains("is required") ||
               errorMessage.contains("argument") ||
               errorMessage.contains("Expected type");
    }

    public static boolean indicatesObjectType(String errorMessage) {
        if (errorMessage == null) return false;
        return errorMessage.contains("must have a selection") ||
               errorMessage.contains("must have a sub selection");
    }

    public static boolean indicatesScalarType(String errorMessage) {
        if (errorMessage == null) return false;
        return errorMessage.contains("must not have a selection") ||
               errorMessage.contains("cannot have a selection");
    }

    public static String normalizeTypeName(String typeName) {
        if (typeName == null) return null;
        return typeName.replaceAll("[\\[\\]!]", "");
    }

    public static boolean isListType(String typeName) {
        return typeName != null && typeName.contains("[");
    }

    public static boolean isNonNullType(String typeName) {
        return typeName != null && typeName.endsWith("!");
    }
}
