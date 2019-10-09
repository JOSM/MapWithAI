// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.net.MalformedURLException;
import java.net.URL;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.remotecontrol.PermissionPrefWithDefault;
import org.openstreetmap.josm.io.remotecontrol.handler.RequestHandler;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;

public class RapiDRemoteControl extends RequestHandler.RawURLParseRequestHandler {

    private BBox download = null;
    private BBox crop = null;
    private Integer maxObj = null;
    private Boolean switchLayer = null;
    private String url = null;

    private static final String MAX_OBJ = "max_obj";
    private static final String SWITCH_LAYER = "switch_layer";

    public RapiDRemoteControl() {
        super();
    }

    @Override
    protected void validateRequest() throws RequestHandlerBadRequestException {
        if (args != null) {
            try {
                if (args.containsKey("bbox")) {
                    download = parseBBox(args.get("bbox"));
                }
                if (args.containsKey("crop_bbox")) {
                    crop = parseBBox(args.get("crop_bbox"));
                }
                if (args.containsKey(MAX_OBJ)) {
                    maxObj = Integer.parseInt(args.get(MAX_OBJ));
                }
                if (args.containsKey("url")) {
                    final String urlString = args.get("url");
                    // Ensure the URL is valid
                    url = new URL(urlString).toString();
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
            MainApplication.getLayerManager().addLayer(new GpxLayer(
                    DetectTaskingManagerUtils.createTaskingManagerGpxData(crop), DetectTaskingManagerUtils.RAPID_CROP_AREA));
        }

        final RapiDLayer layer = RapiDDataUtils.getLayer(true);

        if (maxObj != null) {
            RapiDDataUtils.setMaximumAddition(maxObj, false);
        }
        if (url != null) {
            RapiDDataUtils.setRapiDUrl(url, false);
        }
        if (switchLayer != null) {
            RapiDDataUtils.setSwitchLayers(switchLayer, false);
        }

        if (download != null && download.isInWorld()) {
            layer.getDataSet().unlock();
            layer.getDataSet().mergeFrom(RapiDDataUtils.getData(download));
            layer.getDataSet().lock();
        } else if (MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream()
                .filter(tLayer -> !(tLayer instanceof RapiDLayer)).count() != 0) {
            RapiDDataUtils.getRapiDData(layer);
        } else if (crop != null && crop.isInWorld()) {
            layer.getDataSet().unlock();
            layer.getDataSet().mergeFrom(RapiDDataUtils.getData(crop));
            layer.getDataSet().lock();
        }
    }

    @Override
    public String getPermissionMessage() {
        final String br = "<br />";
        final StringBuilder sb = new StringBuilder();
        sb.append(tr("Remote Control has been asked to load data from the API."));
        sb.append(" (");
        sb.append(url);
        sb.append(")");
        sb.append(br);
        sb.append(tr("{0} will ", RapiDPlugin.NAME));
        if (!switchLayer) {
            sb.append(tr("not "));
        }
        sb.append(tr("automatically switch layers."));
        sb.append(br);
        if (download != null) {
            sb.append(tr("We will download data in "));
            sb.append(download.toStringCSV(","));
            sb.append(br);
        }
        if (crop != null) {
            sb.append(tr("We will crop the data to"));
            sb.append(crop.toStringCSV(","));
            sb.append(br);
        }
        sb.append(tr("There is a maximum addition of {0} objects at one time", maxObj));
        return sb.toString();

    }

    @Override
    public PermissionPrefWithDefault getPermissionPref() {
        return null;
    }

    @Override
    public String[] getMandatoryParams() {
        return new String[] {};
    }

    @Override
    public String[] getOptionalParams() {
        return new String[] { "bbox", "url", MAX_OBJ, SWITCH_LAYER, "crop_bbox" };
    }

}
