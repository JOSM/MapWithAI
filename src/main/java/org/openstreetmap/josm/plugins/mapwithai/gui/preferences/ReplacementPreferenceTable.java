// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.preferences.advanced.PrefEntry;
import org.openstreetmap.josm.gui.preferences.advanced.PreferencesTable;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.spi.preferences.StringSetting;
import org.openstreetmap.josm.tools.GBC;

/**
 * @author Taylor Smock
 */
public class ReplacementPreferenceTable extends PreferencesTable {
    private static final long serialVersionUID = 8057277761625324262L;

    public ReplacementPreferenceTable(List<PrefEntry> displayData) {
        super(displayData);
    }

    @Override
    public PrefEntry addPreference(final JComponent gui) {
        final JPanel p = new JPanel(new GridBagLayout());
        p.add(new JLabel(tr("Original Tag")), GBC.std().insets(0, 0, 5, 0));
        final JosmTextField tkey = new JosmTextField("", 50);
        p.add(tkey, GBC.eop().insets(5, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));
        p.add(new JLabel(tr("Replacement Tag")), GBC.std().insets(0, 0, 5, 0));
        final JosmTextField tValue = new JosmTextField("", 50);
        p.add(tValue, GBC.eop().insets(5, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));

        p.add(new JSeparator(), GBC.eop().insets(5, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));
        p.add(new JLabel(tr("Example")), GBC.eop().insets(5, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));
        p.add(new JLabel(tr("Original Tag")), GBC.std().insets(0, 0, 5, 0));
        JosmTextField tmp = new JosmTextField("highway=residential");
        tmp.setEditable(false);
        p.add(tmp, GBC.eop().insets(5, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));
        p.add(new JLabel(tr("Replacement Tag")), GBC.std().insets(0, 0, 5, 0));
        tmp = new JosmTextField("disused:highway=road");
        tmp.setEditable(false);
        p.add(tmp, GBC.eop().insets(5, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));

        return askAddSetting(gui, p)
                ? new PrefEntry(tkey.getText(), new StringSetting(tValue.getText()), new StringSetting(null), false)
                : null;
    }

    protected static boolean askAddSetting(JComponent gui, JPanel p) {
        return new ExtendedDialog(gui, tr("Add setting"), tr("OK"), tr("Cancel")).setContent(p)
                .setButtonIcons("ok", "cancel").showDialog().getValue() == 1;
    }
}
