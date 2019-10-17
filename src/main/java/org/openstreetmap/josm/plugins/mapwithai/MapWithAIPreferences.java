package org.openstreetmap.josm.plugins.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;

public class MapWithAIPreferences implements SubPreferenceSetting {
    private final JComboBox<String> possibleMapWithAIApiUrl;
    private final JCheckBox switchLayerCheckBox;
    private final JCheckBox mergeBuildingAddressCheckBox;
    private final JSpinner maximumAdditionSpinner;

    public MapWithAIPreferences() {
        possibleMapWithAIApiUrl = new JComboBox<>();
        switchLayerCheckBox = new JCheckBox();
        maximumAdditionSpinner = new JSpinner(
                new SpinnerNumberModel(MapWithAIPreferenceHelper.getMaximumAddition(), 0, 100, 1));
        mergeBuildingAddressCheckBox = new JCheckBox();
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        final JLabel mapWithAIApiUrl = new JLabel(tr("{0} API URL", MapWithAIPlugin.NAME));
        final JLabel switchLayer = new JLabel(tr("Automatically switch layers"));
        final JLabel maximumAddition = new JLabel(tr("Maximum features (add)"));
        final JLabel mergeBuildingWithAddress = new JLabel(tr("Merge existing address nodes onto added buildings?"));
        final JPanel container = new JPanel(new GridBagLayout());
        container.setAlignmentY(Component.TOP_ALIGNMENT);
        final GridBagConstraints constraints = new GridBagConstraints();

        possibleMapWithAIApiUrl.setEditable(true);
        possibleMapWithAIApiUrl.setPrototypeDisplayValue("https://example.url/some/end/point");
        final Component textField = possibleMapWithAIApiUrl.getEditor().getEditorComponent();
        if (textField instanceof JTextField) {
            ((JTextField) textField).setColumns(36);
        }
        for (final String url : MapWithAIPreferenceHelper.getMapWithAIURLs()) {
            possibleMapWithAIApiUrl.addItem(url);
        }
        possibleMapWithAIApiUrl.setSelectedItem(MapWithAIPreferenceHelper.getMapWithAIUrl());

        switchLayerCheckBox.setSelected(MapWithAIPreferenceHelper.isSwitchLayers());
        mergeBuildingAddressCheckBox.setSelected(MapWithAIPreferenceHelper.isMergeBuildingAddress());

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = .1;
        constraints.weighty = 0;
        constraints.insets = new Insets(5, 10, 5, 10);
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        container.add(mapWithAIApiUrl, constraints);

        constraints.gridx++;
        container.add(possibleMapWithAIApiUrl, constraints);

        constraints.gridx--;
        constraints.gridy++;
        container.add(switchLayer, constraints);

        constraints.gridx++;
        container.add(switchLayerCheckBox, constraints);

        constraints.gridx--;
        constraints.gridy++;
        container.add(maximumAddition, constraints);
        constraints.gridx++;
        container.add(maximumAdditionSpinner, constraints);

        constraints.gridx--;
        constraints.gridy++;
        container.add(mergeBuildingWithAddress, constraints);
        constraints.gridx++;
        container.add(mergeBuildingAddressCheckBox, constraints);

        getTabPreferenceSetting(gui).addSubTab(this, MapWithAIPlugin.NAME, container);
    }

    @Override
    public boolean ok() {
        MapWithAIPreferenceHelper.setMapWithAIUrl((String) possibleMapWithAIApiUrl.getSelectedItem(), true);
        MapWithAIPreferenceHelper.setSwitchLayers(switchLayerCheckBox.isSelected(), true);
        final Object value = maximumAdditionSpinner.getValue();
        if (value instanceof Number) {
            MapWithAIPreferenceHelper.setMaximumAddition(((Number) value).intValue(), true);
        }
        return false;
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public TabPreferenceSetting getTabPreferenceSetting(PreferenceTabbedPane gui) {
        return gui.getPluginPreference();
    }

    /**
     * @return {@code JComboBox} with possible MapWithAI api urls
     */
    public JComboBox<String> getPossibleMapWithAIApiUrl() {
        return possibleMapWithAIApiUrl;
    }

    /**
     * @return The {@code JCheckBox} for whether or not we are switching layers.
     */
    public JCheckBox getSwitchLayerCheckBox() {
        return switchLayerCheckBox;
    }

    public JCheckBox getMergeBuildingAddressCheckBox() {
        return mergeBuildingAddressCheckBox;
    }

    /**
     * @return {@code JSpinner} for the maximum additions
     */
    public JSpinner getMaximumAdditionSpinner() {
        return maximumAdditionSpinner;
    }
}
