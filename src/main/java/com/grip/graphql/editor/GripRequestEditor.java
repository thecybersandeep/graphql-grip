package com.grip.graphql.editor;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grip.graphql.GripConfig;
import com.grip.graphql.GripCore;
import com.grip.graphql.ui.GripTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

public class GripRequestEditor implements ExtensionProvidedHttpRequestEditor {

    private final MontoyaApi api;
    private final GripCore core;
    private final GripTheme theme;
    private final boolean repeaterContext;

    private JPanel mainPanel;
    private RawEditor queryEditor;
    private JLabel statusLabel;
    private HttpRequest currentRequest;
    private String originalQuery;
    private String originalRequestBody;
    private String initialEditorContent;
    private PayloadTransport currentTransport = PayloadTransport.JSON;
    private PayloadTransport capturedTransport = PayloadTransport.JSON;

    private GraphQLOperationSource source = GraphQLOperationSource.empty();

    private int aliasCount = 100;
    private int batchCount = 10;
    private int fieldDupCount = 500;
    private int directiveCount = 50;
    private int depthCount = 10;
    private int fragmentCount = 50;

    public GripRequestEditor(MontoyaApi api, GripCore core, EditorCreationContext context) {
        this.api = api;
        this.core = core;
        this.theme = core.getTheme();
        this.repeaterContext = context != null && context.toolSource() != null &&
                context.toolSource().isFromTool(ToolType.REPEATER);
        loadConfigFromPrefs();
        initializeUI();
    }

    private void loadConfigFromPrefs() {

        Integer saved;
        saved = core.getConfig().getInteger(GripConfig.ATTACK_ALIAS_COUNT);
        if (saved != null) aliasCount = saved;

        saved = core.getConfig().getInteger(GripConfig.ATTACK_BATCH_COUNT);
        if (saved != null) batchCount = saved;

        saved = core.getConfig().getInteger(GripConfig.ATTACK_FIELD_COUNT);
        if (saved != null) fieldDupCount = saved;

        saved = core.getConfig().getInteger(GripConfig.ATTACK_DIRECTIVE_COUNT);
        if (saved != null) directiveCount = saved;

        saved = core.getConfig().getInteger(GripConfig.ATTACK_DEPTH_COUNT);
        if (saved != null) depthCount = saved;

        saved = core.getConfig().getInteger(GripConfig.ATTACK_FRAGMENT_COUNT);
        if (saved != null) fragmentCount = saved;
    }

    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel titleLabel = new JLabel("GraphQL Grip");
        titleLabel.setFont(theme.getFont(Font.BOLD, 16));
        titleLabel.setForeground(GripTheme.Colors.ACCENT);

        statusLabel = new JLabel("Select an attack pattern to modify the request");
        statusLabel.setFont(theme.getNormalFont());

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.add(titleLabel, BorderLayout.WEST);

        JLabel sizesLabel = new JLabel("Payload sizes (change in Settings)");
        sizesLabel.setFont(theme.getNormalFont());
        sizesLabel.setForeground(theme.getSecondaryText());
        sizesLabel.setToolTipText(
                "Change these in Burp Settings, under GraphQL Grip. Applies to new payloads.");

        JPanel statusRows = new JPanel();
        statusRows.setLayout(new BoxLayout(statusRows, BoxLayout.Y_AXIS));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRows.add(statusLabel);
        statusRows.add(sizesLabel);

        headerPanel.add(titleRow, BorderLayout.NORTH);
        headerPanel.add(new JSeparator(), BorderLayout.CENTER);
        headerPanel.add(statusRows, BorderLayout.SOUTH);

        JTabbedPane attackTabs = new JTabbedPane();
        attackTabs.setFont(theme.getNormalFont());

        attackTabs.addTab("DoS Attacks", createDosPanel());
        attackTabs.addTab("Mutations", createMutationsPanel());
        attackTabs.addTab("Limits Probing", createLimitsPanel());
        attackTabs.addTab("Directives", createDirectivesPanel());
        attackTabs.addTab("Info Disclosure", createInfoPanel());

        queryEditor = api.userInterface().createRawEditor();
        queryEditor.setEditable(true);

        JPanel queryPanel = new JPanel(new BorderLayout());
        queryPanel.setBorder(BorderFactory.createTitledBorder("Modified Request Body (editable)"));
        queryPanel.add(queryEditor.uiComponent(), BorderLayout.CENTER);
        queryPanel.setMinimumSize(new Dimension(100, 150));
        queryPanel.setPreferredSize(new Dimension(400, 200));

        JPanel utilPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));

        JButton copyBtn = new JButton("Copy to Clipboard");
        copyBtn.addActionListener(e -> {
            String payload = payloadText();
            if (payload == null || payload.isBlank()) {
                statusLabel.setText("Nothing to copy");
                return;
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(payload), null);
            statusLabel.setText("Copied " + payload.length() + " characters to the clipboard");
        });

        JButton resetBtn = new JButton("Reset to Original");
        resetBtn.addActionListener(e -> {
            if (currentRequest == null) {
                return;
            }
                currentTransport = capturedTransport;
            renderCapturedRequest();
        });

        utilPanel.add(copyBtn);
        utilPanel.add(resetBtn);

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(attackTabs, BorderLayout.CENTER);
        topPanel.setMinimumSize(new Dimension(100, 200));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, queryPanel);
        splitPane.setResizeWeight(0.35);
        splitPane.setDividerLocation(300);
        splitPane.setOneTouchExpandable(true);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(utilPanel, BorderLayout.SOUTH);

        api.userInterface().applyThemeToComponent(mainPanel);
    }

    private JPanel createDosPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(GripTheme.SPACING_MD, GripTheme.SPACING_MD, GripTheme.SPACING_MD, GripTheme.SPACING_MD));

        JPanel aliasSection = new JPanel(new BorderLayout(5, 5));
        aliasSection.setBorder(BorderFactory.createTitledBorder("Alias/Width Attacks"));
        aliasSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel aliasButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        addAttackButton(aliasButtons, "Alias Overloading", "N aliased copies of field", e -> applyAliasOverloading());
        addAttackButton(aliasButtons, "Width Attack", "N different __type queries", e -> applyWidthAttack());

        aliasSection.add(aliasButtons, BorderLayout.CENTER);
        aliasSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(aliasSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel fieldSection = new JPanel(new BorderLayout(5, 5));
        fieldSection.setBorder(BorderFactory.createTitledBorder("Field Duplication"));
        fieldSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fieldButtons = new JPanel(new GridLayout(1, 1, 5, 5));
        addAttackButton(fieldButtons, "Field Duplication", "N unaliased copies of one field, to test field merging", e -> applyFieldDuplication());

        fieldSection.add(fieldButtons, BorderLayout.CENTER);
        fieldSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(fieldSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel depthSection = new JPanel(new BorderLayout(5, 5));
        depthSection.setBorder(BorderFactory.createTitledBorder("Depth Attacks"));
        depthSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel depthButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        addAttackButton(depthButtons, "Deep Recursion", "N-level nested query", e -> applyDeepRecursion());
        addAttackButton(depthButtons, "Circular Introspection", "N-level nested introspection", e -> applyCircularIntrospection());

        depthSection.add(depthButtons, BorderLayout.CENTER);
        depthSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(depthSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel fragSection = new JPanel(new BorderLayout(5, 5));
        fragSection.setBorder(BorderFactory.createTitledBorder("Fragment Overloading"));
        fragSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fragButtons = new JPanel(new GridLayout(1, 1, 5, 5));
        addAttackButton(fragButtons, "Fragment Overloading", "N unique fragment spreads", e -> applyFragmentOverloading());

        fragSection.add(fragButtons, BorderLayout.CENTER);
        fragSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(fragSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel batchSection = new JPanel(new BorderLayout(5, 5));
        batchSection.setBorder(BorderFactory.createTitledBorder("Batching Attacks"));
        batchSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel batchButtons = new JPanel(new GridLayout(2, 2, 5, 5));
        addAttackButton(batchButtons, "Simple Batch", "N identical queries in array", e -> applyBatchQuery());
        addAttackButton(batchButtons, "Mixed Batch", "Query + introspection alternating", e -> applyMixedBatch());
        addAttackButton(batchButtons, "Incremental Batch", "Test batch size limits", e -> applyIncrementalBatch());
        addAttackButton(batchButtons, "Batch with Vars", "N queries with unique operationName", e -> applyBatchWithVariables());

        batchSection.add(batchButtons, BorderLayout.CENTER);
        batchSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(batchSection);

        mainPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createMutationsPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(GripTheme.SPACING_MD, GripTheme.SPACING_MD, GripTheme.SPACING_MD, GripTheme.SPACING_MD));

        JLabel warningLabel = new JLabel("\u26A0\uFE0F WARNING: Mutations modify data! Review before sending.");
        warningLabel.setForeground(GripTheme.Colors.WARNING);
        warningLabel.setFont(theme.getFont(Font.BOLD, 12));
        warningLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(warningLabel);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel aliasSection = new JPanel(new BorderLayout(5, 5));
        aliasSection.setBorder(BorderFactory.createTitledBorder("Aliased Mutations"));
        aliasSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel aliasButtons = new JPanel(new GridLayout(1, 1, 5, 5));
        addAttackButton(aliasButtons, "Aliased Mutations", "mutation { m0: field, m1: field... }", e -> applyAliasedMutations());

        aliasSection.add(aliasButtons, BorderLayout.CENTER);
        aliasSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(aliasSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel batchSection = new JPanel(new BorderLayout(5, 5));
        batchSection.setBorder(BorderFactory.createTitledBorder("Batch Mutations"));
        batchSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel batchButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        addAttackButton(batchButtons, "Batch Mutations", "[{mutation}, {mutation}...] array", e -> applyBatchMutations());
        addAttackButton(batchButtons, "Mixed Batch", "Mutations + queries interleaved", e -> applyMixedMutationBatch());

        batchSection.add(batchButtons, BorderLayout.CENTER);
        batchSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(batchSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel fragSection = new JPanel(new BorderLayout(5, 5));
        fragSection.setBorder(BorderFactory.createTitledBorder("Mutation Fragments"));
        fragSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fragButtons = new JPanel(new GridLayout(1, 1, 5, 5));
        addAttackButton(fragButtons, "Mutation Fragments", "N inline fragments, each wrapping your mutation", e -> applyMutationFragments());

        fragSection.add(fragButtons, BorderLayout.CENTER);
        fragSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(fragSection);

        mainPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createLimitsPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(GripTheme.SPACING_LG, GripTheme.SPACING_LG, GripTheme.SPACING_LG, GripTheme.SPACING_LG));

        mainPanel.add(createCategoryLabel("Depth Limit Probing"));
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel depthPanel = new JPanel(new GridLayout(0, 2, GripTheme.SPACING_SM, GripTheme.SPACING_SM));
        depthPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        addAttackButton(depthPanel, "Depth 5",
            "Query with depth 5 - baseline test",
            e -> applyDepthProbe(5));

        addAttackButton(depthPanel, "Depth 10",
            "Query with depth 10",
            e -> applyDepthProbe(10));

        addAttackButton(depthPanel, "Depth 20",
            "Query with depth 20",
            e -> applyDepthProbe(20));

        addAttackButton(depthPanel, "Depth 50",
            "Query with depth 50 - stress test",
            e -> applyDepthProbe(50));

        depthPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, depthPanel.getPreferredSize().height + 20));
        mainPanel.add(depthPanel);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_XL));

        mainPanel.add(createCategoryLabel("Width Limit Probing"));
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel widthPanel = new JPanel(new GridLayout(0, 2, GripTheme.SPACING_SM, GripTheme.SPACING_SM));
        widthPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        addAttackButton(widthPanel, "Width 50",
            "Query with 50 aliases - baseline",
            e -> applyWidthProbe(50));

        addAttackButton(widthPanel, "Width 100",
            "Query with 100 aliases",
            e -> applyWidthProbe(100));

        addAttackButton(widthPanel, "Width 500",
            "Query with 500 aliases",
            e -> applyWidthProbe(500));

        addAttackButton(widthPanel, "Width 1000",
            "Query with 1000 aliases - stress test",
            e -> applyWidthProbe(1000));

        widthPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, widthPanel.getPreferredSize().height + 20));
        mainPanel.add(widthPanel);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_XL));

        mainPanel.add(createCategoryLabel("Batch Size Probing"));
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel batchProbePanel = new JPanel(new GridLayout(0, 2, GripTheme.SPACING_SM, GripTheme.SPACING_SM));
        batchProbePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        addAttackButton(batchProbePanel, "Batch 5",
            "Array batch with 5 queries",
            e -> applyBatchProbe(5));

        addAttackButton(batchProbePanel, "Batch 20",
            "Array batch with 20 queries",
            e -> applyBatchProbe(20));

        addAttackButton(batchProbePanel, "Batch 50",
            "Array batch with 50 queries",
            e -> applyBatchProbe(50));

        addAttackButton(batchProbePanel, "Batch 100",
            "Array batch with 100 queries - stress test",
            e -> applyBatchProbe(100));

        batchProbePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, batchProbePanel.getPreferredSize().height + 20));
        mainPanel.add(batchProbePanel);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDirectivesPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(GripTheme.SPACING_MD, GripTheme.SPACING_MD, GripTheme.SPACING_MD, GripTheme.SPACING_MD));

        JPanel overloadSection = new JPanel(new BorderLayout(5, 5));
        overloadSection.setBorder(BorderFactory.createTitledBorder("Directive Overloading"));
        overloadSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel overloadButtons = new JPanel(new GridLayout(1, 3, 5, 5));
        addAttackButton(overloadButtons, "@include Overload", "field @include(if:true)...", e -> applyDirectiveOverloading("include"));
        addAttackButton(overloadButtons, "@skip Overload", "field @skip(if:false)...", e -> applyDirectiveOverloading("skip"));
        addAttackButton(overloadButtons, "Mixed Directives", "@include/@skip alternating", e -> applyMixedDirectives());

        overloadSection.add(overloadButtons, BorderLayout.CENTER);
        overloadSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(overloadSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel detectSection = new JPanel(new BorderLayout(5, 5));
        detectSection.setBorder(BorderFactory.createTitledBorder("Directive Detection (fixed payloads)"));
        detectSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel detectButtons = new JPanel(new GridLayout(1, 3, 5, 5));
        addAttackButton(detectButtons, "@defer Test", "Test incremental delivery", e -> applyDeferTest());
        addAttackButton(detectButtons, "@stream Test", "Test streaming support", e -> applyStreamTest());
        addAttackButton(detectButtons, "Discovery", "List all directives", e -> applyDirectiveDiscovery());

        detectSection.add(detectButtons, BorderLayout.CENTER);
        detectSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(detectSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel opNameSection = new JPanel(new BorderLayout(5, 5));
        opNameSection.setBorder(BorderFactory.createTitledBorder("Operation Name Testing (fixed payloads)"));
        opNameSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel opNameButtons = new JPanel(new GridLayout(1, 4, 5, 5));
        addAttackButton(opNameButtons, "Long Name", "1000 char operation name", e -> applyLongOperationName());
        addAttackButton(opNameButtons, "Special Chars", "Underscores at edges", e -> applySpecialCharsOperationName());
        addAttackButton(opNameButtons, "Unicode", "éèê characters", e -> applyUnicodeOperationName());
        addAttackButton(opNameButtons, "Reserved", "'query' as op name", e -> applyReservedWordsOperationName());

        opNameSection.add(opNameButtons, BorderLayout.CENTER);
        opNameSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(opNameSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel csrfSection = new JPanel(new BorderLayout(5, 5));
        csrfSection.setBorder(BorderFactory.createTitledBorder("Request Format / CSRF"));
        csrfSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel csrfButtons = new JPanel(new GridLayout(1, 5, 5, 5));
        addAttackButton(csrfButtons, "POST JSON", "Default transport", e -> convertToJson());
        addAttackButton(csrfButtons, "GET Method", "Query as URL param", e -> convertToGet());
        addAttackButton(csrfButtons, "URL-Encoded", "x-www-form-urlencoded", e -> convertToUrlEncoded());
        addAttackButton(csrfButtons, "Multipart", "multipart/form-data", e -> convertToMultipart());
        addAttackButton(csrfButtons, "APQ Hash", "SHA256 persisted query", e -> applyPersistedQueryHash());

        csrfSection.add(csrfButtons, BorderLayout.CENTER);
        csrfSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        mainPanel.add(csrfSection);

        mainPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(GripTheme.SPACING_LG, GripTheme.SPACING_LG, GripTheme.SPACING_LG, GripTheme.SPACING_LG));

        mainPanel.add(createCategoryLabel("Schema Discovery"));
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel discoveryPanel = new JPanel(new GridLayout(0, 2, GripTheme.SPACING_SM, GripTheme.SPACING_SM));
        discoveryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        addAttackButton(discoveryPanel, "Full Introspection",
            "Complete introspection query",
            e -> applyFullIntrospection());

        addAttackButton(discoveryPanel, "Field Suggestion",
            "Trigger 'Did you mean?' errors",
            e -> applyFieldSuggestionProbe());

        addAttackButton(discoveryPanel, "Type Enumeration",
            "Query common type names",
            e -> applyTypeEnumeration());

        addAttackButton(discoveryPanel, "__typename Probe",
            "Basic __typename query",
            e -> applyTypenameProbe());

        discoveryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, discoveryPanel.getPreferredSize().height + 20));
        mainPanel.add(discoveryPanel);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_XL));

        mainPanel.add(createCategoryLabel("Introspection Bypasses"));
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel bypassPanel = new JPanel(new GridLayout(0, 2, GripTheme.SPACING_SM, GripTheme.SPACING_SM));
        bypassPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        addAttackButton(bypassPanel, "__type Bypass",
            "Uses __type(name:\"Query\") instead of __schema",
            e -> applyIntrospectionBypass());

        addAttackButton(bypassPanel, "Newline Injection",
            "Newline after __schema to bypass regex",
            e -> applyBypassNewline());

        addAttackButton(bypassPanel, "Tab Injection",
            "Tab characters to bypass regex",
            e -> applyBypassTab());

        addAttackButton(bypassPanel, "CRLF Bypass",
            "Windows-style line endings",
            e -> applyBypassCRLF());

        addAttackButton(bypassPanel, "Fragment Bypass",
            "Using fragments to access __schema",
            e -> applyBypassFragment());

        addAttackButton(bypassPanel, "Inline Fragment",
            "Inline fragment wrapper",
            e -> applyBypassInlineFragment());

        addAttackButton(bypassPanel, "Aliased __schema",
            "Alias the __schema query",
            e -> applyBypassAlias());

        addAttackButton(bypassPanel, "Batched Bypass",
            "Introspection in batch array",
            e -> applyBypassBatch());

        addAttackButton(bypassPanel, "@include Bypass",
            "Conditional with @include directive",
            e -> applyBypassIncludeDirective());

        addAttackButton(bypassPanel, "@skip Bypass",
            "Conditional with @skip directive",
            e -> applyBypassSkipDirective());

        addAttackButton(bypassPanel, "Long OpName",
            "Long operation name to overflow buffer",
            e -> applyBypassLongOpName());

        addAttackButton(bypassPanel, "GET Method",
            "Send introspection via GET",
            e -> applyBypassGetMethod());

        bypassPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, bypassPanel.getPreferredSize().height + 20));
        mainPanel.add(bypassPanel);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_XL));

        mainPanel.add(createCategoryLabel("Debug & Tracing"));
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel debugPanel = new JPanel(new GridLayout(0, 2, GripTheme.SPACING_SM, GripTheme.SPACING_SM));
        debugPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        addAttackButton(debugPanel, "Enable Tracing",
            "Adds extensions.tracing=true",
            e -> applyTracing());

        addAttackButton(debugPanel, "Enable Debug Mode",
            "Adds debug/verbose/stackTrace flags",
            e -> applyDebugMode());

        addAttackButton(debugPanel, "Trigger Verbose Errors",
            "Sends invalid query to expose error details",
            e -> applyErrorTrigger());

        addAttackButton(debugPanel, "Engine Fingerprint",
            "Queries schema to identify GraphQL engine",
            e -> applyFingerprint());

        debugPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, debugPanel.getPreferredSize().height + 20));
        mainPanel.add(debugPanel);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JLabel createCategoryLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(theme.getFont(Font.BOLD, GripTheme.FONT_SIZE_NORMAL));
        label.setForeground(GripTheme.Colors.ACCENT);
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, GripTheme.Colors.ACCENT),
            BorderFactory.createEmptyBorder(GripTheme.SPACING_SM, GripTheme.SPACING_MD, GripTheme.SPACING_SM, GripTheme.SPACING_MD)
        ));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        Dimension size = new Dimension(Integer.MAX_VALUE, 32);
        label.setMaximumSize(size);
        label.setPreferredSize(new Dimension(400, 32));
        return label;
    }

    private void addAttackButton(JPanel panel, String name, String description, java.awt.event.ActionListener action) {
        JButton btn = new JButton(name);
        btn.setFont(theme.getFont(Font.PLAIN, GripTheme.FONT_SIZE_NORMAL - 1));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(description);
        btn.setBorder(BorderFactory.createCompoundBorder(
            new GripTheme.RoundedBorder(theme.getBorder(), GripTheme.CORNER_RADIUS_SM, 1),
            BorderFactory.createEmptyBorder(GripTheme.SPACING_SM, GripTheme.SPACING_MD, GripTheme.SPACING_SM, GripTheme.SPACING_MD)
        ));
        btn.addActionListener(event -> {
            loadConfigFromPrefs();
            action.actionPerformed(event);
        });
        panel.add(btn);
    }

    private String payloadText() {
        return queryEditor == null ? "" : queryEditor.getContents().toString();
    }

    private void setPayloadText(String payload) {
        queryEditor.setContents(ByteArray.byteArray(payload == null ? "" : payload));
    }

    private void emit(String query, String status) {
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, status);
    }

    private void setQueryOutput(JsonObject body, String status) {
        JsonObject output = body;

        if (body.has("query") && body.get("query").isJsonPrimitive()) {
            output = GraphQLPayloadWriter.body(source, body.get("query").getAsString());
            for (String member : body.keySet()) {
                if (!"query".equals(member)) {
                    output.add(member, body.get(member));
                }
            }
        }

        setPayloadText(GraphQLPayloadWriter.json(output));
        statusLabel.setText(status + "  |  " + currentTransport.description());
    }

    private void applyAliasOverloading() {
        emit(GraphQLPayloads.aliasOverloading(source, aliasCount),
                "Alias Overloading: " + aliasCount + " aliases");
    }

    private void applyBatchQuery() {

        if (originalQuery == null) {
            statusLabel.setText("No query loaded - load a request first");
            return;
        }

        JsonArray batch = GraphQLPayloadWriter.repeated(source, originalQuery, batchCount);

        setPayloadText(GraphQLPayloadWriter.json(batch));
        statusLabel.setText("Batch Query: " + batchCount + " copies of original query");
    }

    private void applyMixedBatch() {
        String query = originalQuery != null ? originalQuery : GraphQLPayloads.TYPENAME_PROBE;

        setPayloadText(GraphQLPayloadWriter.json(GraphQLPayloadWriter.alternating(
                source, query, GraphQLPayloads.INTROSPECTION_PING, batchCount)));
        statusLabel.setText("Mixed Batch: " + batchCount
                + " alternating queries + introspection");
    }

    private void applyIncrementalBatch() {
        String query = originalQuery != null ? originalQuery : GraphQLPayloads.TYPENAME_PROBE;

        setPayloadText(GraphQLPayloadWriter.json(
                GraphQLPayloadWriter.renamedSeries(source, "Op", query, batchCount)));
        statusLabel.setText("Incremental Batch: " + batchCount
                + " queries - raise Operations per batch in Settings and retry"
                + " to find the server limit");
    }

    private void applyBatchWithVariables() {
        String query = originalQuery != null ? originalQuery : GraphQLPayloads.TYPENAME_PROBE;

        setPayloadText(GraphQLPayloadWriter.json(
                GraphQLPayloadWriter.renamedSeries(source, "GripBatch", query, batchCount)));
        statusLabel.setText("Batch with Variables: " + batchCount
                + " queries - each has a unique operationName (bypasses some rate limits)");
    }

    private void applyFieldDuplication() {
        emit(GraphQLPayloads.fieldDuplication(source, fieldDupCount),
                "Field Duplication: " + fieldDupCount + " unaliased copies of one field"
                        + " - a conformant server merges them into a single resolution");
    }

    private void applyWidthAttack() {
        emit(GraphQLPayloads.widthAttack(aliasCount),
                "Width Attack: " + aliasCount
                        + " __type lookups with distinct arguments (breadth, not repetition)");
    }

    private void applyFragmentOverloading() {
        emit(GraphQLPayloads.fragmentOverloading(fragmentCount),
                "Fragment Overloading: " + fragmentCount + " fragments");
    }

    private void applyDeepRecursion() {
        emit(GraphQLPayloads.deepRecursion(depthCount), "Deep Recursion: depth " + depthCount);
    }

    private void applyCircularIntrospection() {
        emit(GraphQLPayloads.circularIntrospection(depthCount),
                "Circular Introspection: depth " + depthCount);
    }

    private void applyAliasedMutations() {
        emit(GraphQLPayloads.aliasedMutations(source, aliasCount), source.isMutation()
                ? "Aliased Mutations: " + aliasCount + " copies of your mutation (⚠ Review!)"
                : "Aliased Mutations: " + aliasCount
                        + " (⚠ PLACEHOLDER - load a mutation request first)");
    }

    private void applyBatchMutations() {
        String mutation = originalQuery != null && source.isMutation()
                ? originalQuery
                : GraphQLPayloads.TYPENAME_MUTATION;

        setPayloadText(GraphQLPayloadWriter.json(
                GraphQLPayloadWriter.repeated(source, mutation, batchCount)));
        statusLabel.setText(source.isMutation()
                ? "Batch Mutations: " + batchCount
                        + " copies of your mutation (⚠ Review before sending!)"
                : "Batch Mutations: " + batchCount
                        + " (⚠ PLACEHOLDER - load a mutation request first)");
    }

    private void applyMixedMutationBatch() {
        boolean hasMutation = source.isMutation() && originalQuery != null;

        String even = hasMutation ? originalQuery : GraphQLPayloads.TYPENAME_MUTATION;
        String odd = hasMutation || originalQuery == null
                ? GraphQLPayloads.TYPENAME_PROBE
                : originalQuery;

        setPayloadText(GraphQLPayloadWriter.json(
                GraphQLPayloadWriter.alternating(source, even, odd, batchCount)));
        statusLabel.setText(hasMutation
                ? "Mixed Batch: " + batchCount
                        + " items - mutation + queries interleaved (⚠ Review!)"
                : "Mixed Batch: " + batchCount
                        + " items - PLACEHOLDER mutation, load a mutation request first");
    }

    private void applyMutationFragments() {
        emit(GraphQLPayloads.mutationFragments(source, fragmentCount), source.isMutation()
                ? "Mutation Fragments: " + fragmentCount
                        + " inline fragments around your mutation (⚠ Review!)"
                : "Mutation Fragments: " + fragmentCount
                        + " inline fragments - load a mutation request to wrap a real mutation");
    }

    private void applyDepthProbe(int depth) {
        emit(GraphQLPayloads.depthProbe(depth),
                "Depth Probe: " + depth + " nested fields/type levels - check if server rejects");
    }

    private void applyWidthProbe(int width) {
        emit(GraphQLPayloads.widthProbe(width),
                "Width Probe: " + width + " aliases - check if server rejects");
    }

    private void applyBatchProbe(int size) {
        setPayloadText(GraphQLPayloadWriter.json(GraphQLPayloadWriter.repeated(
                source, GraphQLPayloads.TYPENAME_PROBE, size)));
        statusLabel.setText("Batch Probe: " + size + " queries - check if server rejects");
    }

    private void applyDirectiveOverloading(String directive) {
        emit(GraphQLPayloads.directiveOverloading(source, directive, directiveCount),
                "@" + directive + " Overloading: " + directiveCount + " directives");
    }



    private void applyDeferTest() {
        emit(GraphQLPayloads.DEFER_TEST,
                "@defer Test: Check if incremental delivery is supported");
    }

    private void applyStreamTest() {
        emit(GraphQLPayloads.STREAM_TEST,
                "@stream Test: Check if streaming is supported");
    }

    private void applyMixedDirectives() {
        emit(GraphQLPayloads.mixedDirectives(source, directiveCount),
                "Mixed Directives: " + directiveCount + " alternating @include/@skip");
    }

    private void applyDirectiveDiscovery() {
        emit(GraphQLPayloads.DIRECTIVE_DISCOVERY,
                "Directive Discovery: Lists all available directives");
    }

    private void applyLongOperationName() {
        emit(GraphQLPayloads.longOperationName(),
                "Long Operation Name: 1000+ characters");
    }

    private void applySpecialCharsOperationName() {
        emit(GraphQLPayloads.SPECIAL_CHARS_OPERATION_NAME,
                "Special Chars: Underscores at edges");
    }

    private void applyUnicodeOperationName() {
        emit(GraphQLPayloads.UNICODE_OPERATION_NAME,
                "Unicode Operation Name: éèê characters, invalid per spec");
    }

    private void applyReservedWordsOperationName() {
        emit(GraphQLPayloads.RESERVED_WORDS_OPERATION_NAME,
                "Reserved Words: 'query' as operation name");
    }

    private void applyFieldSuggestionProbe() {
        emit(GraphQLPayloads.fieldSuggestionProbe(source),
                "Field Suggestion Probe: names near the captured field plus common guesses");
    }

    private void applyTypeEnumeration() {
        emit(GraphQLPayloads.TYPE_ENUMERATION,
                "Type Enumeration: Query common type names");
    }

    private void applyFullIntrospection() {
        emit(GraphQLPayloads.FULL_INTROSPECTION,
                "Full Introspection Query");
    }

    private void applyTracing() {
        JsonObject body = new JsonObject();
        body.addProperty("query",
                originalQuery != null ? originalQuery : GraphQLPayloads.TYPENAME_PROBE);

        JsonObject extensions = new JsonObject();
        extensions.addProperty("tracing", true);
        body.add("extensions", extensions);

        setQueryOutput(body, "Tracing enabled via extensions.tracing=true");
    }

    private void applyDebugMode() {
        JsonObject body = new JsonObject();
        body.addProperty("query",
                originalQuery != null ? originalQuery : GraphQLPayloads.TYPENAME_PROBE);

        JsonObject extensions = new JsonObject();
        extensions.addProperty("debug", true);
        extensions.addProperty("verbose", true);
        extensions.addProperty("includeStackTrace", true);
        body.add("extensions", extensions);

        setQueryOutput(body, "Debug mode enabled via extensions");
    }

    private void applyIntrospectionBypass() {
        emit(GraphQLPayloads.TYPE_BYPASS,
                "Introspection Bypass: __type instead of __schema"
                        + " - if the root type is not named Query, retry with the __typename value");
    }

    private void applyTypenameProbe() {
        emit(GraphQLPayloads.TYPENAME_PROBE,
                "__typename Probe: May reveal info even when introspection is blocked");
    }

    private void applyBypassNewline() {
        emit(GraphQLPayloads.BYPASS_NEWLINE,
                "Newline Bypass: Regex patterns often match __schema{ without newlines");
    }

    private void applyBypassTab() {
        emit(GraphQLPayloads.BYPASS_TAB,
                "Tab Bypass: Tab character between __schema and brace");
    }

    private void applyBypassCRLF() {
        emit(GraphQLPayloads.BYPASS_CRLF,
                "CRLF Bypass: Windows-style line endings may bypass Unix regex");
    }

    private void applyBypassFragment() {
        emit(GraphQLPayloads.BYPASS_FRAGMENT,
                "Fragment Bypass: Reaching __schema through a fragment on __Schema");
    }

    private void applyBypassInlineFragment() {
        emit(GraphQLPayloads.BYPASS_INLINE_FRAGMENT,
                "Inline Fragment Bypass: no type condition, so it is valid on any schema");
    }

    private void applyBypassAlias() {
        emit(GraphQLPayloads.BYPASS_ALIAS,
                "Aliased Bypass: Aliasing __schema may bypass string matching");
    }

    private void applyBypassBatch() {
        JsonArray batch = new JsonArray();
        batch.add(GraphQLPayloadWriter.body(null, GraphQLPayloads.TYPENAME_PROBE));
        batch.add(GraphQLPayloadWriter.body(null, GraphQLPayloads.BYPASS_ALIAS));

        setPayloadText(GraphQLPayloadWriter.json(batch));
        statusLabel.setText("Batch Bypass: Introspection hidden in a batch array");
    }

    private void applyBypassIncludeDirective() {
        emit(GraphQLPayloads.BYPASS_INCLUDE,
                "@include Bypass: Directive may confuse static pattern matching");
    }

    private void applyBypassSkipDirective() {
        emit(GraphQLPayloads.BYPASS_SKIP,
                "@skip Bypass: @skip(if:false) still executes the field");
    }

    private void applyBypassLongOpName() {
        emit(GraphQLPayloads.bypassLongOperationName(),
                "Long OpName Bypass: May exceed WAF pattern matching buffers");
    }

    private void applyBypassGetMethod() {
        currentTransport = PayloadTransport.GET;
        emit(GraphQLPayloads.BYPASS_GET,
                "GET Method Bypass: WAF rules may only inspect POST bodies");
    }

    private void applyErrorTrigger() {
        emit(GraphQLPayloads.ERROR_TRIGGER,
                "Error Trigger: Invalid query to expose error details");
    }

    private void applyFingerprint() {
        emit(GraphQLPayloads.FINGERPRINT,
                "Engine Fingerprint: root type names and the directive set");
    }

    private void convertToJson() {
        switchTransport(PayloadTransport.JSON);
    }

    private void convertToGet() {
        switchTransport(PayloadTransport.GET);
    }

    private void convertToUrlEncoded() {
        switchTransport(PayloadTransport.FORM);
    }

    private void convertToMultipart() {
        switchTransport(PayloadTransport.MULTIPART);
    }

    private void switchTransport(PayloadTransport transport) {
        if (!PayloadDispatch.accepts(transport, isBatchPayload())) {
            statusLabel.setText("Still " + currentTransport.description()
                    + " - a batch payload can only be sent as "
                    + PayloadTransport.JSON.description()
                    + ", generate a single operation first");
            return;
        }

        currentTransport = transport;
        statusLabel.setText("Request format: " + transport.description()
                + " - on send, the payload above is " + transport.sendsAs());
    }

    private boolean isBatchPayload() {
        try {
            return JsonParser.parseString(payloadText()).isJsonArray();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void applyPersistedQueryHash() {
        String query = originalQuery != null ? originalQuery : GraphQLPayloads.TYPENAME_PROBE;
        String sent = GraphQLPayloadWriter.body(source, query).get("query").getAsString();

        JsonObject body = new JsonObject();
        body.addProperty("query", sent);

        JsonObject persistedQuery = new JsonObject();
        persistedQuery.addProperty("version", 1);
        persistedQuery.addProperty("sha256Hash", GraphQLPayloads.sha256(sent));

        JsonObject extensions = new JsonObject();
        extensions.add("persistedQuery", persistedQuery);
        body.add("extensions", extensions);

        setQueryOutput(body, "APQ: SHA256 of the query as sent"
                + " - test if the server accepts persisted queries");
    }


    private JsonObject capturedEnvelope() {
        if (capturedTransport != PayloadTransport.JSON
                || originalRequestBody == null || originalRequestBody.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(originalRequestBody);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String contentTypeOf(HttpRequest request) {
        if (request == null) {
            return null;
        }
        for (HttpHeader header : request.headers()) {
            if ("content-type".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }

    private String extractQueryFromRequest(HttpRequest request) {
        if (request == null) return null;
        return GraphQLRequestDetector.extractQuery(
                request.method(),
                request.path(),
                contentTypeOf(request),
                request.bodyToString());
    }

    private boolean isGraphQLRequest(HttpRequest request) {
        if (request == null) return false;
        return GraphQLRequestDetector.isGraphQLRequest(
                request.method(),
                request.path(),
                contentTypeOf(request),
                request.bodyToString());
    }

    private String validateDispatch(String content) {
        if (currentRequest == null) {
            return "No HTTP request is loaded";
        }

        try {
            URI endpoint = new URI(currentRequest.url());
            String scheme = endpoint.getScheme();
            if (scheme == null || endpoint.getHost() == null ||
                    !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return "The request endpoint must be a valid HTTP or HTTPS URL";
            }
        } catch (Exception e) {
            return "The request endpoint is malformed: " + e.getMessage();
        }

        if (content == null || content.trim().isEmpty()) {
            return "The GraphQL query cannot be empty";
        }

        try {
            JsonElement payload = JsonParser.parseString(content);
            if (payload.isJsonObject()) {
                return validateOperation(payload.getAsJsonObject(), "Request");
            }
            if (payload.isJsonArray()) {
                if (!currentTransport.supportsBatch()) {
                    return "A batch payload can only be sent as "
                            + PayloadTransport.JSON.description();
                }
                JsonArray operations = payload.getAsJsonArray();
                if (operations.size() == 0) {
                    return "The GraphQL batch cannot be empty";
                }
                for (int i = 0; i < operations.size(); i++) {
                    if (!operations.get(i).isJsonObject()) {
                        return "Batch item " + (i + 1) + " must be a JSON object";
                    }
                    String error = validateOperation(operations.get(i).getAsJsonObject(),
                            "Batch item " + (i + 1));
                    if (error != null) return error;
                }
                return null;
            }
            return "The request body must be a JSON object or batch array";
        } catch (Exception e) {
            return "The request body is not valid JSON: " + e.getMessage();
        }
    }

    private String validateOperation(JsonObject operation, String label) {
        if (!operation.has("query") || operation.get("query").isJsonNull() ||
                !operation.get("query").isJsonPrimitive() ||
                !operation.getAsJsonPrimitive("query").isString() ||
                operation.get("query").getAsString().trim().isEmpty()) {
            return label + " must contain a non-empty query string";
        }

        if (operation.has("variables") && !operation.get("variables").isJsonNull() &&
                !operation.get("variables").isJsonObject()) {
            return label + " variables must be a valid JSON object";
        }
        return null;
    }

    private String loadedStatus() {
        if (source.syntaxError() != null) {
            return "Loaded, but the query did not parse: " + source.syntaxError();
        }

        StringBuilder status = new StringBuilder("Loaded. Field: '")
                .append(source.rootFieldName()).append("'");
        int declared = source.declaredVariables().size();
        if (declared > 0) {
            status.append(". Carrying ").append(declared).append(" variable(s)");
        }
        status.append(". Select attack pattern.  |  ").append(currentTransport.description());
        return status.toString();
    }

    private void showValidationError(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("ERROR: " + message));
    }

    @Override
    public HttpRequest getRequest() {
        if (currentRequest == null) return null;

        String content = payloadText();
        if (content == null || content.isEmpty()) {
            return currentRequest;
        }

        String validationError = validateDispatch(content);
        if (validationError != null) {
            showValidationError(validationError);
            return currentRequest;
        }

        try {
            JsonElement payload = JsonParser.parseString(content);

            if (payload.isJsonArray() || currentTransport == PayloadTransport.JSON) {
                return jsonRequest(content);
            }

            JsonObject envelope = payload.getAsJsonObject();
            return switch (currentTransport) {
                case GET -> urlRequest(envelope);
                case FORM -> formRequest(envelope);
                case MULTIPART -> multipartRequest(envelope);
                default -> jsonRequest(content);
            };
        } catch (Exception e) {
            api.logging().logToError("[GraphQL Grip] Failed to build modified request: " + e.getMessage());
            showValidationError("Could not build request: " + e.getMessage());
            return currentRequest;
        }
    }

    private HttpRequest bodyCarryingRequest() {
        return currentRequest.withPath(
                PayloadDispatch.pathWithoutOperationParameters(currentRequest.path()));
    }

    private HttpRequest jsonRequest(String content) {
        return bodyCarryingRequest()
                .withMethod("POST")
                .withUpdatedHeader("Content-Type", "application/json")
                .withBody(content);
    }

    private HttpRequest urlRequest(JsonObject envelope) {
        return currentRequest
                .withMethod("GET")
                .withPath(PayloadDispatch.appendParameters(currentRequest.path(),
                        GraphQLPayloadWriter.parameters(envelope, source)))
                .withRemovedHeader("Content-Type")
                .withBody("");
    }

    private HttpRequest formRequest(JsonObject envelope) {
        return bodyCarryingRequest()
                .withMethod("POST")
                .withUpdatedHeader("Content-Type", "application/x-www-form-urlencoded")
                .withBody(GraphQLPayloadWriter.parameters(envelope, source));
    }

    private HttpRequest multipartRequest(JsonObject envelope) {
        String boundary = "----GripBoundary" + System.currentTimeMillis();
        return bodyCarryingRequest()
                .withMethod("POST")
                .withUpdatedHeader("Content-Type", "multipart/form-data; boundary=" + boundary)
                .withBody(GraphQLPayloadWriter.multipartBody(envelope, source, boundary));
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        if (requestResponse == null || requestResponse.request() == null) {
            currentRequest = null;
            originalQuery = null;
            originalRequestBody = null;
            source = GraphQLOperationSource.empty();
            setPayloadText("No request loaded");
            initialEditorContent = payloadText();
            return;
        }

        currentRequest = requestResponse.request();
        originalRequestBody = currentRequest.bodyToString();
        originalQuery = extractQueryFromRequest(currentRequest);
        capturedTransport = PayloadTransport.of(currentRequest.method(),
                contentTypeOf(currentRequest));
        currentTransport = capturedTransport;

        source = GraphQLOperationSource.from(currentRequest);

        renderCapturedRequest();
        initialEditorContent = payloadText();
    }

    private void renderCapturedRequest() {
        JsonObject captured = capturedEnvelope();
        if (captured != null) {
            setPayloadText(GraphQLPayloadWriter.json(captured));
            statusLabel.setText(loadedStatus());
            return;
        }

        if (originalQuery != null) {
            setPayloadText(GraphQLPayloadWriter.json(
                    GraphQLPayloadWriter.body(source, originalQuery)));
            statusLabel.setText(loadedStatus());
            return;
        }

        JsonObject defaultBody = new JsonObject();
        defaultBody.addProperty("query", "query { __typename }");
        setPayloadText(GraphQLPayloadWriter.json(defaultBody));
        statusLabel.setText("No query found - using default  |  " + currentTransport.description());
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        return requestResponse != null &&
               requestResponse.request() != null &&
               (repeaterContext || isGraphQLRequest(requestResponse.request()));
    }

    @Override
    public String caption() {
        return "GraphQL Grip";
    }

    @Override
    public Component uiComponent() {
        return mainPanel;
    }

    @Override
    public Selection selectedData() {
        return queryEditor == null ? null : queryEditor.selection().orElse(null);
    }

    @Override
    public boolean isModified() {
        return PayloadDispatch.isModified(capturedTransport, currentTransport,
                initialEditorContent, payloadText());
    }
}
