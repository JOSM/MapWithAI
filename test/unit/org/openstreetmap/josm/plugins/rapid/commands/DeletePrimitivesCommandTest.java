// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class DeletePrimitivesCommandTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules();

    @Test
    public void testDeletePrimitives() {
        final DataSet ds = new DataSet();
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(-0.1, 0.1)));
        way1.getNodes().forEach(node -> ds.addPrimitive(node));
        ds.addPrimitive(way1);

        Assert.assertTrue(ds.containsWay(way1));

        DeletePrimitivesCommand delete = new DeletePrimitivesCommand(ds, Collections.singleton(way1));
        delete.executeCommand();
        Assert.assertTrue(way1.isDeleted());
        Assert.assertEquals(0, ds.allNonDeletedPrimitives().size());

        delete.undoCommand();

        Assert.assertTrue(ds.containsWay(way1));
        Assert.assertEquals(3, ds.allNonDeletedPrimitives().size());

        final Node tNode = new Node(new LatLon(0.1, 0.1));
        ds.addPrimitive(tNode);
        Assert.assertEquals(4, ds.allNonDeletedPrimitives().size());

        delete.executeCommand();
        Assert.assertTrue(way1.isDeleted());
        Assert.assertEquals(1, ds.allNonDeletedPrimitives().size());

        delete.undoCommand();
        Assert.assertEquals(4, ds.allNonDeletedPrimitives().size());

        way1.firstNode().put("highway", "stop");

        delete.executeCommand();
        Assert.assertTrue(way1.isDeleted());
        Assert.assertEquals(2, ds.allNonDeletedPrimitives().size());

        delete.undoCommand();
        Assert.assertTrue(way1.firstNode().hasKey("highway"));

        delete = new DeletePrimitivesCommand(ds, Collections.singleton(way1), true);

        delete.executeCommand();
        Assert.assertTrue(way1.isDeleted());
        Assert.assertEquals(1, ds.allNonDeletedPrimitives().size());

        delete.undoCommand();
        Assert.assertEquals(4, ds.allNonDeletedPrimitives().size());

        Assert.assertTrue(way1.firstNode().hasKey("highway"));
    }

}
