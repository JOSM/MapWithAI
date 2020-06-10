// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MapWithAIInfoTest {

    @ParameterizedTest
    @MethodSource("provideMapWithAIInfoInitializers")
    public void assertInitializersWorked(MapWithAIInfo i, String name, String url, String id, MapWithAIType type) {
        assertEquals(name, i.getName());
        assertEquals(id, i.getId());
        assertEquals(url, i.getUrl());
        assertEquals(type, i.getSourceType());
    }

    /**
     * Provide initializers for the tests, along with expected arguments.
     *
     * @return A stream of arguments for assertInitializersWorked
     */
    protected static Stream<Arguments> provideMapWithAIInfoInitializers() {
        String name = "TEST";
        String url = "https://test.url";
        String id = "a;lkdjfadl;ksfj";
        String eula = "";
        MapWithAIType type = MapWithAIType.FACEBOOK;
        MapWithAIType defaultType = MapWithAIType.THIRD_PARTY;
        MapWithAIInfo tempInfo = new MapWithAIInfo(name, url, id);
        return Stream.of(Arguments.of(new MapWithAIInfo(), null, null, null, defaultType),
                Arguments.of(new MapWithAIInfo(name), name, null, null, defaultType),
                Arguments.of(new MapWithAIInfo(name, url), name, url, null, defaultType),
                Arguments.of(new MapWithAIInfo(name, url, id), name, url, id, defaultType),
                Arguments.of(new MapWithAIInfo(name, url, type.getTypeString(), eula, id), name, url, id, type),
                Arguments.of(new MapWithAIInfo(name, url, null, eula, id), name, url, id, defaultType),
                Arguments.of(new MapWithAIInfo(name, url, "", eula, id), name, url, id, defaultType),
                Arguments.of(new MapWithAIInfo(tempInfo), tempInfo.getName(), tempInfo.getUrl(), tempInfo.getId(),
                        tempInfo.getSourceType()));
    }
}
