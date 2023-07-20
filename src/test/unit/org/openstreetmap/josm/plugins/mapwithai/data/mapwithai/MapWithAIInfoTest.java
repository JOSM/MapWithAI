// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.json.Json;
import javax.json.JsonArray;

import java.awt.Polygon;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.tools.Logging;

import mockit.Mock;
import mockit.MockUp;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

@BasicPreferences
class MapWithAIInfoTest {

    @ParameterizedTest
    @MethodSource("provideMapWithAIInfoInitializers")
    void testInitializersWorked(MapWithAIInfo i, String name, String url, String id, MapWithAIType type) {
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
    void testCloneInitializer() throws IllegalArgumentException, IllegalAccessException {
        final List<String> ignoredFields = Collections
                .singletonList("replacementTagsSupplier" /* The supplier shouldn't be copied */);
        MapWithAIInfo orig = new MapWithAIInfo("Test info");
        // Use random to ensure that I do not accidentally introduce a dependency
        Random random = new Random();
        long seed = random.nextLong();
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
                f.set(orig, "Random String Value " + random.nextDouble());
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

    @Test
    void testEquals() {
        EqualsVerifier.forClass(MapWithAIInfo.class).suppress(Warning.NONFINAL_FIELDS)
                .withPrefabValues(Polygon.class, new Polygon(),
                        new Polygon(new int[] { 0, 0, 1, 1 }, new int[] { 0, 1, 0, 1 }, 4))
                .withOnlyTheseFields("url", "sourceType").usingGetClass().verify();
    }

    /**
     * Non-regression test for #22440: NPE in
     * {@link MapWithAIInfo#getConflationUrl}. This is caused by the
     * {@link MapWithAIInfo#getId()} being {@code null}.
     */
    @Test
    void testNonRegression22440UpdateFallback() {
        TestUtils.assumeWorkingJMockit();
        MapWithAIInfo info = new MapWithAIInfo("22440", "https://test.example", null);
        info.setConflationUrl("https://test.example/{id}");
        info.setConflation(true);
        MapWithAILayerInfo.getInstance().clear();
        MapWithAILayerInfo.getInstance().add(info);
        AtomicBoolean updateCalled = new AtomicBoolean();
        new MockUp<MapWithAILayerInfo>() {
            @Mock
            public void load(boolean fastFail, MapWithAILayerInfo.FinishListener listener) {
                updateCalled.set(true);
                listener.onFinish();
            }
        };
        assertDoesNotThrow(info::getUrlExpanded);
        MainApplication.worker.submit(() -> {
            /* Sync thread */ });
        GuiHelper.runInEDTAndWait(() -> {
            /* Sync thread */ });
        assertTrue(updateCalled.get());
        assertEquals(1, MapWithAILayerInfo.getInstance().getLayers().size());
        assertSame(info, MapWithAILayerInfo.getInstance().getLayers().get(0));
    }

    /**
     * Non-regression test for #22440: NPE in
     * {@link MapWithAIInfo#getConflationUrl}. This is caused by the
     * {@link MapWithAIInfo#getId()} being {@code null}.
     */
    @Test
    void testNonRegression22440Update() {
        TestUtils.assumeWorkingJMockit();
        MapWithAIInfo info = new MapWithAIInfo("22440", "https://test.example", null);
        info.setConflationUrl("https://test.example/{id}");
        info.setConflation(true);
        MapWithAILayerInfo.getInstance().clear();
        MapWithAILayerInfo.getInstance().add(info);
        AtomicBoolean updateCalled = new AtomicBoolean();
        new MockUp<MapWithAILayerInfo>() {
            @Mock
            public void load(boolean fastFail, MapWithAILayerInfo.FinishListener listener) {
                updateCalled.set(true);
                listener.onFinish();
            }

            @Mock
            public List<MapWithAIInfo> getAllDefaultLayers() {
                return Collections.singletonList(new MapWithAIInfo("22440Update", "https://test.example", "22"));
            }
        };
        assertNull(info.getId());
        assertDoesNotThrow(info::getUrlExpanded);
        MainApplication.worker.submit(() -> {
            /* Sync thread */ });
        GuiHelper.runInEDTAndWait(() -> {
            /* Sync thread */ });
        assertTrue(updateCalled.get());
        assertEquals(1, MapWithAILayerInfo.getInstance().getLayers().size());
        assertSame(info, MapWithAILayerInfo.getInstance().getLayers().get(0));
        assertEquals("22", info.getId());
    }
}
