package com.grip.graphql.ui;

import com.grip.graphql.GripCore;
import com.grip.graphql.model.schema.*;
import com.grip.graphql.schema.IntrospectionHandler;
import com.grip.graphql.schema.SchemaReconstructor;
import com.grip.graphql.http.GripHttpClient;
import com.grip.graphql.security.GripEngineFingerprinter;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class GripMainTab extends JPanel {

    private final GripCore core;
    private final GripTheme theme;
    private final JTabbedPane tabbedPane;

    private JLabel statsLabel;

    private JTextField targetField;
    private JTextPane logPane;
    private javax.swing.text.StyledDocument logDoc;

    private JTabbedPane schemaTabbedPane;
    private List<SchemaTabData> schemaTabs = new ArrayList<>();
    private int schemaTabCounter = 0;

    private SchemaTabData currentSchemaTab;
    private GripSchema currentSchema;

    private DefaultTableModel headersTableModel;
    private Map<String, String> customHeaders = new HashMap<>();

    private JLabel statusBar;
    private javax.swing.Timer statusClearTimer;

    private JButton scanBtn;
    private JButton blindBtn;
    private JButton fingerprintBtn;
    private JButton discoverBtn;
    private JButton cancelBtn;
    private volatile boolean scanning = false;

    @SuppressWarnings("this-escape")
    public GripMainTab(GripCore core) {
        super(new BorderLayout());
        this.core = core;
        this.theme = core.getTheme();

        this.tabbedPane = new JTabbedPane();
        tabbedPane.setFont(theme.getBoldFont());

        tabbedPane.addTab("Scanner", null, createScannerPanel(), "Scan endpoints & run security checks");
        tabbedPane.addTab("Schema", null, createSchemaPanel(), "Browse schema & craft requests");

        core.getApi().userInterface().applyThemeToComponent(tabbedPane);

        add(createHeader(), BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, theme.getBorder()),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));

        statusBar = new JLabel(" ");
        statusBar.setFont(theme.getNormalFont());
        panel.add(statusBar, BorderLayout.CENTER);

        core.getApi().userInterface().applyThemeToComponent(panel);
        return panel;
    }

    private void showStatus(String message, boolean isError) {
        SwingUtilities.invokeLater(() -> {
            statusBar.setText(message);
            statusBar.setForeground(isError ? new Color(200, 50, 50) : GripTheme.Colors.ACCENT);

            if (statusClearTimer != null) {
                statusClearTimer.stop();
            }
            statusClearTimer = new javax.swing.Timer(5000, e -> {
                statusBar.setText(" ");
                statusBar.setForeground(theme.getForeground());
            });
            statusClearTimer.setRepeats(false);
            statusClearTimer.start();
        });
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, theme.getSeparator()),
            BorderFactory.createEmptyBorder(GripTheme.SPACING_MD, GripTheme.SPACING_LG, GripTheme.SPACING_MD, GripTheme.SPACING_LG)
        ));

        JLabel titleLabel = new JLabel("GraphQL Grip");
        titleLabel.setFont(theme.getFont(Font.BOLD, GripTheme.FONT_SIZE_TITLE));
        titleLabel.setForeground(GripTheme.Colors.ACCENT);

        statsLabel = new JLabel("GraphQL Security Testing");
        statsLabel.setFont(theme.getNormalFont());
        statsLabel.setForeground(theme.getSecondaryText());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, GripTheme.SPACING_MD, 0));
        left.setOpaque(false);
        left.add(titleLabel);
        left.add(new JLabel("  •  "));
        left.add(statsLabel);

        header.add(left, BorderLayout.WEST);
        core.getApi().userInterface().applyThemeToComponent(header);
        return header;
    }

    private JPanel createScannerPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel configPanel = new JPanel(new BorderLayout(10, 10));

        JPanel targetPanel = new JPanel(new BorderLayout(10, 0));
        targetPanel.setBorder(theme.createTitledBorder("Target Endpoint"));

        targetField = new JTextField();
        targetField.setFont(theme.getNormalFont());
        theme.styleTextField(targetField);
        addPlaceholder(targetField, "https://example.com/graphql");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, GripTheme.SPACING_SM, GripTheme.SPACING_SM));

        scanBtn = new JButton("Scan & Introspect");
        theme.stylePrimaryButton(scanBtn);
        scanBtn.addActionListener(e -> runFullScan());

        blindBtn = createSecondaryButton("Blind Discovery", "Reconstruct schema when introspection is disabled");
        blindBtn.addActionListener(e -> runBlindDiscovery());

        fingerprintBtn = createSecondaryButton("Fingerprint", "Identify GraphQL engine type");
        fingerprintBtn.addActionListener(e -> runEngineFingerprint());

        discoverBtn = createSecondaryButton("Discover Paths", "Find GraphQL endpoints on target domain");
        discoverBtn.addActionListener(e -> runEndpointDiscovery());

        cancelBtn = new JButton("Cancel");
        cancelBtn.setForeground(new Color(220, 53, 69));
        cancelBtn.setVisible(false);
        cancelBtn.addActionListener(e -> cancelScan());

        buttons.add(scanBtn);
        buttons.add(Box.createHorizontalStrut(GripTheme.SPACING_MD));
        buttons.add(blindBtn);
        buttons.add(fingerprintBtn);
        buttons.add(discoverBtn);
        buttons.add(cancelBtn);

        targetPanel.add(targetField, BorderLayout.CENTER);
        targetPanel.add(buttons, BorderLayout.SOUTH);

        JPanel headersPanel = new JPanel(new BorderLayout(5, 5));
        headersPanel.setBorder(theme.createTitledBorder("Custom Headers (applied to all requests)"));

        String[] headerColumns = {"Header Name", "Value"};
        headersTableModel = new DefaultTableModel(headerColumns, 0);

        headersTableModel.addRow(new Object[]{"Authorization", ""});
        headersTableModel.addRow(new Object[]{"X-Custom-Header", ""});

        JTable headersTable = new JTable(headersTableModel);
        headersTable.setFont(theme.getNormalFont());
        headersTable.setRowHeight(22);

        JPanel headerBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addHeader = new JButton("Add");
        JButton removeHeader = new JButton("Remove");
        JButton applyHeaders = new JButton("Apply Headers");

        addHeader.addActionListener(e -> headersTableModel.addRow(new Object[]{"", ""}));
        removeHeader.addActionListener(e -> {
            int row = headersTable.getSelectedRow();
            if (row >= 0) headersTableModel.removeRow(row);
        });
        applyHeaders.addActionListener(e -> applyCustomHeaders());

        headerBtns.add(addHeader);
        headerBtns.add(removeHeader);
        headerBtns.add(applyHeaders);

        JScrollPane headersScroll = new JScrollPane(headersTable);
        headersScroll.setPreferredSize(new Dimension(400, 90));
        headersPanel.add(headersScroll, BorderLayout.CENTER);
        headersPanel.add(headerBtns, BorderLayout.SOUTH);

        configPanel.add(targetPanel, BorderLayout.NORTH);
        configPanel.add(headersPanel, BorderLayout.CENTER);

        panel.add(configPanel, BorderLayout.NORTH);

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(theme.getCodeFont());
        logDoc = logPane.getStyledDocument();
        initLogStyles();

        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLogStyled("  GraphQL Grip Scanner", "title");
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLogStyled("", "normal");
        appendLogStyled("  Ready to scan. Enter a target endpoint above.", "info");
        appendLogStyled("", "normal");

        JScrollPane logScroll = new JScrollPane(logPane);
        logScroll.setBorder(theme.createTitledBorder("Scan Log"));
        logScroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(logScroll, BorderLayout.CENTER);

        core.getApi().userInterface().applyThemeToComponent(panel);
        return panel;
    }

    private void applyCustomHeaders() {
        customHeaders.clear();
        for (int i = 0; i < headersTableModel.getRowCount(); i++) {
            String name = (String) headersTableModel.getValueAt(i, 0);
            String value = (String) headersTableModel.getValueAt(i, 1);
            if (name != null && !name.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
                customHeaders.put(name.trim(), value.trim());
                core.getHttpClient().setDefaultHeader(name.trim(), value.trim());
            }
        }
        appendLog("[*] Applied " + customHeaders.size() + " custom headers");
    }

    private void clearLog() {
        SwingUtilities.invokeLater(() -> {
            try {
                logDoc.remove(0, logDoc.getLength());
            } catch (javax.swing.text.BadLocationException e) {

            }
        });
    }

    public void triggerScan() {
        scanning = false;
        setScanningState(false);
        runFullScan();
    }

    private void runFullScan() {
        if (scanning) {
            appendLog("[!] Scan already in progress");
            return;
        }
        String endpoint = targetField.getText().trim();
        if (endpoint.isEmpty()) {
            clearLog();
            appendLogStyled("✗ Error: Enter an endpoint URL", "error");
            return;
        }
        if (!isValidUrl(endpoint)) {
            clearLog();
            appendLogStyled("✗ Error: Invalid URL. Must start with http:// or https://", "error");
            return;
        }

        applyCustomHeaders();
        setScanningState(true);

        clearLog();
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLogStyled("  Scanning: " + endpoint, "title");
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLogStyled("", "normal");

        CompletableFuture.runAsync(() -> {
            try {
                appendLog("[*] Testing introspection...");

                IntrospectionHandler handler = new IntrospectionHandler(core.getHttpClient());

                HttpRequestResponse response = core.getHttpClient().sendQueryWithLog(
                        endpoint,
                        "query { __schema { queryType { name } } }",
                        "Introspection Test"
                );

                if (response == null || response.response() == null) {
                    appendLogStyled("[!] No response from server. Check endpoint URL and authentication.", "error");
                    return;
                }

                boolean hasIntrospection = response.response().bodyToString().contains("__schema");

                if (hasIntrospection) {
                    appendLog("[+] Introspection ENABLED - fetching full schema...");

                    GripSchema schema = handler.fetchSchema(endpoint).join();

                    if (schema != null) {
                        schema.setSourceEndpoint(endpoint);
                        currentSchema = schema;
                        appendLog("[+] Schema fetched: " + schema.getTypeCount() + " types, " +
                                schema.getQueries().size() + " queries, " +
                                schema.getMutations().size() + " mutations");

                        SwingUtilities.invokeLater(() -> {
                            getOrCreateSchemaTab();
                            currentSchemaTab.endpoint = endpoint;
                            if (currentSchemaTab.endpointField != null) {
                                currentSchemaTab.endpointField.setText(endpoint);
                            }
                            populateSchemaTree(schema);
                            tabbedPane.setSelectedIndex(1);
                        });

                        appendLog("[*] Schema loaded - check Schema tab to browse and craft requests");
                    }
                } else {
                    appendLog("[-] Introspection DISABLED");
                    appendLog("[*] Try 'Blind Discovery' to reconstruct schema");
                }

            } catch (Exception e) {
                Throwable cause = e;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (cause instanceof GripHttpClient.GripAuthException) {
                    appendLogStyled("[!] AUTHENTICATION ERROR: " + cause.getMessage(), "error");
                    appendLog("[*] Configure auth headers in 'Custom Headers' section above, then retry.");
                } else {
                    appendLog("[!] Error: " + cause.getMessage());
                }
                core.logError("runFullScan failed: " + cause.getMessage());
            } finally {
                setScanningState(false);
            }
        }, core.getHttpClient().getExecutor());
    }

    private void runBlindDiscovery() {
        if (scanning) {
            appendLog("[!] Scan already in progress");
            return;
        }
        String endpoint = targetField.getText().trim();
        if (endpoint.isEmpty()) {
            clearLog();
            appendLogStyled("✗ Error: Enter an endpoint URL", "error");
            return;
        }
        if (!isValidUrl(endpoint)) {
            clearLog();
            appendLogStyled("✗ Error: Invalid URL. Must start with http:// or https://", "error");
            return;
        }

        applyCustomHeaders();

        appendLogStyled("", "normal");
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLogStyled("  Blind Schema Discovery", "title");
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLog("[*] This may take a while...");
        setScanningState(true);

        CompletableFuture.runAsync(() -> {
            SchemaReconstructor reconstructor = null;
            try {
                reconstructor = new SchemaReconstructor(core.getHttpClient());
                reconstructor.setProgressCallback(msg -> appendLog(msg));

                GripSchema schema = reconstructor.reconstructSchema(endpoint).join();

                if (schema != null && schema.getTypeCount() > 0) {
                    schema.setSourceEndpoint(endpoint);
                    currentSchema = schema;
                    appendLog("\n[+] Discovered " + schema.getTypeCount() + " types");

                    SwingUtilities.invokeLater(() -> {
                        getOrCreateSchemaTab();
                        currentSchemaTab.endpoint = endpoint;
                        if (currentSchemaTab.endpointField != null) {
                            currentSchemaTab.endpointField.setText(endpoint);
                        }
                        populateSchemaTree(schema);
                        tabbedPane.setSelectedIndex(1);
                    });
                }

            } catch (Exception e) {
                Throwable cause = e;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (cause instanceof GripHttpClient.GripAuthException) {
                    appendLogStyled("[!] AUTHENTICATION ERROR: " + cause.getMessage(), "error");
                    appendLog("[*] Configure auth headers in 'Custom Headers' section above, then retry.");
                } else {
                    appendLog("[!] Error: " + cause.getMessage());
                }
                core.logError("runBlindDiscovery failed: " + cause.getMessage());
            } finally {
                if (reconstructor != null) {
                    reconstructor.shutdown();
                }
                setScanningState(false);
            }
        }, core.getHttpClient().getExecutor());
    }

    private void runEngineFingerprint() {
        String endpoint = targetField.getText().trim();
        if (endpoint.isEmpty()) {
            clearLog();
            appendLogStyled("✗ Error: Enter an endpoint URL", "error");
            return;
        }
        if (!isValidUrl(endpoint)) {
            clearLog();
            appendLogStyled("✗ Error: Invalid URL. Must start with http:// or https://", "error");
            return;
        }

        applyCustomHeaders();

        appendLogStyled("", "normal");
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLogStyled("  Engine Fingerprinting", "title");
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");

        CompletableFuture.runAsync(() -> {
            try {
                GripEngineFingerprinter fingerprinter = new GripEngineFingerprinter(core.getHttpClient());
                fingerprinter.setProgressCallback(msg -> appendLog(msg));

                GripEngineFingerprinter.EngineResult result = fingerprinter.fingerprint(endpoint).join();

                appendLogStyled("", "normal");
                appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
                appendLog("[+] ENGINE: " + result.engineName);
                appendLog("[+] Confidence: " + result.confidence);
                appendLog("[+] Evidence: " + result.evidence);
                appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
                appendLog("");
                appendLog("[!] " + GripEngineFingerprinter.DETECTION_DISCLAIMER);
                appendLogStyled("", "normal");

            } catch (Exception e) {
                Throwable cause = e;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (cause instanceof GripHttpClient.GripAuthException) {
                    appendLogStyled("[!] AUTHENTICATION ERROR: " + cause.getMessage(), "error");
                } else {
                    appendLog("[!] Error: " + cause.getMessage());
                }
                core.logError("runEngineFingerprint failed: " + cause.getMessage());
            }
        }, core.getHttpClient().getExecutor());
    }

    private static final String[] GRAPHQL_PATHS = {
        "graphql", "graphiql", "playground", "altair", "explorer",
        "graphql/console", "graphql-explorer", "subscriptions", "api/graphql", "graph",
        "graphiql.css", "graphiql/finland", "graphiql.js", "graphiql.min.css",
        "graphiql.min.js", "graphiql.php", "graphql.php",
        "graphql/schema.json", "graphql/schema.xml", "graphql/schema.yaml",
        "v1/graphql", "v1/graphiql", "v1/playground", "v1/altair", "v1/explorer",
        "v1/graphql/console", "v1/graphql-explorer", "v1/api/graphql", "v1/graph",
        "v2/graphql", "v2/graphiql", "v2/playground", "v2/altair", "v2/explorer",
        "v2/graphql/console", "v2/graphql-explorer", "v2/api/graphql", "v2/graph",
        "v3/graphql", "v3/graphiql", "v3/playground", "v3/api/graphql",
        "v4/graphql", "v4/graphiql", "v4/playground", "v4/api/graphql",
        "api/v1/graphql", "api/v2/graphql", "api/v3/graphql",
        "query", "gql", "data", "api/data", "api/query",
        "console", "dev/graphql", "test/graphql", "staging/graphql",
        "__graphql", "_graphql", "graphql-api", "graphql/v1", "graphql/v2"
    };

    private void runEndpointDiscovery() {
        String baseUrl = targetField.getText().trim();
        if (baseUrl.isEmpty()) {
            clearLog();
            appendLogStyled("✗ Error: Enter a base URL (e.g., https://example.com/)", "error");
            return;
        }
        if (!isValidUrl(baseUrl)) {
            clearLog();
            appendLogStyled("✗ Error: Invalid URL. Must start with http:// or https://", "error");
            return;
        }

        applyCustomHeaders();

        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        try {
            java.net.URI uri = new java.net.URI(baseUrl);
            baseUrl = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() > 0 && uri.getPort() != 80 && uri.getPort() != 443) {
                baseUrl += ":" + uri.getPort();
            }
            baseUrl += "/";
        } catch (Exception e) {

        }

        final String finalBaseUrl = baseUrl;
        clearLog();
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLogStyled("  GraphQL Endpoint Discovery", "title");
        appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
        appendLogStyled("", "normal");
        appendLog("[*] Base URL: " + finalBaseUrl);
        appendLog("[*] Testing " + GRAPHQL_PATHS.length + " common paths...");
        appendLogStyled("", "normal");

        CompletableFuture.runAsync(() -> {
            try {
            java.util.List<String> foundEndpoints = new java.util.ArrayList<>();
            int tested = 0;

            for (String path : GRAPHQL_PATHS) {
                tested++;
                String testUrl = finalBaseUrl + path;

                try {

                    JsonObject queryBody = new JsonObject();
                    queryBody.addProperty("query", "query{__typename}");

                    Map<String, String> headers = new HashMap<>(customHeaders);
                    headers.put("Content-Type", "application/json");

                    HttpRequestResponse response = core.getHttpClient().sendRaw(
                        testUrl, "POST", queryBody.toString(), headers
                    );

                    int status = response.response().statusCode();
                    String body = response.response().bodyToString();

                    boolean isGraphQL = false;
                    String evidence = "";

                    if (body.contains("\"data\"") || body.contains("\"errors\"")) {
                        isGraphQL = true;
                        evidence = "GraphQL response structure";
                    } else if (body.contains("__typename") || body.contains("__schema")) {
                        isGraphQL = true;
                        evidence = "Introspection response";
                    } else if (body.contains("Must provide query string") ||
                               body.contains("GraphQL") ||
                               body.contains("query must be a string")) {
                        isGraphQL = true;
                        evidence = "GraphQL error message";
                    } else if (status == 200 && body.contains("{") &&
                               (body.contains("query") || body.contains("mutation"))) {
                        isGraphQL = true;
                        evidence = "Possible GraphQL (200 + JSON)";
                    }

                    if (isGraphQL) {
                        foundEndpoints.add(testUrl);
                        appendLog("[+] FOUND: " + testUrl + " (" + evidence + ")");
                    }

                    if (status == 200 && (body.contains("GraphiQL") || body.contains("graphql-playground") ||
                            body.contains("Apollo") || body.contains("Altair"))) {
                        if (!foundEndpoints.contains(testUrl)) {
                            foundEndpoints.add(testUrl);
                            appendLog("[+] FOUND UI: " + testUrl + " (GraphQL IDE detected)");
                        }
                    }

                } catch (Exception e) {

                }

                if (tested % 10 == 0) {
                    final int t = tested;
                    SwingUtilities.invokeLater(() -> {
                        showStatus("Scanning... " + t + "/" + GRAPHQL_PATHS.length, false);
                    });
                }
            }

            appendLogStyled("", "normal");
            appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");
            if (foundEndpoints.isEmpty()) {
                appendLog("[-] No GraphQL endpoints discovered");
                appendLog("[*] Try manual testing or check for non-standard paths");
            } else {
                appendLog("[+] Found " + foundEndpoints.size() + " potential endpoint(s):");
                for (String ep : foundEndpoints) {
                    appendLogStyled("    → " + ep, "success");
                }
                appendLogStyled("", "normal");
                appendLog("[*] Click on an endpoint above and paste into Target field to scan");
            }
            appendLogStyled("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "header");

            SwingUtilities.invokeLater(() -> {
                showStatus("Discovery complete: " + foundEndpoints.size() + " endpoint(s) found", false);
            });
            } catch (Exception e) {
                core.logError("runEndpointDiscovery failed: " + e.getMessage());
                appendLog("[!] Error: " + e.getMessage());
            }
        }, core.getHttpClient().getExecutor());
    }

    private void initLogStyles() {
        javax.swing.text.Style def = javax.swing.text.StyleContext.getDefaultStyleContext()
                .getStyle(javax.swing.text.StyleContext.DEFAULT_STYLE);

        javax.swing.text.Style normal = logDoc.addStyle("normal", def);
        javax.swing.text.StyleConstants.setFontFamily(normal, theme.getCodeFont().getFamily());
        javax.swing.text.StyleConstants.setFontSize(normal, 12);

        javax.swing.text.Style header = logDoc.addStyle("header", normal);
        javax.swing.text.StyleConstants.setForeground(header, GripTheme.Colors.ACCENT);

        javax.swing.text.Style title = logDoc.addStyle("title", normal);
        javax.swing.text.StyleConstants.setForeground(title, GripTheme.Colors.ACCENT);
        javax.swing.text.StyleConstants.setBold(title, true);
        javax.swing.text.StyleConstants.setFontSize(title, 14);

        javax.swing.text.Style success = logDoc.addStyle("success", normal);
        javax.swing.text.StyleConstants.setForeground(success, new Color(46, 160, 67));
        javax.swing.text.StyleConstants.setBold(success, true);

        javax.swing.text.Style error = logDoc.addStyle("error", normal);
        javax.swing.text.StyleConstants.setForeground(error, new Color(220, 53, 69));
        javax.swing.text.StyleConstants.setBold(error, true);

        javax.swing.text.Style warning = logDoc.addStyle("warning", normal);
        javax.swing.text.StyleConstants.setForeground(warning, new Color(255, 193, 7));

        javax.swing.text.Style info = logDoc.addStyle("info", normal);
        javax.swing.text.StyleConstants.setForeground(info, new Color(13, 110, 253));

        javax.swing.text.Style separator = logDoc.addStyle("separator", normal);
        javax.swing.text.StyleConstants.setForeground(separator, new Color(108, 117, 125));
    }

    private void appendLogStyled(String message, String styleName) {
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.text.Style style = logDoc.getStyle(styleName);
                if (style == null) style = logDoc.getStyle("normal");
                logDoc.insertString(logDoc.getLength(), message + "\n", style);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (javax.swing.text.BadLocationException e) {

            }
        });
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            try {

                String timestamp = "[" + LocalTime.now().format(TIME_FORMAT) + "] ";

                String styleName = "normal";
                if (message.startsWith("[+]")) {
                    styleName = "success";
                } else if (message.startsWith("[-]") || message.startsWith("[!]")) {
                    styleName = "error";
                } else if (message.startsWith("[*]")) {
                    styleName = "info";
                } else if (message.startsWith("===") || message.contains("====")) {
                    styleName = "header";
                    timestamp = "";
                }

                javax.swing.text.Style style = logDoc.getStyle(styleName);
                logDoc.insertString(logDoc.getLength(), timestamp + message + "\n", style);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (javax.swing.text.BadLocationException e) {

            }
        });
    }

    private JPanel createSchemaPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JButton newTabBtn = new JButton("+ New Schema Tab");
        newTabBtn.addActionListener(e -> createNewSchemaTab());
        toolbar.add(newTabBtn);

        JButton importBtn = new JButton("Import Schema");
        importBtn.addActionListener(e -> importSchemaFromFile());
        toolbar.add(importBtn);

        JButton exportBtn = new JButton("Export Schema (SDL)");
        exportBtn.addActionListener(e -> exportSchemaToFile());
        toolbar.add(exportBtn);

        JButton refreshBtn = new JButton("Refresh from Endpoint");
        refreshBtn.addActionListener(e -> {
            if (currentSchemaTab != null && !currentSchemaTab.endpoint.isEmpty()) {
                runIntrospection(currentSchemaTab.endpoint);
            } else {
                String endpoint = targetField.getText().trim();
                if (!endpoint.isEmpty()) {
                    runIntrospection(endpoint);
                } else {
                    showStatus("Enter endpoint in Scanner tab first or set schema endpoint", true);
                }
            }
        });
        toolbar.add(refreshBtn);

        panel.add(toolbar, BorderLayout.NORTH);

        schemaTabbedPane = new JTabbedPane();
        schemaTabbedPane.setFont(theme.getNormalFont());

        schemaTabbedPane.addChangeListener(e -> {
            int idx = schemaTabbedPane.getSelectedIndex();
            if (idx >= 0 && idx < schemaTabs.size()) {
                currentSchemaTab = schemaTabs.get(idx);
                currentSchema = currentSchemaTab.schema;
            }
        });

        createNewSchemaTab();

        panel.add(schemaTabbedPane, BorderLayout.CENTER);

        core.getApi().userInterface().applyThemeToComponent(panel);
        return panel;
    }

    private void createNewSchemaTab() {
        schemaTabCounter++;
        String tabName = "Schema " + schemaTabCounter;
        SchemaTabData tabData = new SchemaTabData();
        schemaTabs.add(tabData);

        JTabbedPane viewTabs = new JTabbedPane(JTabbedPane.BOTTOM);
        viewTabs.setFont(theme.getNormalFont());

        JPanel treePanel = createSchemaTreePanel(tabData);
        viewTabs.addTab("Tree View", treePanel);

        SchemaGraphPanel graphPanel = new SchemaGraphPanel(core);
        tabData.graphPanel = graphPanel;
        viewTabs.addTab("Graph View", graphPanel);

        schemaTabbedPane.addTab(tabName, viewTabs);
        int tabIndex = schemaTabbedPane.getTabCount() - 1;

        JPanel tabComponent = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabComponent.setOpaque(false);

        JLabel tabLabel = new JLabel(tabName + " ");
        tabData.tabLabel = tabLabel;
        tabComponent.add(tabLabel);

        JButton closeBtn = new JButton("x");
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setFont(closeBtn.getFont().deriveFont(10f));
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusable(false);
        closeBtn.addActionListener(e -> closeSchemaTab(tabData));
        tabComponent.add(closeBtn);

        schemaTabbedPane.setTabComponentAt(tabIndex, tabComponent);
        schemaTabbedPane.setSelectedIndex(tabIndex);

        currentSchemaTab = tabData;
        currentSchema = tabData.schema;
    }

    private void getOrCreateSchemaTab() {

        if (currentSchemaTab != null && currentSchemaTab.schema == null) {
            return;
        }

        createNewSchemaTab();
    }

    private JPanel createSchemaTreePanel(SchemaTabData tabData) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel endpointPanel = new JPanel(new BorderLayout(5, 0));
        endpointPanel.setBorder(theme.createTitledBorder("Schema Endpoint"));

        JTextField endpointField = new JTextField(tabData.endpoint);
        endpointField.setFont(theme.getNormalFont());
        tabData.endpointField = endpointField;

        endpointField.addActionListener(e -> {
            tabData.endpoint = endpointField.getText().trim();
            if (tabData.graphPanel != null) {
                tabData.graphPanel.setEndpoint(tabData.endpoint);
            }
        });

        JButton setEndpointBtn = new JButton("Set Endpoint");
        setEndpointBtn.addActionListener(e -> {
            tabData.endpoint = endpointField.getText().trim();
            if (tabData.graphPanel != null) {
                tabData.graphPanel.setEndpoint(tabData.endpoint);
            }
            showStatus("Endpoint set: " + tabData.endpoint, false);
        });

        endpointPanel.add(endpointField, BorderLayout.CENTER);
        endpointPanel.add(setEndpointBtn, BorderLayout.EAST);

        JPanel headersPanel = new JPanel(new BorderLayout(5, 5));
        headersPanel.setBorder(theme.createTitledBorder("Request Headers"));

        String[] headerColumns = {"Header Name", "Value"};
        tabData.headersTableModel = new DefaultTableModel(headerColumns, 0);
        tabData.headersTableModel.addRow(new Object[]{"Content-Type", "application/json"});
        tabData.headersTableModel.addRow(new Object[]{"Authorization", ""});

        JTable headersTable = new JTable(tabData.headersTableModel);
        headersTable.setFont(theme.getNormalFont());
        headersTable.setRowHeight(22);
        headersTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        headersTable.getColumnModel().getColumn(1).setPreferredWidth(300);

        JScrollPane headersScroll = new JScrollPane(headersTable);
        headersScroll.setPreferredSize(new Dimension(500, 80));
        headersScroll.setMinimumSize(new Dimension(300, 70));

        JPanel headerBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton addHeaderBtn = new JButton("+");
        JButton removeHeaderBtn = new JButton("-");
        addHeaderBtn.setMargin(new java.awt.Insets(2, 8, 2, 8));
        removeHeaderBtn.setMargin(new java.awt.Insets(2, 8, 2, 8));

        addHeaderBtn.addActionListener(e -> tabData.headersTableModel.addRow(new Object[]{"", ""}));
        removeHeaderBtn.addActionListener(e -> {
            int row = headersTable.getSelectedRow();
            if (row >= 0) tabData.headersTableModel.removeRow(row);
        });

        headerBtns.add(addHeaderBtn);
        headerBtns.add(removeHeaderBtn);

        headersPanel.add(headersScroll, BorderLayout.CENTER);
        headersPanel.add(headerBtns, BorderLayout.EAST);
        headersPanel.setMinimumSize(new Dimension(300, 100));
        headersPanel.setPreferredSize(new Dimension(500, 100));

        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        JLabel searchLabel = new JLabel("Search: ");
        searchLabel.setFont(theme.getNormalFont());

        JTextField searchField = new JTextField();
        searchField.setFont(theme.getNormalFont());
        addPlaceholder(searchField, "Filter fields...");

        JButton clearSearchBtn = new JButton("Clear");
        clearSearchBtn.addActionListener(e -> {
            searchField.setText("");
            searchField.setForeground(Color.GRAY);
            searchField.setText("Filter fields...");
            filterSchemaTree(tabData, "");
        });

        searchField.addActionListener(e -> {
            String text = searchField.getText().trim();
            if (text.equals("Filter fields...") || text.isEmpty()) {
                if (tabData.filtered) {
                    tabData.filtered = false;
                    populateSchemaTreeForTab(tabData.schema, tabData);
                }
                return;
            }
            tabData.filtered = true;
            filterSchemaTree(tabData, text);
        });

        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(clearSearchBtn, BorderLayout.EAST);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(endpointPanel);
        topPanel.add(headersPanel);
        topPanel.add(searchPanel);

        panel.add(topPanel, BorderLayout.NORTH);

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Schema (not loaded)");
        DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
        JTree tree = new JTree(treeModel);
        tree.setFont(theme.getNormalFont());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        tabData.rootNode = rootNode;
        tabData.treeModel = treeModel;
        tabData.tree = tree;

        tree.addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
            @Override
            public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (node.getChildCount() == 1) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getFirstChild();
                    if ("Loading...".equals(child.getUserObject())) {
                        asyncLoadChildren(node, tabData);
                    }
                }
            }

            @Override
            public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {}
        });

        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObj = node.getUserObject();
                if (userObj instanceof SchemaTreeNode stn) {
                    setText(stn.getDisplayName());
                    if (stn.isQuery()) {
                        setForeground(new Color(0, 128, 0));
                    } else if (stn.isMutation()) {
                        setForeground(new Color(200, 100, 0));
                    } else if (stn.isSubscription()) {
                        setForeground(new Color(128, 0, 128));
                    }
                }
                return this;
            }
        });

        JTextArea queryArea = new JTextArea();
        queryArea.setFont(theme.getCodeFont());
        queryArea.setText("Select a Query or Mutation from the tree to generate a request.\n\n" +
                "Right-click on any field to:\n" +
                "  - Send to Repeater\n" +
                "  - Execute Query\n" +
                "  - Copy Query");
        tabData.queryPreviewArea = queryArea;

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node == null) return;
            Object userObj = node.getUserObject();
            if (userObj instanceof SchemaTreeNode stn) {
                String query = stn.generateQuery();
                if (query != null) {
                    queryArea.setText(query);
                    queryArea.setCaretPosition(0);
                }
            } else if (userObj instanceof TypeTreeNode ttn) {
                queryArea.setText(ttn.getDescription());
                queryArea.setCaretPosition(0);
            }
        });

        JPopupMenu treePopup = new JPopupMenu();
        JMenuItem sendToRepeater = new JMenuItem("Send to Repeater");
        JMenuItem sendToIntruder = new JMenuItem("Send to Intruder");
        JMenuItem copyQuery = new JMenuItem("Copy Query");
        JMenuItem executeQuery = new JMenuItem("Execute Query");

        sendToRepeater.addActionListener(e -> sendSelectedQueryToRepeater());
        sendToIntruder.addActionListener(e -> sendSelectedQueryToIntruder());
        copyQuery.addActionListener(e -> copySelectedQuery());
        executeQuery.addActionListener(e -> executeSelectedQuery());

        treePopup.add(sendToRepeater);
        treePopup.add(sendToIntruder);
        treePopup.add(executeQuery);
        treePopup.addSeparator();
        treePopup.add(copyQuery);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { showPopup(e); }
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        tree.setSelectionPath(path);
                        treePopup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(theme.createTitledBorder("Schema Browser - Click to craft request, Right-click to Send to Repeater"));

        JScrollPane queryScroll = new JScrollPane(queryArea);
        queryScroll.setBorder(theme.createTitledBorder("Generated GraphQL Query (editable)"));

        JPanel queryButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton sendBtn = new JButton("Send to Repeater");
        JButton intruderBtn = new JButton("Send to Intruder");
        JButton execBtn = new JButton("Execute & View Response");
        JButton copyBtn = new JButton("Copy Query");

        sendBtn.addActionListener(e -> sendSelectedQueryToRepeater());
        intruderBtn.addActionListener(e -> sendSelectedQueryToIntruder());
        execBtn.addActionListener(e -> executeSelectedQuery());
        copyBtn.addActionListener(e -> copySelectedQuery());

        queryButtons.add(sendBtn);
        queryButtons.add(intruderBtn);
        queryButtons.add(execBtn);
        queryButtons.add(copyBtn);

        JPanel queryPanel = new JPanel(new BorderLayout());
        queryPanel.add(queryScroll, BorderLayout.CENTER);
        queryPanel.add(queryButtons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, queryPanel);
        split.setResizeWeight(0.4);

        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private void closeSchemaTab(SchemaTabData tabData) {
        int idx = schemaTabs.indexOf(tabData);
        if (idx >= 0 && schemaTabs.size() > 1) {
            schemaTabs.remove(idx);
            schemaTabbedPane.removeTabAt(idx);

            if (idx >= schemaTabs.size()) idx = schemaTabs.size() - 1;
            if (idx >= 0) {
                schemaTabbedPane.setSelectedIndex(idx);
            }
        } else if (schemaTabs.size() == 1) {
            showStatus("Cannot close the last schema tab", false);
        }
    }

    private void updateSchemaTabName(SchemaTabData tabData, String name) {
        if (tabData.tabLabel != null) {
            tabData.tabLabel.setText(name + " ");
        }
    }

    private void populateSchemaTree(GripSchema schema) {
        populateSchemaTreeForTab(schema, currentSchemaTab);
    }

    private void populateSchemaTreeForTab(GripSchema schema, SchemaTabData tabData) {
        if (tabData == null) return;

        tabData.schema = schema;
        tabData.rootNode.removeAllChildren();
        tabData.rootNode.setUserObject("Schema: " + schema.getSourceEndpoint());

        DefaultMutableTreeNode queriesNode = new DefaultMutableTreeNode(
                "Queries (" + schema.getQueries().size() + ")");
        if (!schema.getQueries().isEmpty()) {
            queriesNode.add(new DefaultMutableTreeNode("Loading..."));
        }
        tabData.rootNode.add(queriesNode);

        DefaultMutableTreeNode mutationsNode = new DefaultMutableTreeNode(
                "Mutations (" + schema.getMutations().size() + ")");
        if (!schema.getMutations().isEmpty()) {
            mutationsNode.add(new DefaultMutableTreeNode("Loading..."));
        }
        tabData.rootNode.add(mutationsNode);

        DefaultMutableTreeNode subsNode = new DefaultMutableTreeNode(
                "Subscriptions (" + schema.getSubscriptions().size() + ")");
        if (!schema.getSubscriptions().isEmpty()) {
            subsNode.add(new DefaultMutableTreeNode("Loading..."));
        }
        tabData.rootNode.add(subsNode);

        int totalTypes = (int) schema.getAllTypes().stream()
                .filter(t -> !t.getName().startsWith("__")).count();
        DefaultMutableTreeNode typesNode = new DefaultMutableTreeNode("Types (" + totalTypes + ")");
        if (totalTypes > 0) {
            typesNode.add(new DefaultMutableTreeNode("Loading..."));
        }
        tabData.rootNode.add(typesNode);

        tabData.treeModel.reload();

        populateHeadersFromClient(tabData);

        if (tabData.graphPanel != null) {
            tabData.graphPanel.setSchema(schema, tabData.endpoint);
        }

        String shortName = schema.getSourceEndpoint();
        if (shortName != null && shortName.length() > 30) {
            shortName = "..." + shortName.substring(shortName.length() - 27);
        }
        updateSchemaTabName(tabData, shortName != null ? shortName : "Schema " + (schemaTabs.indexOf(tabData) + 1));

        if (tabData == currentSchemaTab) {
            currentSchema = schema;
        }
    }

    private void populateHeadersFromClient(SchemaTabData tabData) {
        if (tabData.headersTableModel == null) return;
        Map<String, String> inherited = core.getHttpClient().getInheritedHeaders();
        if (inherited.isEmpty()) return;
        tabData.headersTableModel.setRowCount(0);
        tabData.headersTableModel.addRow(new Object[]{"Content-Type", "application/json"});
        for (Map.Entry<String, String> entry : inherited.entrySet()) {
            if ("Content-Type".equalsIgnoreCase(entry.getKey())) continue;
            tabData.headersTableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
    }

    private static final int PAGE_SIZE = 50;

    private void asyncLoadChildren(DefaultMutableTreeNode node, SchemaTabData tabData) {
        if (tabData.schema == null) return;
        String label = node.getUserObject().toString();
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
        String parentLabel = parentNode != null ? parentNode.getUserObject().toString() : "";
        GripSchema schema = tabData.schema;

        new javax.swing.SwingWorker<java.util.List<DefaultMutableTreeNode>, Void>() {
            @Override
            protected java.util.List<DefaultMutableTreeNode> doInBackground() {
                return buildChildNodes(label, parentLabel, schema);
            }

            @Override
            protected void done() {
                try {
                    java.util.List<DefaultMutableTreeNode> children = get();
                    node.removeAllChildren();
                    for (DefaultMutableTreeNode child : children) {
                        node.add(child);
                    }
                    tabData.treeModel.nodeStructureChanged(node);
                    tabData.tree.expandPath(new TreePath(node.getPath()));
                } catch (Exception e) {
                    node.removeAllChildren();
                    node.add(new DefaultMutableTreeNode("Error: " + e.getMessage()));
                    tabData.treeModel.nodeStructureChanged(node);
                }
            }
        }.execute();
    }

    private java.util.List<DefaultMutableTreeNode> buildChildNodes(String label, String parentLabel, GripSchema schema) {
        java.util.List<DefaultMutableTreeNode> children = new ArrayList<>();

        if (label.startsWith("Queries")) {
            createPages(children, schema.getQueries().size());
        } else if (label.startsWith("Mutations")) {
            createPages(children, schema.getMutations().size());
        } else if (label.startsWith("Subscriptions")) {
            createPages(children, schema.getSubscriptions().size());
        } else if (label.startsWith("Types (")) {
            int total = (int) schema.getAllTypes().stream()
                    .filter(t -> !t.getName().startsWith("__")).count();
            createPages(children, total);
        } else if (label.matches("\\d+ - \\d+")) {
            int dash = label.indexOf(" - ");
            int start = Integer.parseInt(label.substring(0, dash)) - 1;
            int end = Integer.parseInt(label.substring(dash + 3));
            loadPage(children, parentLabel, start, end, schema);
        } else {
            String typeName = label.contains("(") ? label.substring(0, label.indexOf(" (")).trim() : label;
            GripType type = schema.getType(typeName);
            if (type != null) {
                for (GripField field : type.getFields()) {
                    String ts = field.getType() != null ? field.getType().toGraphQLString() : "Unknown";
                    children.add(new DefaultMutableTreeNode(field.getName() + ": " + ts));
                }
                for (GripField field : type.getInputFields()) {
                    String ts = field.getType() != null ? field.getType().toGraphQLString() : "Unknown";
                    children.add(new DefaultMutableTreeNode(field.getName() + ": " + ts));
                }
                for (GripEnumValue ev : type.getEnumValues()) {
                    children.add(new DefaultMutableTreeNode(ev.getName()));
                }
            }
        }

        return children;
    }

    private void createPages(java.util.List<DefaultMutableTreeNode> children, int total) {
        for (int i = 0; i < total; i += PAGE_SIZE) {
            int end = Math.min(i + PAGE_SIZE, total);
            DefaultMutableTreeNode page = new DefaultMutableTreeNode((i + 1) + " - " + end);
            page.add(new DefaultMutableTreeNode("Loading..."));
            children.add(page);
        }
    }

    private void loadPage(java.util.List<DefaultMutableTreeNode> children,
            String parentLabel, int start, int end, GripSchema schema) {
        if (parentLabel.startsWith("Queries")) {
            java.util.List<GripField> items = schema.getQueries();
            for (int i = start; i < Math.min(end, items.size()); i++) {
                children.add(new DefaultMutableTreeNode(new SchemaTreeNode(items.get(i), "Query", schema)));
            }
        } else if (parentLabel.startsWith("Mutations")) {
            java.util.List<GripField> items = schema.getMutations();
            for (int i = start; i < Math.min(end, items.size()); i++) {
                children.add(new DefaultMutableTreeNode(new SchemaTreeNode(items.get(i), "Mutation", schema)));
            }
        } else if (parentLabel.startsWith("Subscriptions")) {
            java.util.List<GripField> items = schema.getSubscriptions();
            for (int i = start; i < Math.min(end, items.size()); i++) {
                children.add(new DefaultMutableTreeNode(new SchemaTreeNode(items.get(i), "Subscription", schema)));
            }
        } else if (parentLabel.startsWith("Types")) {
            java.util.List<GripType> allTypes = new ArrayList<>();
            for (GripType type : schema.getAllTypes()) {
                if (!type.getName().startsWith("__")) allTypes.add(type);
            }
            for (int i = start; i < Math.min(end, allTypes.size()); i++) {
                GripType type = allTypes.get(i);
                DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(
                        new TypeTreeNode(type.getName(), type.getKind().toString(), schema));
                typeNode.add(new DefaultMutableTreeNode("Loading..."));
                children.add(typeNode);
            }
        }
    }

    private void addArgumentNodes(DefaultMutableTreeNode parent, GripField field, GripSchema schema) {
        if (!field.getArguments().isEmpty()) {
            DefaultMutableTreeNode argsNode = new DefaultMutableTreeNode("Arguments");
            for (GripArgument arg : field.getArguments()) {
                String argType = arg.getType() != null ? arg.getType().toGraphQLString() : "Unknown";
                argsNode.add(new DefaultMutableTreeNode(arg.getName() + ": " + argType));
            }
            parent.add(argsNode);
        }
    }

    private void filterSchemaTree(SchemaTabData tabData, String filter) {
        if (tabData.schema == null) return;

        String lowerFilter = filter.toLowerCase().trim();

        if (lowerFilter.isEmpty()) {
            populateSchemaTreeForTab(tabData.schema, tabData);
            return;
        }

        tabData.rootNode.removeAllChildren();
        tabData.rootNode.setUserObject("Schema (filtered: \"" + filter + "\")");

        GripSchema schema = tabData.schema;

        int queryCount = filterOperationFields(tabData.rootNode, "Queries", "Query", schema.getQueries(), lowerFilter, schema);
        int mutationCount = filterOperationFields(tabData.rootNode, "Mutations", "Mutation", schema.getMutations(), lowerFilter, schema);
        int subCount = filterOperationFields(tabData.rootNode, "Subscriptions", "Subscription", schema.getSubscriptions(), lowerFilter, schema);

        DefaultMutableTreeNode typesNode = new DefaultMutableTreeNode("Types");
        int typeCount = 0;
        for (GripType type : schema.getAllTypes()) {
            if (type.getName().startsWith("__")) continue;
            if (type.getName().toLowerCase().contains(lowerFilter)) {
                DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type.getName() + " (" + type.getKind() + ")");
                for (GripField field : type.getFields()) {
                    String fts = field.getType() != null ? field.getType().toGraphQLString() : "Unknown";
                    typeNode.add(new DefaultMutableTreeNode(field.getName() + ": " + fts));
                }
                typesNode.add(typeNode);
                typeCount++;
            }
        }
        if (typeCount > 0) {
            typesNode.setUserObject("Types (" + typeCount + " matches)");
            tabData.rootNode.add(typesNode);
        }

        tabData.treeModel.reload();

        for (int i = 0; i < tabData.rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) tabData.rootNode.getChildAt(i);
            tabData.tree.expandPath(new TreePath(child.getPath()));
        }

        int total = queryCount + mutationCount + subCount + typeCount;
        showStatus("Found " + total + " matches for \"" + filter + "\"", false);
    }

    private int filterOperationFields(DefaultMutableTreeNode root, String categoryName, String opType,
            java.util.List<GripField> fields, String lowerFilter, GripSchema schema) {
        DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(categoryName);
        int count = 0;
        for (GripField field : fields) {
            if (matchesFilter(field, lowerFilter)) {
                SchemaTreeNode stn = new SchemaTreeNode(field, opType, schema);
                DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(stn);
                addArgumentNodes(fieldNode, field, schema);
                categoryNode.add(fieldNode);
                count++;
            }
        }
        if (count > 0) {
            categoryNode.setUserObject(categoryName + " (" + count + " matches)");
            root.add(categoryNode);
        }
        return count;
    }

    private boolean matchesFilter(GripField field, String lowerFilter) {
        if (field.getName().toLowerCase().contains(lowerFilter)) {
            return true;
        }
        if (field.getType() != null) {
            String typeName = field.getType().getName();
            if (typeName != null && typeName.toLowerCase().contains(lowerFilter)) {
                return true;
            }
        }
        for (GripArgument arg : field.getArguments()) {
            if (arg.getName().toLowerCase().contains(lowerFilter)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidQuery(String text) {
        if (text == null || text.isEmpty()) return false;
        String t = text.trim();
        return t.startsWith("query") || t.startsWith("mutation") || t.startsWith("subscription") || t.startsWith("{");
    }

    private void sendSelectedQueryToRepeater() {
        if (currentSchemaTab == null || currentSchemaTab.queryPreviewArea == null) {
            showStatus("No query selected", true);
            return;
        }
        String query = currentSchemaTab.queryPreviewArea.getText().trim();
        if (!isValidQuery(query)) {
            showStatus("No query selected - select a Query or Mutation first", true);
            return;
        }

        String endpoint = getCurrentEndpoint();
        if (endpoint.isEmpty()) {
            showStatus("Enter target endpoint in Schema tab or Scanner tab", true);
            return;
        }

        try {
            JsonObject body = new JsonObject();
            body.addProperty("query", query);

            HttpRequest request = core.getHttpClient().buildPostRequest(endpoint, body.toString());

            Map<String, String> headersToUse = (currentSchemaTab != null)
                ? currentSchemaTab.getHeaders()
                : customHeaders;
            for (Map.Entry<String, String> header : headersToUse.entrySet()) {
                if (!header.getValue().isEmpty()) {
                    request = request.withUpdatedHeader(header.getKey(), header.getValue());
                }
            }

            String operationName = extractOperationName(query);
            String tabName = "GraphQL Grip - " + operationName;

            core.getApi().repeater().sendToRepeater(request, tabName);

            showStatus("Sent to Repeater: " + tabName, false);
        } catch (Exception e) {
            showStatus("Error: " + e.getMessage(), true);
        }
    }

    private void sendSelectedQueryToIntruder() {
        if (currentSchemaTab == null || currentSchemaTab.queryPreviewArea == null) {
            showStatus("No query selected", true);
            return;
        }
        String query = currentSchemaTab.queryPreviewArea.getText().trim();
        if (!isValidQuery(query)) {
            showStatus("No query selected - select a Query or Mutation first", true);
            return;
        }

        String endpoint = getCurrentEndpoint();
        if (endpoint.isEmpty()) {
            showStatus("Enter target endpoint in Schema tab or Scanner tab", true);
            return;
        }

        try {
            JsonObject body = new JsonObject();
            body.addProperty("query", query);

            HttpRequest request = core.getHttpClient().buildPostRequest(endpoint, body.toString());

            Map<String, String> headersToUse = (currentSchemaTab != null)
                ? currentSchemaTab.getHeaders()
                : customHeaders;
            for (Map.Entry<String, String> header : headersToUse.entrySet()) {
                if (!header.getValue().isEmpty()) {
                    request = request.withUpdatedHeader(header.getKey(), header.getValue());
                }
            }

            core.getApi().intruder().sendToIntruder(request);

            showStatus("Sent to Intruder", false);
        } catch (Exception e) {
            showStatus("Error: " + e.getMessage(), true);
        }
    }

    private String extractOperationName(String query) {

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?:query|mutation|subscription)\\s*\\{\\s*(\\w+)",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            String fieldName = matcher.group(1);

            return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }
        return "GraphQL";
    }

    private String getCurrentEndpoint() {

        if (currentSchemaTab != null && !currentSchemaTab.endpoint.isEmpty()) {
            return currentSchemaTab.endpoint;
        }

        return targetField.getText().trim();
    }

    private void executeSelectedQuery() {
        if (currentSchemaTab == null || currentSchemaTab.queryPreviewArea == null) {
            showStatus("No query selected", true);
            return;
        }
        String query = currentSchemaTab.queryPreviewArea.getText().trim();
        if (!isValidQuery(query)) {
            showStatus("No query selected - select a Query or Mutation first", true);
            return;
        }

        String endpoint = getCurrentEndpoint();
        if (endpoint.isEmpty()) {
            showStatus("Enter target endpoint in Schema tab or Scanner tab", true);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequestResponse response = core.getHttpClient().sendQueryWithLog(endpoint, query, "Schema Browser");

                SwingUtilities.invokeLater(() -> {

                    JTextArea responseArea = new JTextArea(response.response().bodyToString());
                    responseArea.setFont(theme.getCodeFont());
                    responseArea.setEditable(false);
                    JScrollPane scroll = new JScrollPane(responseArea);
                    scroll.setPreferredSize(new Dimension(600, 400));

                    JOptionPane.showMessageDialog(core.getApi().userInterface().swingUtils().suiteFrame(), scroll, "Response", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                core.logError("executeSelectedQuery failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    showStatus("Error executing query: " + e.getMessage(), true);
                });
            }
        }, core.getHttpClient().getExecutor());
    }

    private void copySelectedQuery() {
        if (currentSchemaTab == null || currentSchemaTab.queryPreviewArea == null) {
            showStatus("No query selected", true);
            return;
        }
        String query = currentSchemaTab.queryPreviewArea.getText().trim();
        if (query.isEmpty() || query.startsWith("Select a Query") || query.startsWith("#")) {
            showStatus("No query to copy", true);
            return;
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(query), null);
        showStatus("Query copied to clipboard", false);
    }

    private void importSchemaFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import GraphQL Schema");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(java.io.File f) {
                return f.isDirectory() || f.getName().endsWith(".json") ||
                       f.getName().endsWith(".graphql") || f.getName().endsWith(".sdl");
            }
            @Override
            public String getDescription() {
                return "GraphQL Schema Files (*.json, *.graphql, *.sdl)";
            }
        });

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();

            String defaultEndpoint = targetField.getText().trim();
            if (defaultEndpoint.isEmpty()) {
                defaultEndpoint = "http://localhost:8080/graphql";
            }

            JPanel urlPanel = new JPanel(new BorderLayout(5, 5));
            urlPanel.add(new JLabel("Enter the GraphQL endpoint URL for this schema:"), BorderLayout.NORTH);
            JTextField urlField = new JTextField(defaultEndpoint);
            urlField.setPreferredSize(new Dimension(400, 25));
            urlPanel.add(urlField, BorderLayout.CENTER);
            urlPanel.add(new JLabel("(Required to send requests from Schema tab)"), BorderLayout.SOUTH);

            int result = JOptionPane.showConfirmDialog(core.getApi().userInterface().swingUtils().suiteFrame(), urlPanel, "Set Schema Endpoint",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            String endpoint = urlField.getText().trim();
            if (endpoint.isEmpty()) {
                showStatus("Endpoint URL is required", true);
                return;
            }

            if (currentSchemaTab != null) {
                currentSchemaTab.endpoint = endpoint;
                if (currentSchemaTab.endpointField != null) {
                    currentSchemaTab.endpointField.setText(endpoint);
                }
                if (currentSchemaTab.graphPanel != null) {
                    currentSchemaTab.graphPanel.setEndpoint(endpoint);
                }
            }

            showStatus("Importing schema from " + file.getName() + "...", false);
            final String filePath = file.getAbsolutePath();
            final String fileName = file.getName();
            final boolean isJson = fileName.endsWith(".json");
            final String ep = endpoint;

            new javax.swing.SwingWorker<GripSchema, Void>() {
                private String statusMessage;

                @Override
                protected GripSchema doInBackground() throws Exception {
                    String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)));

                    if (isJson) {
                        return parseIntrospectionJson(content, ep);
                    } else {
                        return parseSDLSchema(content, ep);
                    }
                }

                @Override
                protected void done() {
                    try {
                        GripSchema schema = get();
                        if (schema != null) {
                            currentSchema = schema;
                            populateSchemaTree(schema);
                            showStatus(statusMessage, false);
                        }
                    } catch (Exception e) {
                        String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        showStatus("Error importing schema: " + msg, true);
                    }
                }

                private GripSchema parseIntrospectionJson(String json, String endpoint) throws Exception {
                    JsonObject jsonObj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

                    JsonObject schemaData = null;
                    if (jsonObj.has("data")) {
                        JsonObject data = jsonObj.getAsJsonObject("data");
                        if (data.has("__schema")) {
                            schemaData = data.getAsJsonObject("__schema");
                        }
                    } else if (jsonObj.has("__schema")) {
                        schemaData = jsonObj.getAsJsonObject("__schema");
                    }

                    if (schemaData != null) {
                        IntrospectionHandler handler = new IntrospectionHandler(core.getHttpClient());
                        GripSchema schema = handler.parseIntrospectionResult(schemaData, endpoint);
                        statusMessage = "Schema imported: " + schema.getTypeCount() + " types, " +
                                schema.getQueries().size() + " queries, " +
                                schema.getMutations().size() + " mutations";
                        return schema;
                    } else {
                        throw new Exception("Invalid JSON format. Expected introspection result.");
                    }
                }

                private GripSchema parseSDLSchema(String sdl, String endpoint) {
                    GripSchema schema = new GripSchema();
                    schema.setSourceEndpoint(endpoint);
                    schema.setQueryTypeName("Query");

                    GripType queryType = new GripType("Query", GripTypeKind.OBJECT);

                    java.util.regex.Pattern typePattern = java.util.regex.Pattern.compile(
                            "type\\s+(\\w+)\\s*\\{([^}]+)\\}",
                            java.util.regex.Pattern.MULTILINE | java.util.regex.Pattern.DOTALL);
                    java.util.regex.Matcher typeMatcher = typePattern.matcher(sdl);

                    while (typeMatcher.find()) {
                        String typeName = typeMatcher.group(1);
                        String fieldsBlock = typeMatcher.group(2);

                        GripType type = new GripType(typeName, GripTypeKind.OBJECT);

                        java.util.regex.Pattern fieldPattern = java.util.regex.Pattern.compile(
                                "(\\w+)(?:\\([^)]*\\))?\\s*:\\s*([\\[\\]\\w!]+)");
                        java.util.regex.Matcher fieldMatcher = fieldPattern.matcher(fieldsBlock);

                        while (fieldMatcher.find()) {
                            String fieldName = fieldMatcher.group(1);
                            String fieldType = fieldMatcher.group(2);
                            type.addField(new GripField(fieldName, GripTypeRef.fromString(fieldType)));
                        }

                        schema.addType(type);

                        if ("Query".equals(typeName)) {
                            queryType = type;
                        }
                    }

                    if (!schema.hasType("Query")) {
                        schema.addType(queryType);
                    }

                    statusMessage = "SDL schema imported: " + schema.getTypeCount() + " types (basic parsing)";
                    return schema;
                }
            }.execute();
        }
    }

    private void exportSchemaToFile() {

        GripSchema schemaToExport = (currentSchemaTab != null) ? currentSchemaTab.schema : currentSchema;

        if (schemaToExport == null) {
            showStatus("No schema loaded in current tab to export", true);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export GraphQL Schema");

        String suggestedName = "schema.graphql";
        if (currentSchemaTab != null && currentSchemaTab.endpoint != null && !currentSchemaTab.endpoint.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(currentSchemaTab.endpoint);
                String host = uri.getHost();
                if (host != null) {
                    suggestedName = host.replace(".", "_") + "_schema.graphql";
                }
            } catch (Exception ignored) {}
        }
        fileChooser.setSelectedFile(new java.io.File(suggestedName));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            try {
                String sdl = schemaToExport.toSDL();
                java.nio.file.Files.writeString(file.toPath(), sdl);

                showStatus("Schema exported to: " + file.getAbsolutePath(), false);
            } catch (Exception e) {
                showStatus("Error exporting schema: " + e.getMessage(), true);
            }
        }
    }

    private void runIntrospection(String endpoint) {
        CompletableFuture.runAsync(() -> {
            try {
                IntrospectionHandler handler = new IntrospectionHandler(core.getHttpClient());
                GripSchema schema = handler.fetchSchema(endpoint).join();

                if (schema != null && schema.getTypeCount() > 0) {
                    schema.setSourceEndpoint(endpoint);
                    currentSchema = schema;

                    SwingUtilities.invokeLater(() -> {

                        if (currentSchemaTab != null) {
                            currentSchemaTab.endpoint = endpoint;
                            if (currentSchemaTab.endpointField != null) {
                                currentSchemaTab.endpointField.setText(endpoint);
                            }
                        }

                        populateSchemaTree(schema);
                        showStatus("Schema loaded: " + schema.getTypeCount() + " types, " +
                                schema.getQueries().size() + " queries, " +
                                schema.getMutations().size() + " mutations", false);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        showStatus("Could not fetch schema. Introspection may be disabled. Try Blind Discovery.", true);
                    });
                }
            } catch (Exception e) {
                Throwable cause = e;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                core.logError("runIntrospection failed: " + cause.getMessage());
                final String errorMsg = cause.getMessage();
                SwingUtilities.invokeLater(() -> {
                    showStatus("Error: " + errorMsg, true);
                });
            }
        }, core.getHttpClient().getExecutor());
    }

    public void selectTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(tabIndex);
        }
    }

    public void setTargetEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isEmpty()) {
            SwingUtilities.invokeLater(() -> targetField.setText(endpoint));
        }
    }

    private void setScanningState(boolean isScanning) {
        this.scanning = isScanning;
        SwingUtilities.invokeLater(() -> {
            scanBtn.setEnabled(!isScanning);
            blindBtn.setEnabled(!isScanning);
            fingerprintBtn.setEnabled(!isScanning);
            discoverBtn.setEnabled(!isScanning);
            cancelBtn.setVisible(isScanning);
        });
    }

    private void cancelScan() {
        scanning = false;
        appendLog("[*] Cancelling scan...");
        setScanningState(false);
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    private JButton createSecondaryButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(theme.getNormalFont());
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setForeground(theme.getSecondaryText());
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.setBorder(BorderFactory.createCompoundBorder(
            new GripTheme.RoundedBorder(theme.getBorder(), GripTheme.CORNER_RADIUS_SM, 1),
            BorderFactory.createEmptyBorder(GripTheme.SPACING_SM, GripTheme.SPACING_MD, GripTheme.SPACING_SM, GripTheme.SPACING_MD)
        ));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setForeground(GripTheme.Colors.ACCENT);
                btn.setBorder(BorderFactory.createCompoundBorder(
                    new GripTheme.RoundedBorder(GripTheme.Colors.ACCENT, GripTheme.CORNER_RADIUS_SM, 1),
                    BorderFactory.createEmptyBorder(GripTheme.SPACING_SM, GripTheme.SPACING_MD, GripTheme.SPACING_SM, GripTheme.SPACING_MD)
                ));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setForeground(theme.getSecondaryText());
                btn.setBorder(BorderFactory.createCompoundBorder(
                    new GripTheme.RoundedBorder(theme.getBorder(), GripTheme.CORNER_RADIUS_SM, 1),
                    BorderFactory.createEmptyBorder(GripTheme.SPACING_SM, GripTheme.SPACING_MD, GripTheme.SPACING_SM, GripTheme.SPACING_MD)
                ));
            }
        });
        return btn;
    }

    private void addPlaceholder(JTextField field, String placeholder) {
        field.setForeground(Color.GRAY);
        field.setText(placeholder);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(theme.getForeground());
                }
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setForeground(Color.GRAY);
                    field.setText(placeholder);
                }
            }
        });
    }

    private static class TypeTreeNode {
        private final String typeName;
        private final String kind;
        private final GripSchema schema;

        TypeTreeNode(String typeName, String kind, GripSchema schema) {
            this.typeName = typeName;
            this.kind = kind;
            this.schema = schema;
        }

        String getDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(typeName).append(" (").append(kind).append(")\n#\n");
            GripType type = schema.getType(typeName);
            if (type == null) return sb.toString();
            if (!type.getFields().isEmpty()) {
                sb.append("# Fields:\n");
                for (GripField f : type.getFields()) {
                    String ts = f.getType() != null ? f.getType().toGraphQLString() : "Unknown";
                    sb.append("#   ").append(f.getName()).append(": ").append(ts).append("\n");
                }
            }
            if (!type.getInputFields().isEmpty()) {
                sb.append("# Input Fields:\n");
                for (GripField f : type.getInputFields()) {
                    String ts = f.getType() != null ? f.getType().toGraphQLString() : "Unknown";
                    sb.append("#   ").append(f.getName()).append(": ").append(ts).append("\n");
                }
            }
            if (!type.getEnumValues().isEmpty()) {
                sb.append("# Values:\n");
                for (GripEnumValue ev : type.getEnumValues()) {
                    sb.append("#   ").append(ev.getName()).append("\n");
                }
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return typeName + " (" + kind + ")";
        }
    }

    private static class SchemaTreeNode {
        private final GripField field;
        private final String operationType;
        private final GripSchema schema;

        public SchemaTreeNode(GripField field, String operationType, GripSchema schema) {
            this.field = field;
            this.operationType = operationType;
            this.schema = schema;
        }

        public String getDisplayName() {
            StringBuilder sb = new StringBuilder(field.getName());
            if (!field.getArguments().isEmpty()) {
                sb.append("(");
                for (int i = 0; i < field.getArguments().size(); i++) {
                    if (i > 0) sb.append(", ");
                    GripArgument arg = field.getArguments().get(i);
                    sb.append(arg.getName()).append(": ");
                    sb.append(arg.getType() != null ? arg.getType().toGraphQLString() : "Unknown");
                }
                sb.append(")");
            }
            sb.append(": ").append(field.getType() != null ? field.getType().toGraphQLString() : "Unknown");
            return sb.toString();
        }

        public boolean isQuery() { return "Query".equals(operationType); }
        public boolean isMutation() { return "Mutation".equals(operationType); }
        public boolean isSubscription() { return "Subscription".equals(operationType); }

        public String generateQuery() {
            StringBuilder sb = new StringBuilder();
            String opKeyword = operationType.toLowerCase();
            sb.append(opKeyword).append(" {\n");
            sb.append("  ").append(field.getName());

            if (!field.getArguments().isEmpty()) {
                sb.append("(");
                for (int i = 0; i < field.getArguments().size(); i++) {
                    if (i > 0) sb.append(", ");
                    GripArgument arg = field.getArguments().get(i);
                    sb.append(arg.getName()).append(": ");
                    sb.append(arg.getType() != null ? getPlaceholderValue(arg.getType()) : "\"placeholder\"");
                }
                sb.append(")");
            }

            boolean needsSelection = true;
            if (field.getType() != null) {
                String rtName = field.getType().getName();
                if (rtName != null) {
                    GripType rt = schema.getType(rtName);
                    needsSelection = rt != null && rt.getKind() != GripTypeKind.SCALAR && rt.getKind() != GripTypeKind.ENUM;
                }
            }

            if (needsSelection) {
                sb.append(" {\n");
                if (field.getType() != null) {
                    java.util.Set<String> visited = new java.util.HashSet<>();
                    addReturnFields(sb, field.getType(), 2, visited);
                } else {
                    sb.append("    __typename\n");
                }
                sb.append("  }\n");
            } else {
                sb.append("\n");
            }
            sb.append("}\n");

            return sb.toString();
        }

        private String getPlaceholderValue(GripTypeRef type) {
            if (type == null) return "\"placeholder\"";
            String name = type.getName();
            if (name == null) name = "String";

            if (type.isList()) {
                String itemValue = getScalarPlaceholder(name);
                return "[" + itemValue + "]";
            }

            GripType inputType = schema.getType(name);
            if (inputType != null && (inputType.getKind() == GripTypeKind.INPUT_OBJECT ||
                    name.endsWith("Input"))) {
                return buildInputPlaceholder(inputType);
            }

            return getScalarPlaceholder(name);
        }

        private String getScalarPlaceholder(String typeName) {

            return switch (typeName) {
                case "String" -> "\"example\"";
                case "ID" -> "\"1\"";
                case "Int" -> "0";
                case "Float" -> "0.0";
                case "Boolean" -> "true";
                default -> "\"placeholder\"";
            };
        }

        private String buildInputPlaceholder(GripType inputType) {
            StringBuilder sb = new StringBuilder("{ ");
            List<GripField> fields = inputType.getFields();
            for (int i = 0; i < Math.min(fields.size(), 3); i++) {
                if (i > 0) sb.append(", ");
                GripField f = fields.get(i);
                sb.append(f.getName()).append(": ");
                String tn = (f.getType() != null && f.getType().getName() != null) ? f.getType().getName() : "String";
                sb.append(getScalarPlaceholder(tn));
            }
            sb.append(" }");
            return sb.toString();
        }

        private void addReturnFields(StringBuilder sb, GripTypeRef typeRef, int depth, java.util.Set<String> visited) {
            String indent = "  ".repeat(depth);
            if (typeRef == null) {
                sb.append(indent).append("__typename\n");
                return;
            }

            String typeName = typeRef.getName();
            if (typeName == null || visited.contains(typeName)) {
                sb.append(indent).append("__typename\n");
                return;
            }

            GripType type = schema.getType(typeName);
            if (type == null) {
                sb.append(indent).append("__typename\n");
                return;
            }

            visited.add(typeName);

            int count = 0;
            int nestedCount = 0;
            for (GripField f : type.getFields()) {
                String fieldType = f.getType().getName();
                GripType subType = schema.getType(fieldType);

                if (subType == null || subType.getKind() == GripTypeKind.SCALAR ||
                        subType.getKind() == GripTypeKind.ENUM) {

                    sb.append(indent).append(f.getName()).append("\n");
                    count++;
                } else if (depth < 3 && nestedCount < 3) {

                    sb.append(indent).append(f.getName()).append(" {\n");
                    addReturnFields(sb, f.getType(), depth + 1, visited);
                    sb.append(indent).append("}\n");
                    count++;
                    nestedCount++;
                }
            }

            if (count == 0) {
                sb.append(indent).append("__typename\n");
            }

            visited.remove(typeName);
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private static class SchemaTabData {
        String endpoint = "";
        GripSchema schema;
        boolean filtered;

        JTree tree;
        DefaultMutableTreeNode rootNode;
        DefaultTreeModel treeModel;
        JTextArea queryPreviewArea;

        SchemaGraphPanel graphPanel;

        JLabel tabLabel;
        JTextField endpointField;

        DefaultTableModel headersTableModel;

        SchemaTabData() {
        }

        Map<String, String> getHeaders() {
            Map<String, String> headers = new HashMap<>();
            if (headersTableModel != null) {
                for (int i = 0; i < headersTableModel.getRowCount(); i++) {
                    String headerName = (String) headersTableModel.getValueAt(i, 0);
                    String headerValue = (String) headersTableModel.getValueAt(i, 1);
                    if (headerName != null && !headerName.trim().isEmpty()) {
                        headers.put(headerName.trim(), headerValue != null ? headerValue.trim() : "");
                    }
                }
            }
            return headers;
        }
    }
}
