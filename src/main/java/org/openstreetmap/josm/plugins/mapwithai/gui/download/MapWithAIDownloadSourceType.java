// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.download;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JCheckBox;
import javax.swing.event.ChangeListener;

import java.util.stream.Stream;

import org.openstreetmap.josm.actions.downloadtasks.AbstractDownloadTask;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.download.IDownloadSourceType;
import org.openstreetmap.josm.plugins.mapwithai.backend.DownloadMapWithAITask;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo.LayerChangeListener;

/**
 * Adds the MapWithAI download checkbox to the JOSM download UI
 */
public class MapWithAIDownloadSourceType implements IDownloadSourceType, LayerChangeListener {
    static final BooleanProperty IS_ENABLED = new BooleanProperty("download.mapwithai.data", false);
    JCheckBox cbDownloadMapWithAIData;

    @Override
    public JCheckBox getCheckBox(ChangeListener checkboxChangeListener) {
        if (cbDownloadMapWithAIData == null) {
            cbDownloadMapWithAIData = new JCheckBox(tr("MapWithAI data"), getBooleanProperty().get());
            cbDownloadMapWithAIData
                    .setToolTipText(tr("Select to download MapWithAI data in the selected download area."));
            cbDownloadMapWithAIData.getModel()
                    .addActionListener(l -> getBooleanProperty().put(cbDownloadMapWithAIData.isSelected()));
            MapWithAILayerInfo.getInstance().addListener(this);
        }
        if (checkboxChangeListener != null) {
            cbDownloadMapWithAIData.getModel().addChangeListener(checkboxChangeListener);
        }
        return cbDownloadMapWithAIData;
    }

    @Override
    public Class<? extends AbstractDownloadTask<?>> getDownloadClass() {
        return DownloadMapWithAITask.class;
    }

    @Override
    public BooleanProperty getBooleanProperty() {
        return IS_ENABLED;
    }

    @Override
    public boolean isDownloadAreaTooLarge(Bounds bound) {
        return isDownloadAreaTooLargeStatic(bound);
    }

    /**
     * Check if the download area is too large
     *
     * @param bound The bound to check
     * @return {@code true} if the area is too large
     */
    public static boolean isDownloadAreaTooLargeStatic(Bounds bound) {
        double width = Math.max(bound.getMin().greatCircleDistance(new LatLon(bound.getMinLat(), bound.getMaxLon())),
                bound.getMax().greatCircleDistance(new LatLon(bound.getMaxLat(), bound.getMinLon())));
        double height = Math.max(bound.getMin().greatCircleDistance(new LatLon(bound.getMaxLat(), bound.getMinLon())),
                bound.getMax().greatCircleDistance(new LatLon(bound.getMinLat(), bound.getMaxLon())));
        return height > MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS
                || width > MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS;
    }

    @Override
    public void changeEvent(MapWithAIInfo modified) {
        if (Stream.of(Thread.currentThread().getStackTrace()).map(StackTraceElement::getClassName)
                .noneMatch(p -> p.contains("PreferenceDialog"))) {
            if (MapWithAILayerInfo.getInstance().getLayers().contains(modified)) {
                this.cbDownloadMapWithAIData.setSelected(true);
            } else if (MapWithAILayerInfo.getInstance().getLayers().isEmpty()) {
                this.cbDownloadMapWithAIData.setSelected(false);
            }
        }
    }

}
