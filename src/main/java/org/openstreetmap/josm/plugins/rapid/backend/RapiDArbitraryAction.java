// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.SelectAllOnFocusGainedDecorator;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.OsmUrlToBounds;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * @author Taylor Smock
 */
public class RapiDArbitraryAction extends JosmAction {
    private static final long serialVersionUID = 9048113038651190619L;

    private final JosmTextField lowerLat = new JosmTextField();
    private final JosmTextField upperLat = new JosmTextField();
    private final JosmTextField leftLon = new JosmTextField();
    private final JosmTextField rightLon = new JosmTextField();
    private final JCheckBox checkbox = new JCheckBox();

    public RapiDArbitraryAction() {
        super(RapiDPlugin.NAME, null, tr("Get arbitrary data from RapiD"), Shortcut.registerShortcut("data:rapid",
                tr("Data: Arbitrary {0} Data", tr("Get arbitrary data from RapiD")), KeyEvent.VK_R,
                Shortcut.ALT_CTRL_SHIFT), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        showDownloadDialog();
    }

    static class RapiDArbitraryDialog extends ExtendedDialog {
        private static final long serialVersionUID = 2795301151521238635L;

        RapiDArbitraryDialog(String[] buttons, JPanel panel) {
            super(MainApplication.getMainFrame(), tr("Get arbitrary data from RapiD"), buttons);
            setButtonIcons("ok", "cancel");
            configureContextsensitiveHelp(ht("/Action/DownloadArbitraryRapiDData"), true);
            setContent(panel);
            setCancelButton(2);
        }
    }

    public void showDownloadDialog() {
        final Optional<Bounds> boundsFromClipboard = Optional.ofNullable(ClipboardUtils.getClipboardStringContent())
                .map(OsmUrlToBounds::parse);
        if (boundsFromClipboard.isPresent() && Config.getPref().getBoolean("jumpto.use.clipboard", true)) {
            setBounds(boundsFromClipboard.get());
        } else if (MainApplication.isDisplayingMapView()) {
            final MapView mv = MainApplication.getMap().mapView;
            setBounds(mv.getState().getViewArea().getCornerBounds());
        }

        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("<html>" + tr("Enter Lat/Lon to download RapiD data.") + "<br>" + "</html>"),
                BorderLayout.NORTH);

        SelectAllOnFocusGainedDecorator.decorate(lowerLat);
        SelectAllOnFocusGainedDecorator.decorate(leftLon);

        final JPanel p = new JPanel(new GridBagLayout());
        panel.add(p, BorderLayout.NORTH);

        p.add(new JLabel(tr("Lower Latitude")), GBC.eol());
        p.add(lowerLat, GBC.eol().fill(GBC.HORIZONTAL));

        p.add(new JLabel(tr("Left Longitude")), GBC.eol());
        p.add(leftLon, GBC.eol().fill(GBC.HORIZONTAL));
        p.add(new JLabel(tr("Upper Latitude")), GBC.eol());
        p.add(upperLat, GBC.eol().fill(GBC.HORIZONTAL));
        p.add(new JLabel(tr("Right Longitude")), GBC.eol());
        p.add(rightLon, GBC.eol().fill(GBC.HORIZONTAL));

        p.add(new JLabel(tr("Crop to bbox?")));
        p.add(checkbox, GBC.eol());

        final String[] buttons = { tr("Download"), tr("Cancel") };
        BBox bbox = new BBox();
        while (!bbox.isInWorld()) {
            final int option = new RapiDArbitraryDialog(buttons, panel).showDialog().getValue();
            if (option != 1) {
                return;
            }
            try {
                bbox = new BBox();
                bbox.add(new LatLon(Double.parseDouble(lowerLat.getText()), Double.parseDouble(leftLon.getText())));
                bbox.add(new LatLon(Double.parseDouble(upperLat.getText()), Double.parseDouble(rightLon.getText())));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                        tr("Could not parse Latitude or Longitude. Please check."), tr("Unable to parse Lon/Lat"),
                        JOptionPane.ERROR_MESSAGE);
                bbox = new BBox();
            }
        }

        if (checkbox.isSelected()) {
            final GpxData data = new GpxData();
            data.addWaypoint(new WayPoint(bbox.getBottomRight()));
            data.addWaypoint(new WayPoint(bbox.getTopLeft()));
            MainApplication.getLayerManager().addLayer(new GpxLayer(data, tr("{0}: Crop Area", RapiDPlugin.NAME)));
        }

        final DataSet data = RapiDDataUtils.getData(bbox);
        final RapiDLayer layer = RapiDAction.getLayer(true);
        final DataSet rapidData = layer.getDataSet();
        boolean locked = rapidData.isLocked();
        if (locked) {
            rapidData.unlock();
        }
        rapidData.mergeFrom(data);
        if (locked) {
            rapidData.lock();
        }
    }

    private void setBounds(Bounds b) {
        if (b != null) {
            final LatLon min = b.getMin();
            final LatLon max = b.getMax();
            lowerLat.setText(Double.toString(min.lat()));
            leftLon.setText(Double.toString(min.lon()));
            rightLon.setText(Double.toString(max.lon()));
            upperLat.setText(Double.toString(max.lat()));
        }
    }
}
