// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.io.remotecontrol.handler.RequestHandler.RequestHandlerBadRequestException;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Utils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class RapiDRemoteControlTest {

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

    private static RapiDRemoteControl newHandler(String url) throws RequestHandlerBadRequestException {
        final RapiDRemoteControl req = new RapiDRemoteControl();
        if (url != null) {
            req.setUrl(url);
        }
        return req;
    }

    /**
     * Unit test for bad request - invalid URL.
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
        newHandler("https://localhost?url=" + Utils.encodeUrl(RapiDDataUtils.getRapiDURL())).handle();
        Assert.assertFalse(MainApplication.getLayerManager().getLayersOfType(RapiDLayer.class).isEmpty());

        Assert.assertTrue(RapiDDataUtils.getLayer(false).getDataSet().getDataSourceBounds().isEmpty());
    }

    @Test
    public void testTemporaryUrl() throws Exception {
        String badUrl = "https://bad.url";
        newHandler("https://localhost?url=" + Utils.encodeUrl(badUrl)).handle();
        Assert.assertFalse(MainApplication.getLayerManager().getLayersOfType(RapiDLayer.class).isEmpty());

        Assert.assertEquals(badUrl, RapiDDataUtils.getRapiDURL());
        MainApplication.getLayerManager().removeLayer(RapiDDataUtils.getLayer(false));
        Assert.assertNotEquals(badUrl, RapiDDataUtils.getRapiDURL());

        badUrl = "NothingToSeeHere";
        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage("MalformedURLException: no protocol: " + badUrl);

        newHandler("https://localhost?url=" + Utils.encodeUrl(badUrl)).handle();
    }

    @Test
    public void testTemporaryMaxAdd() throws Exception {
        final Integer maxObj = 1;
        newHandler("http://127.0.0.1:8111/mapwithai?bbox=" + getTestBBox().toStringCSV(",") + "&max_obj="
                + maxObj.toString()).handle();
        Assert.assertFalse(MainApplication.getLayerManager().getLayersOfType(RapiDLayer.class).isEmpty());

        Assert.assertEquals(maxObj.intValue(), RapiDDataUtils.getMaximumAddition());
        MainApplication.getLayerManager().removeLayer(RapiDDataUtils.getLayer(false));
        Assert.assertNotEquals(maxObj.intValue(), RapiDDataUtils.getMaximumAddition());

        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage("NumberFormatException (For input string: \"BAD_VALUE\")");
        newHandler(
                "http://127.0.0.1:8111/mapwithai?bbox=" + getTestBBox().toStringCSV(",") + "&max_obj=BAD_VALUE")
        .handle();
    }

    @Test
    public void testBBox() throws Exception {
        BBox temp = getTestBBox();
        newHandler("http://127.0.0.1:8111/mapwithai?bbox={bbox}".replace("{bbox}", temp.toStringCSV(","))).handle();
        Assert.assertFalse(MainApplication.getLayerManager().getLayersOfType(RapiDLayer.class).isEmpty());

        final BBox added = RapiDDataUtils.getLayer(false).getDataSet().getDataSourceBounds().iterator().next().toBBox();
        Assert.assertTrue(temp.bounds(added));

        MainApplication.getLayerManager().removeLayer(RapiDDataUtils.getLayer(false));
        temp = new BBox(39.0621223, -108.4625421, 39.0633059, -108.4594728);
        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage(
                "Bad bbox: 39.0621223,-108.4625421,39.0633059,-108.4594728 (converted to [ x: 39.0621223 -> 39.0633059, y: -108.4625421 -> -108.4594728 ])");
        newHandler("http://127.0.0.1:8111/mapwithai?bbox={bbox}".replace("{bbox}", temp.toStringCSV(","))).handle();
    }
}
