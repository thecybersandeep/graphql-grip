package com.grip.graphql.ui;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import com.grip.graphql.GripCore;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GripContextMenu implements ContextMenuItemsProvider {

    private final GripCore core;

    public GripContextMenu(GripCore core) {
        this.core = core;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> menuItems = new ArrayList<>();

        if (!isRelevantTool(event.toolType())) {
            return menuItems;
        }

        List<HttpRequestResponse> requestResponses = event.messageEditorRequestResponse()
                .map(editor -> List.of(editor.requestResponse()))
                .orElseGet(() -> event.selectedRequestResponses());

        if (requestResponses.isEmpty()) {
            return menuItems;
        }

        JMenuItem sendToGrip = new JMenuItem("Send to GraphQL Grip");
        sendToGrip.setFont(core.getTheme().getBoldFont());
        sendToGrip.addActionListener(e -> sendToGrip(requestResponses));

        menuItems.add(sendToGrip);
        return menuItems;
    }

    private boolean isRelevantTool(ToolType toolType) {
        return toolType == ToolType.PROXY ||
               toolType == ToolType.REPEATER ||
               toolType == ToolType.SCANNER ||
               toolType == ToolType.INTRUDER ||
               toolType == ToolType.TARGET ||
               toolType == ToolType.LOGGER;
    }

    private void sendToGrip(List<HttpRequestResponse> requestResponses) {
        if (requestResponses.isEmpty()) return;

        HttpRequestResponse rr = requestResponses.get(0);
        if (rr.request() == null) return;
        String endpoint = rr.request().url();

        core.getMainTab().setTargetEndpoint(endpoint);
        core.getMainTab().selectTab(0);

        SwingUtilities.invokeLater(() -> {
            core.getMainTab().triggerScan();
        });

        core.log("Scanning endpoint: " + endpoint);
    }
}
