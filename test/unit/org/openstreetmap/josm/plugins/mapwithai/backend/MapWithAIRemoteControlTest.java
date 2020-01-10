// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.stream.Collectors;

import org.awaitility.Durations;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.io.remotecontrol.handler.RequestHandler.RequestHandlerBadRequestException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Utils;

import com.github.tomakehurst.wiremock.WireMockServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAIRemoteControlTest {

    /**
     * Rule used for tests throwing exceptions.
     */
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Setup test.
     */
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().main().projection();

    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        wireMock.start();
        MapWithAIPreferenceHelper.setMapWithAIURLs(MapWithAIPreferenceHelper.getMapWithAIURLs().stream().map(map -> {
            map.put("url", map.getOrDefault("url", MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)
                    .replace("https://www.facebook.com", wireMock.baseUrl()));
            return map;
        }).collect(Collectors.toList()));
    }

    @After
    public void tearDown() {
        wireMock.stop();
    }

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
     * @throws Exception if any error occurs
     */
    @Test
    public void testBadRequestInvalidUrl() throws Exception {
        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage("MalformedURLException: no protocol: invalid_url");
        newHandler("https://localhost?url=invalid_url").handle();
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
    public void testNominalRequest() throws Exception {
        newHandler("https://localhost?url="
                + Utils.encodeUrl(MapWithAIPreferenceHelper.getMapWithAIUrl().get(0).get("url"))).handle();
        assertFalse(MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertTrue(MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().isEmpty());
    }

    @Test
    public void testTemporaryUrl() throws Exception {
        final String badUrl = "https://bad.url";
        newHandler("https://localhost?url=" + Utils.encodeUrl(badUrl)).handle();
        assertFalse(MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .anyMatch(map -> badUrl.equals(map.get("url"))));
        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));
        assertNotEquals(badUrl, MapWithAIPreferenceHelper.getMapWithAIUrl());

        final String badUrl2 = "NothingToSeeHere";
        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage("MalformedURLException: no protocol: " + badUrl2);

        newHandler("https://localhost?url=" + Utils.encodeUrl(badUrl2)).handle();
    }

    @Test
    public void testTemporaryMaxAdd() throws Exception {
        final Integer maxObj = 1;
        newHandler("http://127.0.0.1:8111/mapwithai?bbox=" + getTestBBox().toStringCSV(",") + "&max_obj="
                + maxObj.toString()).handle();
        assertFalse(MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        assertEquals(maxObj.intValue(), MapWithAIPreferenceHelper.getMaximumAddition());
        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));
        assertNotEquals(maxObj.intValue(), MapWithAIPreferenceHelper.getMaximumAddition());

        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage("NumberFormatException (For input string: \"BAD_VALUE\")");
        newHandler("http://127.0.0.1:8111/mapwithai?bbox=" + getTestBBox().toStringCSV(",") + "&max_obj=BAD_VALUE")
                .handle();
    }

    @Test
    public void testBBox() throws Exception {
        BBox temp = getTestBBox();
        newHandler("http://127.0.0.1:8111/mapwithai?bbox={bbox}".replace("{bbox}", temp.toStringCSV(","))).handle();
        assertFalse(MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).isEmpty());

        await().atMost(Durations.TEN_SECONDS)
                .until(() -> !MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().isEmpty());
        final BBox added = MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().iterator().next()
                .toBBox();
        assertTrue(temp.bounds(added));

        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));
        temp = new BBox(39.0621223, -108.4625421, 39.0633059, -108.4594728);
        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage(
                "Bad bbox: 39.0621223,-108.4625421,39.0633059,-108.4594728 (converted to [ x: 39.0621223 -> 39.0633059, y: -108.4625421 -> -108.4594728 ])");
        newHandler("http://127.0.0.1:8111/mapwithai?bbox={bbox}".replace("{bbox}", temp.toStringCSV(","))).handle();
    }

    @Test
    public void testGetUsage() throws Exception {
        assertEquals(tr("downloads {0} data", MapWithAIPlugin.NAME), newHandler(null).getUsage());
    }

    @Test
    public void testGetPermissionMessage() throws Exception {
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
    public void testGetUsageExamples() throws Exception {
        assertEquals(6, newHandler(null).getUsageExamples().length);
    }
}
