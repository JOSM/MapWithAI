// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands.cleanup;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.SimplifyWayAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.plugins.mapwithai.commands.AbstractConflationCommand;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Utils;

public class OverNodedWays extends AbstractConflationCommand {

    private double threshold;
    private int acceptableRemovalPercentage;

    public OverNodedWays(DataSet data) {
        super(data);
    }

    @Override
    public String getDescriptionText() {
        return tr("Fix overnoded ways");
    }

    @Override
    public Collection<Class<? extends OsmPrimitive>> getInterestedTypes() {
        return Collections.singleton(Way.class);
    }

    @Override
    public String getKey() {
        // Currently only interested in highways
        return "highway";
    }

    @Override
    public Command getRealCommand() {
        threshold = Config.getPref().getDouble("mapwithai.conflation.simplifyway", 0.5);
        acceptableRemovalPercentage = Config.getPref().getInt("mapwithai.conflation.simplifywaynodepercentagerequired",
                20);
        Layer current = MainApplication.getLayerManager().getActiveLayer();
        Collection<Way> ways = Utils.filteredCollection(possiblyAffectedPrimitives, Way.class);
        if (!ways.isEmpty()) {
            DataSet ds = this.getAffectedDataSet();
            Layer toSwitch = MainApplication.getLayerManager().getLayersOfType(AbstractOsmDataLayer.class).stream()
                    .filter(d -> ds.equals(d.getDataSet())).findAny().orElse(null);
            if (toSwitch != null) {
                MainApplication.getLayerManager().setActiveLayer(toSwitch);
            }
        }
        Collection<Command> commands = new ArrayList<>();
        for (Way way : ways) {
            Command command = SimplifyWayAction.createSimplifyCommand(way, threshold);
            if (command == null) {
                continue;
            }
            int count = Utils.filteredCollection(new ArrayList<>(command.getParticipatingPrimitives()), Node.class)
                    .size();
            if ((count / (double) way.getNodesCount()) * 100 > this.acceptableRemovalPercentage) {
                AutoScaleAction.zoomTo(Collections.singleton(way));
                double length = SimplifyWayAction.askSimplifyWays(
                        tr("You are about to simplify {0} way with a total length of {1}.", 1, way.getLength()), true);
                command = SimplifyWayAction.createSimplifyCommand(way, length);
                if (command != null) {
                    commands.add(command);
                }
            }
        }
        if (current != null) {
            MainApplication.getLayerManager().setActiveLayer(current);
        }
        if (commands.size() == 1) {
            return commands.iterator().next();
        } else if (!commands.isEmpty()) {
            return new SequenceCommand(tr("Simplify ways"), commands);
        }
        return null;
    }

    @Override
    public boolean allowUndo() {
        return false;
    }

    @Override
    public boolean keyShouldNotExistInOSM() {
        return false;
    }

}
