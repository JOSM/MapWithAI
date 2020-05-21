// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.download;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JCheckBox;
import javax.swing.event.ChangeListener;

import org.openstreetmap.josm.actions.downloadtasks.AbstractDownloadTask;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.download.IDownloadSourceType;
import org.openstreetmap.josm.plugins.mapwithai.backend.DownloadMapWithAITask;

public class MapWithAIDownloadSourceType implements IDownloadSourceType {
    static final BooleanProperty IS_ENABLED = new BooleanProperty("download.mapwithai.data", false);
    JCheckBox cbDownloadMapWithAIData;

    @Override
    public JCheckBox getCheckBox(ChangeListener checkboxChangeListener) {
        if (cbDownloadMapWithAIData == null) {
            cbDownloadMapWithAIData = new JCheckBox(tr("MapWithAI data"), true);
            cbDownloadMapWithAIData
                    .setToolTipText(tr("Select to download MapWithAI data in the selected download area."));
            cbDownloadMapWithAIData.getModel().addChangeListener(checkboxChangeListener);
        }
        if (checkboxChangeListener != null) {
            cbDownloadMapWithAIData.getModel().addChangeListener(checkboxChangeListener);
        }
        return cbDownloadMapWithAIData;
    }

    @Override
    public Class<? extends AbstractDownloadTask<?>> getDownloadClass() {
        return DownloadMapWithAITask.class;
    }

    @Override
    public BooleanProperty getBooleanProperty() {
        return IS_ENABLED;
    }

    @Override
    public boolean isDownloadAreaTooLarge(Bounds bound) {
        return MapWithAIDownloadReader.MapWithAIDownloadPanel.isDownloadAreaTooLarge(bound);
    }

}
