// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonValue;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.tools.DefaultGeoProperty;
import org.openstreetmap.josm.tools.GeoPropertyIndex;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Territories;
import org.openstreetmap.josm.tools.Utils;

/**
 * Reader to parse the list of available MapWithAI servers from an JSON
 * definition file.
 * <p>
 * The format is specified in the <a href=
 * "https://gitlab.com/gokaart/JOSM_MapWithAI/-/blob/pages/public/json/sources.json">MapWithAI
 * source</a>.
 */
public class MapWithAISourceReader implements Closeable {

    private final String source;
    private CachedFile cachedFile;
    private boolean fastFail;

    /**
     * Constructs a {@code ImageryReader} from a given filename, URL or internal
     * resource.
     *
     * @param source can be:
     *               <ul>
     *               <li>relative or absolute file name</li>
     *               <li>{@code file:///SOME/FILE} the same as above</li>
     *               <li>{@code http://...} a URL. It will be cached on disk.</li>
     *               <li>{@code resource://SOME/FILE} file from the classpath
     *               (usually in the current *.jar)</li>
     *               <li>{@code josmdir://SOME/FILE} file inside josm user data
     *               directory (since r7058)</li>
     *               <li>{@code josmplugindir://SOME/FILE} file inside josm plugin
     *               directory (since r7834)</li>
     *               </ul>
     */
    public MapWithAISourceReader(String source) {
        this.source = source;
    }

    /**
     * Parses MapWithAI source.
     *
     * @return list of source info
     * @throws IOException if any I/O error occurs
     */
    public List<MapWithAIInfo> parse() throws IOException {
        List<MapWithAIInfo> entries = Collections.emptyList();
        cachedFile = new CachedFile(source);
        cachedFile.setParam(String.join(",", ImageryInfo.getActiveIds()));
        cachedFile.setFastFail(fastFail);
        try (JsonReader reader = Json.createReader(cachedFile.setMaxAge(CachedFile.DAYS)
                .setCachingStrategy(CachedFile.CachingStrategy.IfModifiedSince).getContentReader())) {
            JsonStructure struct = reader.read();
            if (JsonValue.ValueType.OBJECT.equals(struct.getValueType())) {
                JsonObject jsonObject = struct.asJsonObject();
                entries = jsonObject.entrySet().stream().map(MapWithAISourceReader::parse).collect(Collectors.toList());
            }
            return entries;
        }
    }

    private static MapWithAIInfo parse(Map.Entry<String, JsonValue> entry) {
        String name = entry.getKey();
        if (JsonValue.ValueType.OBJECT.equals(entry.getValue().getValueType())) {
            JsonObject values = entry.getValue().asJsonObject();
            String url = values.getString("url", "");
            String type = MapWithAIInfo.MapWithAIType.THIRD_PARTY.getTypeString();
            String eula = values.getString("eula", "");
            String id = name.replace(" ", "_");
            JsonValue countries = values.getOrDefault("countries", JsonValue.EMPTY_JSON_OBJECT);
            List<ImageryBounds> bounds = new ArrayList<>();
            if (JsonValue.ValueType.OBJECT.equals(countries.getValueType())) {
                Set<String> codes = Territories.getKnownIso3166Codes();
                for (Map.Entry<String, JsonValue> country : countries.asJsonObject().entrySet()) {
                    if (codes.contains(country.getKey())) {
                        GeoPropertyIndex<Boolean> geoPropertyIndex = Territories.getGeoPropertyIndex(country.getKey());
                        if (geoPropertyIndex.getGeoProperty() instanceof DefaultGeoProperty) {
                            DefaultGeoProperty prop = (DefaultGeoProperty) geoPropertyIndex.getGeoProperty();
                            Rectangle2D areaBounds = prop.getArea().getBounds2D();
                            ImageryBounds tmp = new ImageryBounds(bboxToBoundsString(new BBox(areaBounds.getMinX(),
                                    areaBounds.getMinY(), areaBounds.getMaxX(), areaBounds.getMaxY()), ","), ",");
                            areaToShapes(prop.getArea()).forEach(tmp::addShape);
                            bounds.add(tmp);
                        }
                    }
                }
            }
            MapWithAIInfo info = new MapWithAIInfo(name, url, type, eula, id);
            info.setDefaultEntry(values.getBoolean("default", false));
            info.setParameters(values.getJsonArray("parameters"));
            if (values.containsKey("terms_of_use_url")) {
                info.setTermsOfUseURL(values.getString("terms_of_use_url"));
            }
            if (values.containsKey("privacy_policy_url")) {
                info.setPrivacyPolicyURL(values.getString("privacy_policy_url"));
            }
            if (!bounds.isEmpty()) {
                ImageryBounds bound = bounds.get(0);
                bounds.remove(0);
                bounds.forEach(bound::extend);
                bounds.forEach(b -> b.getShapes().forEach(bound::addShape));
                info.setBounds(bound);
            }
            return info;
        }
        return new MapWithAIInfo(name);
    }

    private static Collection<Shape> areaToShapes(java.awt.Shape shape) {
        PathIterator iterator = shape.getPathIterator(new AffineTransform());
        Shape defaultShape = new Shape();
        Collection<Shape> shapes = new ArrayList<>();
        float[] moveTo = null;
        while (!iterator.isDone()) {
            float[] coords = new float[6];
            int type = iterator.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                if (type == PathIterator.SEG_MOVETO) {
                    moveTo = coords;
                }
                defaultShape.addPoint(Float.toString(coords[1]), Float.toString(coords[0]));
            } else if (type == PathIterator.SEG_CLOSE && moveTo != null && moveTo.length >= 2) {
                defaultShape.addPoint(Float.toString(moveTo[1]), Float.toString(moveTo[0]));
                shapes.add(defaultShape);
                defaultShape = new Shape();
            } else {
                Logging.error(tr("No implementation for converting a segment of type {0} to coordinates", type));
            }
            iterator.next();
        }
        if (!defaultShape.getPoints().isEmpty()) {
            shapes.add(defaultShape);
        }
        return shapes;
    }

    private static String bboxToBoundsString(BBox bbox, String separator) {
        return String.join(separator, LatLon.cDdFormatter.format(bbox.getBottomRightLat()),
                LatLon.cDdFormatter.format(bbox.getTopLeftLon()), LatLon.cDdFormatter.format(bbox.getTopLeftLat()),
                LatLon.cDdFormatter.format(bbox.getBottomRightLon()));
    }

    /**
     * Sets whether opening HTTP connections should fail fast, i.e., whether a
     * {@link HttpClient#setConnectTimeout(int) low connect timeout} should be used.
     *
     * @param fastFail whether opening HTTP connections should fail fast
     * @see CachedFile#setFastFail(boolean)
     */
    public void setFastFail(boolean fastFail) {
        this.fastFail = fastFail;
    }

    @Override
    public void close() throws IOException {
        Utils.close(cachedFile);
    }
}