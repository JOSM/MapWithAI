// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.download;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.download.AbstractDownloadSourcePanel;
import org.openstreetmap.josm.gui.download.DownloadDialog;
import org.openstreetmap.josm.gui.download.DownloadSettings;
import org.openstreetmap.josm.gui.download.DownloadSource;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.DownloadMapWithAITask;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;

public class MapWithAIDownloadReader implements DownloadSource<MapWithAIDownloadReader.MapWithAIDownloadData> {

    @Override
    public AbstractDownloadSourcePanel<MapWithAIDownloadData> createPanel(DownloadDialog dialog) {
        return new MapWithAIDownloadPanel(this);
    }

    @Override
    public void doDownload(MapWithAIDownloadData data, DownloadSettings settings) {
        Bounds area = settings.getDownloadBounds().orElse(new Bounds(0, 0, 0, 0));
        DownloadMapWithAITask task = new DownloadMapWithAITask();
        task.setZoomAfterDownload(settings.zoomToData());
        DownloadParams params = new DownloadParams();
        params.withNewLayer(settings.asNewLayer());
        task.download(params, area, null);
    }

    @Override
    public String getLabel() {
        return tr("Download from {0} API", MapWithAIPlugin.NAME);
    }

    @Override
    public boolean onlyExpert() {
        return false;
    }

    /**
     * Encapsulates data that is required to perform download from MapWithAI API
     */
    static class MapWithAIDownloadData {
        private final List<MapWithAIInfo> url;
        private final Consumer<Collection<Object>> errorReporter;

        MapWithAIDownloadData(List<MapWithAIInfo> list, Consumer<Collection<Object>> errorReporter) {
            this.url = list;
            this.errorReporter = errorReporter;
        }

        List<MapWithAIInfo> getUrls() {
            return url;
        }

        Consumer<Collection<Object>> getErrorReporter() {
            return errorReporter;
        }
    }

    public static class MapWithAIDownloadPanel extends AbstractDownloadSourcePanel<MapWithAIDownloadData> {
        private static final long serialVersionUID = -6934457612643307520L;
        private final JLabel sizeCheck = new JLabel();

        public MapWithAIDownloadPanel(MapWithAIDownloadReader downloadReader) {
            super(downloadReader);
            setLayout(new GridBagLayout());

            Font labelFont = sizeCheck.getFont();
            sizeCheck.setFont(labelFont.deriveFont(Font.PLAIN, labelFont.getSize()));
            add(sizeCheck, GBC.eol().anchor(GridBagConstraints.EAST).insets(5, 5, 5, 2));
            setMinimumSize(new Dimension(450, 115));

        }

        @Override
        public MapWithAIDownloadData getData() {
            Consumer<Collection<Object>> errorReporter = errors -> {
            };
            return new MapWithAIDownloadData(MapWithAIPreferenceHelper.getMapWithAIUrl(), errorReporter);
        }

        @Override
        public void rememberSettings() {
            // Do nothing
        }

        @Override
        public void restoreSettings() {
            // Do nothing
        }

        @Override
        public boolean checkDownload(DownloadSettings settings) {
            if (!settings.getDownloadBounds().isPresent()) {
                JOptionPane.showMessageDialog(this.getParent(), tr("Please select a download area first."), tr("Error"),
                        JOptionPane.ERROR_MESSAGE);
            }
            return settings.getDownloadBounds().isPresent();
        }

        @Override
        public String getSimpleName() {
            return "mapwithaidownloadpanel";
        }

        @Override
        public Icon getIcon() {
            return new ImageProvider("dialogs", "mapwithai")
                    .setMaxHeight(ImageProvider.ImageSizes.SIDEBUTTON.getVirtualHeight()).get();
        }

        @Override
        public void boundingBoxChanged(Bounds bbox) {
            updateSizeCheck(bbox);
        }

        private void updateSizeCheck(Bounds bbox) {
            if (bbox == null) {
                sizeCheck.setText(tr("No area selected yet"));
                sizeCheck.setForeground(Color.darkGray);
                return;
            }

            displaySizeCheckResult(isDownloadAreaTooLarge(bbox));
        }

        /**
         * Check if the download area is too large
         *
         * @param bound The bound to check
         * @return {@code true} if the area is too large
         */
        public static boolean isDownloadAreaTooLarge(Bounds bound) {
            double width = Math.max(
                    bound.getMin().greatCircleDistance(new LatLon(bound.getMinLat(), bound.getMaxLon())),
                    bound.getMax().greatCircleDistance(new LatLon(bound.getMaxLat(), bound.getMinLon())));
            double height = Math.max(
                    bound.getMin().greatCircleDistance(new LatLon(bound.getMaxLat(), bound.getMinLon())),
                    bound.getMax().greatCircleDistance(new LatLon(bound.getMinLat(), bound.getMaxLon())));
            return height > MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS
                    || width > MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS;
        }

        private void displaySizeCheckResult(boolean isAreaTooLarge) {
            if (isAreaTooLarge) {
                sizeCheck.setText(tr("Download area too large; will probably be rejected by server"));
                sizeCheck.setForeground(Color.red);
            } else {
                sizeCheck.setText(tr("Download area ok, size probably acceptable to server"));
                sizeCheck.setForeground(Color.darkGray);
            }
        }
    }
}
