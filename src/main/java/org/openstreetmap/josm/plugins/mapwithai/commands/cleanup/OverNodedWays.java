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

    private class PostponedOverNodedWayCommand extends Command {
        private Command realCommand;

        protected PostponedOverNodedWayCommand(DataSet data) {
            super(data);
        }

        @Override
        public boolean executeCommand() {
            if (realCommand == null) {
                double threshold = Config.getPref().getDouble("mapwithai.conflation.simplifyway", 0.5);
                int acceptableRemovalPercentage = Config.getPref()
                        .getInt("mapwithai.conflation.simplifywaynodepercentagerequired", 20);
                Layer current = MainApplication.getLayerManager().getActiveLayer();
                Collection<Way> ways = Utils.filteredCollection(possiblyAffectedPrimitives, Way.class);
                if (!ways.isEmpty()) {
                    DataSet ds = this.getAffectedDataSet();
                    Layer toSwitch = MainApplication.getLayerManager().getLayersOfType(AbstractOsmDataLayer.class)
                            .stream().filter(d -> ds.equals(d.getDataSet())).findAny().orElse(null);
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
                    int count = Utils
                            .filteredCollection(new ArrayList<>(command.getParticipatingPrimitives()), Node.class)
                            .size();
                    if ((count / (double) way.getNodesCount()) * 100 > acceptableRemovalPercentage) {
                        AutoScaleAction.zoomTo(Collections.singleton(way));
                        double length = SimplifyWayAction.askSimplifyWays(
                                tr("You are about to simplify {0} way with a total length of {1}.", 1, way.getLength()),
                                true);
                        command = SimplifyWayAction.createSimplifyCommand(way, length);
                        if (command != null) {
                            commands.add(command);
                        }
                    }
                }
                if (current != null) {
                    MainApplication.getLayerManager().setActiveLayer(current);
                }
                if (!commands.isEmpty()) {
                    realCommand = SequenceCommand.wrapIfNeeded(tr("Simplify ways"), commands);
                    realCommand.executeCommand();
                }

            } else {
                realCommand.executeCommand();
            }
            return true;
        }

        @Override
        public void undoCommand() {
            if (realCommand != null) {
                realCommand.undoCommand();
            }
        }

        @Override
        public String getDescriptionText() {
            return realCommand != null ? realCommand.getDescriptionText() : "Command not run";
        }

        @Override
        public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
                Collection<OsmPrimitive> added) {
            if (realCommand != null) {
                realCommand.fillModifiedData(modified, deleted, added);
            }
        }

    }

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
        return new PostponedOverNodedWayCommand(this.getAffectedDataSet());
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
