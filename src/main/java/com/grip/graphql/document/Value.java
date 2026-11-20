package com.grip.graphql.document;

import java.util.List;

public sealed interface Value {

    record VariableValue(String name) implements Value {
    }

    record IntValue(String raw) implements Value {
    }

    record FloatValue(String raw) implements Value {
    }

    record StringValue(String value, boolean block) implements Value {
    }

    record BooleanValue(boolean value) implements Value {
    }

    record NullValue() implements Value {
    }

    record EnumValue(String name) implements Value {
    }

    record ListValue(List<Value> values) implements Value {

        public ListValue(List<Value> values) {
            this.values = values == null ? List.of() : List.copyOf(values);
        }
    }

    record ObjectValue(List<ObjectField> fields) implements Value {

        public ObjectValue(List<ObjectField> fields) {
            this.fields = fields == null ? List.of() : List.copyOf(fields);
        }

        public Value field(String name) {
            for (ObjectField candidate : fields) {
                if (candidate.name().equals(name)) {
                    return candidate.value();
                }
            }
            return null;
        }
    }

    record ObjectField(String name, Value value) {
    }
}
