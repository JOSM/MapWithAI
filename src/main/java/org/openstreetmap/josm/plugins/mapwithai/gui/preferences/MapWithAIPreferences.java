// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.advanced.PrefEntry;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai.MapWithAIProvidersPanel;
import org.openstreetmap.josm.spi.preferences.StringSetting;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.OpenBrowser;

/**
 * The MapWithAI preference pane
 */
public class MapWithAIPreferences extends DefaultTabPreferenceSetting {
    private final JCheckBox switchLayerCheckBox;
    private final JCheckBox mergeBuildingAddressCheckBox;
    private final JSpinner maximumAdditionSpinner;
    private final ReplacementPreferenceTable replacementPreferenceTable;
    private final List<PrefEntry> replacementTableDisplayData;
    private static final int MAX_SELECTED_TO_EDIT = 1;

    /**
     * Create the preference panel
     */
    public MapWithAIPreferences() {
        super("mapwithai", tr("MapWithAI preferences"), tr("Modify MapWithAI preferences"), false, new JTabbedPane());

        switchLayerCheckBox = new JCheckBox();
        maximumAdditionSpinner = new JSpinner(new SpinnerNumberModel(MapWithAIPreferenceHelper.getMaximumAddition(), 0,
                MapWithAIPreferenceHelper.getDefaultMaximumAddition() * 10, 1));
        mergeBuildingAddressCheckBox = new JCheckBox();
        replacementTableDisplayData = new ArrayList<>();
        fillReplacementTagDisplayData(replacementTableDisplayData);
        replacementPreferenceTable = new ReplacementPreferenceTable(replacementTableDisplayData);
    }

    private static void fillReplacementTagDisplayData(List<PrefEntry> list) {
        MapWithAIPreferenceHelper.getReplacementTags().forEach(
                (key, value) -> list.add(new PrefEntry(key, new StringSetting(value), new StringSetting(null), false)));
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        final var p = gui.createPreferenceTab(this);
        final var panel = getTabPane();
        if (panel.getTabCount() == 0) {
            panel.addTab(tr("Servers"), getServerList(gui));
            panel.addTab(tr("Settings"), getSettingsPanel(gui));
        }
        p.add(panel, GBC.std().fill(GridBagConstraints.BOTH));
    }

    private static Component getServerList(PreferenceTabbedPane gui) {
        return new MapWithAIProvidersPanel(gui, MapWithAIProvidersPanel.Options.SHOW_ACTIVE);
    }

    private Component getSettingsPanel(PreferenceTabbedPane gui) {
        final var pane = new JPanel(new GridBagLayout());
        final var width = 200;
        final var height = 200;
        final var switchLayer = new JLabel(tr("Automatically switch layers"));
        final var maximumAddition = new JLabel(tr("Maximum features (add)"));
        final var mergeBuildingWithAddress = new JLabel(tr("Merge address nodes and buildings"));

        switchLayer.setToolTipText(
                tr("If checked, automatically switch from the {0} layer to the OSM layer when objects are added",
                        MapWithAIPlugin.NAME));
        maximumAddition.setToolTipText(
                tr("This is the maximum number of complete OSM objects that can be added from the {0} layer, "
                        + "child objects do not count to this limit", MapWithAIPlugin.NAME));
        mergeBuildingWithAddress
                .setToolTipText(tr("If checked, automatically merge address nodes onto added buildings, "
                        + "if and only if one address is within the building boundary"));

        switchLayerCheckBox.setSelected(MapWithAIPreferenceHelper.isSwitchLayers());
        switchLayerCheckBox.setToolTipText(switchLayer.getToolTipText());
        mergeBuildingAddressCheckBox.setSelected(MapWithAIPreferenceHelper.isMergeBuildingAddress());
        mergeBuildingAddressCheckBox.setToolTipText(mergeBuildingWithAddress.getToolTipText());

        pane.setAlignmentY(Component.TOP_ALIGNMENT);
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);

        final var first = GBC.std().weight(0, 1).anchor(GridBagConstraints.WEST);
        final var second = GBC.eol().fill(GridBagConstraints.HORIZONTAL);
        final var buttonInsets = GBC.std().insets(5, 5, 0, 0);

        pane.add(switchLayer, first);

        pane.add(switchLayerCheckBox, second);
        switchLayerCheckBox.setToolTipText(switchLayer.getToolTipText());

        pane.add(maximumAddition, first);
        pane.add(maximumAdditionSpinner, second);
        maximumAdditionSpinner.setToolTipText(maximumAddition.getToolTipText());

        pane.add(mergeBuildingWithAddress, first);
        pane.add(mergeBuildingAddressCheckBox, second);

        final var expertHorizontalGlue = Box.createHorizontalGlue();
        pane.add(expertHorizontalGlue, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        final var previewFeatureSets = new JLabel(tr("Show Preview DataSets"));
        final var previewFeatureSetCheckbox = new JCheckBox();
        final var previewFeatureSetProperty = MapWithAILayerInfo.SHOW_PREVIEW;
        previewFeatureSetCheckbox.setToolTipText(tr("If selected, show datasets which may have various issues"));
        previewFeatureSetCheckbox.setSelected(Boolean.TRUE.equals(previewFeatureSetProperty.get()));
        previewFeatureSetCheckbox
                .addChangeListener(l -> previewFeatureSetProperty.put(previewFeatureSetCheckbox.isSelected()));
        pane.add(previewFeatureSets, first);
        pane.add(previewFeatureSetCheckbox, second);

        final var replacementTags = new JLabel(tr("Replacement Tags (to be replaced on download)"));
        pane.add(replacementTags, first);
        final var scroll2 = new JScrollPane(replacementPreferenceTable);
        pane.add(scroll2, GBC.eol().fill(GridBagConstraints.BOTH));
        scroll2.setPreferredSize(new Dimension(width, height));

        pane.add(new JLabel(), first);
        final var replaceAddEditDeleteScroll2 = new JPanel(new GridBagLayout());
        pane.add(replaceAddEditDeleteScroll2, second);
        final var addScroll2 = new JButton(tr("Add"));
        replaceAddEditDeleteScroll2.add(addScroll2, buttonInsets);
        addScroll2.addActionListener(e -> {
            final var pe = replacementPreferenceTable.addPreference(gui);
            if ((pe != null) && (pe.getValue() instanceof StringSetting)) {
                replacementTableDisplayData.add(pe);
                Collections.sort(replacementTableDisplayData);
                replacementPreferenceTable.fireDataChanged();
            }
        });

        final var editScroll2 = new JButton(tr("Edit"));
        replaceAddEditDeleteScroll2.add(editScroll2, buttonInsets);
        editScroll2.addActionListener(e -> {
            final var toEdit = replacementPreferenceTable.getSelectedItems();
            if (toEdit.size() == MAX_SELECTED_TO_EDIT) {
                replacementPreferenceTable.editPreference(gui);
            }
        });

        final var deleteScroll2 = new JButton(tr("Delete"));
        replaceAddEditDeleteScroll2.add(deleteScroll2, buttonInsets);
        deleteScroll2.addActionListener(e -> {
            final var toRemove = replacementPreferenceTable.getSelectedItems();
            if (!toRemove.isEmpty()) {
                replacementTableDisplayData.removeAll(toRemove);
            }
            replacementPreferenceTable.fireDataChanged();
        });

        pane.add(Box.createHorizontalGlue(), second);

        final var kaartLogo = new JButton(ImageProvider.getIfAvailable("kaart") == null ? null
                : new ImageProvider("kaart").setHeight(ImageProvider.ImageSizes.SETTINGS_TAB.getAdjustedHeight())
                        .get());
        kaartLogo.setToolTipText(tr("Link to source code repository"));
        kaartLogo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                OpenBrowser.displayUrl("https://github.com/JOSM/MapWithAI");
            }
        });
        kaartLogo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        pane.add(kaartLogo, GBC.std().anchor(GridBagConstraints.WEST));

        final var mapWithAILogo = new JButton(ImageProvider.getIfAvailable("mapwithai_text") == null ? null
                : new ImageProvider("mapwithai_text")
                        .setHeight(ImageProvider.ImageSizes.SETTINGS_TAB.getAdjustedHeight()).get());
        mapWithAILogo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        mapWithAILogo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                OpenBrowser.displayUrl("https://mapwith.ai");
            }
        });
        pane.add(mapWithAILogo, GBC.eol().anchor(GridBagConstraints.EAST));

        Arrays.asList(replaceAddEditDeleteScroll2, scroll2, expertHorizontalGlue, replacementTags, previewFeatureSets,
                previewFeatureSetCheckbox).forEach(ExpertToggleAction::addVisibilitySwitcher);
        return pane;
    }

    @Override
    public boolean ok() {
        MapWithAIPreferenceHelper.setSwitchLayers(switchLayerCheckBox.isSelected(), true);
        final var value = maximumAdditionSpinner.getValue();
        MapWithAIPreferenceHelper.setMergeBuildingAddress(this.mergeBuildingAddressCheckBox.isSelected(), true);
        if (value instanceof Number number) {
            MapWithAIPreferenceHelper.setMaximumAddition(number.intValue(), true);
        }
        MapWithAILayerInfo.getInstance().save();
        MapWithAILayerInfo.getInstance().clear();
        MapWithAILayerInfo.getInstance().load(false, null);
        MapWithAIPreferenceHelper.setReplacementTags(convertReplacementPrefToMap(replacementTableDisplayData));
        return false;
    }

    private static Map<String, String> convertReplacementPrefToMap(List<PrefEntry> displayData) {
        return displayData.stream()
                .collect(Collectors.toMap(PrefEntry::getKey, entry -> entry.getValue().getValue().toString()));
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    /**
     * This method returns the checkbox used for deciding if layers should be
     * switched.
     *
     * @return The {@code JCheckBox} for whether or not we are switching layers.
     */
    public JCheckBox getSwitchLayerCheckBox() {
        return switchLayerCheckBox;
    }

    /**
     * Get the checkbox for merging buildings/addresses
     *
     * @return The checkbox
     */
    public JCheckBox getMergeBuildingAddressCheckBox() {
        return mergeBuildingAddressCheckBox;
    }

    /**
     * This is the spinner used to determine maximum additions (and maximum
     * selections).
     *
     * @return {@code JSpinner} for the maximum additions
     */
    public JSpinner getMaximumAdditionSpinner() {
        return maximumAdditionSpinner;
    }
}
