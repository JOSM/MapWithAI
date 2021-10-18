// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.json.Json;
import javax.json.JsonArray;
import javax.management.ReflectionException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.tools.Logging;

class MapWithAIInfoTest {

    @ParameterizedTest
    @MethodSource("provideMapWithAIInfoInitializers")
    void assertInitializersWorked(MapWithAIInfo i, String name, String url, String id, MapWithAIType type) {
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

    @Test
    void testCloneInitializer() throws ReflectionException, IllegalArgumentException, IllegalAccessException {
        final List<String> ignoredFields = Collections
                .singletonList("replacementTagsSupplier" /* The supplier shouldn't be copied */);
        MapWithAIInfo orig = new MapWithAIInfo("Test info");
        // Use random to ensure that I do not accidentally introduce a dependency
        Random random = new Random();
        Long seed = random.nextLong();
        random.setSeed(seed);
        Logging.debug("Random seed for testCloneInitializer is {0}", seed);
        for (Field f : MapWithAIInfo.class.getDeclaredFields()) {
            if (Modifier.isFinal(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            Class<?> type = f.getType();
            if (f.getType().isAssignableFrom(Integer.TYPE)) {
                f.setInt(orig, random.nextInt());
            } else if (f.getType().isAssignableFrom(Double.TYPE)) {
                f.setDouble(orig, random.nextDouble());
            } else if (f.getType().isAssignableFrom(Boolean.TYPE)) {
                f.setBoolean(orig, !f.getBoolean(orig)); // just set to non-default
            } else if (f.getType().isAssignableFrom(String.class)) {
                f.set(orig, "Random String Value " + Double.toString(random.nextDouble()));
            } else if (f.getType().isAssignableFrom(List.class)) {
                List<?> list = new ArrayList<>();
                list.add(null);
                f.set(orig, list);
            } else if (f.getType().isAssignableFrom(Map.class)) {
                Map<?, ?> map = new HashMap<>();
                f.set(orig, map);
            } else if (f.getType().isAssignableFrom(JsonArray.class)) {
                JsonArray array = Json.createArrayBuilder().addNull().addNull().build();
                f.set(orig, array);
            } else if (!ignoredFields.contains(f.getName())) {
                throw new IllegalArgumentException("Account for " + type.getSimpleName() + " " + f.getName());
            }
        }

        MapWithAIInfo copy = new MapWithAIInfo(orig);
        for (Field f : MapWithAIInfo.class.getDeclaredFields()) {
            if (Modifier.isFinal(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            assertEquals(f.get(orig), f.get(copy), MessageFormat.format("{0} should be the same", f.getName()));
        }
    }
}
