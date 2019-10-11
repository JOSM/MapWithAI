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
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;

public class MapWithAIPreferences implements SubPreferenceSetting {
    private final JComboBox<String> possibleMapWithAIApiUrl;

    private final JCheckBox switchLayerCheckBox;

    private final JSpinner maximumAdditionSpinner;

    public MapWithAIPreferences() {
        possibleMapWithAIApiUrl = new JComboBox<>();
        switchLayerCheckBox = new JCheckBox();
        maximumAdditionSpinner = new JSpinner(new SpinnerNumberModel(MapWithAIDataUtils.getMaximumAddition(), 0, 100, 1));
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        final JLabel mapWithAIApiUrl = new JLabel(tr("{0} API URL", MapWithAIPlugin.NAME));
        final JLabel switchLayer = new JLabel(tr("Automatically switch layers"));
        final JLabel maximumAddition = new JLabel(tr("Maximum features (add)"));
        final JPanel container = new JPanel(new GridBagLayout());
        container.setAlignmentY(Component.TOP_ALIGNMENT);
        final GridBagConstraints constraints = new GridBagConstraints();

        possibleMapWithAIApiUrl.setEditable(true);
        possibleMapWithAIApiUrl.setPrototypeDisplayValue("https://example.url/some/end/point");
        final Component textField = possibleMapWithAIApiUrl.getEditor().getEditorComponent();
        if (textField instanceof JTextField) {
            ((JTextField) textField).setColumns(36);
        }
        for (final String url : MapWithAIDataUtils.getMapWithAIURLs()) {
            possibleMapWithAIApiUrl.addItem(url);
        }
        possibleMapWithAIApiUrl.setSelectedItem(MapWithAIDataUtils.getMapWithAIUrl());

        switchLayerCheckBox.setSelected(MapWithAIDataUtils.isSwitchLayers());

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

        getTabPreferenceSetting(gui).addSubTab(this, MapWithAIPlugin.NAME, container);
    }

    @Override
    public boolean ok() {
        MapWithAIDataUtils.setMapWithAIUrl((String) possibleMapWithAIApiUrl.getSelectedItem(), true);
        MapWithAIDataUtils.setSwitchLayers(switchLayerCheckBox.isSelected(), true);
        final Object value = maximumAdditionSpinner.getValue();
        if (value instanceof Number) {
            MapWithAIDataUtils.setMaximumAddition(((Number) value).intValue(), true);
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

    /**
     * @return {@code JSpinner} for the maximum additions
     */
    public JSpinner getMaximumAdditionSpinner() {
        return maximumAdditionSpinner;
    }
}
