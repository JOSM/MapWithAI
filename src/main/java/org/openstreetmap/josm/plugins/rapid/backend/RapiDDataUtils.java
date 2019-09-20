// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

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
	private static Set<String> API_LIST = new HashSet<>();
	static {
		addRapidApi(new StringBuilder().append("https://www.facebook.com/maps/ml_roads?").append("conflate_with_osm=")
				.append(true).append("&").append("theme=")
				.append("ml_road_vector").append("&").append("collaborator=").append("fbid").append("&")
				.append("token=").append(RAPID_API_TOKEN).append("&").append("hash=").append("ASYM8LPNy8k1XoJiI7A")
				.append("&").append("bbox={bbox}").toString());
	}

	private RapiDDataUtils() {
		// Hide the constructor
	}

	@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
	public static DataSet getData(BBox bbox) {
		Logging.setLogLevel(Logging.LEVEL_DEBUG);
		InputStream inputStream = null;
		DataSet dataSet = new DataSet();
		for (String urlString : API_LIST) {
			try {
				final URL url = new URL(urlString.replace("{bbox}", bbox.toStringCSV(",")));
				Logging.error("{0}: Getting {1}", RapiDPlugin.NAME, url.toString());

				inputStream = url.openStream();
				dataSet.mergeFrom(OsmReader.parseDataSet(inputStream, null));
			} catch (UnsupportedOperationException | IllegalDataException | IOException e) {
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
		}
		return dataSet;
	}

	public static void addRapidApi(String url) {
		API_LIST.add(url);
	}
}
