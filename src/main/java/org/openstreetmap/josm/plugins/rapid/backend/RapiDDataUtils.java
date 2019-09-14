// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.tools.Logging;

/**
 * @author Taylor Smock
 *
 */
public final class RapiDDataUtils {
	private static final String RAPID_API_TOKEN = "ASZUVdYpCkd3M6ZrzjXdQzHulqRMnxdlkeBJWEKOeTUoY_Gwm9fuEd2YObLrClgDB_xfavizBsh0oDfTWTF7Zb4C";

	private RapiDDataUtils() {
		// Hide the constructor
	}

	@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
	public static DataSet getData(BBox bbox) {
		Logging.setLogLevel(Logging.LEVEL_DEBUG);
		InputStream inputStream = null;
		DataSet dataSet = new DataSet();
		try {
			final String query = new StringBuilder().append("conflate_with_osm=").append(true).append("theme=")
					.append("ml_road_vector").append("&").append("collaborator=").append("fbid").append("&")
					.append("token=").append(RAPID_API_TOKEN).append("&").append("hash=").append("ASYM8LPNy8k1XoJiI7A")
					.append("&").append("bbox=").append(bbox.toString()).toString();
			final URI uri = new URI("https", null, "www.facebook.com", 80, "/maps/ml_roads", query, null);
			final URL url = uri.toURL();
			inputStream = url.openStream();
			Logging.info("{0}: Getting {1}", RapiDPlugin.NAME, uri.toASCIIString());
			dataSet = OsmReader.parseDataSet(inputStream, null);
		} catch (URISyntaxException | UnsupportedOperationException | IllegalDataException | IOException e) {
			Logging.debug(e);
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					Logging.debug(e);
				}
			}
		}
		return dataSet;
	}
}
