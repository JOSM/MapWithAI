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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mockit.Mock;
import mockit.MockUp;

/**
 * @author Taylor Smock
 *
 */
public class ReplacementPreferenceTableTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules rule = new JOSMTestRules().preferences();
    ReplacementPreferenceTable test;

    static class ReplacementPreferenceTableMock extends MockUp<ReplacementPreferenceTable> {
        private boolean set;

        /**
         * Initialize the mock with a preset return value
         *
         * @param set the return value for
         *            {@link ReplacementPreferenceTableMock#askAddSetting}
         */
        public ReplacementPreferenceTableMock(boolean set) {
            this.set = set;
        }

        @Mock
        protected boolean askAddSetting(JComponent gui, JPanel p) {
            return set;
        }

        /**
         * Set the return value
         *
         * @param set the new return value for
         *            {@link ReplacementPreferenceTableMock#askAddSetting}
         */
        public void setAskAddSetting(boolean set) {
            this.set = set;
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
