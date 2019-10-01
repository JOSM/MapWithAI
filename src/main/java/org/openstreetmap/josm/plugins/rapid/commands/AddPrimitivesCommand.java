// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Utils;

public class AddPrimitivesCommand extends Command {
    private final Collection<OsmPrimitive> add;
    private final Collection<OsmPrimitive> actuallyAdded;
    private final Collection<OsmPrimitive> selection;

    /**
     * Add source primitives (not {@code PrimitiveData}) to a dataset.
     *
     * @param data      The destination dataset
     * @param add       The primitives to add. It must be complete.
     * @param selection The primitives to select in the dataset. May be null.
     */
    public AddPrimitivesCommand(DataSet data, Collection<OsmPrimitive> add, Collection<OsmPrimitive> selection) {
        super(data);
        this.add = add;
        this.selection = selection;
        actuallyAdded = new ArrayList<>();
    }

    @Override
    public boolean executeCommand() {
        actuallyAdded.addAll(addPrimitives(getAffectedDataSet(), add));
        if (selection != null) {
            getAffectedDataSet().setSelected(selection);
        }
        return true;
    }

    @Override
    public void undoCommand() {
        DataSet ds = getAffectedDataSet();
        Utils.filteredCollection(actuallyAdded, Relation.class).stream().filter(ds::containsRelation)
        .forEach(ds::removePrimitive);
        Utils.filteredCollection(actuallyAdded, Way.class).stream().filter(ds::containsWay)
        .forEach(ds::removePrimitive);
        Utils.filteredCollection(actuallyAdded, Node.class).stream().filter(ds::containsNode)
        .forEach(ds::removePrimitive);
    }

    /**
     * Add primitives to a dataset
     *
     * @param ds         The dataset to add primitives to
     * @param primitives A collection of primitives to add
     * @return The primitives actually added
     */
    public static Collection<OsmPrimitive> addPrimitives(DataSet ds, Collection<OsmPrimitive> primitives) {
        Collection<OsmPrimitive> returnCollection = new ArrayList<>();
        returnCollection.addAll(addRelations(ds, Utils.filteredCollection(primitives, Relation.class)));
        returnCollection.addAll(addWays(ds, Utils.filteredCollection(primitives, Way.class)));
        returnCollection.addAll(addNodes(ds, Utils.filteredCollection(primitives, Node.class)));
        return returnCollection;
    }

    private static Collection<OsmPrimitive> addNodes(DataSet ds, Collection<Node> nodes) {
        Collection<OsmPrimitive> toAdd = nodes.stream().filter(node -> node.getDataSet() == null)
                .distinct().collect(Collectors.toList());
        toAdd.stream().forEach(ds::addPrimitive);
        return toAdd;
    }

    private static Collection<OsmPrimitive> addWays(DataSet ds, Collection<Way> ways) {
        Collection<OsmPrimitive> toAdd = new ArrayList<>();
        ways.stream().map(Way::getNodes).forEach(list -> toAdd.addAll(addNodes(ds, list)));
        ways.stream().distinct()
        .filter(way -> way.getDataSet() == null
        && way.getNodes().stream().filter(node -> node.getDataSet() != ds).count() == 0)
        .forEach(way -> {
            ds.addPrimitive(way);
            toAdd.add(way);
        });
        return toAdd;
    }

    // This might break with relations. TODO (not needed right now)
    private static Collection<OsmPrimitive> addRelations(DataSet ds, Collection<Relation> relations) {
        Collection<OsmPrimitive> toAdd = relations.stream().distinct().filter(relation -> relation.getDataSet() != null)
                .collect(Collectors.toList());
        toAdd.forEach(ds::addPrimitive);
        return toAdd;
    }

    @Override
    public String getDescriptionText() {
        return tr("Add OSM Primitives to a dataset");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        added.addAll(actuallyAdded);
    }

}
