// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.sources.SourceBounds;
import org.openstreetmap.josm.tools.DefaultGeoProperty;
import org.openstreetmap.josm.tools.GeoPropertyIndex;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Territories;

/**
 * Get country data for use in info classes
 */
public final class CountryUtils {
    private CountryUtils() {
        /* Hide constructor */
    }

    /**
     * Get the country shape
     *
     * @param country The country to get the shape for
     * @return The (optional) bounds (may be empty if no country matched)
     */
    public static Optional<ImageryInfo.ImageryBounds> getCountryShape(String country) {
        GeoPropertyIndex<Boolean> geoPropertyIndex = Territories.getGeoPropertyIndex(country);
        if (geoPropertyIndex != null && geoPropertyIndex.getGeoProperty() instanceof DefaultGeoProperty) {
            DefaultGeoProperty prop = (DefaultGeoProperty) geoPropertyIndex.getGeoProperty();
            Rectangle2D areaBounds = prop.getArea().getBounds2D();
            ImageryInfo.ImageryBounds tmp = new ImageryInfo.ImageryBounds(bboxToBoundsString(
                    new BBox(areaBounds.getMinX(), areaBounds.getMinY(), areaBounds.getMaxX(), areaBounds.getMaxY())),
                    ",");
            areaToShapes(prop.getArea()).forEach(tmp::addShape);
            return Optional.of(tmp);
        }
        return Optional.empty();
    }

    /**
     * Get the country for a given shape
     *
     * @param shape The shape to get a country for
     * @return The country, if found
     */
    public static Optional<String> shapeToCountry(Shape shape) {
        for (String country : Territories.getKnownIso3166Codes()) {
            List<Shape> shapes = getCountryShape(country).map(SourceBounds::getShapes)
                    .orElseGet(Collections::emptyList);
            for (Shape checkShape : shapes) {
                if (Objects.equals(shape, checkShape)) {
                    return Optional.of(country);
                }
            }
        }
        return Optional.empty();
    }

    private static Collection<Shape> areaToShapes(java.awt.Shape shape) {
        PathIterator iterator = shape.getPathIterator(new AffineTransform());
        Shape defaultShape = new Shape();
        Collection<Shape> shapes = new ArrayList<>();
        float[] moveTo = null;
        float[] coords = new float[6];
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                if (type == PathIterator.SEG_MOVETO) {
                    moveTo = coords;
                }
                defaultShape.addPoint(Float.toString(coords[1]), Float.toString(coords[0]));
            } else if (type == PathIterator.SEG_CLOSE && moveTo != null) {
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

    private static String bboxToBoundsString(BBox bbox) {
        return String.join(",", LatLon.cDdFormatter.format(bbox.getBottomRightLat()),
                LatLon.cDdFormatter.format(bbox.getTopLeftLon()), LatLon.cDdFormatter.format(bbox.getTopLeftLat()),
                LatLon.cDdFormatter.format(bbox.getBottomRightLon()));
    }
}
