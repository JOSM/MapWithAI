// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.advanced.PrefEntry;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
import org.openstreetmap.josm.spi.preferences.StringSetting;
import org.openstreetmap.josm.tools.GBC;

public class MapWithAIPreferences implements SubPreferenceSetting {
    private final JCheckBox switchLayerCheckBox;
    private final JCheckBox mergeBuildingAddressCheckBox;
    private final JSpinner maximumAdditionSpinner;
    private final ReplacementPreferenceTable replacementPreferenceTable;
    private final MapWithAIURLPreferenceTable mapwithaiUrlPreferenceTable;
    private final List<PrefEntry> replacementTableDisplayData;
    private final List<DataUrl> mapwithaiurlTableDisplayData;
    private static final int MAX_SELECTED_TO_EDIT = 1;
    private static final String SOURCE = "source";

    public MapWithAIPreferences() {
        switchLayerCheckBox = new JCheckBox();
        maximumAdditionSpinner = new JSpinner(
                new SpinnerNumberModel(MapWithAIPreferenceHelper.getMaximumAddition(), 0, 100, 1));
        mergeBuildingAddressCheckBox = new JCheckBox();
        replacementTableDisplayData = new ArrayList<>();
        fillReplacementTagDisplayData(replacementTableDisplayData);
        replacementPreferenceTable = new ReplacementPreferenceTable(replacementTableDisplayData);

        mapwithaiurlTableDisplayData = new ArrayList<>();
        fillMapWithAIURLTableDisplayData(mapwithaiurlTableDisplayData);
        mapwithaiUrlPreferenceTable = new MapWithAIURLPreferenceTable(mapwithaiurlTableDisplayData);
    }

    private static void fillReplacementTagDisplayData(List<PrefEntry> list) {
        final Map<String, String> current = new TreeMap<>(MapWithAIPreferenceHelper.getReplacementTags());
        for (final Entry<String, String> entry : current.entrySet()) {
            list.add(
                    new PrefEntry(entry.getKey(), new StringSetting(entry.getValue()), new StringSetting(null), false));
        }
    }

    private static void fillMapWithAIURLTableDisplayData(List<DataUrl> list) {
        MapWithAIPreferenceHelper.getMapWithAIURLs()
                .forEach(entry -> list.add(new DataUrl(entry.get(SOURCE), entry.get("url"),
                        Boolean.valueOf(entry.getOrDefault("enabled", "false")),
                        entry.getOrDefault("parameters", "[]"))));
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        final int width = 200;
        final int height = 200;
        final JLabel mapWithAIApiUrl = new JLabel(tr("{0} API URLs", MapWithAIPlugin.NAME));
        final JLabel switchLayer = new JLabel(tr("Automatically switch layers"));
        final JLabel maximumAddition = new JLabel(tr("Maximum features (add)"));
        final JLabel mergeBuildingWithAddress = new JLabel(tr("Merge address nodes and buildings"));

        mapWithAIApiUrl.setToolTipText(tr("The URL that will be called to get data from"));
        switchLayer.setToolTipText(
                tr("If checked, automatically switch from the {0} layer to the OSM layer when objects are added",
                        MapWithAIPlugin.NAME));
        maximumAddition.setToolTipText(tr(
                "This is the maximum number of complete OSM objects that can be added from the {0} layer, child objects do not count to this limit",
                MapWithAIPlugin.NAME));
        mergeBuildingWithAddress.setToolTipText(tr(
                "If checked, automatically merge address nodes onto added buildings, if and only if one address is withing the building boundary"));

        switchLayerCheckBox.setSelected(MapWithAIPreferenceHelper.isSwitchLayers());
        switchLayerCheckBox.setToolTipText(switchLayer.getToolTipText());
        mergeBuildingAddressCheckBox.setSelected(MapWithAIPreferenceHelper.isMergeBuildingAddress());
        mergeBuildingAddressCheckBox.setToolTipText(mergeBuildingWithAddress.getToolTipText());

        final JPanel pane = new JPanel(new GridBagLayout());
        pane.setAlignmentY(Component.TOP_ALIGNMENT);
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);

        final GBC first = GBC.std().weight(0, 1).anchor(GridBagConstraints.WEST);
        final GBC second = GBC.eol().fill(GridBagConstraints.HORIZONTAL);
        final GBC buttonInsets = GBC.std().insets(5, 5, 0, 0);

        pane.add(mapWithAIApiUrl, first);

        final JScrollPane scroll1 = new JScrollPane(mapwithaiUrlPreferenceTable);
        scroll1.setToolTipText(mapWithAIApiUrl.getToolTipText());
        pane.add(scroll1, GBC.eol().fill(GridBagConstraints.BOTH));
        scroll1.setPreferredSize(new Dimension(width, height));

        pane.add(new JLabel(), first);
        final JPanel replaceAddEditDeleteScroll1 = new JPanel(new GridBagLayout());
        pane.add(replaceAddEditDeleteScroll1, second);
        final JButton addScroll1 = new JButton(tr("Add"));
        replaceAddEditDeleteScroll1.add(addScroll1, buttonInsets);
        addScroll1.addActionListener(e -> {
            mapwithaiurlTableDisplayData.add(DataUrl.emptyData());
            mapwithaiUrlPreferenceTable.fireDataChanged();
        });
        final JButton editScroll1 = new JButton(tr("Edit Parameters"));
        replaceAddEditDeleteScroll1.add(editScroll1, buttonInsets);
        editScroll1.addActionListener(e -> {
            final List<DataUrl> toEdit = mapwithaiUrlPreferenceTable.getSelectedItems();
            if (toEdit.size() == MAX_SELECTED_TO_EDIT) {
                mapwithaiUrlPreferenceTable.editPreference(gui);
            }
        });
        final JButton deleteScroll1 = new JButton(tr("Delete"));
        replaceAddEditDeleteScroll1.add(deleteScroll1, buttonInsets);
        deleteScroll1.addActionListener(e -> {
            final List<DataUrl> toRemove = mapwithaiUrlPreferenceTable.getSelectedItems();
            if (!toRemove.isEmpty()) {
                mapwithaiurlTableDisplayData.removeAll(toRemove);
            }
            mapwithaiUrlPreferenceTable.fireDataChanged();
        });

        pane.add(switchLayer, first);

        pane.add(switchLayerCheckBox, second);
        switchLayerCheckBox.setToolTipText(switchLayer.getToolTipText());

        pane.add(maximumAddition, first);
        pane.add(maximumAdditionSpinner, second);
        maximumAdditionSpinner.setToolTipText(maximumAddition.getToolTipText());

        pane.add(mergeBuildingWithAddress, first);
        pane.add(mergeBuildingAddressCheckBox, second);

        final Component expertHorizontalGlue = Box.createHorizontalGlue();
        pane.add(expertHorizontalGlue, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        final JLabel replacementTags = new JLabel(tr("Replacement Tags"));
        pane.add(replacementTags, first);
        final JScrollPane scroll2 = new JScrollPane(replacementPreferenceTable);
        pane.add(scroll2, GBC.eol().fill(GridBagConstraints.BOTH));
        scroll2.setPreferredSize(new Dimension(width, height));

        pane.add(new JLabel(), first);
        final JPanel replaceAddEditDeleteScroll2 = new JPanel(new GridBagLayout());
        pane.add(replaceAddEditDeleteScroll2, second);
        final JButton addScroll2 = new JButton(tr("Add"));
        replaceAddEditDeleteScroll2.add(addScroll2, buttonInsets);
        addScroll2.addActionListener(e -> {
            final PrefEntry pe = replacementPreferenceTable.addPreference(gui);
            if ((pe != null) && (pe.getValue() instanceof StringSetting)) {
                replacementTableDisplayData.add(pe);
                Collections.sort(replacementTableDisplayData);
                replacementPreferenceTable.fireDataChanged();
            }
        });

        final JButton editScroll2 = new JButton(tr("Edit"));
        replaceAddEditDeleteScroll2.add(editScroll2, buttonInsets);
        editScroll2.addActionListener(e -> {
            final List<PrefEntry> toEdit = replacementPreferenceTable.getSelectedItems();
            if (toEdit.size() == MAX_SELECTED_TO_EDIT) {
                replacementPreferenceTable.editPreference(gui);
            }
        });

        final JButton deleteScroll2 = new JButton(tr("Delete"));
        replaceAddEditDeleteScroll2.add(deleteScroll2, buttonInsets);
        deleteScroll2.addActionListener(e -> {
            final List<PrefEntry> toRemove = replacementPreferenceTable.getSelectedItems();
            if (!toRemove.isEmpty()) {
                replacementTableDisplayData.removeAll(toRemove);
            }
            replacementPreferenceTable.fireDataChanged();
        });

        Arrays.asList(replaceAddEditDeleteScroll2, scroll2, expertHorizontalGlue, replacementTags)
                .forEach(ExpertToggleAction::addVisibilitySwitcher);

        getTabPreferenceSetting(gui).addSubTab(this, MapWithAIPlugin.NAME, pane,
                tr("{0} preferences", MapWithAIPlugin.NAME));
    }

    @Override
    public boolean ok() {
        final ArrayList<DataUrl> tData = new ArrayList<>(
                mapwithaiurlTableDisplayData.stream().distinct()
                        .filter(data -> !data.getMap().getOrDefault("url", "http://example.com")
                                .equalsIgnoreCase(DataUrl.emptyData().getMap().get("url")))
                        .collect(Collectors.toList()));
        mapwithaiurlTableDisplayData.clear();
        mapwithaiurlTableDisplayData.addAll(tData);
        MapWithAIPreferenceHelper.setMapWithAIURLs(convertUrlPrefToMap(mapwithaiurlTableDisplayData));
        MapWithAIPreferenceHelper.setSwitchLayers(switchLayerCheckBox.isSelected(), true);
        final Object value = maximumAdditionSpinner.getValue();
        if (value instanceof Number) {
            MapWithAIPreferenceHelper.setMaximumAddition(((Number) value).intValue(), true);
        }
        MapWithAIPreferenceHelper.setReplacementTags(convertReplacementPrefToMap(replacementTableDisplayData));
        return false;
    }

    private static List<Map<String, String>> convertUrlPrefToMap(List<DataUrl> displayData) {
        return displayData.stream().map(DataUrl::getMap)
                .filter(map -> !map.getOrDefault("url", "").isEmpty()
                        && !DataUrl.emptyData().getMap().get("url").equals(map.get("url")))
                .collect(Collectors.toList());
    }

    private static Map<String, String> convertReplacementPrefToMap(List<PrefEntry> displayData) {
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
