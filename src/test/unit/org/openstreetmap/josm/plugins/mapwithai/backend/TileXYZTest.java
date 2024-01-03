// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.data.Bounds;

/**
 * Test class for {@link TileXYZ}
 */
class TileXYZTest {
    static Stream<Arguments> testTileCalculations() {
        return Stream.of(Arguments.of(39.07035, -108.5709286, 52013, 100120, 18),
                Arguments.of(39.0643941, -108.5610312, 52020, 100125, 18),
                Arguments.of(39.0643941, -108.5709286, 52013, 100125, 18),
                Arguments.of(39.07035, -108.5610312, 52020, 100120, 18));
    }

    @ParameterizedTest
    @MethodSource
    void testTileCalculations(double lat, double lon, int x, int y, int z) {
        final var tile = TileXYZ.tileFromLatLonZoom(lon, lat, z);
        assertAll(() -> assertEquals(x, tile.x()), () -> assertEquals(y, tile.y()), () -> assertEquals(z, tile.z()));
    }

    /**
     * Check that the tiles calculated for a bbox are correct
     */
    @Test
    void testNonRegressionGH44() {
        final var tiles = TileXYZ.tilesFromBBox(18, new Bounds(39.0643941, -108.5709286, 39.07035, -108.5610312))
                .toArray(TileXYZ[]::new);
        assertAll(() -> assertEquals(100125, Arrays.stream(tiles).mapToInt(TileXYZ::y).max().orElse(0)),
                () -> assertEquals(100120, Arrays.stream(tiles).mapToInt(TileXYZ::y).min().orElse(0)),
                () -> assertEquals(52013, Arrays.stream(tiles).mapToInt(TileXYZ::x).min().orElse(0)),
                () -> assertEquals(52020, Arrays.stream(tiles).mapToInt(TileXYZ::x).max().orElse(0)));
        assertEquals(48, tiles.length, "Should be 6x8 tiles (rangeClosed)");
    }
}
