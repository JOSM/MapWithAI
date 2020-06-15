// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.plugins.mapwithai.commands.cleanup.MissingConnectionTags;
import org.openstreetmap.josm.testutils.mockers.JOptionPaneSimpleMocker;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;

/**
 * This mocks the fixErrors to avoid creating a ConditionalOptionPane dialog The
 * default is {@link JOptionPane#NO_OPTION}
 *
 * @author Taylor Smock
 *
 */
public class MissingConnectionTagsMocker extends MockUp<MissingConnectionTags> {
    private final JOptionPaneSimpleMocker joptionPaneMocker;
    private int defaultOption = JOptionPane.NO_OPTION;
    private final Map<String, Object> map;

    /**
     * Only use the default option for joptionpanes
     */
    public MissingConnectionTagsMocker() {
        this(new HashMap<>());
    }

    /**
     * Initialize with a map of results
     *
     * @param map See {@link JOptionPaneSimpleMocker#JOptionPaneSimpleMocker(Map)}
     */
    public MissingConnectionTagsMocker(Map<String, Object> map) {
        joptionPaneMocker = new JOptionPaneSimpleMocker(map);
        this.map = map;
    }

    @Mock
    protected void fixErrors(Invocation inv, String prefKey, Collection<Command> commands,
            Collection<TestError> issues) {
        /*
         * This has caused issues in the past, where mocks are not properly cleaned up.
         */
        issues.stream().filter(TestError::isFixable).map(TestError::getFix).filter(Objects::nonNull)
                .map(Command::getDescriptionText).forEach(m -> map.putIfAbsent(m, defaultOption));
        inv.proceed(prefKey, commands, issues);
    }

    /**
     * Set the default option
     *
     * @param jOptionPaneOption Use one of the {@link JOptionPane#getOptions()}
     *                          integers
     */
    public void setDefaultOption(int jOptionPaneOption) {
        defaultOption = jOptionPaneOption;
    }

    /**
     * Get the mocker being used for the option pane
     *
     * @return The JOptionPaneSimpleMocker
     */
    public JOptionPaneSimpleMocker getJOptionPaneSimpleMocker() {
        return joptionPaneMocker;
    }
}
