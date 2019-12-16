// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.frontend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.json.JsonObject;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.download.AbstractDownloadSourcePanel;
import org.openstreetmap.josm.gui.download.DownloadDialog;
import org.openstreetmap.josm.gui.download.DownloadSettings;
import org.openstreetmap.josm.gui.download.DownloadSource;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.BoundingBoxMapWithAIDownloader;
import org.openstreetmap.josm.plugins.mapwithai.backend.DetectTaskingManagerUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.DownloadMapWithAITask;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
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
        data.getUrls().forEach(url -> {
            Future<?> future = task.download(new BoundingBoxMapWithAIDownloader(area, getUrl(url),
                    DetectTaskingManagerUtils.hasTaskingManagerLayer()), new DownloadParams(), area, null);
            MainApplication.worker.execute(new PostDownloadHandler(task, future, data.getErrorReporter()));
        });
    }

    private static String getUrl(Map<String, String> urlInformation) {
        StringBuilder sb = new StringBuilder();
        if (urlInformation.containsKey("url")) {
            sb.append(urlInformation.get("url"));
            if (urlInformation.containsKey("parameters")) {
                List<String> parameters = DataUrl.readJsonStringArraySimple(urlInformation.get("parameters"))
                        .parallelStream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                        .filter(map -> map.getBoolean("enabled", false)).filter(map -> map.containsKey("parameter"))
                        .map(map -> map.getString("parameter")).collect(Collectors.toList());
                if (!parameters.isEmpty()) {
                    sb.append('&').append(String.join("&", parameters));
                }
            }
        }
        return sb.toString();
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
        private final List<Map<String, String>> url;
        private final Consumer<Collection<Object>> errorReporter;

        MapWithAIDownloadData(List<Map<String, String>> list, Consumer<Collection<Object>> errorReporter) {
            this.url = list;
            this.errorReporter = errorReporter;
        }

        List<Map<String, String>> getUrls() {
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

            double width = Math.max(bbox.getMin().greatCircleDistance(new LatLon(bbox.getMinLat(), bbox.getMaxLon())),
                    bbox.getMax().greatCircleDistance(new LatLon(bbox.getMaxLat(), bbox.getMinLon())));
            double height = Math.max(bbox.getMin().greatCircleDistance(new LatLon(bbox.getMaxLat(), bbox.getMinLon())),
                    bbox.getMax().greatCircleDistance(new LatLon(bbox.getMinLat(), bbox.getMaxLon())));
            displaySizeCheckResult(height > MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS
                    || width > MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS);
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
