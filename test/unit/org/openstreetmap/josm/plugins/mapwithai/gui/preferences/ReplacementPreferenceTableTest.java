// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.gui.preferences.advanced.PrefEntry;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

import mockit.Mock;
import mockit.MockUp;

/**
 * @author Taylor Smock
 *
 */
public class ReplacementPreferenceTableTest {
    @Rule
    public JOSMTestRules rule = new JOSMTestRules().preferences();
    ReplacementPreferenceTable test;

    private static final class ReplacementPreferenceTableMock extends MockUp<ReplacementPreferenceTable> {
        private boolean set;

        public ReplacementPreferenceTableMock(boolean set) {
            this.set = set;
        }

        @Mock
        protected boolean askAddSetting(JComponent gui, JPanel p) {
            return set;
        }
    }

    @Before
    public void setUp() {
        TestUtils.assumeWorkingJMockit();
        new WindowMocker();
        test = new ReplacementPreferenceTable(new ArrayList<PrefEntry>());
    }

    @Test
    public void test() {
        new ReplacementPreferenceTableMock(true);
        assertNotNull(test.addPreference(new JPanel()));
        new ReplacementPreferenceTableMock(false);
        assertNull(test.addPreference(new JPanel()));
    }
}
