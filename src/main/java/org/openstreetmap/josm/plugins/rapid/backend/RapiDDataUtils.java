// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
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
	private static final Set<String> API_LIST = new HashSet<>();
	static {
		addRapidApi(new StringBuilder().append("https://www.facebook.com/maps/ml_roads?").append("conflate_with_osm=")
				.append(true).append("&").append("theme=")
				.append("ml_road_vector").append("&").append("collaborator=").append("fbid").append("&")
				.append("token=").append(RAPID_API_TOKEN).append("&").append("hash=").append("ASYM8LPNy8k1XoJiI7A")
				.append("&").append("result_type=").append("road_building_vector_xml")
				.append("&").append("bbox={bbox}").toString());
	}

	private RapiDDataUtils() {
		// Hide the constructor
	}

	/**
	 * Get a dataset from the API servers using a bbox
	 *
	 * @param bbox The bbox from which to get data
	 * @return A DataSet with data inside the bbox
	 */
	public static DataSet getData(BBox bbox) {
		InputStream inputStream = null;
		DataSet dataSet = new DataSet();
		for (String urlString : API_LIST) {
			try {
				final URL url = new URL(urlString.replace("{bbox}", bbox.toStringCSV(",")));
				Logging.debug("{0}: Getting {1}", RapiDPlugin.NAME, url.toString());

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

	/**
	 * Add specified source tags to objects without a source tag that also have a
	 * specific key
	 *
	 * @param dataSet    The {#link DataSet} to look through
	 * @param primaryKey The primary key that must be in the {@link OsmPrimitive}
	 * @param source     The specified source value (not tag)
	 */
	public static void addSourceTags(DataSet dataSet, String primaryKey, String source) {
		dataSet.allPrimitives().stream().filter(p -> p.hasKey(primaryKey) && !p.hasKey("source")).forEach(p -> {
			p.put("source", source);
			p.save();
		});
	}

	/**
	 * Add a url to the the API_LIST
	 *
	 * @param url A url with a "{bbox}" inside it (this is what gets replaced in {@link RapiDDataUtils#getData})
	 */
	public static void addRapidApi(String url) {
		API_LIST.add(url);
	}

	/**
	 * Remove primitives and their children from a dataset.
	 *
	 * @param primitives The primitives to remove
	 */
	public static void removePrimitivesFromDataSet(Collection<OsmPrimitive> primitives) {
		for (OsmPrimitive primitive : primitives) {
			if (primitive instanceof Relation) {
				removePrimitivesFromDataSet(((Relation) primitive).getMemberPrimitives());
			} else if (primitive instanceof Way) {
				for (Node node : ((Way) primitive).getNodes()) {
					DataSet ds = node.getDataSet();
					if (ds != null) {
						ds.removePrimitive(node);
					}
				}
			}
			DataSet ds = primitive.getDataSet();
			if (ds != null) {
				ds.removePrimitive(primitive);
			}
		}
	}

	/**
	 * Add primitives and their children to a collection
	 *
	 * @param collection A collection to add the primitives to
	 * @param primitives The primitives to add to the collection
	 */
	public static void addPrimitivesToCollection(Collection<OsmPrimitive> collection,
			Collection<OsmPrimitive> primitives) {
		Collection<OsmPrimitive> temporaryCollection = new TreeSet<>();
		for (OsmPrimitive primitive : primitives) {
			if (primitive instanceof Way) {
				temporaryCollection.addAll(((Way) primitive).getNodes());
			} else if (primitive instanceof Relation) {
				addPrimitivesToCollection(temporaryCollection, ((Relation) primitive).getMemberPrimitives());
			}
			temporaryCollection.add(primitive);
		}
		collection.addAll(temporaryCollection);
	}
}
