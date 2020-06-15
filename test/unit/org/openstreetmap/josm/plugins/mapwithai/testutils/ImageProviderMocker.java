// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import javax.swing.ImageIcon;

import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;

/**
 * ImageProvider mock for Eclipse testing (resources not visible for Eclipse
 * runs).
 *
 * @author Taylor Smock
 *
 */
public class ImageProviderMocker extends MockUp<ImageProvider> {
    @Mock
    public static ImageIcon get(Invocation inv, String subdir, String name, ImageSizes size) {
        return new ImageProvider(subdir, name).setSize(size).setOptional(true).get();
    }
}
