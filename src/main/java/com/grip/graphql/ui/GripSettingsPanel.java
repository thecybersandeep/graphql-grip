package com.grip.graphql.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanel;
import com.grip.graphql.GripConfig;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class GripSettingsPanel implements SettingsPanel {

    private final GripConfig config;
    private final JPanel panel;
    private final JSpinner aliasCount;
    private final JSpinner batchCount;
    private final JSpinner fieldCount;
    private final JSpinner directiveCount;
    private final JSpinner depthCount;
    private final JSpinner fragmentCount;
    private final JLabel statusLabel;

    public GripSettingsPanel(MontoyaApi api, GripConfig config, GripTheme theme) {
        this.config = config;
        this.panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(
                GripTheme.SPACING_LG,
                GripTheme.SPACING_LG,
                GripTheme.SPACING_LG,
                GripTheme.SPACING_LG));

        aliasCount = createSpinner(config.getInteger(GripConfig.ATTACK_ALIAS_COUNT), 10, 10000, 50);
        batchCount = createSpinner(config.getInteger(GripConfig.ATTACK_BATCH_COUNT), 2, 1000, 5);
        fieldCount = createSpinner(config.getInteger(GripConfig.ATTACK_FIELD_COUNT), 50, 10000, 100);
        directiveCount = createSpinner(config.getInteger(GripConfig.ATTACK_DIRECTIVE_COUNT), 10, 500, 10);
        depthCount = createSpinner(config.getInteger(GripConfig.ATTACK_DEPTH_COUNT), 3, 100, 5);
        fragmentCount = createSpinner(config.getInteger(GripConfig.ATTACK_FRAGMENT_COUNT), 10, 500, 10);

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(theme.createTitledBorder("Attack Payload Defaults"));
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 6, 4, 6);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridy = 0;

        addSettingRow(settings, constraints, "Alias count", aliasCount);
        addSettingRow(settings, constraints, "Batch count", batchCount);
        addSettingRow(settings, constraints, "Field count", fieldCount);
        addSettingRow(settings, constraints, "Directive count", directiveCount);
        addSettingRow(settings, constraints, "Depth count", depthCount);
        addSettingRow(settings, constraints, "Fragment count", fragmentCount);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, GripTheme.SPACING_SM, 0));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveButton = new JButton("Save");
        theme.stylePrimaryButton(saveButton);
        saveButton.addActionListener(event -> save());

        JButton resetButton = new JButton("Reset to Defaults");
        theme.styleSecondaryButton(resetButton);
        resetButton.addActionListener(event -> reset());

        statusLabel = new JLabel(" ");
        statusLabel.setFont(theme.getNormalFont());

        actions.add(saveButton);
        actions.add(resetButton);
        actions.add(statusLabel);

        panel.add(settings);
        panel.add(Box.createVerticalStrut(GripTheme.SPACING_MD));
        panel.add(actions);
        panel.add(Box.createVerticalGlue());

        api.userInterface().applyThemeToComponent(panel);
    }

    private JSpinner createSpinner(Integer value, int minimum, int maximum, int step) {
        int initialValue = value != null ? Math.max(minimum, Math.min(maximum, value)) : minimum;
        return new JSpinner(new SpinnerNumberModel(initialValue, minimum, maximum, step));
    }

    private void addSettingRow(JPanel target, GridBagConstraints constraints,
                               String label, JSpinner spinner) {
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        target.add(new JLabel(label + ":"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        target.add(spinner, constraints);
        constraints.gridy++;
    }

    private void save() {
        config.setInteger(GripConfig.ATTACK_ALIAS_COUNT, (Integer) aliasCount.getValue());
        config.setInteger(GripConfig.ATTACK_BATCH_COUNT, (Integer) batchCount.getValue());
        config.setInteger(GripConfig.ATTACK_FIELD_COUNT, (Integer) fieldCount.getValue());
        config.setInteger(GripConfig.ATTACK_DIRECTIVE_COUNT, (Integer) directiveCount.getValue());
        config.setInteger(GripConfig.ATTACK_DEPTH_COUNT, (Integer) depthCount.getValue());
        config.setInteger(GripConfig.ATTACK_FRAGMENT_COUNT, (Integer) fragmentCount.getValue());
        statusLabel.setText("Settings saved");
    }

    private void reset() {
        config.resetToDefault(GripConfig.ATTACK_ALIAS_COUNT);
        config.resetToDefault(GripConfig.ATTACK_BATCH_COUNT);
        config.resetToDefault(GripConfig.ATTACK_FIELD_COUNT);
        config.resetToDefault(GripConfig.ATTACK_DIRECTIVE_COUNT);
        config.resetToDefault(GripConfig.ATTACK_DEPTH_COUNT);
        config.resetToDefault(GripConfig.ATTACK_FRAGMENT_COUNT);

        aliasCount.setValue(config.getInteger(GripConfig.ATTACK_ALIAS_COUNT));
        batchCount.setValue(config.getInteger(GripConfig.ATTACK_BATCH_COUNT));
        fieldCount.setValue(config.getInteger(GripConfig.ATTACK_FIELD_COUNT));
        directiveCount.setValue(config.getInteger(GripConfig.ATTACK_DIRECTIVE_COUNT));
        depthCount.setValue(config.getInteger(GripConfig.ATTACK_DEPTH_COUNT));
        fragmentCount.setValue(config.getInteger(GripConfig.ATTACK_FRAGMENT_COUNT));
        statusLabel.setText("Defaults restored");
    }

    @Override
    public JComponent uiComponent() {
        return panel;
    }

    @Override
    public Set<String> keywords() {
        return Set.of("GraphQL", "Grip", "attack", "alias", "batch", "depth", "fragment");
    }
}
