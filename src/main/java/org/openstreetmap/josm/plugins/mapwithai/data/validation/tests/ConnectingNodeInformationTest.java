// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DownloadPolicy;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.commands.CreateConnectionsCommand;
import org.openstreetmap.josm.tools.Logging;

/**
 * Ensure that no conflation keys remain
 *
 * @author Taylor Smock
 */
public class ConnectingNodeInformationTest extends Test {
    private static final int ERROR_CODE = 827277536;
    Map<String, String> badTags;

    public ConnectingNodeInformationTest() {
        super(tr("Left over conflation information (MapWithAI)"),
                tr("Checks conflation keys that should not exist in OpenStreetMap."));
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        badTags = new HashMap<>();
        CreateConnectionsCommand.getConflationCommands().forEach(clazz -> {
            try {
                badTags.put(clazz.getConstructor(DataSet.class).newInstance(new DataSet()).getKey(), null);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                Logging.error(e);
            }
        });
    }

    @Override
    public void visit(Relation relation) {
        checkTags(relation);
    }

    @Override
    public void visit(Way way) {
        checkTags(way);
    }

    @Override
    public void visit(Node node) {
        checkTags(node);
    }

    private void checkTags(OsmPrimitive prim) {
        DataSet ds = prim.getDataSet();
        if (!UploadPolicy.BLOCKED.equals(ds.getUploadPolicy()) && !DownloadPolicy.BLOCKED.equals(ds.getDownloadPolicy())
                && prim.hasKey(badTags.keySet().toArray(new String[0]))) {
            errors.add(TestError.builder(this, Severity.ERROR, ERROR_CODE).primitives(prim)
                    .message(tr("Don''t leave conflation keys in the data {0}",
                            badTags.keySet().stream().filter(prim::hasKey).collect(Collectors.toList())))
                    .fix(() -> new ChangePropertyCommand(Collections.singleton(prim), badTags)).build());
        }
    }

}