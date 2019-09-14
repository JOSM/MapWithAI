// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
		CloseableHttpResponse response = null;
		DataSet dataSet = new DataSet();
		try {
			final URI uri = new URIBuilder().setScheme("https").setHost("www.facebook.com").setPath("/maps/ml_roads")
					.setParameter("conflate_with_osm", "true").setParameter("theme", "ml_road_vector")
					.setParameter("collaborator", "fbid").setParameter("token", RAPID_API_TOKEN)
					.setParameter("hash", "ASYM8LPNy8k1XoJiI7A").setParameter("bbox", bbox.toStringCSV(",")).build();
			final HttpGet httpget = new HttpGet(uri);
			final CloseableHttpClient httpclient = HttpClients.createDefault();
			Logging.info("{0}: Getting {1}", RapiDPlugin.NAME, uri.toASCIIString());
			response = httpclient.execute(httpget);
			dataSet = OsmReader.parseDataSet(response.getEntity().getContent(), null);
		} catch (URISyntaxException | UnsupportedOperationException | IllegalDataException | IOException e) {
			Logging.debug(e);
		} finally {
			if (response != null) {
				try {
					response.close();
				} catch (IOException e) {
					Logging.debug(e);
				}
			}
		}
		return dataSet;
	}
}
