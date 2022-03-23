// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.MapPolygonImpl;
import org.openstreetmap.gui.jmapviewer.MapRectangleImpl;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.interfaces.MapPolygon;
import org.openstreetmap.gui.jmapviewer.interfaces.MapRectangle;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.bbox.SlippyMapBBoxChooser;
import org.openstreetmap.josm.gui.preferences.imagery.ImageryProvidersPanel;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.FilterField;
import org.openstreetmap.josm.gui.widgets.HtmlPanel;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo.LayerChangeListener;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.ESRISourceReader;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.ImageResource;
import org.openstreetmap.josm.tools.ListenerList;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.OpenBrowser;

/**
 * A panel displaying imagery providers. Largely duplicates
 * {@link ImageryProvidersPanel}.
 *
 * @since 15115 (extracted from ImageryPreferences)
 */
public class MapWithAIProvidersPanel extends JPanel {

    public enum Options {
        /** Hide the active table */
        SHOW_ACTIVE
    }

    private static final long serialVersionUID = -5876039771496409422L;
    // Public JTables and JosmMapViewer
    /** The table of active providers **/
    public final JTable activeTable;
    /** The table of default providers **/
    public final JTable defaultTable;
    /** The filter of default providers **/
    private final FilterField defaultFilter;
    /** The directory for dialog images */
    private static final String DIALOG_IMAGES_DIR = "dialogs";
    /**
     * The selection listener synchronizing map display with table of default
     * providers
     **/
    private final transient DefListSelectionListener defaultTableListener;
    /** The map displaying imagery bounds of selected default providers **/
    public final SlippyMapBBoxChooser defaultMap;

    // Public models
    /** The model of active providers **/
    public static final MapWithAILayerTableModel ACTIVE_MODEL = new MapWithAILayerTableModel();
    /** The model of default providers **/
    public static final MapWithAIDefaultLayerTableModel DEFAULT_MODEL = new MapWithAIDefaultLayerTableModel();

    // Public JToolbars
    /** The toolbar on the right of active providers **/
    public final JToolBar activeToolbar;
    /** The toolbar on the middle of the panel **/
    public final JToolBar middleToolbar;
    /** The toolbar on the right of default providers **/
    public final JToolBar defaultToolbar;

    // Private members
    private final JComponent gui;
    private final transient ListenerList<AreaListener> areaListeners = ListenerList.create();
    /** Options that were passed to the constructor */
    private final Options[] options;

    protected interface AreaListener {

        void updateArea(Bounds area);
    }

    /**
     * class to render an information of MapWithAI source
     *
     * @param <T> type of information
     */
    private static class MapWithAITableCellRenderer<T> extends DefaultTableCellRenderer implements AreaListener {

        private static final NamedColorProperty IMAGERY_BACKGROUND_COLOR = new NamedColorProperty(
                marktr("MapWithAI Background: Default"), new Color(200, 255, 200));
        private static final NamedColorProperty MAPWITHAI_AREA_BACKGROUND_COLOR = new NamedColorProperty(
                marktr("MapWithAI Background: Layer in area"), Color.decode("#f1ffc7"));

        private final transient Function<T, Object> mapper;
        private final transient Function<T, String> tooltip;
        private final transient BiConsumer<T, JLabel> decorator;
        private final transient Function<Object, MapWithAIInfo> reverseMapper;

        private final boolean highlightIfActive;

        private transient Bounds area;

        /**
         * Initialize a cell renderer with specific rules
         *
         * @param mapper            Map from <T> to an Object (to get a cell renderer)
         * @param reverseMapper     Map from an Object to <T>
         * @param tooltip           The tooltip to show
         * @param decorator         The decorator
         * @param highlightIfActive If true, highlight when the entry is activated
         */
        MapWithAITableCellRenderer(Function<T, Object> mapper, Function<Object, MapWithAIInfo> reverseMapper,
                Function<T, String> tooltip, BiConsumer<T, JLabel> decorator, boolean highlightIfActive) {
            this.mapper = mapper;
            this.reverseMapper = reverseMapper;
            this.tooltip = tooltip;
            this.decorator = decorator;
            this.highlightIfActive = highlightIfActive;
        }

        @Override
        @SuppressWarnings("unchecked")
        public final Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            T obj = (T) value;
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, mapper.apply(obj), isSelected, hasFocus,
                    row, column);
            Color defaultColor = UIManager.getColor("Table.background");
            Color selectedColor = UIManager.getColor("Table.selectionBackground");
            GuiHelper.setBackgroundReadable(label, defaultColor);

            GuiHelper.setBackgroundReadable(label, isSelected ? selectedColor : defaultColor);
            if (this.highlightIfActive) {
                MapWithAIInfo info = obj instanceof MapWithAIInfo ? (MapWithAIInfo) obj : reverseMapper.apply(obj);
                if (info == null) {
                    GuiHelper.setBackgroundReadable(label, defaultColor);
                } else {
                    if (MapWithAILayerTableModel.contains(info)) {
                        Color t = IMAGERY_BACKGROUND_COLOR.get();
                        GuiHelper.setBackgroundReadable(label, isSelected ? t.darker() : t);
                    } else if (this.area != null && info.getBounds() != null
                            && (this.area.intersects(info.getBounds()) || info.getBounds().intersects(this.area))) {
                        Color t = MAPWITHAI_AREA_BACKGROUND_COLOR.get();
                        GuiHelper.setBackgroundReadable(label, isSelected ? t.darker() : t);
                    } else {
                        GuiHelper.setBackgroundReadable(label, isSelected ? selectedColor : defaultColor);
                    }
                }

            }
            if (obj != null) {
                label.setToolTipText(tooltip.apply(obj));
                if (decorator != null) {
                    decorator.accept(obj, label);
                }
            }
            return label;
        }

        @SuppressWarnings("hiding")
        @Override
        public void updateArea(Bounds area) {
            this.area = area;
        }
    }

    /**
     * class to render the license/terms of use URL of MapWithAI source
     */
    private static class MapWithAILicenseTableCellRenderer extends MapWithAITableCellRenderer<String> {

        MapWithAILicenseTableCellRenderer() {
            super(s -> s != null && !s.isEmpty() ? "<html><a href=\"" + s + "\">License</a>" : "",
                    u -> MapWithAILayerInfo.getInstance().getAllDefaultLayers().stream()
                            .filter(i -> u.equals(i.getUrl())).findFirst().orElse(null),
                    u -> u, null, true);
        }
    }

    /**
     * class to render the URL information of MapWithAI source
     *
     * @since 8065
     */
    private static class MapWithAIURLTableCellRenderer extends MapWithAITableCellRenderer<String> {

        MapWithAIURLTableCellRenderer() {
            super(s -> s, u -> MapWithAILayerInfo.getInstance().getAllDefaultLayers().stream()
                    .filter(i -> u.equals(i.getUrl())).findFirst().orElse(null), u -> u, null, true);
        }
    }

    /**
     * class to render the category information of MapWithAI source
     */
    private static class MapWithAICategoryTableCellRenderer
            extends MapWithAIProvidersPanel.MapWithAITableCellRenderer<MapWithAICategory> {

        MapWithAICategoryTableCellRenderer() {
            super(cat -> null, i -> null, cat -> tr("MapWithAI category: {0}", cat.getDescription()),
                    (cat, label) -> label.setIcon(cat.getIcon(ImageSizes.TABLE)), false);
        }
    }

    /**
     * class to render the country information of MapWithAI source
     */
    private static class MapWithAITypeTableCellRenderer
            extends MapWithAIProvidersPanel.MapWithAITableCellRenderer<List<String>> {

        MapWithAITypeTableCellRenderer() {
            super(MapWithAITypeTableCellRenderer::joinList, i -> null, MapWithAITypeTableCellRenderer::joinList, null,
                    false);
        }

        private static String joinList(List<String> list) {
            return list != null ? String.join(",", list) : "";
        }
    }

    /**
     * class to render the source provider information of a MapWithAI source
     */
    private static class MapWithAIProviderTableCellRenderer
            extends MapWithAIProvidersPanel.MapWithAITableCellRenderer<String> {

        MapWithAIProviderTableCellRenderer() {
            super(s -> s, s -> null, s -> s, null, false);
        }

    }

    /**
     * class to render the name information of Imagery source
     */
    private static class MapWithAINameTableCellRenderer
            extends MapWithAIProvidersPanel.MapWithAITableCellRenderer<MapWithAIInfo> {

        private static final long serialVersionUID = 6669934435517244629L;

        MapWithAINameTableCellRenderer(boolean showActive) {
            super(info -> info == null ? null : info.getName(), i -> null, MapWithAIInfo::getToolTipText, null,
                    showActive);
        }
    }

    /**
     * Constructs a new {@code MapWithAIProvidersPanel}.
     *
     * @param gui     The parent preference tab pane
     * @param options The options for this instance
     */
    public MapWithAIProvidersPanel(final JComponent gui, Options... options) {
        super(new GridBagLayout());
        this.gui = gui;
        this.options = options;
        boolean showActive = Arrays.asList(options).contains(Options.SHOW_ACTIVE);

        activeTable = new JTable(ACTIVE_MODEL) {

            private static final long serialVersionUID = -6136421378119093719L;

            @Override
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                try {
                    return ACTIVE_MODEL.getValueAt(rowAtPoint(p), columnAtPoint(p)).toString();
                } catch (ArrayIndexOutOfBoundsException ex) {
                    Logging.debug(ex);
                    return null;
                }
            }
        };
        activeTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        defaultTable = new JTable(DEFAULT_MODEL);
        defaultTable.setAutoCreateRowSorter(true);
        defaultFilter = new FilterField().filter(defaultTable, DEFAULT_MODEL);

        DEFAULT_MODEL.addTableModelListener(e -> activeTable.repaint());
        ACTIVE_MODEL.addTableModelListener(e -> defaultTable.repaint());

        setupDefaultTable(defaultTable, options, areaListeners);

        TableColumnModel mod = activeTable.getColumnModel();
        mod.getColumn(1).setPreferredWidth(800);
        MapWithAIURLTableCellRenderer activeTableCellRenderer = new MapWithAIURLTableCellRenderer();
        areaListeners.addListener(activeTableCellRenderer);
        mod.getColumn(1).setCellRenderer(activeTableCellRenderer);
        mod.getColumn(0).setMaxWidth(200);

        RemoveEntryAction remove = new RemoveEntryAction();
        activeTable.getSelectionModel().addListSelectionListener(remove);

        EditEntryAction edit = new EditEntryAction();
        activeTable.getSelectionModel().addListSelectionListener(edit);

        add(new JLabel(tr("Available default entries:")), GBC.std().insets(5, 5, 0, 0));
        add(new JLabel(tr("Boundaries of selected MapWithAI entries:")), GBC.eol().insets(5, 5, 0, 0));

        // Add default item list
        JPanel defaultPane = new JPanel(new GridBagLayout());
        JScrollPane scrolldef = new JScrollPane(defaultTable);
        scrolldef.setPreferredSize(new Dimension(200, 200));
        defaultPane.add(defaultFilter, GBC.eol().insets(0, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));
        defaultPane.add(scrolldef, GBC.eol().insets(0, 0, 0, 0).fill(GridBagConstraints.BOTH));
        add(defaultPane, GBC.std().fill(GridBagConstraints.BOTH).weight(1.0, 0.6).insets(5, 0, 0, 0));

        // Add default item map
        defaultMap = new SlippyMapBBoxChooser();
        defaultMap.setTileSource(SlippyMapBBoxChooser.DefaultOsmTileSourceProvider.get()); // for attribution
        defaultMap.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    defaultMap.getAttribution().handleAttribution(e.getPoint(), true);
                }
            }
        });
        defaultMap.setZoomControlsVisible(false);
        defaultMap.setMinimumSize(new Dimension(100, 200));
        defaultMap.addJMVListener(e -> {
            Rectangle2D visibleRect = defaultMap.getVisibleRect();
            ICoordinate max = defaultMap.getPosition((int) visibleRect.getMaxX(), (int) visibleRect.getMaxY());
            ICoordinate min = defaultMap.getPosition((int) visibleRect.getMinX(), (int) visibleRect.getMinY());
            Bounds b = new Bounds(
                    new LatLon(Math.min(max.getLat(), min.getLat()),
                            LatLon.toIntervalLon(Math.min(max.getLon(), min.getLon()))),
                    new LatLon(Math.max(max.getLat(), min.getLat()),
                            LatLon.toIntervalLon(Math.max(max.getLon(), min.getLon()))));

            this.areaListeners.fireEvent(f -> f.updateArea(b));
            // This is required to ensure that all cells are appropriately coloured
            // Both revalidate and repaint are needed.
            this.defaultTable.revalidate();
            this.defaultTable.repaint();
        });
        add(defaultMap, GBC.std().fill(GridBagConstraints.BOTH).weight(0.33, 0.6).insets(5, 0, 0, 0));

        defaultTableListener = new DefListSelectionListener();
        defaultTable.getSelectionModel().addListSelectionListener(defaultTableListener);

        defaultToolbar = new JToolBar(JToolBar.VERTICAL);
        defaultToolbar.setFloatable(false);
        defaultToolbar.setBorderPainted(false);
        defaultToolbar.setOpaque(false);
        defaultToolbar.add(new ReloadAction());
        add(defaultToolbar, GBC.eol().anchor(GBC.SOUTH).insets(0, 0, 5, 0));

        HtmlPanel help = new HtmlPanel(
                tr("New default entries can be added in the <a href=\"{0}\">GitLab Repository</a>.",
                        "https://gitlab.com/gokaart/JOSM_MapWithAI/-/blob/pages/public/json/sources.json"));
        help.enableClickableHyperlinks();
        add(help, GBC.eol().insets(10, 0, 0, 0).fill(GBC.HORIZONTAL));

        ActivateAction activate = new ActivateAction();
        defaultTable.getSelectionModel().addListSelectionListener(activate);
        JButton btnActivate = new JButton(activate);

        middleToolbar = new JToolBar(JToolBar.HORIZONTAL);
        middleToolbar.setFloatable(false);
        middleToolbar.setBorderPainted(false);
        middleToolbar.setOpaque(false);
        middleToolbar.add(btnActivate);
        add(middleToolbar, GBC.eol().anchor(GBC.CENTER).insets(5, 5, 5, 0));

        add(Box.createHorizontalGlue(), GBC.eol().fill(GridBagConstraints.HORIZONTAL));

        JScrollPane scroll = new JScrollPane(activeTable);
        scroll.setPreferredSize(new Dimension(200, 200));

        activeToolbar = new JToolBar(JToolBar.VERTICAL);
        activeToolbar.setFloatable(false);
        activeToolbar.setBorderPainted(false);
        activeToolbar.setOpaque(false);
        activeToolbar.add(new NewEntryAction(MapWithAIType.THIRD_PARTY));
        activeToolbar.add(edit);
        activeToolbar.add(remove);
        if (showActive) {
            add(new JLabel(tr("Selected entries:")), GBC.eol().insets(5, 0, 0, 0));
            add(scroll, GBC.std().fill(GridBagConstraints.BOTH).span(GridBagConstraints.RELATIVE).weight(1.0, 0.4)
                    .insets(5, 0, 0, 5));
            add(activeToolbar, GBC.eol().anchor(GBC.NORTH).insets(0, 0, 5, 5));
        }
    }

    private static void setupDefaultTable(JTable defaultTable, Options[] options,
            ListenerList<AreaListener> areaListeners) {
        boolean showActive = Arrays.asList(options).contains(Options.SHOW_ACTIVE);
        int tenXWidth = defaultTable.getFontMetrics(defaultTable.getFont()).stringWidth("XXXXXXXXXX");
        TableColumnModel mod = defaultTable.getColumnModel();
        int urlWidth = (showActive ? 3 : 0) * tenXWidth;
        mod.getColumn(6).setCellRenderer(defaultTable.getDefaultRenderer(Boolean.class));
        mod.getColumn(6).setMaxWidth(20);
        mod.getColumn(5).setMaxWidth((!showActive ? 1 : 0) * tenXWidth);
        mod.getColumn(5).setCellRenderer(new MapWithAILicenseTableCellRenderer());
        mod.getColumn(4).setPreferredWidth((showActive ? 2 : 0) * tenXWidth);
        mod.getColumn(4).setCellRenderer(new MapWithAIProviderTableCellRenderer());
        mod.getColumn(3).setPreferredWidth(urlWidth);
        MapWithAIURLTableCellRenderer defaultUrlTableCellRenderer = new MapWithAIURLTableCellRenderer();
        mod.getColumn(3).setCellRenderer(defaultUrlTableCellRenderer);
        mod.getColumn(2).setPreferredWidth((int) ((showActive ? 0 : 0.3) * tenXWidth));

        mod.getColumn(2).setCellRenderer(new MapWithAITypeTableCellRenderer());

        MapWithAINameTableCellRenderer defaultNameTableCellRenderer = new MapWithAINameTableCellRenderer(!showActive);
        mod.getColumn(1).setCellRenderer(defaultNameTableCellRenderer);
        mod.getColumn(1).setPreferredWidth((showActive ? 3 : 2) * tenXWidth);
        mod.getColumn(0).setCellRenderer(new MapWithAICategoryTableCellRenderer());
        mod.getColumn(0).setMaxWidth(ImageProvider.ImageSizes.MENU.getAdjustedWidth() + 5);

        if (showActive) {
            defaultTable.removeColumn(mod.getColumn(6));
            defaultTable.removeColumn(mod.getColumn(4));
            defaultTable.removeColumn(mod.getColumn(2));
            areaListeners.addListener(defaultUrlTableCellRenderer);
        } else {
            defaultTable.removeColumn(mod.getColumn(3));
            areaListeners.addListener(defaultNameTableCellRenderer);
        }
        defaultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clickListener(e);
            }
        });
    }

    /**
     * This is a function to be called when a cell is clicked
     *
     * @param e The MouseEvent (used to get the appropriate JTable)
     */
    private static void clickListener(MouseEvent e) {
        if (e.getSource() instanceof JTable) {
            JTable table = (JTable) e.getSource();
            if (table.getSelectedRow() >= 0 && table.getSelectedColumn() >= 0) {
                int realCol = table.convertColumnIndexToModel(table.getSelectedColumn());
                int realRow = table.convertRowIndexToModel(table.getSelectedRow());
                String tableName = table.getModel().getColumnName(realCol);
                if (tr("License").equals(tableName)) {
                    MapWithAIInfo info = MapWithAIDefaultLayerTableModel.getRow(realRow);
                    if (info.getTermsOfUseURL() != null) {
                        OpenBrowser.displayUrl(info.getTermsOfUseURL());
                    }
                } else if (tr("Enabled").equals(tableName)) {
                    MapWithAIInfo info = MapWithAIDefaultLayerTableModel.getRow(realRow);
                    MapWithAILayerInfo instance = MapWithAILayerInfo.getInstance();
                    if (instance.getLayers().contains(info)) {
                        instance.remove(info);
                    } else {
                        instance.add(info);
                    }
                }
            }
        }
    }

    /**
     * Set the current bounds of the map and the area to select
     *
     * @param area The current area to highlight data from
     */
    public void setCurrentBounds(Bounds area) {
        this.defaultMap.setBoundingBox(area);
        fireAreaListeners();
    }

    /**
     * Fire area listeners
     */
    public void fireAreaListeners() {
        this.areaListeners.fireEvent(f -> f.updateArea(this.defaultMap.getBoundingBox()));
    }

    // Listener of default providers list selection
    private final class DefListSelectionListener implements ListSelectionListener {

        // The current drawn rectangles and polygons
        private final Map<Integer, MapRectangle> mapRectangles;
        private final Map<Integer, List<MapPolygon>> mapPolygons;

        private DefListSelectionListener() {
            this.mapRectangles = new HashMap<>();
            this.mapPolygons = new HashMap<>();
        }

        private void clearMap() {
            defaultMap.removeAllMapRectangles();
            defaultMap.removeAllMapPolygons();
            mapRectangles.clear();
            mapPolygons.clear();
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            // First index can be set to -1 when the list is refreshed, so discard all map
            // rectangles and polygons
            if (e.getFirstIndex() == -1) {
                clearMap();
            } else if (!e.getValueIsAdjusting()) {
                // Only process complete (final) selection events
                for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
                    if (i < defaultTable.getRowCount()) {
                        updateBoundsAndShapes(defaultTable.convertRowIndexToModel(i));
                    }
                }
                // Cleanup residual selected bounds which may have disappeared after a filter
                cleanupResidualBounds();
                // If needed, adjust map to show all map rectangles and polygons
                if (!mapRectangles.isEmpty() || !mapPolygons.isEmpty()) {
                    defaultMap.setDisplayToFitMapElements(false, true, true);
                    defaultMap.zoomOut();
                }
            }
        }

        /**
         * update bounds and shapes for a new entry
         *
         * @param i model index
         */
        private void updateBoundsAndShapes(int i) {
            ImageryBounds bounds = MapWithAIDefaultLayerTableModel.getRow(i).getBounds();
            if (bounds != null) {
                int viewIndex = defaultTable.convertRowIndexToView(i);
                List<Shape> shapes = bounds.getShapes();
                if (shapes != null && !shapes.isEmpty()) {
                    if (defaultTable.getSelectionModel().isSelectedIndex(viewIndex)) {
                        mapPolygons.computeIfAbsent(i, key -> {
                            List<MapPolygon> list = new ArrayList<>();
                            // Add new map polygons
                            for (Shape shape : shapes) {
                                MapPolygon polygon = new MapPolygonImpl(shape.getPoints());
                                list.add(polygon);
                                defaultMap.addMapPolygon(polygon);
                            }
                            return list;
                        });
                    } else if (mapPolygons.containsKey(i)) {
                        // Remove previously drawn map polygons
                        for (MapPolygon polygon : mapPolygons.get(i)) {
                            defaultMap.removeMapPolygon(polygon);
                        }
                        mapPolygons.remove(i);
                    }
                    // Only display bounds when no polygons (shapes) are defined for this provider
                } else {
                    if (defaultTable.getSelectionModel().isSelectedIndex(viewIndex)) {
                        mapRectangles.computeIfAbsent(i, key -> {
                            // Add new map rectangle
                            Coordinate topLeft = new Coordinate(bounds.getMaxLat(), bounds.getMinLon());
                            Coordinate bottomRight = new Coordinate(bounds.getMinLat(), bounds.getMaxLon());
                            MapRectangle rectangle = new MapRectangleImpl(topLeft, bottomRight);
                            defaultMap.addMapRectangle(rectangle);
                            return rectangle;
                        });
                    } else if (mapRectangles.containsKey(i)) {
                        // Remove previously drawn map rectangle
                        defaultMap.removeMapRectangle(mapRectangles.get(i));
                        mapRectangles.remove(i);
                    }
                }
            }
        }

        private <T> void doCleanupResidualBounds(Map<Integer, T> map, Consumer<T> removalEffect) {
            List<Integer> toRemove = new ArrayList<>();
            for (Integer i : map.keySet()) {
                int viewIndex = defaultTable.convertRowIndexToView(i);
                if (!defaultTable.getSelectionModel().isSelectedIndex(viewIndex)) {
                    toRemove.add(i);
                }
            }
            toRemove.forEach(i -> removalEffect.accept(map.remove(i)));
        }

        private void cleanupResidualBounds() {
            doCleanupResidualBounds(mapPolygons, l -> l.forEach(defaultMap::removeMapPolygon));
            doCleanupResidualBounds(mapRectangles, defaultMap::removeMapRectangle);
        }
    }

    private class NewEntryAction extends AbstractAction {

        private static final long serialVersionUID = 7451336680150337942L;

        NewEntryAction(MapWithAIType type) {
            putValue(NAME, type.toString());
            putValue(SHORT_DESCRIPTION, tr("Add a new {0} entry by entering the URL", type.toString()));
            String icon = /* ICON(dialogs/) */ "add";
            new ImageProvider(DIALOG_IMAGES_DIR, icon).getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            final AddMapWithAIPanel p = new AddMapWithAIPanel();
            final AddMapWithAIDialog addDialog = new AddMapWithAIDialog(gui, p);
            addDialog.showDialog();

            if (addDialog.getValue() == 1) {
                try {
                    MapWithAIInfo info = p.getSourceInfo();
                    // Fix a possible NPE
                    if (info.getSourceType() == null) {
                        info.setSourceType(MapWithAIType.THIRD_PARTY);
                    }
                    if (MapWithAIType.ESRI == info.getSourceType()) {
                        final ESRISourceReader reader = new ESRISourceReader(info);
                        try {
                            for (Future<MapWithAIInfo> i : reader.parse()) {
                                try {
                                    ACTIVE_MODEL.addRow(i.get());
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    Logging.error(e);
                                } catch (ExecutionException e) {
                                    Logging.error(e);
                                }
                            }
                        } catch (IOException e) {
                            Logging.error(e);
                        }
                    } else {
                        ACTIVE_MODEL.addRow(info);
                    }
                } catch (IllegalArgumentException ex) {
                    if (ex.getMessage() == null || ex.getMessage().isEmpty()) {
                        throw ex;
                    }
                    JOptionPane.showMessageDialog(MainApplication.getMainFrame(), ex.getMessage(), tr("Error"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private class EditEntryAction extends AbstractAction implements ListSelectionListener {

        private static final long serialVersionUID = -1682304557691078801L;

        /**
         * Constructs a new {@code EditEntryAction}.
         */
        EditEntryAction() {
            putValue(NAME, tr("Edit"));
            putValue(SHORT_DESCRIPTION, tr("Edit entry"));
            new ImageProvider(DIALOG_IMAGES_DIR, "edit").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        protected final void updateEnabledState() {
            setEnabled(activeTable.getSelectedRowCount() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (activeTable.getSelectedRow() != -1) {
                final AddMapWithAIPanel p = new AddMapWithAIPanel(
                        MapWithAILayerTableModel.getRow(activeTable.getSelectedRow()));
                final AddMapWithAIDialog addDialog = new AddMapWithAIDialog(gui, p);
                addDialog.showDialog();
                if (addDialog.getValue() == 1) {
                    p.getSourceInfo();
                }
            }
        }
    }

    private class RemoveEntryAction extends AbstractAction implements ListSelectionListener {

        private static final long serialVersionUID = 2666450386256004180L;

        /**
         * Constructs a new {@code RemoveEntryAction}.
         */
        RemoveEntryAction() {
            putValue(NAME, tr("Remove"));
            putValue(SHORT_DESCRIPTION, tr("Remove entry"));
            new ImageProvider(DIALOG_IMAGES_DIR, "delete").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        protected final void updateEnabledState() {
            setEnabled(activeTable.getSelectedRowCount() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int i;
            while ((i = activeTable.getSelectedRow()) != -1) {
                ACTIVE_MODEL.removeRow(i);
            }
        }
    }

    private class ActivateAction extends AbstractAction implements ListSelectionListener, LayerChangeListener {

        private static final long serialVersionUID = -452335751201424801L;
        private final transient ImageResource activate;
        private final transient ImageResource deactivate;

        /**
         * Constructs a new {@code ActivateAction}.
         */
        ActivateAction() {
            putValue(NAME, tr("Activate"));
            putValue(SHORT_DESCRIPTION, tr("Copy selected default entries from the list above into the list below."));
            activate = new ImageProvider("svpDown").setMaxSize(ImageProvider.ImageSizes.MENU).getResource();
            activate.attachImageIcon(this, true);
            deactivate = new ImageProvider("svpUp").setMaxSize(ImageProvider.ImageSizes.MENU).getResource();
            MapWithAILayerInfo.getInstance().addListener(this);
        }

        protected void updateEnabledState() {
            setEnabled(defaultTable.getSelectedRowCount() > 0);
            List<MapWithAIInfo> selected = Arrays.stream(defaultTable.getSelectedRows())
                    .map(defaultTable::convertRowIndexToModel).mapToObj(MapWithAIDefaultLayerTableModel::getRow)
                    .collect(Collectors.toList());
            if (selected.stream().anyMatch(MapWithAILayerTableModel::doesNotContain)) {
                activate.attachImageIcon(this, true);
                putValue(NAME, tr("Activate"));
                putValue(SHORT_DESCRIPTION,
                        tr("Copy selected default entries from the list above into the list below."));
            } else {
                deactivate.attachImageIcon(this, true);
                putValue(NAME, tr("Deactivate"));
                putValue(SHORT_DESCRIPTION,
                        tr("Remove selected default entries from the list above into the list below."));
            }
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int[] lines = defaultTable.getSelectedRows();
            if (lines.length == 0) {
                JOptionPane.showMessageDialog(gui, tr("Please select at least one row to copy."), tr("Information"),
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            List<MapWithAIInfo> selected = Arrays.stream(defaultTable.getSelectedRows())
                    .map(defaultTable::convertRowIndexToModel).mapToObj(MapWithAIDefaultLayerTableModel::getRow)
                    .collect(Collectors.toList());
            if (selected.stream().anyMatch(MapWithAILayerTableModel::doesNotContain)) {
                List<MapWithAIInfo> toAdd = selected.stream().filter(MapWithAILayerTableModel::doesNotContain)
                        .collect(Collectors.toList());
                activeTable.getSelectionModel().clearSelection();
                for (MapWithAIInfo info : toAdd) {
                    ACTIVE_MODEL.addRow(new MapWithAIInfo(info));
                    int lastLine = ACTIVE_MODEL.getRowCount() - 1;
                    activeTable.getSelectionModel().setSelectionInterval(lastLine, lastLine);
                    activeTable.scrollRectToVisible(activeTable.getCellRect(lastLine, 0, true));
                }
                selected.removeIf(toAdd::contains);
                selected.stream().mapToInt(ACTIVE_MODEL::getRowIndex).filter(i -> i >= 0).forEach(j -> {
                    activeTable.getSelectionModel().addSelectionInterval(j, j);
                    activeTable.scrollRectToVisible(activeTable.getCellRect(j, 0, true));
                });
            } else {
                selected.stream().mapToInt(ACTIVE_MODEL::getRowIndex).filter(i -> i >= 0).boxed()
                        .sorted(Collections.reverseOrder()).forEach(ACTIVE_MODEL::removeRow);
            }
            updateEnabledState();
            if (Stream.of(options).noneMatch(Options.SHOW_ACTIVE::equals)) {
                MapWithAILayerInfo.getInstance().save();
            }
        }

        @Override
        public void changeEvent(MapWithAIInfo modified) {
            GuiHelper.runInEDT(this::updateEnabledState);
        }
    }

    private class ReloadAction extends AbstractAction {

        private static final long serialVersionUID = 7801339998423585685L;

        /**
         * Constructs a new {@code ReloadAction}.
         */
        ReloadAction() {
            putValue(SHORT_DESCRIPTION, tr("Update default entries"));
            new ImageProvider(DIALOG_IMAGES_DIR, "refresh").getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            this.setEnabled(false);
            MapWithAILayerInfo.getInstance().loadDefaults(true, MainApplication.worker, false, () ->
            // This needs to be run in a block to avoid race conditions.
            GuiHelper.runInEDT(() -> {
                defaultTable.getSelectionModel().clearSelection();
                defaultTableListener.clearMap();
                DEFAULT_MODEL.fireTableDataChanged();
                /* loading new file may change active layers */
                ACTIVE_MODEL.fireTableDataChanged();
                this.setEnabled(true);
            }));
        }
    }
}
