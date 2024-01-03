// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.Bounds;

/**
 * Create a tile
 *
 * @param x The x coordinate of the tile
 * @param y The y coordinate of the tile
 * @param z The zoom level
 */
record TileXYZ(int x, int y, int z) {
    /**
     * Checks to see if the given bounds are functionally equal to this tile
     *
     * @param left   left
     * @param bottom bottom
     * @param right  right
     * @param top    top
     */
    boolean checkBounds(double left, double bottom, double right, double top) {
        final var thisLeft = xToLongitude(this.x, this.z);
        final var thisRight = xToLongitude(this.x + 1, this.z);
        final var thisBottom = yToLatitude(this.y + 1, this.z);
        final var thisTop = yToLatitude(this.y, this.z);
        return equalsEpsilon(thisLeft, left, this.z) && equalsEpsilon(thisRight, right, this.z)
                && equalsEpsilon(thisBottom, bottom, this.z) && equalsEpsilon(thisTop, top, this.z);
    }

    private static boolean equalsEpsilon(double first, double second, int z) {
        // 0.1% of tile size is considered to be "equal"
        final var maxDiff = (360 / Math.pow(2, z)) / 1000;
        final var diff = Math.abs(first - second);
        return diff <= maxDiff;
    }

    private static double xToLongitude(int x, int z) {
        return (x / Math.pow(2, z)) * 360 - 180;
    }

    private static double yToLatitude(int y, int z) {
        var t = Math.PI - 2 * Math.PI * y / Math.pow(2, z);
        return 180 / Math.PI * Math.atan((Math.exp(t) - Math.exp(-t)) / 2);
    }

    /**
     * Convert bounds to tiles
     *
     * @param zoom   The zoom level to use
     * @param bounds The bounds to convert to tiles
     * @return A stream of tiles for the bounds at the given zoom level
     */
    static Stream<TileXYZ> tilesFromBBox(int zoom, Bounds bounds) {
        final var left = bounds.getMinLon();
        final var bottom = bounds.getMinLat();
        final var right = bounds.getMaxLon();
        final var top = bounds.getMaxLat();
        final var tile1 = tileFromLatLonZoom(left, bottom, zoom);
        final var tile2 = tileFromLatLonZoom(right, top, zoom);
        return IntStream.rangeClosed(tile1.x, tile2.x)
                .mapToObj(x -> IntStream.rangeClosed(tile2.y, tile1.y).mapToObj(y -> new TileXYZ(x, y, zoom)))
                .flatMap(stream -> stream);
    }

    /**
     * Checks to see if the given bounds are functionally equal to this tile
     *
     * @param left   left lon
     * @param bottom bottom lat
     * @param right  right lon
     * @param top    top lat
     */
    static TileXYZ tileFromBBox(double left, double bottom, double right, double top) {
        var zoom = 18;
        while (zoom > 0) {
            final var tile1 = tileFromLatLonZoom(left, bottom, zoom);
            final var tile2 = tileFromLatLonZoom(right, top, zoom);
            if (tile1.equals(tile2)) {
                return tile1;
            } else if (tile1.checkBounds(left, bottom, right, top)) {
                return tile1;
            } else if (tile2.checkBounds(left, bottom, right, top)) {
                return tile2;
                // Just in case the coordinates are _barely_ in other tiles and not the "common"
                // tile
            } else if (Math.abs(tile1.x() - tile2.x()) <= 2 && Math.abs(tile1.y() - tile2.y()) <= 2) {
                final var tileT = new TileXYZ((tile1.x() + tile2.x()) / 2, (tile1.y() + tile2.y()) / 2, zoom);
                if (tileT.checkBounds(left, bottom, right, top)) {
                    return tileT;
                }
            }
            zoom--;
        }
        return new TileXYZ(0, 0, 0);
    }

    static TileXYZ tileFromLatLonZoom(double lon, double lat, int zoom) {
        var xCoordinate = Math.toIntExact(Math.round(Math.floor(Math.pow(2, zoom) * (180 + lon) / 360)));
        var yCoordinate = Math.toIntExact(Math.round(Math.floor(Math.pow(2, zoom)
                * (1 - (Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI)) / 2)));
        return new TileXYZ(xCoordinate, yCoordinate, zoom);
    }

    /**
     * Extends a bounds object to contain this tile
     *
     * @param currentBounds The bounds to extend
     */
    void expandBounds(Bounds currentBounds) {
        final var thisLeft = xToLongitude(this.x, this.z);
        final var thisRight = xToLongitude(this.x + 1, this.z);
        final var thisBottom = yToLatitude(this.y + 1, this.z);
        final var thisTop = yToLatitude(this.y, this.z);
        currentBounds.extend(thisBottom, thisLeft);
        currentBounds.extend(thisTop, thisRight);
    }
}
