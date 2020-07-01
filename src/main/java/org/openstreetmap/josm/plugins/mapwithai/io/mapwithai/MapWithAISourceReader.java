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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
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

    private static final int MIN_NODE_FOR_CLOSED_WAY = 2;
    private static final int COORD_ARRAY_SIZE = 6;

    /**
     * Constructs a {@code MapWithAISourceReader} from a given filename, URL or
     * internal resource.
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
     * Parses MapWithAI entry sources
     *
     * @param jsonObject The json of the data sources
     * @return The parsed entries
     */
    public static List<MapWithAIInfo> parseJson(JsonObject jsonObject) {
        return jsonObject.entrySet().stream().map(MapWithAISourceReader::parse).collect(Collectors.toList());
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
        cachedFile.setFastFail(fastFail);
        try (JsonReader reader = Json.createReader(cachedFile.setMaxAge(CachedFile.DAYS)
                .setCachingStrategy(CachedFile.CachingStrategy.IfModifiedSince).getContentReader())) {
            JsonStructure struct = reader.read();
            if (JsonValue.ValueType.OBJECT == struct.getValueType()) {
                JsonObject jsonObject = struct.asJsonObject();
                entries = parseJson(jsonObject);
            }
            return entries;
        }
    }

    private static MapWithAIInfo parse(Map.Entry<String, JsonValue> entry) {
        String name = entry.getKey();
        if (JsonValue.ValueType.OBJECT == entry.getValue().getValueType()) {
            JsonObject values = entry.getValue().asJsonObject();
            String url = values.getString("url", "");
            String type = values.getString("type", MapWithAIType.values()[0].getDefault().getTypeString());
            String category = values.getString("category",
                    MapWithAICategory.values()[0].getDefault().getCategoryString());
            String eula = values.getString("eula", "");
            boolean conflation = values.getBoolean("conflate", false);
            String conflationUrl = values.getString("conflationUrl", null);
            String id = values.getString("id", name.replace(" ", "_"));
            String alreadyConflatedKey = values.getString("conflated_key", null);
            JsonValue countries = values.getOrDefault("countries", JsonValue.EMPTY_JSON_OBJECT);
            List<ImageryBounds> bounds = getBounds(countries);
            MapWithAIInfo info = new MapWithAIInfo(name, url, type, eula, id);
            info.setDefaultEntry(values.getBoolean("default", false));
            info.setParameters(values.getJsonArray("parameters"));
            info.setConflationParameters(values.getJsonArray("conflationParameters"));
            info.setConflation(conflation);
            info.setConflationUrl(conflationUrl);
            info.setSource(values.getString("source", null));
            info.setAlreadyConflatedKey(alreadyConflatedKey);
            info.setCategory(MapWithAICategory.fromString(category));
            if (values.containsKey("conflation_ignore_categories")) {
                JsonArray ignore = values.getJsonArray("conflation_ignore_categories");
                for (MapWithAICategory cat : ignore.getValuesAs(JsonString.class).stream().map(JsonString::getString)
                        .map(MapWithAICategory::fromString).filter(Objects::nonNull).collect(Collectors.toList())) {
                    info.addConflationIgnoreCategory(cat);
                }
            }
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

    private static List<ImageryInfo.ImageryBounds> getBounds(JsonValue countries) {
        if (JsonValue.ValueType.OBJECT == countries.getValueType()) {
            Set<String> codes;
            try {
                codes = Territories.getKnownIso3166Codes();
                List<ImageryBounds> bounds = new ArrayList<>();
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
                return bounds;
            } catch (NullPointerException e) {
                Logging.error(e);
            }

        }
        return new ArrayList<>();
    }

    private static Collection<Shape> areaToShapes(java.awt.Shape shape) {
        PathIterator iterator = shape.getPathIterator(new AffineTransform());
        Shape defaultShape = new Shape();
        Collection<Shape> shapes = new ArrayList<>();
        float[] moveTo = null;
        while (!iterator.isDone()) {
            float[] coords = new float[COORD_ARRAY_SIZE];
            int type = iterator.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                if (type == PathIterator.SEG_MOVETO) {
                    moveTo = coords;
                }
                defaultShape.addPoint(Float.toString(coords[1]), Float.toString(coords[0]));
            } else if (type == PathIterator.SEG_CLOSE && moveTo != null && moveTo.length >= MIN_NODE_FOR_CLOSED_WAY) {
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
