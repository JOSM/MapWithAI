// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.datatransfer.data.PrimitiveTransferData;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Logging;

import javax.swing.JOptionPane;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.stream.Stream;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Keep users from copying from the MapWithAI layer to the OSM layer
 */
public class MapWithAICopyProhibit implements MainLayerManager.ActiveLayerChangeListener, Destroyable {
    /**
     * Create a new listener to keep copy-paste from happening between the MapWithAI
     * layer and the OSM layer
     */
    public MapWithAICopyProhibit() {
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);
    }

    @Override
    public void activeOrEditLayerChanged(MainLayerManager.ActiveLayerChangeEvent e) {
        if (e.getPreviousActiveLayer() instanceof MapWithAILayer && ClipboardUtils.getClipboardContent() != null
                && Stream.of(ClipboardUtils.getClipboardContent().getTransferDataFlavors())
                        .anyMatch(PrimitiveTransferData.DATA_FLAVOR::equals)) {
            PrimitiveTransferData data;
            try {
                Object tData = ClipboardUtils.getClipboardContent().getTransferData(PrimitiveTransferData.DATA_FLAVOR);
                if (tData instanceof PrimitiveTransferData) {
                    data = (PrimitiveTransferData) tData;
                } else {
                    return;
                }
            } catch (UnsupportedFlavorException | IOException exception) {
                Logging.error(exception);
                return;
            }
            DataSet dataSet = ((MapWithAILayer) e.getPreviousActiveLayer()).getDataSet();
            if (data.getAll().stream().anyMatch(pdata -> dataSet.getPrimitiveById(pdata) != null)) {
                ClipboardUtils.clear();
                Notification notification = new Notification(tr(
                        "Please use the `MapWithAI: Add Selected Data` command instead of copying and pasting from the MapWithAI Layer."))
                                .setDuration(Notification.TIME_DEFAULT).setIcon(JOptionPane.INFORMATION_MESSAGE)
                                .setHelpTopic(ht("Plugin/MapWithAI#BasicUsage"));
                GuiHelper.runInEDT(notification::show);
            }
        }
    }

    @Override
    public void destroy() {
        MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
    }
}
