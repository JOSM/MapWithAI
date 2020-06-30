// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;

import java.awt.event.ActionEvent;

import org.openstreetmap.josm.actions.AdaptableAction;
import org.openstreetmap.josm.actions.AddImageryLayerAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmData;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.mapwithai.backend.BoundingBoxMapWithAIDownloader;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.ImageResource;
import org.openstreetmap.josm.tools.Logging;

/**
 * Action displayed in MapWithAI menu to add data to the MapWithAI layer.
 * Largely copied from {@link AddImageryLayerAction}.
 */
public class AddMapWithAILayerAction extends JosmAction implements AdaptableAction {
    private final transient MapWithAIInfo info;

    /**
     * Constructs a new {@code AddMapWithAILayerAction} for the given
     * {@code MapWithAIInfo}. If an http:// icon is specified, it is fetched
     * asynchronously.
     *
     * @param info The source info
     */
    public AddMapWithAILayerAction(MapWithAIInfo info) {
        super(info.getName(), /* ICON */"imagery_menu", info.getToolTipText(), null, true,
                ToolbarPreferences.IMAGERY_PREFIX + info.getToolbarName(), false);
        setHelpId(ht("/Preferences/Imagery"));
        this.info = info;
        installAdapters();

        // change toolbar icon from if specified
        String icon = info.getIcon();
        if (icon != null) {
            new ImageProvider(icon).setOptional(true).getResourceAsync(result -> {
                if (result != null) {
                    GuiHelper.runInEDT(() -> result.attachImageIcon(this));
                }
            });
        } else {
            ImageResource resource = new ImageResource(
                    this.info.getSourceCategory().getIcon(ImageSizes.MENU).getImage());
            resource.attachImageIcon(this);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) {
            return;
        }

        MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        final DataSet ds;
        final OsmData<?, ?, ?, ?> boundsSource;
        if (layer != null && !layer.getData().getDataSourceBounds().isEmpty()) {
            ds = layer.getDataSet();
            boundsSource = ds;
        } else if (MainApplication.getLayerManager().getActiveData() != null
                && !MainApplication.getLayerManager().getActiveData().getDataSourceBounds().isEmpty()) {
            boundsSource = MainApplication.getLayerManager().getActiveData();
            ds = MapWithAIDataUtils.getLayer(true).getDataSet();
        } else {
            boundsSource = null;
            ds = null;
        }
        if (boundsSource != null && ds != null) {
            boundsSource.getDataSourceBounds().forEach(b -> MainApplication.worker.execute(() -> {
                try {
                    ds.mergeFrom(
                            new BoundingBoxMapWithAIDownloader(b, info, false).parseOsm(NullProgressMonitor.INSTANCE));
                } catch (OsmTransferException error) {
                    Logging.error(error);
                }
            }));
        }

    }

    @Override
    protected boolean listenToSelectionChange() {
        return false;
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(!info.isBlacklisted());
    }

    @Override
    public String toString() {
        return "AddMapWithAILayerAction [info=" + info + ']';
    }
}
