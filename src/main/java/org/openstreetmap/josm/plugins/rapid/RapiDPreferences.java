package org.openstreetmap.josm.plugins.rapid;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDDataUtils;

public class RapiDPreferences implements SubPreferenceSetting {

	private final JLabel rapidApiUrl = new JLabel(tr("RapiD API URL"));
	private final JComboBox<String> possibleRapidApiUrl = new JComboBox<>();

	@Override
	public void addGui(PreferenceTabbedPane gui) {
		final JPanel container = new JPanel(new GridBagLayout());
		container.setAlignmentY(JPanel.TOP_ALIGNMENT);
		final GridBagConstraints constraints = new GridBagConstraints();

		for (String url : RapiDDataUtils.getRapiDURLs()) {
			possibleRapidApiUrl.addItem(url);
		}

		possibleRapidApiUrl.setEditable(true);
		possibleRapidApiUrl.setSelectedItem(RapiDDataUtils.getRapiDURL());

		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = .1;
		constraints.weighty = 0;
		constraints.insets = new Insets(5, 10, 5, 10);
		constraints.anchor = GridBagConstraints.EAST;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		container.add(rapidApiUrl, constraints);

		constraints.gridx++;
		constraints.weightx = 1;
		container.add(possibleRapidApiUrl, constraints);
		gui.getMaximumSize().getWidth();

		getTabPreferenceSetting(gui).addSubTab(this, "RapiD", container);
	}

	@Override
	public boolean ok() {
		RapiDDataUtils.setRapiDUrl((String) possibleRapidApiUrl.getSelectedItem());
		return false;
	}

	@Override
	public boolean isExpert() {
		return false;
	}

	@Override
	public TabPreferenceSetting getTabPreferenceSetting(PreferenceTabbedPane gui) {
		return gui.getPluginPreference();
	}

}
