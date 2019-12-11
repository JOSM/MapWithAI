// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.net.MalformedURLException;
import java.net.URL;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.remotecontrol.PermissionPrefWithDefault;
import org.openstreetmap.josm.io.remotecontrol.handler.RequestHandler;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;

public class MapWithAIRemoteControl extends RequestHandler.RawURLParseRequestHandler {

    private static final PermissionPrefWithDefault PERMISSION_PREF_WITH_DEFAULT = new PermissionPrefWithDefault(
            MapWithAIPlugin.NAME.concat(".remote_control"), true, tr("MapWithAI"));

    private BBox download;
    private BBox crop;
    private Integer maxObj;
    private Boolean switchLayer;
    private String url;
    private String source;

    private static final String MAX_OBJ = "max_obj";
    private static final String SWITCH_LAYER = "switch_layer";
    private static final String BBOX = "bbox";
    private static final String CROP_BBOX = "crop_bbox";
    private static final String URL_STRING = "url";
    private static final String SOURCE_STRING = "source";

    public MapWithAIRemoteControl() {
        super();
    }

    @Override
    protected void validateRequest() throws RequestHandlerBadRequestException {
        if (args != null) {
            try {
                if (args.containsKey(BBOX)) {
                    download = parseBBox(args.get(BBOX));
                }
                if (args.containsKey(CROP_BBOX)) {
                    crop = parseBBox(args.get(CROP_BBOX));
                }
                if (args.containsKey(MAX_OBJ)) {
                    maxObj = Integer.parseInt(args.get(MAX_OBJ));
                }
                if (args.containsKey(URL_STRING)) {
                    final String urlString = args.get(URL_STRING);
                    // Ensure the URL_STRING is valid
                    url = new URL(urlString).toString();
                }
                if (args.containsKey(SOURCE_STRING)) {
                    source = args.get(SOURCE_STRING);
                }
                if (args.containsKey(SWITCH_LAYER)) {
                    switchLayer = Boolean.parseBoolean(args.get(SWITCH_LAYER));
                }
            } catch (NumberFormatException e) {
                throw new RequestHandlerBadRequestException("NumberFormatException (" + e.getMessage() + ')', e);
            } catch (MalformedURLException e) {
                throw new RequestHandlerBadRequestException("MalformedURLException: " + e.getMessage(), e);
            }
        }
    }

    private static BBox parseBBox(String coordinates) throws RequestHandlerBadRequestException {
        final String[] coords = coordinates.split(",", -1);
        final BBox tBBox = new BBox();
        if (coords.length >= 4 && coords.length % 2 == 0) {
            for (int i = 0; i < coords.length / 2; i++) {
                tBBox.add(Double.parseDouble(coords[2 * i]), Double.parseDouble(coords[2 * i + 1]));
            }
        }
        if (!tBBox.isInWorld()) {
            throw new RequestHandlerBadRequestException(
                    tr("Bad bbox: {0} (converted to {1})", coordinates, tBBox.toString()));
        }
        return tBBox;
    }

    @Override
    protected void handleRequest() throws RequestHandlerErrorException, RequestHandlerBadRequestException {
        if (crop != null && crop.isInWorld()) {
            MainApplication.getLayerManager()
                    .addLayer(new GpxLayer(DetectTaskingManagerUtils.createTaskingManagerGpxData(crop),
                            DetectTaskingManagerUtils.MAPWITHAI_CROP_AREA));
        }

        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);

        if (maxObj != null) {
            MapWithAIPreferenceHelper.setMaximumAddition(maxObj, false);
        }
        if (url != null) {
            // TODO make option for permanent url
            String tSource = source == null ? url : source;
            MapWithAIPreferenceHelper.setMapWithAIUrl(tSource, url, true, false);
        }
        if (switchLayer != null) {
            MapWithAIPreferenceHelper.setSwitchLayers(switchLayer, false);
        }

        if (download != null && download.isInWorld()) {
            MapWithAIDataUtils.getMapWithAIData(layer, download);
        } else if (MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream()
                .anyMatch(tLayer -> !(tLayer instanceof MapWithAILayer))) {
            MapWithAIDataUtils.getMapWithAIData(layer);
        } else if (crop != null && crop.isInWorld()) {
            MapWithAIDataUtils.getMapWithAIData(layer, crop);
        }
    }

    @Override
    public String getPermissionMessage() {
        final String br = "<br />";
        final StringBuilder sb = new StringBuilder();
        sb.append(tr("Remote Control has been asked to load data from the API.")).append(" (").append(url).append(')')
                .append(br).append(tr("{0} will ", MapWithAIPlugin.NAME));
        if (Boolean.FALSE.equals(switchLayer)) {
            sb.append(tr("not "));
        }
        sb.append(tr("automatically switch layers.")).append(br);
        if (download != null) {
            sb.append(tr("We will download data in ")).append(download.toStringCSV(",")).append(br);
        }
        if (crop != null) {
            sb.append(tr("We will crop the data to ")).append(crop.toStringCSV(",")).append(br);
        }
        sb.append(tr("There is a maximum addition of {0} objects at one time", maxObj));
        return sb.toString();

    }

    @Override
    public PermissionPrefWithDefault getPermissionPref() {
        return PERMISSION_PREF_WITH_DEFAULT;
    }

    @Override
    public String[] getMandatoryParams() {
        return new String[] {};
    }

    @Override
    public String[] getOptionalParams() {
        return new String[] { BBOX, URL_STRING, MAX_OBJ, SWITCH_LAYER, CROP_BBOX };
    }

    @Override
    public String getUsage() {
        return tr("downloads {0} data", MapWithAIPlugin.NAME);
    }

    @Override
    public String[] getUsageExamples() {
        return new String[] { "/mapwithai", "/mapwithai?bbox=-108.4625421,39.0621223,-108.4594728,39.0633059",
                "/mapwithai?url=https://www.facebook.com/maps/ml_roads?conflate_with_osm=true&theme=ml_road_vector&collaborator=josm&token=ASb3N5o9HbX8QWn8G_NtHIRQaYv3nuG2r7_f3vnGld3KhZNCxg57IsaQyssIaEw5rfRNsPpMwg4TsnrSJtIJms5m&hash=ASawRla3rBcwEjY4HIY&bbox={bbox}",
                "/mapwithai?bbox=-108.4625421,39.0621223,-108.4594728,39.0633059&max_obj=1",
                "/mapwithai?bbox=-108.4625421,39.0621223,-108.4594728,39.0633059&switch_layer=false",
                "/mapwithai?crop_bbox=-108.4625421,39.0621223,-108.4594728,39.0633059" };
    }
}
