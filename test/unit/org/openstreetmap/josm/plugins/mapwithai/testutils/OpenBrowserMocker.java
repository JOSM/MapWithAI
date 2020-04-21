// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.net.URI;

import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.OpenBrowser;

import mockit.Mock;
import mockit.MockUp;

/** TODO Remove if #18766 is merged */
public class OpenBrowserMocker extends MockUp<OpenBrowser> {
    @Mock
    public static String displayUrl(URI uri) {
        CheckParameterUtil.ensureParameterNotNull(uri, "uri");

        Logging.debug(tr("Opening URL: {0}", uri));

        return null;
    }
}
