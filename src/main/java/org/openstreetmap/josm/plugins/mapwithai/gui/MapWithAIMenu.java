// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trc;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.MenuComponent;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.sources.SourceInfo;
import org.openstreetmap.josm.gui.ImageryMenu;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.MenuScroller;
import org.openstreetmap.josm.gui.preferences.imagery.ImageryPreference;
import org.openstreetmap.josm.plugins.mapwithai.actions.AddMapWithAILayerAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.ImageResource;
import org.openstreetmap.josm.tools.Logging;

/**
 * MapWithAI menu, holding entries for MapWithAI preferences and dynamic source
 * entries depending on current mapview coordinates.
 *
 * Largely copied from {@link ImageryMenu}, but highly modified.
 */
public class MapWithAIMenu extends JMenu {
    /**
     * Compare MapWithAIInfo objects alphabetically by name.
     *
     * MapWithAIInfo objects are normally sorted by country code first (for the
     * preferences). We don't want this in the MapWithAI menu.
     */
    public static final Comparator<SourceInfo<?, ?, ?, ?>> alphabeticSourceComparator = (ii1, ii2) -> ii1.getName()
            .toLowerCase(Locale.ENGLISH).compareTo(ii2.getName().toLowerCase(Locale.ENGLISH));

    /**
     * Constructs a new {@code ImageryMenu}.
     */
    public MapWithAIMenu() {
        /* I18N: mnemonic: I */
        super(trc("menu", "MapWithAI"));
        ImageProvider mapwithai = new ImageProvider("MapWithAI").setOptional(true)
                .setMaxSize(ImageProvider.ImageSizes.MENU);
        ImageResource resource = mapwithai.getResource();
        if (resource != null) {
            super.setIcon(resource.getImageIconBounded(ImageProvider.ImageSizes.MENU.getImageDimension()));
        }
        setupMenuScroller();
        // build dynamically
        addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                refreshImageryMenu();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
                // Do nothing
            }

            @Override
            public void menuCanceled(MenuEvent e) {
                // Do nothing
            }
        });
    }

    private void setupMenuScroller() {
        if (!GraphicsEnvironment.isHeadless()) {
            MenuScroller.setScrollerFor(this, 150, 2);
        }
    }

    /**
     * For layers containing complex shapes, check that center is in one of its
     * shapes (fix #7910)
     *
     * @param info layer info
     * @param pos  center
     * @return {@code true} if center is in one of info shapes
     */
    private static boolean isPosInOneShapeIfAny(SourceInfo<?, ?, ?, ?> info, LatLon pos) {
        List<Shape> shapes = info.getBounds().getShapes();
        return shapes == null || shapes.isEmpty() || shapes.stream().anyMatch(s -> s.contains(pos));
    }

    /**
     * Refresh imagery menu.
     *
     * Outside this class only called in {@link ImageryPreference#initialize()}. (In
     * order to have actions ready for the toolbar, see #8446.)
     */
    public void refreshImageryMenu() {
        removeDynamicItems();

        addDynamicSeparator();
        // Get layers in use
        MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        final Collection<MapWithAIInfo> alreadyInUse = layer == null ? Collections.emptyList()
                : layer.getDownloadedInfo();

        // for each configured ImageryInfo, add a menu entry.
        final List<MapWithAIInfo> savedLayers = new ArrayList<>(MapWithAILayerInfo.getInstance().getLayers());
        savedLayers.sort(alphabeticSourceComparator);
        savedLayers.removeIf(alreadyInUse::contains);
        for (final MapWithAIInfo u : savedLayers) {
            addDynamic(trackJosmAction(new AddMapWithAILayerAction(u)), null);
        }

        // list all imagery entries where the current map location is within the imagery
        // bounds
        if (MainApplication.isDisplayingMapView()) {
            MapView mv = MainApplication.getMap().mapView;
            LatLon pos = mv.getProjection().eastNorth2latlon(mv.getCenter());
            final List<MapWithAIInfo> inViewLayers = MapWithAILayerInfo.getInstance().getAllDefaultLayers().stream()
                    .filter(i -> i.getBounds() != null && i.getBounds().contains(pos) && !alreadyInUse.contains(i)
                            && !savedLayers.contains(i) && isPosInOneShapeIfAny(i, pos))
                    .sorted(alphabeticSourceComparator).collect(Collectors.toList());
            if (!inViewLayers.isEmpty()) {
                if (inViewLayers.stream().anyMatch(i -> i.getCategory() == i.getCategory().getDefault())) {
                    addDynamicSeparator();
                }
                for (MapWithAIInfo i : inViewLayers) {
                    addDynamic(trackJosmAction(new AddMapWithAILayerAction(i)), i.getCategory());
                }
            }
            if (!dynamicNonPhotoItems.isEmpty()) {
                addDynamicSeparator();
                for (Map.Entry<MapWithAICategory, List<JMenuItem>> e : dynamicNonPhotoItems.entrySet()) {
                    MapWithAICategory cat = e.getKey();
                    List<JMenuItem> list = e.getValue();
                    if (list.size() > 1) {
                        JMenuItem categoryMenu = new JMenu(cat.getDescription());
                        categoryMenu.setIcon(cat.getIcon(ImageSizes.MENU));
                        for (JMenuItem it : list) {
                            categoryMenu.add(it);
                        }
                        dynamicNonPhotoMenus.add(add(categoryMenu));
                    } else if (!list.isEmpty()) {
                        dynamicNonPhotoMenus.add(add(list.get(0)));
                    }
                }
            }
            if (dynJosmActions.isEmpty()) {
                JosmAction infoAction = new JosmAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // Do nothing
                    }
                };
                infoAction.putValue(Action.NAME, tr("No futher download options"));
                infoAction.setEnabled(false);
                infoAction.setTooltip(tr("No further download actions possible in this area"));
                addDynamic(infoAction, null);
            }
        }
    }

    /**
     * List to store temporary "photo" menu items. They will be deleted (and
     * possibly recreated) when refreshImageryMenu() is called.
     */
    private final List<Object> dynamicItems = new ArrayList<>(20);
    /**
     * Map to store temporary "not photo" menu items. They will be deleted (and
     * possibly recreated) when refreshImageryMenu() is called.
     */
    private final Map<MapWithAICategory, List<JMenuItem>> dynamicNonPhotoItems = new EnumMap<>(MapWithAICategory.class);
    /**
     * List to store temporary "not photo" submenus. They will be deleted (and
     * possibly recreated) when refreshImageryMenu() is called.
     */
    private final List<JMenuItem> dynamicNonPhotoMenus = new ArrayList<>(20);
    private final List<JosmAction> dynJosmActions = new ArrayList<>(20);

    /**
     * Remove all the items in dynamic items collection
     *
     * @since 5803
     */
    private void removeDynamicItems() {
        dynJosmActions.forEach(JosmAction::destroy);
        dynJosmActions.clear();
        dynamicItems.forEach(this::removeDynamicItem);
        dynamicItems.clear();
        dynamicNonPhotoMenus.forEach(this::removeDynamicItem);
        dynamicItems.clear();
        dynamicNonPhotoItems.clear();
    }

    private void removeDynamicItem(Object item) {
        if (item instanceof JMenuItem) {
            remove((JMenuItem) item);
        } else if (item instanceof MenuComponent) {
            remove((MenuComponent) item);
        } else if (item instanceof Component) {
            remove((Component) item);
        } else {
            Logging.error("Unknown imagery menu item type: {0}", item);
        }
    }

    private void addDynamicSeparator() {
        JPopupMenu.Separator s = new JPopupMenu.Separator();
        dynamicItems.add(s);
        add(s);
    }

    private void addDynamic(Action a, MapWithAICategory category) {
        JMenuItem item = createActionComponent(a);
        item.setAction(a);
        doAddDynamic(item, category);
    }

    private void doAddDynamic(JMenuItem item, MapWithAICategory category) {
        if (category == null || category == MapWithAICategory.FEATURED) {
            dynamicItems.add(this.add(item));
        } else {
            dynamicNonPhotoItems.computeIfAbsent(category, x -> new ArrayList<>()).add(item);
        }
    }

    private Action trackJosmAction(Action action) {
        if (action instanceof JosmAction) {
            dynJosmActions.add((JosmAction) action);
        }
        return action;
    }

}
