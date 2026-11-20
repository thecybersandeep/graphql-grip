package com.grip.graphql.editor;

import java.util.Set;

final class PayloadDispatch {

    private static final Set<String> OPERATION_PARAMETERS =
            Set.of("query", "operationName", "variables", "extensions");

    private PayloadDispatch() {
    }

    static String pathWithoutOperationParameters(String path) {
        if (path == null) {
            return "";
        }

        int mark = path.indexOf('?');
        if (mark < 0) {
            return path;
        }

        String base = path.substring(0, mark);
        StringBuilder kept = new StringBuilder();

        for (String pair : path.substring(mark + 1).split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            if (OPERATION_PARAMETERS.contains(name)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(pair);
        }

        return kept.length() == 0 ? base : base + "?" + kept;
    }

    static String appendParameters(String path, String parameters) {
        String base = pathWithoutOperationParameters(path);
        if (parameters == null || parameters.isEmpty()) {
            return base;
        }
        return base + (base.indexOf('?') < 0 ? "?" : "&") + parameters;
    }

    static boolean accepts(PayloadTransport transport, boolean batchPayload) {
        return transport != null && (!batchPayload || transport.supportsBatch());
    }

    static boolean isModified(PayloadTransport captured, PayloadTransport current,
                              String initialContent, String currentContent) {
        if (captured != current) {
            return true;
        }
        if (initialContent == null) {
            return false;
        }
        return !initialContent.equals(currentContent);
    }
}
