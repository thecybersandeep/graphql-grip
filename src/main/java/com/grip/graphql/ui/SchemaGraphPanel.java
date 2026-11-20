package com.grip.graphql.ui;

import com.grip.graphql.GripCore;
import com.grip.graphql.model.schema.*;
import com.grip.graphql.http.GraphQLTransport;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.ui.editor.RawEditor;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class SchemaGraphPanel extends JPanel {

    private final GripCore core;
    private final GripTheme theme;
    private GripSchema schema;
    private String endpoint;
    private GraphQLTransport transport = GraphQLTransport.POST_JSON;

    private final SchemaGraphModel graph = new SchemaGraphModel();

    private String selectedNodeName = null;
    private String focusNodeName = null;
    private String emptyReason = null;
    private boolean viewTouched = false;
    private String hoveredNodeName = null;
    private Point lastDragPoint = null;
    private SchemaGraphModel.Node draggedNode = null;
    private double viewScale = 1.0;
    private double viewOffsetX = 0;
    private double viewOffsetY = 0;

    private static final int NODE_WIDTH = SchemaGraphModel.NODE_WIDTH;
    private static final int NODE_HEIGHT = SchemaGraphModel.NODE_HEIGHT;
    private static final int MARGIN = SchemaGraphModel.MARGIN;

    private static final Color COLOR_QUERY = new Color(46, 125, 50);
    private static final Color COLOR_MUTATION = new Color(230, 81, 0);
    private static final Color COLOR_SUBSCRIPTION = new Color(106, 27, 154);
    private static final Color COLOR_OBJECT = new Color(25, 118, 210);
    private static final Color COLOR_INPUT = new Color(142, 36, 170);
    private static final Color COLOR_ENUM = new Color(211, 47, 47);
    private static final Color COLOR_SCALAR = new Color(117, 117, 117);
    private static final Color COLOR_INTERFACE = new Color(0, 121, 107);
    private static final Color COLOR_UNION = new Color(255, 152, 0);
    private static final Color COLOR_SELECTED = new Color(255, 193, 7);
    private static final Color COLOR_EDGE = new Color(100, 100, 100, 100);
    private static final Color COLOR_EDGE_HIGHLIGHT = new Color(33, 150, 243, 200);

    @SuppressWarnings("this-escape")
    public SchemaGraphPanel(GripCore core) {
        this.core = core;
        this.theme = core.getTheme();
        setBackground(new Color(248, 249, 250));
        setDoubleBuffered(true);
        setFocusable(true);
        initializeMouseHandlers();
        initializeKeyBindings();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!viewTouched && !graph.isEmpty()) {
                    centerView();
                    repaint();
                }
            }
        });
    }

    private void initializeMouseHandlers() {
        MouseAdapter handler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point worldPt = toWorldCoords(e.getPoint());
                SchemaGraphModel.Node clickedNode = findNodeAt(worldPt);

                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (clickedNode != null) {
                        selectedNodeName = clickedNode.name;
                        draggedNode = clickedNode;
                        lastDragPoint = e.getPoint();
                    } else {
                        selectedNodeName = null;
                        draggedNode = null;
                        lastDragPoint = e.getPoint();
                    }
                    repaint();
                } else if (SwingUtilities.isRightMouseButton(e) && clickedNode != null) {
                    selectedNodeName = clickedNode.name;
                    repaint();
                    displayContextMenu(e, clickedNode);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggedNode = null;
                lastDragPoint = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastDragPoint == null) return;

                int deltaX = e.getX() - lastDragPoint.x;
                int deltaY = e.getY() - lastDragPoint.y;

                viewTouched = true;

                if (draggedNode != null) {

                    draggedNode.x += deltaX / viewScale;
                    draggedNode.y += deltaY / viewScale;
                } else {

                    viewOffsetX += deltaX;
                    viewOffsetY += deltaY;
                }

                lastDragPoint = e.getPoint();
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Point worldPt = toWorldCoords(e.getPoint());
                SchemaGraphModel.Node node = findNodeAt(worldPt);
                String newHover = (node != null) ? node.name : null;

                if (!Objects.equals(newHover, hoveredNodeName)) {
                    hoveredNodeName = newHover;
                    setCursor(hoveredNodeName != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                viewTouched = true;
                double prevScale = viewScale;
                double scaleFactor = (e.getWheelRotation() < 0) ? 1.15 : 0.87;
                viewScale = Math.max(0.15, Math.min(3.0, viewScale * scaleFactor));

                Point mouse = e.getPoint();
                viewOffsetX = mouse.x - (mouse.x - viewOffsetX) * (viewScale / prevScale);
                viewOffsetY = mouse.y - (mouse.y - viewOffsetY) * (viewScale / prevScale);

                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selectedNodeName != null) {
                    SchemaGraphModel.Node node = graph.node(selectedNodeName);
                    if (node != null) {
                        sendTypeToRepeater(node);
                    }
                }
            }
        };

        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
    }

    private static final int MAX_GRAPH_NODES = 400;

    public void setSchema(GripSchema schema, String endpoint) {
        this.schema = schema;
        this.endpoint = endpoint;
        this.focusNodeName = null;
        this.selectedNodeName = null;
        rebuildGraph();
    }

    private void initializeKeyBindings() {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearFocus");
        getActionMap().put("clearFocus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (focusNodeName != null) {
                    applyFocus(null);
                }
            }
        });
    }

    private void applyFocus(String typeName) {
        focusNodeName = typeName;
        selectedNodeName = typeName;
        viewTouched = false;
        rebuildGraph();
    }

    private void rebuildGraph() {
        graph.build(schema, focusNodeName, MAX_GRAPH_NODES);
        focusNodeName = graph.focusName();
        draggedNode = null;

        emptyReason = schema != null && graph.isEmpty()
                ? "This schema has no types to draw"
                : null;

        centerView();
        repaint();
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setTransport(GraphQLTransport transport) {
        if (transport != null) {
            this.transport = transport;
        }
    }

    private Color determineNodeColor(GripType type) {
        String name = type.getName();

        if (name.equals(schema.getQueryTypeName())) {
            return COLOR_QUERY;
        }
        if (name.equals(schema.getMutationTypeName())) {
            return COLOR_MUTATION;
        }
        if (name.equals(schema.getSubscriptionTypeName())) {
            return COLOR_SUBSCRIPTION;
        }

        return switch (type.getKind()) {
            case OBJECT -> COLOR_OBJECT;
            case INPUT_OBJECT -> COLOR_INPUT;
            case ENUM -> COLOR_ENUM;
            case SCALAR -> COLOR_SCALAR;
            case INTERFACE -> COLOR_INTERFACE;
            case UNION -> COLOR_UNION;
            default -> COLOR_OBJECT;
        };
    }

    private void centerView() {
        if (graph.isEmpty()) {
            viewScale = 1.0;
            viewOffsetX = getWidth() / 2.0;
            viewOffsetY = getHeight() / 2.0;
            return;
        }

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (SchemaGraphModel.Node node : graph.nodes()) {
            minX = Math.min(minX, node.x - NODE_WIDTH / 2.0);
            maxX = Math.max(maxX, node.x + NODE_WIDTH / 2.0);
            minY = Math.min(minY, node.y - NODE_HEIGHT / 2.0);
            maxY = Math.max(maxY, node.y + NODE_HEIGHT / 2.0);
        }

        double graphWidth = maxX - minX + MARGIN * 2;
        double graphHeight = maxY - minY + MARGIN * 2;

        int panelWidth = Math.max(getWidth(), 800);
        int panelHeight = Math.max(getHeight(), 600);

        viewScale = Math.min(panelWidth / graphWidth, panelHeight / graphHeight) * 0.9;
        viewScale = Math.max(0.15, Math.min(1.5, viewScale));

        viewOffsetX = panelWidth / 2.0 - (minX + maxX) / 2.0 * viewScale;
        viewOffsetY = panelHeight / 2.0 - (minY + maxY) / 2.0 * viewScale;
    }
    private Point toWorldCoords(Point screenPoint) {
        double worldX = (screenPoint.x - viewOffsetX) / viewScale;
        double worldY = (screenPoint.y - viewOffsetY) / viewScale;
        return new Point((int) worldX, (int) worldY);
    }

    private SchemaGraphModel.Node findNodeAt(Point worldPoint) {

        List<SchemaGraphModel.Node> visible = graph.nodes();
        for (int i = visible.size() - 1; i >= 0; i--) {
            SchemaGraphModel.Node node = visible.get(i);
            Rectangle2D bounds = new Rectangle2D.Double(
                node.x - NODE_WIDTH / 2.0,
                node.y - NODE_HEIGHT / 2.0,
                NODE_WIDTH,
                NODE_HEIGHT
            );
            if (bounds.contains(worldPoint)) {
                return node;
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g2.translate(viewOffsetX, viewOffsetY);
        g2.scale(viewScale, viewScale);

        if (schema == null || graph.isEmpty()) {
            g2.dispose();
            drawEmptyState(g);
            return;
        }

        drawEdges(g2);

        drawNodes(g2);

        g2.dispose();

        drawLegend(g);
        drawInstructions(g);
        drawStats(g);
    }

    private void drawEmptyState(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        g2.setColor(new Color(200, 200, 200));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 64));
        FontMetrics fm = g2.getFontMetrics();
        String icon = "\u2B21";
        g2.drawString(icon, centerX - fm.stringWidth(icon) / 2, centerY - 30);

        String headline = emptyReason != null ? "Nothing to Draw" : "No Schema Loaded";
        String detail = emptyReason != null
                ? emptyReason
                : "Import or fetch a schema to view the graph";

        g2.setColor(new Color(120, 120, 120));
        g2.setFont(theme.getNormalFont().deriveFont(16f));
        fm = g2.getFontMetrics();
        g2.drawString(headline, centerX - fm.stringWidth(headline) / 2, centerY + 40);

        g2.setFont(theme.getNormalFont().deriveFont(13f));
        fm = g2.getFontMetrics();
        g2.drawString(detail, centerX - fm.stringWidth(detail) / 2, centerY + 65);

        g2.dispose();
    }
    private void drawEdges(Graphics2D g2) {
        List<SchemaGraphModel.Edge> highlighted = null;
        for (SchemaGraphModel.Edge edge : graph.edges()) {
            boolean isHighlighted = selectedNodeName != null &&
                (edge.source.name.equals(selectedNodeName) || edge.target.name.equals(selectedNodeName));
            if (isHighlighted) {
                if (highlighted == null) highlighted = new ArrayList<>();
                highlighted.add(edge);
            } else {
                drawEdge(g2, edge, false);
            }
        }
        if (highlighted != null) {
            for (SchemaGraphModel.Edge edge : highlighted) {
                drawEdge(g2, edge, true);
            }
        }
    }

    private void drawEdge(Graphics2D g2, SchemaGraphModel.Edge edge, boolean highlighted) {
        float width = highlighted ? 2.5f : 1.2f;
        g2.setColor(highlighted ? COLOR_EDGE_HIGHLIGHT : COLOR_EDGE);
        g2.setStroke(edge.kind == SchemaGraphModel.EdgeKind.FIELD
                ? new BasicStroke(width)
                : new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[]{6f, 5f}, 0f));

        if (edge.source == edge.target) {
            double loopSize = NODE_HEIGHT * 0.8;
            double x = edge.source.x + NODE_WIDTH / 2.0 - loopSize / 3.0;
            double y = edge.source.y - NODE_HEIGHT / 2.0 - loopSize / 2.0;
            g2.draw(new Ellipse2D.Double(x, y, loopSize, loopSize));
            return;
        }

        double[] p1 = getEdgePoint(edge.source, edge.target.x, edge.target.y);
        double[] p2 = getEdgePoint(edge.target, edge.source.x, edge.source.y);

        double dx = p2[0] - p1[0];
        double dy = p2[1] - p1[1];
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 0.001) {
            return;
        }

        double curvature = Math.min(30, dist * 0.15);
        double midX = (p1[0] + p2[0]) / 2 - dy / dist * curvature;
        double midY = (p1[1] + p2[1]) / 2 + dx / dist * curvature;

        QuadCurve2D curve = new QuadCurve2D.Double();
        curve.setCurve(p1[0], p1[1], midX, midY, p2[0], p2[1]);
        g2.draw(curve);

        g2.setStroke(new BasicStroke(width));
        drawArrowHead(g2, midX, midY, p2[0], p2[1]);
    }
    private double[] getEdgePoint(SchemaGraphModel.Node node, double targetX, double targetY) {
        double cx = node.x;
        double cy = node.y;
        double halfW = NODE_WIDTH / 2.0;
        double halfH = NODE_HEIGHT / 2.0;

        double dx = targetX - cx;
        double dy = targetY - cy;

        if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) {
            return new double[]{cx, cy + halfH};
        }

        double scaleX = (dx != 0) ? halfW / Math.abs(dx) : Double.MAX_VALUE;
        double scaleY = (dy != 0) ? halfH / Math.abs(dy) : Double.MAX_VALUE;
        double scale = Math.min(scaleX, scaleY);

        return new double[]{cx + dx * scale, cy + dy * scale};
    }

    private void drawArrowHead(Graphics2D g2, double fromX, double fromY, double toX, double toY) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        int arrowLen = 8;
        double arrowAngle = Math.PI / 7;

        double x1 = toX - arrowLen * Math.cos(angle - arrowAngle);
        double y1 = toY - arrowLen * Math.sin(angle - arrowAngle);
        double x2 = toX - arrowLen * Math.cos(angle + arrowAngle);
        double y2 = toY - arrowLen * Math.sin(angle + arrowAngle);

        Path2D arrow = new Path2D.Double();
        arrow.moveTo(toX, toY);
        arrow.lineTo(x1, y1);
        arrow.lineTo(x2, y2);
        arrow.closePath();
        g2.fill(arrow);
    }

    private void drawNodes(Graphics2D g2) {
        for (SchemaGraphModel.Node node : graph.nodes()) {
            boolean isSelected = node.name.equals(selectedNodeName);
            boolean isHovered = node.name.equals(hoveredNodeName);

            double x = node.x - NODE_WIDTH / 2.0;
            double y = node.y - NODE_HEIGHT / 2.0;

            RoundRectangle2D rect = new RoundRectangle2D.Double(x, y, NODE_WIDTH, NODE_HEIGHT, 8, 8);

            g2.setColor(new Color(0, 0, 0, 25));
            g2.fill(new RoundRectangle2D.Double(x + 2, y + 2, NODE_WIDTH, NODE_HEIGHT, 8, 8));

            if (isSelected) {
                g2.setColor(COLOR_SELECTED);
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new RoundRectangle2D.Double(x - 3, y - 3, NODE_WIDTH + 6, NODE_HEIGHT + 6, 10, 10));
            } else if (isHovered) {
                g2.setColor(new Color(100, 181, 246, 180));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Double(x - 2, y - 2, NODE_WIDTH + 4, NODE_HEIGHT + 4, 9, 9));
            }

            Color nodeColor = determineNodeColor(node.type);
            Color baseColor = isHovered ? nodeColor.brighter() : nodeColor;
            GradientPaint gradient = new GradientPaint(
                (float) x, (float) y, baseColor.brighter(),
                (float) x, (float) (y + NODE_HEIGHT), baseColor
            );
            g2.setPaint(gradient);
            g2.fill(rect);

            g2.setColor(baseColor.darker());
            g2.setStroke(new BasicStroke(1f));
            g2.draw(rect);

            g2.setColor(Color.WHITE);
            g2.setFont(theme.getNormalFont().deriveFont(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();

            String displayName = node.name;
            int maxTextWidth = NODE_WIDTH - 12;
            if (fm.stringWidth(displayName) > maxTextWidth) {
                while (displayName.length() > 3 && fm.stringWidth(displayName + "...") > maxTextWidth) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "...";
            }

            int textX = (int) (x + (NODE_WIDTH - fm.stringWidth(displayName)) / 2);
            int textY = (int) (y + (NODE_HEIGHT + fm.getAscent() - fm.getDescent()) / 2);

            g2.setColor(new Color(0, 0, 0, 60));
            g2.drawString(displayName, textX + 1, textY + 1);
            g2.setColor(Color.WHITE);
            g2.drawString(displayName, textX, textY);

            int fieldCount = node.type.getFields().size();
            if (fieldCount > 0) {
                String badge = String.valueOf(fieldCount);
                g2.setFont(theme.getNormalFont().deriveFont(9f));
                fm = g2.getFontMetrics();
                int badgeWidth = fm.stringWidth(badge) + 8;
                int badgeX = (int) (x + NODE_WIDTH - badgeWidth - 4);
                int badgeY = (int) (y + 4);

                g2.setColor(new Color(0, 0, 0, 80));
                g2.fillRoundRect(badgeX, badgeY, badgeWidth, 14, 7, 7);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.drawString(badge, badgeX + 4, badgeY + 11);
            }
        }
    }

    private void drawLegend(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int x = 12;
        int y = 12;
        int boxSize = 12;
        int lineHeight = 20;
        int legendWidth = 105;
        int legendHeight = lineHeight * 7 + 12;

        g2.setColor(new Color(255, 255, 255, 235));
        g2.fillRoundRect(x - 6, y - 6, legendWidth, legendHeight, 8, 8);
        g2.setColor(new Color(200, 200, 200));
        g2.drawRoundRect(x - 6, y - 6, legendWidth, legendHeight, 8, 8);

        g2.setFont(theme.getNormalFont().deriveFont(10f));

        drawLegendEntry(g2, x, y, COLOR_QUERY, "Query", boxSize);
        drawLegendEntry(g2, x, y + lineHeight, COLOR_MUTATION, "Mutation", boxSize);
        drawLegendEntry(g2, x, y + lineHeight * 2, COLOR_OBJECT, "Object", boxSize);
        drawLegendEntry(g2, x, y + lineHeight * 3, COLOR_INPUT, "Input", boxSize);
        drawLegendEntry(g2, x, y + lineHeight * 4, COLOR_ENUM, "Enum", boxSize);
        drawLegendEntry(g2, x, y + lineHeight * 5, COLOR_INTERFACE, "Interface", boxSize);
        drawLegendEntry(g2, x, y + lineHeight * 6, COLOR_SCALAR, "Scalar", boxSize);

        g2.dispose();
    }

    private void drawLegendEntry(Graphics2D g2, int x, int y, Color color, String label, int boxSize) {

        g2.setColor(color);
        g2.fillRoundRect(x, y, boxSize, boxSize, 3, 3);
        g2.setColor(color.darker());
        g2.drawRoundRect(x, y, boxSize, boxSize, 3, 3);

        g2.setColor(new Color(60, 60, 60));
        g2.drawString(label, x + boxSize + 6, y + boxSize - 2);
    }

    private void drawInstructions(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(theme.getNormalFont().deriveFont(10f));
        g2.setColor(new Color(130, 130, 130));

        String instructions = focusNodeName != null
                ? "Scroll: Zoom | Drag: Pan/Move | Esc: Clear focus | Right-click: Menu"
                : "Scroll: Zoom | Drag: Pan/Move | Double-click: Repeater | Right-click: Menu";

        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(instructions, (getWidth() - fm.stringWidth(instructions)) / 2,
                getHeight() - 8);
        g2.dispose();
    }
    private void drawStats(Graphics g) {
        if (schema == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(theme.getNormalFont().deriveFont(10f));
        g2.setColor(new Color(130, 130, 130));

        StringBuilder stats = new StringBuilder();
        if (focusNodeName != null) {
            stats.append("Focus: ").append(focusNodeName).append(" | ");
        }
        stats.append(String.format("Types: %d | Edges: %d",
                graph.nodes().size(), graph.edges().size()));
        if (graph.hiddenTypeCount() > 0) {
            stats.append(" | Hidden: ").append(graph.hiddenTypeCount());
        }
        stats.append(String.format(" | Zoom: %.0f%%", viewScale * 100));

        String text = stats.toString();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, getWidth() - fm.stringWidth(text) - 12, getHeight() - 8);
        g2.dispose();
    }
    private void displayContextMenu(MouseEvent e, SchemaGraphModel.Node node) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem viewDetails = new JMenuItem("View Type Details");
        viewDetails.addActionListener(ev -> showTypeDetails(node));
        menu.add(viewDetails);

        boolean focused = node.name.equals(focusNodeName);
        JMenuItem focus = new JMenuItem(focused
                ? "Show the Whole Schema"
                : "Focus on " + node.name + " and Its Neighbours");
        focus.addActionListener(ev -> applyFocus(focused ? null : node.name));
        menu.add(focus);

        JMenuItem centerOnNode = new JMenuItem("Center on Node");
        centerOnNode.addActionListener(ev -> {
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            viewOffsetX = panelWidth / 2.0 - node.x * viewScale;
            viewOffsetY = panelHeight / 2.0 - node.y * viewScale;
            repaint();
        });
        menu.add(centerOnNode);

        menu.addSeparator();

        if (node.type.getKind() == GripTypeKind.OBJECT && !node.type.getFields().isEmpty()) {
            JMenu fieldsMenu = new JMenu("Send Field to Repeater");
            int fieldCount = 0;
            for (GripField field : node.type.getFields()) {
                if (fieldCount >= 20) {
                    JMenuItem more = new JMenuItem("... and " + (node.type.getFields().size() - 20) + " more");
                    more.setEnabled(false);
                    fieldsMenu.add(more);
                    break;
                }
                JMenuItem fieldItem = new JMenuItem(field.getName() + ": " + field.getType().toGraphQLString());
                fieldItem.addActionListener(ev -> sendFieldToRepeater(node, field));
                fieldsMenu.add(fieldItem);
                fieldCount++;
            }
            menu.add(fieldsMenu);

            JMenuItem allFields = new JMenuItem("Send All Fields to Repeater");
            allFields.addActionListener(ev -> sendTypeToRepeater(node));
            menu.add(allFields);
        }

        menu.show(this, e.getX(), e.getY());
    }

    private void showTypeDetails(SchemaGraphModel.Node node) {
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(node.name).append("\n");
        sb.append("Kind: ").append(node.type.getKind()).append("\n\n");

        List<GripField> fields = node.type.getFields();
        if (!fields.isEmpty()) {
            sb.append("Fields (").append(fields.size()).append("):\n");
            for (GripField field : fields) {
                sb.append("  ").append(field.getName());
                if (!field.getArguments().isEmpty()) {
                    sb.append("(");
                    for (int i = 0; i < field.getArguments().size(); i++) {
                        if (i > 0) sb.append(", ");
                        GripArgument arg = field.getArguments().get(i);
                        sb.append(arg.getName()).append(": ").append(arg.getType().toGraphQLString());
                    }
                    sb.append(")");
                }
                sb.append(": ").append(field.getType().toGraphQLString()).append("\n");
            }
        }

        RawEditor typeEditor = core.getApi().userInterface().createRawEditor();
        typeEditor.setEditable(false);
        typeEditor.setContents(ByteArray.byteArray(sb.toString()));

        JPanel typePanel = new JPanel(new BorderLayout());
        typePanel.add(typeEditor.uiComponent(), BorderLayout.CENTER);
        typePanel.setPreferredSize(new Dimension(550, 400));

        JOptionPane.showMessageDialog(core.getApi().userInterface().swingUtils().suiteFrame(), typePanel, "Type: " + node.name, JOptionPane.INFORMATION_MESSAGE);
    }

    private void sendTypeToRepeater(SchemaGraphModel.Node node) {
        if (endpoint == null || endpoint.isEmpty()) {
            JOptionPane.showMessageDialog(core.getApi().userInterface().swingUtils().suiteFrame(), "No endpoint set. Configure endpoint in Schema tab.",
                    "Missing Endpoint", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StringBuilder queryBuilder = new StringBuilder();
        String opType = node.name.equals(schema.getMutationTypeName()) ? "mutation" : "query";
        queryBuilder.append(opType).append(" {\n");

        for (GripField field : node.type.getFields()) {
            queryBuilder.append("  ").append(field.getName());
            appendArgs(queryBuilder, field);
            appendSelection(queryBuilder, field, 2);
            queryBuilder.append("\n");
        }
        queryBuilder.append("}");

        executeRepeaterSend(queryBuilder.toString(), node.name);
    }

    private void sendFieldToRepeater(SchemaGraphModel.Node parentNode, GripField field) {
        if (endpoint == null || endpoint.isEmpty()) {
            JOptionPane.showMessageDialog(core.getApi().userInterface().swingUtils().suiteFrame(), "No endpoint set. Configure endpoint in Schema tab.",
                    "Missing Endpoint", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StringBuilder queryBuilder = new StringBuilder();
        String opType = parentNode.name.equals(schema.getMutationTypeName()) ? "mutation" : "query";
        queryBuilder.append(opType).append(" {\n");
        queryBuilder.append("  ").append(field.getName());
        appendArgs(queryBuilder, field);
        appendSelection(queryBuilder, field, 2);
        queryBuilder.append("\n}");

        executeRepeaterSend(queryBuilder.toString(), field.getName());
    }

    private void appendArgs(StringBuilder sb, GripField field) {
        if (field.getArguments().isEmpty()) return;

        sb.append("(");
        for (int i = 0; i < field.getArguments().size(); i++) {
            if (i > 0) sb.append(", ");
            GripArgument arg = field.getArguments().get(i);
            sb.append(arg.getName()).append(": ").append(generatePlaceholder(arg.getType()));
        }
        sb.append(")");
    }

    private void appendSelection(StringBuilder sb, GripField field, int depth) {
        String typeName = field.getType().getName();
        if (typeName == null) return;

        GripType fieldType = schema.getType(typeName);
        if (fieldType == null || fieldType.getKind() == GripTypeKind.SCALAR ||
                fieldType.getKind() == GripTypeKind.ENUM) {
            return;
        }

        if (depth > 3) {
            sb.append(" { __typename }");
            return;
        }

        sb.append(" {\n");
        String indent = "  ".repeat(depth + 1);
        int count = 0;

        for (GripField f : fieldType.getFields()) {
            if (count >= 6) {
                sb.append(indent).append("__typename\n");
                break;
            }
            sb.append(indent).append(f.getName());

            String subTypeName = f.getType().getName();
            GripType subType = subTypeName != null ? schema.getType(subTypeName) : null;
            if (subType != null && subType.getKind() == GripTypeKind.OBJECT && depth < 2) {
                sb.append(" { __typename }");
            }
            sb.append("\n");
            count++;
        }

        sb.append("  ".repeat(depth)).append("}");
    }

    private String generatePlaceholder(GripTypeRef typeRef) {
        String name = typeRef.getName();
        if (name == null) name = "String";

        return switch (name) {
            case "String", "ID" -> "\"<VALUE>\"";
            case "Int" -> "0";
            case "Float" -> "0.0";
            case "Boolean" -> "true";
            default -> "\"<" + name.toUpperCase() + ">\"";
        };
    }

    private void executeRepeaterSend(String query, String tabName) {
        try {
            HttpRequest request = core.getHttpClient().buildRequest(transport, endpoint, query);

            core.getApi().repeater().sendToRepeater(request, "GraphQL Grip - " + tabName);
            JOptionPane.showMessageDialog(core.getApi().userInterface().swingUtils().suiteFrame(),
                    "Sent to Repeater as " + transport.description(), "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(core.getApi().userInterface().swingUtils().suiteFrame(), "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void resetView() {
        focusNodeName = null;
        selectedNodeName = null;
        viewTouched = false;
        rebuildGraph();
    }

    public void fitToView() {
        viewTouched = false;
        centerView();
        repaint();
    }

}
