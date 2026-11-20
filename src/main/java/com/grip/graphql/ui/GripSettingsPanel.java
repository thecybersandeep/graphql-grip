package com.grip.graphql.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanel;
import com.grip.graphql.AttackPayloadSetting;
import com.grip.graphql.GripConfig;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class GripSettingsPanel implements SettingsPanel {

    private static final int VALUE_COLUMNS = 7;

    private final GripConfig config;
    private final JPanel panel;
    private final Map<String, JSpinner> spinners = new LinkedHashMap<>();
    private final JLabel statusLabel;

    public GripSettingsPanel(MontoyaApi api, GripConfig config, GripTheme theme) {
        this.config = config;

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(theme.createTitledBorder("Attack Payload Defaults"));
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 6, 4, 6);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridy = 0;

        JLabel intro = new JLabel("Payload sizes used by the GraphQL Grip tab in Burp Repeater.");
        intro.setFont(theme.getNormalFont());
        intro.setForeground(theme.getSecondaryText());
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        settings.add(intro, constraints);
        constraints.gridwidth = 1;
        constraints.gridy++;

        for (AttackPayloadSetting setting : AttackPayloadSetting.values()) {
            addSettingRow(settings, constraints, theme, setting);
        }

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

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(settings);
        content.add(Box.createVerticalStrut(GripTheme.SPACING_MD));
        content.add(actions);

        this.panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(
                GripTheme.SPACING_LG,
                GripTheme.SPACING_LG,
                GripTheme.SPACING_LG,
                GripTheme.SPACING_LG));
        panel.add(content, BorderLayout.NORTH);

        api.userInterface().applyThemeToComponent(panel);
    }

    private void addSettingRow(JPanel target, GridBagConstraints constraints, GripTheme theme,
                               AttackPayloadSetting setting) {
        JSpinner spinner = createSpinner(setting);
        spinners.put(setting.key(), spinner);

        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        JLabel name = new JLabel(setting.label() + ":");
        name.setFont(theme.getNormalFont());
        target.add(name, constraints);

        constraints.gridx = 1;
        target.add(spinner, constraints);

        constraints.gridx = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        JLabel range = new JLabel(setting.minimum() + " to " + setting.maximum());
        range.setFont(theme.getNormalFont());
        range.setForeground(theme.getSecondaryText());
        target.add(range, constraints);

        constraints.gridy++;
    }

    private JSpinner createSpinner(AttackPayloadSetting setting) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                setting.clamp(config.getInteger(setting.key())),
                setting.minimum(), setting.maximum(), setting.step()));
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setColumns(VALUE_COLUMNS);
        }
        return spinner;
    }

    private void save() {
        for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
            config.setInteger(entry.getKey(), (Integer) entry.getValue().getValue());
        }
        statusLabel.setText("Saved. Payloads generated from now on use these sizes.");
    }

    private void reset() {
        for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
            config.resetToDefault(entry.getKey());
            entry.getValue().setValue(config.getInteger(entry.getKey()));
        }
        statusLabel.setText("Defaults restored.");
    }

    @Override
    public JComponent uiComponent() {
        return panel;
    }

    @Override
    public Set<String> keywords() {
        return Set.of("GraphQL", "Grip", "attack", "payload", "alias", "batch", "depth",
                "fragment", "directive", "count", "size", "dos");
    }
}
