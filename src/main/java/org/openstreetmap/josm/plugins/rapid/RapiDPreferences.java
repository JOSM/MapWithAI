package org.openstreetmap.josm.plugins.rapid;

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
import org.openstreetmap.josm.plugins.rapid.backend.RapiDDataUtils;

public class RapiDPreferences implements SubPreferenceSetting {
    private final JComboBox<String> possibleRapidApiUrl;

    private final JCheckBox switchLayerCheckBox;

    private final JSpinner maximumAdditionSpinner;

    public RapiDPreferences() {
        possibleRapidApiUrl = new JComboBox<>();
        switchLayerCheckBox = new JCheckBox();
        maximumAdditionSpinner = new JSpinner(new SpinnerNumberModel(RapiDDataUtils.getMaximumAddition(), 0, 100, 1));
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        final JLabel rapidApiUrl = new JLabel(tr("RapiD API URL"));
        final JLabel switchLayer = new JLabel(tr("Automatically switch layers"));
        final JLabel maximumAddition = new JLabel(tr("Maximum features (add)"));
        final JPanel container = new JPanel(new GridBagLayout());
        container.setAlignmentY(Component.TOP_ALIGNMENT);
        final GridBagConstraints constraints = new GridBagConstraints();

        possibleRapidApiUrl.setEditable(true);
        possibleRapidApiUrl.setPrototypeDisplayValue("https://example.url/some/end/point");
        final Component textField = possibleRapidApiUrl.getEditor().getEditorComponent();
        if (textField instanceof JTextField) {
            ((JTextField) textField).setColumns(36);
        }
        for (final String url : RapiDDataUtils.getRapiDURLs()) {
            possibleRapidApiUrl.addItem(url);
        }
        possibleRapidApiUrl.setSelectedItem(RapiDDataUtils.getRapiDURL());

        switchLayerCheckBox.setSelected(RapiDDataUtils.isSwitchLayers());

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = .1;
        constraints.weighty = 0;
        constraints.insets = new Insets(5, 10, 5, 10);
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        container.add(rapidApiUrl, constraints);

        constraints.gridx++;
        container.add(possibleRapidApiUrl, constraints);

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

        getTabPreferenceSetting(gui).addSubTab(this, "RapiD", container);
    }

    @Override
    public boolean ok() {
        RapiDDataUtils.setRapiDUrl((String) possibleRapidApiUrl.getSelectedItem(), true);
        RapiDDataUtils.setSwitchLayers(switchLayerCheckBox.isSelected(), true);
        final Object value = maximumAdditionSpinner.getValue();
        if (value instanceof Number) {
            RapiDDataUtils.setMaximumAddition(((Number) value).intValue(), true);
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
     * @return {@code JComboBox} with possible rapid api urls
     */
    public JComboBox<String> getPossibleRapidApiUrl() {
        return possibleRapidApiUrl;
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
