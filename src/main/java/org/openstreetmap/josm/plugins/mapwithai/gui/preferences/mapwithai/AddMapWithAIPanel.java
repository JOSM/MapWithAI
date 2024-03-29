// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.awt.event.ItemEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;
import org.openstreetmap.josm.data.imagery.TMSCachedTileLoaderJob;
import org.openstreetmap.josm.gui.preferences.imagery.AddImageryPanel.ContentValidationListener;
import org.openstreetmap.josm.gui.preferences.imagery.HeadersTable;
import org.openstreetmap.josm.gui.widgets.JosmTextArea;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;

/**
 * A panel used to add MapWithAI sources.
 */
class AddMapWithAIPanel extends JPanel {
    @Serial
    private static final long serialVersionUID = -2838267045934203122L;
    private final transient JPanel layerPanel = new JPanel(new GridBagLayout());

    protected final JosmTextArea rawUrl = new JosmTextArea(3, 40).transferFocusOnTab();
    protected final JosmTextField name = new JosmTextField();

    protected final transient Collection<ContentValidationListener> listeners = new ArrayList<>();

    private final JCheckBox validGeoreference = new JCheckBox(tr("Is layer properly georeferenced?"));
    private HeadersTable headersTable;
    private MapWithAIParametersPanel parametersTable;
    private JSpinner minimumCacheExpiry;
    private JComboBox<String> minimumCacheExpiryUnit;
    private TimeUnit currentUnit;
    private MapWithAIType type;

    private transient MapWithAIInfo info;
    private JComboBox<MapWithAIType> typeBox;

    protected AddMapWithAIPanel(LayoutManager layout) {
        super(layout);
        registerValidableComponent(name);
    }

    /**
     * default constructor
     */
    public AddMapWithAIPanel() {
        this(new GridBagLayout());
        headersTable = new HeadersTable();
        parametersTable = new MapWithAIParametersPanel();
        minimumCacheExpiry = new JSpinner(
                new SpinnerNumberModel(TimeUnit.MILLISECONDS.toSeconds(TMSCachedTileLoaderJob.MINIMUM_EXPIRES.get()),
                        0L, Integer.MAX_VALUE, 1));
        List<String> units = Arrays.asList(tr("seconds"), tr("minutes"), tr("hours"), tr("days"));
        minimumCacheExpiryUnit = new JComboBox<>(units.toArray(new String[] {}));
        currentUnit = TimeUnit.SECONDS;
        minimumCacheExpiryUnit.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                long newValue = 0;
                switch (units.indexOf(e.getItem())) {
                case 0 -> {
                    newValue = currentUnit.toSeconds((long) minimumCacheExpiry.getValue());
                    currentUnit = TimeUnit.SECONDS;
                }
                case 1 -> {
                    newValue = currentUnit.toMinutes((long) minimumCacheExpiry.getValue());
                    currentUnit = TimeUnit.MINUTES;
                }
                case 2 -> {
                    newValue = currentUnit.toHours((long) minimumCacheExpiry.getValue());
                    currentUnit = TimeUnit.HOURS;
                }
                case 3 -> {
                    newValue = currentUnit.toDays((long) minimumCacheExpiry.getValue());
                    currentUnit = TimeUnit.DAYS;
                }
                default -> Logging.warn("Unknown unit: " + units.indexOf(e.getItem()));
                }
                minimumCacheExpiry.setValue(newValue);
            }
        });
        add(new JLabel(tr("{0} Make sure OSM has the permission to use this service", "1.")), GBC.eol());
        add(new JLabel(tr("{0} Enter Service URL", "2.")), GBC.eol());
        add(rawUrl, GBC.eop().fill(GridBagConstraints.HORIZONTAL));
        rawUrl.setLineWrap(true);
        rawUrl.setAlignmentY(TOP_ALIGNMENT);
        add(layerPanel, GBC.eol().fill());

        addCommonSettings();

        add(new JLabel(tr("{0} Enter name for this source", "3.")), GBC.eol());
        add(name, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        add(new JLabel(tr("{0} What is the type of this source?", "4.")), GBC.eol());
        typeBox = new JComboBox<>(MapWithAIType.values());
        typeBox.setSelectedItem(MapWithAIType.THIRD_PARTY);
        typeBox.addItemListener(l -> {
            type = (MapWithAIType) typeBox.getSelectedItem();
            notifyListeners();
        });
        add(typeBox, GBC.eol());
        registerValidableComponent(rawUrl);
    }

    public AddMapWithAIPanel(MapWithAIInfo info) {
        this();
        this.info = info;
        rawUrl.setText(info.getUrl());
        name.setText(info.getName());
        typeBox.setSelectedItem(info.getSourceType());
        this.info.setSourceType(this.type);
        if (info.getParameters() != null) {
            parametersTable.setParameters(info.getParameters());
        }
        parametersTable.addListener(e -> notifyListeners());
    }

    protected void addCommonSettings() {
        add(new JLabel(tr("Set parameters")), GBC.eop());
        add(parametersTable, GBC.eol().fill());
        if (ExpertToggleAction.isExpert()) {
            add(new JLabel(tr("Minimum cache expiry: ")));
            add(minimumCacheExpiry);
            add(minimumCacheExpiryUnit, GBC.eol());
            add(new JLabel(tr("Set custom HTTP headers (if needed):")), GBC.eop());
            add(headersTable, GBC.eol().fill());
            add(validGeoreference, GBC.eop().fill(GridBagConstraints.HORIZONTAL));
        }
    }

    protected Map<String, String> getCommonHeaders() {
        return headersTable.getHeaders();
    }

    protected Map<String, Pair<String, Boolean>> getCommonParameters() {
        return parametersTable.getParameters();
    }

    protected boolean getCommonIsValidGeoreference() {
        return validGeoreference.isSelected();
    }

    protected final void registerValidableComponent(AbstractButton component) {
        component.addChangeListener(e -> notifyListeners());
    }

    protected final void registerValidableComponent(JTextComponent component) {
        component.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                notifyListeners();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                notifyListeners();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notifyListeners();
            }
        });
    }

    private static JsonArray convertToJsonParameterArray(Map<String, Pair<String, Boolean>> parameters) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (Map.Entry<String, Pair<String, Boolean>> entry : parameters.entrySet()) {
            JsonObjectBuilder entryBuilder = Json.createObjectBuilder();
            entryBuilder.add("description", entry.getKey());
            entryBuilder.add("parameter", entry.getValue().a);
            entryBuilder.add("enabled", entry.getValue().b);
            builder.add(entryBuilder.build());
        }
        return builder.build();
    }

    protected MapWithAIInfo getSourceInfo() {
        MapWithAIInfo ret = info == null ? new MapWithAIInfo() : info;
        ret.setName(getImageryName());
        ret.setUrl(getImageryRawUrl());
        ret.setCustomHttpHeaders(getCommonHeaders());
        ret.setSourceType(this.type);
        ret.setParameters(convertToJsonParameterArray(getCommonParameters()));
        return ret;
    }

    protected static String sanitize(String s) {
        return s.replaceAll("[\r\n]+", "").trim();
    }

    protected static String sanitize(String s, ImageryType type) {
        String ret = s;
        String imageryType = type.getTypeString() + ':';
        if (ret.startsWith(imageryType)) {
            // remove ImageryType from URL
            ret = ret.substring(imageryType.length());
        }
        return sanitize(ret);
    }

    protected final String getImageryName() {
        return sanitize(name.getText());
    }

    protected final String getImageryRawUrl() {
        return sanitize(rawUrl.getText());
    }

    protected boolean isSourceValid() {
        return !getImageryName().isEmpty() && !getImageryRawUrl().isEmpty();
    }

    /**
     * Registers a new ContentValidationListener
     *
     * @param l The new ContentValidationListener that will be notified of
     *          validation status changes
     */
    public final void addContentValidationListener(ContentValidationListener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    private void notifyListeners() {
        for (ContentValidationListener l : listeners) {
            l.contentChanged(isSourceValid());
        }
    }
}
