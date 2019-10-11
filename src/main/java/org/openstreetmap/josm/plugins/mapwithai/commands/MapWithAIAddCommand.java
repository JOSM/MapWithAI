// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.tools.Logging;

public class MapWithAIAddCommand extends Command implements Runnable {
    DataSet editable;
    DataSet mapWithAI;
    Collection<OsmPrimitive> primitives;
    Command command = null;

    /**
     * Add primitives from MapWithAI to the OSM data layer
     *
     * @param mapWithAILayer The MapWithAI layer
     * @param editLayer      The OSM layer
     * @param selection      The primitives to add from MapWithAI
     */
    public MapWithAIAddCommand(MapWithAILayer mapWithAILayer, OsmDataLayer editLayer, Collection<OsmPrimitive> selection) {
        this(mapWithAILayer.getDataSet(), editLayer.getDataSet(), selection);

    }

    /**
     * Add primitives from MapWithAI to the OSM data layer
     *
     * @param mapWithAI The MapWithAI dataset
     * @param editable  The OSM dataset
     * @param selection The primitives to add from MapWithAI
     */
    public MapWithAIAddCommand(DataSet mapWithAI, DataSet editable, Collection<OsmPrimitive> selection) {
        super(mapWithAI);
        this.mapWithAI = mapWithAI;
        this.editable = editable;
        this.primitives = selection;
    }

    @Override
    public boolean executeCommand() {
        if (EventQueue.isDispatchThread()) {
            new Thread((Runnable) this::run, getClass().getName()).start();
        } else {
            run();
        }
        return true;
    }

    @Override
    public void run() {
        if (mapWithAI.equals(editable)) {
            Logging.error("{0}: DataSet mapWithAI ({1}) should not be the same as DataSet editable ({2})", MapWithAIPlugin.NAME,
                    mapWithAI, editable);
            throw new IllegalArgumentException();
        }
        synchronized (this) {
            final boolean locked = mapWithAI.isLocked();
            try {
                if (locked) {
                    mapWithAI.unlock();
                }
                final Command movePrimitivesCommand = new MovePrimitiveDataSetCommand(editable, mapWithAI, primitives);
                final List<OsmPrimitive> allPrimitives = new ArrayList<>();
                MapWithAIDataUtils.addPrimitivesToCollection(allPrimitives, primitives);
                final Command createConnectionsCommand = createConnections(editable, allPrimitives);
                if (command == null) { // needed for undo/redo (don't create a new command)
                    command = new SequenceCommand(getDescriptionText(), movePrimitivesCommand, createConnectionsCommand);
                }
                command.executeCommand();
            } finally {
                if (locked) {
                    mapWithAI.lock();
                }
            }
        }
    }

    /**
     * Create connections based off of current MapWithAI syntax
     *
     * @param dataSet    The {@link DataSet} that should have the primitives we are
     *                   connecting to
     * @param collection The primitives with connection information (currently only
     *                   checks Nodes)
     * @return A Command to create connections from the collection
     */
    public static Command createConnections(DataSet dataSet, Collection<OsmPrimitive> collection) {
        return new CreateConnectionsCommand(dataSet, collection);
    }

    @Override
    public void undoCommand() {
        final boolean locked = mapWithAI.isLocked();
        try {
            if (locked) {
                mapWithAI.unlock();
            }
            synchronized (this) {
                if (command != null) {
                    command.undoCommand();
                }
            }
        } finally {
            if (locked) {
                mapWithAI.lock();
            }
        }
    }

    @Override
    public String getDescriptionText() {
        return tr("Add object from {0}", MapWithAIPlugin.NAME);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        modified.addAll(primitives);
    }
}
