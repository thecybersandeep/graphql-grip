package com.grip.graphql.editor;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import com.grip.graphql.GripCore;

public class GripRequestEditorProvider implements HttpRequestEditorProvider {

    private final MontoyaApi api;
    private final GripCore core;

    public GripRequestEditorProvider(MontoyaApi api, GripCore core) {
        this.api = api;
        this.core = core;
    }

    @Override
    public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(EditorCreationContext creationContext) {
        return new GripRequestEditor(api, core, creationContext);
    }
}
