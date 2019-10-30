package org.openstreetmap.josm.plugins.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.advanced.PrefEntry;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.spi.preferences.StringSetting;
import org.openstreetmap.josm.tools.GBC;

public class MapWithAIPreferences implements SubPreferenceSetting {
    private final JComboBox<String> possibleMapWithAIApiUrl;
    private final JCheckBox switchLayerCheckBox;
    private final JCheckBox mergeBuildingAddressCheckBox;
    private final JSpinner maximumAdditionSpinner;
    private final ReplacementPreferenceTable table;
    private final List<PrefEntry> displayData;
    private static final int MAX_SELECTED_TO_EDIT = 1;

    public MapWithAIPreferences() {
        possibleMapWithAIApiUrl = new JComboBox<>();
        switchLayerCheckBox = new JCheckBox();
        maximumAdditionSpinner = new JSpinner(
                new SpinnerNumberModel(MapWithAIPreferenceHelper.getMaximumAddition(), 0, 100, 1));
        mergeBuildingAddressCheckBox = new JCheckBox();
        displayData = new ArrayList<>();
        fillDisplayData(displayData);
        table = new ReplacementPreferenceTable(displayData);
    }

    private static void fillDisplayData(List<PrefEntry> list) {
        final Map<String, String> current = new TreeMap<>(MapWithAIPreferenceHelper.getReplacementTags());
        for (final Entry<String, String> entry : current.entrySet()) {
            list.add(new PrefEntry(entry.getKey(), new StringSetting(entry.getValue()), new StringSetting(null), false));
        }
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        final JLabel mapWithAIApiUrl = new JLabel(tr("{0} API URL", MapWithAIPlugin.NAME));
        final JLabel switchLayer = new JLabel(tr("Automatically switch layers"));
        final JLabel maximumAddition = new JLabel(tr("Maximum features (add)"));
        final JLabel mergeBuildingWithAddress = new JLabel(tr("Merge address nodes and buildings"));
        final JPanel nonExpert = new JPanel(new GridBagLayout());
        nonExpert.setAlignmentY(Component.TOP_ALIGNMENT);
        nonExpert.setAlignmentX(Component.LEFT_ALIGNMENT);

        possibleMapWithAIApiUrl.setEditable(true);
        possibleMapWithAIApiUrl.setPrototypeDisplayValue("https://example.url/some/end/point");
        final Component textField = possibleMapWithAIApiUrl.getEditor().getEditorComponent();
        if (textField instanceof JTextField) {
            ((JTextField) textField).setColumns(24);
        }
        for (final String url : MapWithAIPreferenceHelper.getMapWithAIURLs()) {
            possibleMapWithAIApiUrl.addItem(url);
        }
        possibleMapWithAIApiUrl.setSelectedItem(MapWithAIPreferenceHelper.getMapWithAIUrl());

        switchLayerCheckBox.setSelected(MapWithAIPreferenceHelper.isSwitchLayers());
        mergeBuildingAddressCheckBox.setSelected(MapWithAIPreferenceHelper.isMergeBuildingAddress());

        nonExpert.add(mapWithAIApiUrl);

        nonExpert.add(possibleMapWithAIApiUrl, GBC.eol().fill(GridBagConstraints.HORIZONTAL));

        nonExpert.add(switchLayer);

        nonExpert.add(switchLayerCheckBox, GBC.eol().fill(GridBagConstraints.HORIZONTAL));

        nonExpert.add(maximumAddition);
        nonExpert.add(maximumAdditionSpinner, GBC.eol().fill(GridBagConstraints.HORIZONTAL));

        nonExpert.add(mergeBuildingWithAddress);
        nonExpert.add(mergeBuildingAddressCheckBox, GBC.eol().fill(GridBagConstraints.HORIZONTAL));

        final JPanel expert = new JPanel(new GridBagLayout());
        expert.add(Box.createHorizontalGlue(), GBC.std().fill(GridBagConstraints.HORIZONTAL));
        final JScrollPane scroll = new JScrollPane(table);
        expert.add(scroll, GBC.eol().fill(GridBagConstraints.BOTH));
        scroll.setPreferredSize(new Dimension(400, 200));

        final JButton add = new JButton(tr("Add"));
        expert.add(Box.createHorizontalGlue(), GBC.std().fill(GridBagConstraints.HORIZONTAL));
        expert.add(add, GBC.std().insets(0, 5, 0, 0));
        add.addActionListener(e -> {
            final PrefEntry pe = table.addPreference(gui);
            if (pe != null && pe.getValue() instanceof StringSetting) {
                displayData.add(pe);
                Collections.sort(displayData);
                table.fireDataChanged();
            }
        });

        final JButton edit = new JButton(tr("Edit"));
        expert.add(edit, GBC.std().insets(5, 5, 0, 0));
        edit.addActionListener(e -> {
            final List<PrefEntry> toEdit = table.getSelectedItems();
            if (toEdit.size() == MAX_SELECTED_TO_EDIT) {
                table.editPreference(gui);
            }
        });

        final JButton delete = new JButton(tr("Delete"));
        expert.add(delete, GBC.std().insets(5, 5, 0, 0));
        delete.addActionListener(e -> {
            final List<PrefEntry> toRemove = table.getSelectedItems();
            if (!toRemove.isEmpty()) {
                displayData.removeAll(toRemove);
            }
            table.fireDataChanged();
        });

        ExpertToggleAction.addVisibilitySwitcher(expert);

        final JPanel pane = new JPanel(new GridBagLayout());
        pane.add(nonExpert, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        pane.add(expert);

        getTabPreferenceSetting(gui).addSubTab(this, MapWithAIPlugin.NAME, pane);
    }

    @Override
    public boolean ok() {
        MapWithAIPreferenceHelper.setMapWithAIUrl((String) possibleMapWithAIApiUrl.getSelectedItem(), true);
        MapWithAIPreferenceHelper.setSwitchLayers(switchLayerCheckBox.isSelected(), true);
        final Object value = maximumAdditionSpinner.getValue();
        if (value instanceof Number) {
            MapWithAIPreferenceHelper.setMaximumAddition(((Number) value).intValue(), true);
        }
        MapWithAIPreferenceHelper.setReplacementTags(convertPrefToMap(displayData));
        return false;
    }

    private static Map<String, String> convertPrefToMap(List<PrefEntry> displayData) {
        final Map<String, String> returnMap = displayData.isEmpty() ? Collections.emptyMap() : new TreeMap<>();
        displayData.forEach(entry -> returnMap.put(entry.getKey(), entry.getValue().getValue().toString()));
        return returnMap;
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
