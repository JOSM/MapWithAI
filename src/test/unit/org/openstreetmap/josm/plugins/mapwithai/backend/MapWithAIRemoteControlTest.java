// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.io.remotecontrol.handler.RequestHandler.RequestHandlerBadRequestException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.annotations.Territories;
import org.openstreetmap.josm.testutils.annotations.ThreadSync;
import org.openstreetmap.josm.tools.Utils;

/**
 * Test class for {@link MapWithAIRemoteControl}
 *
 * @author Taylor Smock
 */
@BasicPreferences
@Main
@MapWithAISources
@NoExceptions
@Projection
@Territories
@ThreadSync
@Wiremock
class MapWithAIRemoteControlTest {
    private static MapWithAIRemoteControl newHandler(String url) throws RequestHandlerBadRequestException {
        final MapWithAIRemoteControl req = new MapWithAIRemoteControl();
        if (url != null) {
            req.setUrl(url);
        }
        return req;
    }

    /**
     * Unit test for bad request - invalid URL.
     *
     */
    @Test
    void testBadRequestInvalidUrl() {
        MapWithAIRemoteControl handler = assertDoesNotThrow(() -> newHandler("https://localhost?url=invalid_url"));
        Exception exception = assertThrows(RequestHandlerBadRequestException.class, handler::handle);
        assertEquals("MalformedURLException: no protocol: invalid_url", exception.getMessage());
    }

    private static BBox getTestBBox() {
        return new BBox(-108.4625, 39.0621, -108.4594, 39.0633);
    }

    /**
     * Unit test for nominal request.
     *
     * @throws Exception if any error occurs
     */
    @Test
    void testNominalRequest() throws Exception {
        newHandler("https://localhost?url="
                + Utils.encodeUrl(MapWithAILayerInfo.getInstance().getLayers().get(0).getUrl())).handle();
        await().atMost(Durations.ONE_SECOND)
                .until(() -> !MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertFalse(MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertTrue(MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().isEmpty());
    }

    @Test
    void testTemporaryUrl() throws Exception {
        final String badUrl = "https://bad.url";
        newHandler("https://localhost?url=" + Utils.encodeUrl(badUrl)).handle();
        await().atMost(Durations.ONE_SECOND)
                .until(() -> !MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());
        assertFalse(MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().stream().anyMatch(map -> badUrl.equals(map.getUrl())));
        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().stream().map(MapWithAIInfo::getUrl)
                .noneMatch(badUrl::equals));

        final String badUrl2 = "NothingToSeeHere";
        final MapWithAIRemoteControl handler = newHandler("https://localhost?url=" + Utils.encodeUrl(badUrl2));
        Exception exception = assertThrows(RequestHandlerBadRequestException.class, handler::handle);
        assertEquals("MalformedURLException: no protocol: " + badUrl2, exception.getMessage());

    }

    @Test
    void testTemporaryMaxAdd() throws Exception {
        final int maxObj = 1;
        newHandler("http://127.0.0.1:8111/mapwithai?bbox=" + getTestBBox().toStringCSV(",") + "&max_obj=" + maxObj)
                .handle();
        await().atMost(Durations.TWO_SECONDS)
                .until(() -> !MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertFalse(MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertEquals(maxObj, MapWithAIPreferenceHelper.getMaximumAddition());
        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));
        assertNotEquals(maxObj, MapWithAIPreferenceHelper.getMaximumAddition());
        final MapWithAIRemoteControl handler = assertDoesNotThrow(() -> newHandler(
                "http://127.0.0.1:8111/mapwithai?bbox=" + getTestBBox().toStringCSV(",") + "&max_obj=BAD_VALUE"));
        Exception exception = assertThrows(RequestHandlerBadRequestException.class, handler::handle);
        assertEquals("NumberFormatException (For input string: \"BAD_VALUE\")", exception.getMessage());
    }

    @Test
    void testBBox() throws Exception {
        BBox temp = getTestBBox();
        newHandler("http://127.0.0.1:8111/mapwithai?bbox={bbox}".replace("{bbox}", temp.toStringCSV(","))).handle();
        await().atMost(Durations.TWO_SECONDS)
                .until(() -> !MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertFalse(MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        await().atMost(Durations.TEN_SECONDS)
                .until(() -> !MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().isEmpty());
        final BBox added = MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().iterator().next()
                .toBBox();
        assertTrue(temp.bounds(added));

        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));
        BBox temp2 = new BBox(39.0621223, -108.4625421, 39.0633059, -108.4594728);
        final MapWithAIRemoteControl handler = assertDoesNotThrow(() -> newHandler(
                "http://127.0.0.1:8111/mapwithai?bbox={bbox}".replace("{bbox}", temp2.toStringCSV(","))));
        Exception exception = assertThrows(RequestHandlerBadRequestException.class, handler::handle);
        assertEquals(
                "Bad bbox: 39.0621223,-108.4625421,39.0633059,-108.4594728 (converted to Bounds[-108.4625421,39.0621223,-108.4594728,39.0633059])",
                exception.getMessage());
    }

    @Test
    void testGetUsage() throws Exception {
        assertEquals(tr("downloads {0} data", MapWithAIPlugin.NAME), newHandler(null).getUsage());
    }

    @Test
    void testGetPermissionMessage() throws Exception {
        MapWithAIRemoteControl handler = newHandler(null);
        assertEquals(tr(
                "Remote Control has been asked to load data from the API. (null)<br />MapWithAI will automatically switch layers.<br />There is a maximum addition of null objects at one time"),
                handler.getPermissionMessage());
        handler = newHandler("http://127.0.0.1:8111/mapwithai?switch_layer=false");
        handler.handle();
        assertEquals(tr(
                "Remote Control has been asked to load data from the API. (null)<br />MapWithAI will not automatically switch layers.<br />There is a maximum addition of null objects at one time"),
                handler.getPermissionMessage());
        handler = newHandler("http://127.0.0.1:8111/mapwithai?switch_layer=true");
        handler.handle();
        assertEquals(tr(
                "Remote Control has been asked to load data from the API. (null)<br />MapWithAI will automatically switch layers.<br />There is a maximum addition of null objects at one time"),
                handler.getPermissionMessage());
        handler = newHandler("http://127.0.0.1:8111/mapwithai?max_obj=1");
        handler.handle();
        assertEquals(tr(
                "Remote Control has been asked to load data from the API. (null)<br />MapWithAI will automatically switch layers.<br />There is a maximum addition of 1 objects at one time"),
                handler.getPermissionMessage());
        handler = newHandler("http://127.0.0.1:8111/mapwithai?max_obj=5");
        handler.handle();
        assertEquals(tr(
                "Remote Control has been asked to load data from the API. (null)<br />MapWithAI will automatically switch layers.<br />There is a maximum addition of 5 objects at one time"),
                handler.getPermissionMessage());
        BBox crop = new BBox(0, 0, 0.001, 0.001);
        handler = newHandler("http://127.0.0.1:8111/mapwithai?crop_bbox=".concat(crop.toStringCSV(",")));
        handler.handle();
        assertEquals(tr(
                "Remote Control has been asked to load data from the API. (null)<br />MapWithAI will automatically switch layers.<br />We will crop the data to 0.0,0.0,0.001,0.001<br />There is a maximum addition of null objects at one time"),
                handler.getPermissionMessage());
    }

    @Test
    void testGetUsageExamples() throws Exception {
        assertEquals(6, newHandler(null).getUsageExamples().length);
    }
}
