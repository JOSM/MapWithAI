// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleSetting;
import org.openstreetmap.josm.gui.mappaint.StyleSetting.StyleSettingGroup;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIConfig;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.Logging;

/**
 * Utils for the MapWithAI paint style
 */
public final class MapPaintUtils {
    /** The default url for the MapWithAI paint style */
    private static final Pattern TEST_PATTERN = Pattern
            .compile("^https?://(www\\.)?localhost[:\\d]*/josmfile\\?page=Styles/MapWithAI&zip=1$");

    private static final String SOURCE_KEY = "source";
    private static final String MAPWITHAI_SOURCE_KEY = "mapwithai:" + SOURCE_KEY;

    private static final String MAPWITHAI_MAPCSS_ZIP_NAME = "Styles_MapWithAI-style.mapcss";
    private static final double CRC_DIVIDE_TO_TEN_K_MAX = 429496.7296;

    /**
     * Safe colors
     */
    public enum SafeColors {
        RED(Color.RED), ORANGE(Color.ORANGE), GOLD(Objects.requireNonNull(ColorHelper.html2color("#ffd700"))), LIME(
                Objects.requireNonNull(ColorHelper.html2color("#00ff00"))), CYAN(Color.CYAN), DODGER_BLUE(
                        Objects.requireNonNull(ColorHelper.html2color("#1e90ff"))), AI_MAGENTA(
                                Objects.requireNonNull(ColorHelper.html2color("#ff26d4"))), PINK(
                                        Color.PINK), LIGHT_GREY(
                                                Objects.requireNonNull(ColorHelper.html2color("#d3d3d3"))), LINEN(
                                                        Objects.requireNonNull(ColorHelper.html2color("#faf0e6")));

        private final Color color;

        SafeColors(Color color) {
            this.color = new Color(color.getRGB());
        }

        /**
         * Get the safe color
         *
         * @return The safe color
         */
        public Color getColor() {
            return new Color(this.color.getRGB());
        }
    }

    private MapPaintUtils() {
        // This is a utils class. Don't allow constructing.
    }

    /**
     * Add a paintstyle from the jar
     *
     * @return The added paint style, or the already existing paint style. May be
     *         {@code null} in some unusual circumstances.
     */
    public static synchronized StyleSource addMapWithAIPaintStyles() {
        // Remove old url's that were automatically added -- remove after Jan 01, 2020
        final List<Pattern> oldUrls = Arrays.asList(
                Pattern.compile("https://gitlab.com/(gokaart/JOSM_MapWithAI|smocktaylor/rapid)"
                        + "/raw/master/src/resources/styles/standard/(mapwithai|rapid).mapcss"),
                TEST_PATTERN, Pattern.compile("resource://styles/standard/mapwithai.mapcss"));
        new ArrayList<>(MapPaintStyles.getStyles().getStyleSources()).stream()
                .filter(style -> oldUrls.stream().anyMatch(p -> p.matcher(style.url).matches()))
                .forEach(MapPaintStyles::removeStyle);

        if (!checkIfMapWithAIPaintStyleExists()) {
            final MapCSSStyleSource style = new MapCSSStyleSource(MapWithAIConfig.getUrls().getMapWithAIPaintStyle(),
                    MapWithAIPlugin.NAME, "MapWithAI");
            return MapPaintStyles.addStyle(style);
        }
        return getMapWithAIPaintStyle();
    }

    /**
     * Check if the paint style exists
     *
     * @return {@code true} if the paint style exists
     */
    public static synchronized boolean checkIfMapWithAIPaintStyleExists() {
        return MapPaintStyles.getStyles().getStyleSources().stream().filter(MapCSSStyleSource.class::isInstance)
                .map(MapCSSStyleSource.class::cast)
                .anyMatch(source -> MapWithAIConfig.getUrls().getMapWithAIPaintStyle().equals(source.url)
                        || TEST_PATTERN.matcher(source.url).matches());
    }

    /**
     * Remove MapWithAI paint styles
     */
    public static synchronized void removeMapWithAIPaintStyles() {
        // WebStart has issues with streams and EDT permissions. Don't use streams.
        for (StyleSource style : new ArrayList<>(MapPaintStyles.getStyles().getStyleSources())) {
            if (MapWithAIConfig.getUrls().getMapWithAIPaintStyle().equals(style.url)
                    || TEST_PATTERN.matcher(style.url).matches()) {
                GuiHelper.runInEDT(() -> MapPaintStyles.removeStyle(style));
            }
        }
    }

    /**
     * Get any MapWithAI paint style
     *
     * @return get the MapWithAI Paint style
     */
    public static synchronized StyleSource getMapWithAIPaintStyle() {
        return MapPaintStyles.getStyles().getStyleSources().stream()
                .filter(source -> MapWithAIConfig.getUrls().getMapWithAIPaintStyle().equals(source.url)
                        || TEST_PATTERN.matcher(source.url).matches())
                .findAny().orElse(null);
    }

    /**
     * Add sources to the paint style
     *
     * @param ds The dataset to add sources to
     */
    public static synchronized void addSourcesToPaintStyle(DataSet ds) {
        StyleSource styleSource = addMapWithAIPaintStyles();
        if (styleSource == null) {
            return;
        }
        List<String> sources = ds.allPrimitives().stream().map(MapPaintUtils::getSourceValue).filter(Objects::nonNull)
                .map(s -> s.replace('.', '_')).distinct().collect(Collectors.toList());
        if (!styleSource.isLoaded()) {
            styleSource.loadStyleSource();
        }
        List<StyleSetting> list = styleSource.settings;
        for (StyleSetting setting : list) {
            if (setting instanceof StyleSetting.ColorStyleSetting) {
                StyleSetting.ColorStyleSetting csetting = (StyleSetting.ColorStyleSetting) setting;
                if (csetting.label != null) {
                    String rLabel = csetting.label.replaceAll("color$", "").trim();
                    sources.removeIf(rLabel::equalsIgnoreCase);
                }
            }
        }
        Map<StyleSettingGroup, List<StyleSetting>> groups = styleSource.settingGroups;
        String group = groups.keySet().stream().filter(p -> p.key != null && p.key.contains("color")).map(p -> p.key)
                .findFirst().orElse(null);
        try (CachedFile cachedFile = styleSource.getCachedFile()) {
            File file = cachedFile.getFile();
            String path = file.getAbsolutePath();
            for (String prefix : Arrays.asList("file:", "jar:")) {
                if (!path.startsWith(prefix)) {
                    path = prefix.concat(path);
                }
            }
            try (ZipFile zipFile = new ZipFile(file.getAbsolutePath())) {
                writeZipData(zipFile, group, sources);
            } catch (ZipException e) {
                Logging.trace(e);
                // Assume that it is a standard file, not a zip file.
                try (OutputStream out = Files.newOutputStream(Paths.get(file.getName() + ".tmp"));
                        BufferedReader bufferedReader = new BufferedReader(
                                new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
                    writeData(out, bufferedReader, group, sources);
                }
                Files.move(new File(file.getName() + ".tmp").toPath(), new File(file.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }

            styleSource.loadStyleSource();
        } catch (IOException e) {
            Logging.error(e);
        }
    }

    private static void writeZipData(ZipFile file, String group, List<String> sources) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Paths.get(file.getName() + ".tmp")))) {
            for (Iterator<? extends ZipEntry> e = file.stream().iterator(); e.hasNext();) {
                ZipEntry current = e.next();
                // For the entry we are overwriting, we cannot use the current zipentry, we must
                // make a new one.
                if (!current.getName().equalsIgnoreCase(MAPWITHAI_MAPCSS_ZIP_NAME)) {
                    out.putNextEntry(current);
                    try (InputStream is = file.getInputStream(current)) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = is.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                    out.closeEntry();
                    continue;
                }
                out.putNextEntry(new ZipEntry(MAPWITHAI_MAPCSS_ZIP_NAME));
                try (BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(file.getInputStream(current), StandardCharsets.UTF_8))) {
                    writeData(out, bufferedReader, group, sources);
                }
                out.closeEntry();
            }
        }
        Files.move(new File(file.getName() + ".tmp").toPath(), new File(file.getName()).toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void writeData(OutputStream out, BufferedReader bufferedReader, String group, List<String> sources)
            throws IOException {
        String line = bufferedReader.readLine();
        while (line != null && !line.contains("End Settings for the paint style")) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            line = bufferedReader.readLine();
        }
        /* Finish writing the comment */
        while (line != null && !line.endsWith("*/")) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            line = bufferedReader.readLine();
        }
        if (line != null) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
        }

        for (String source : sources) {
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            String simpleSource = source.replaceAll("[() /${}:,]", "_");
            StringBuilder sb = new StringBuilder(64).append("setting::").append(simpleSource).append("{")
                    .append("type:color;").append("default:").append(simpleSource)
                    .append(ColorHelper.color2html(getRandomColor(source))).append(";label:tr(\"{0} color\",\"")
                    .append(source).append("\");");
            if (group != null) {
                sb.append("group:\"").append(group).append("\";");
            }
            sb.append('}');
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            sb = new StringBuilder(
                    "*[/^(source|mapwithai:source)$/][any(tag(\"source\"), tag(\"mapwithai:source\"))=\"")
                            .append(source).append("\"]{set_color_programatic:setting(\"").append(simpleSource)
                            .append("\");}");
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        while ((line = bufferedReader.readLine()) != null) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Color getRandomColor(String sourceName) {
        if (Stream.of("mapwithai", "maxar", "digitalglobe", "microsoft/")
                .anyMatch(i -> sourceName.toLowerCase(Locale.ROOT).contains(i.toLowerCase(Locale.ROOT)))) {
            return SafeColors.AI_MAGENTA.getColor();
        }
        SafeColors[] colors = Stream.of(SafeColors.values()).filter(c -> SafeColors.AI_MAGENTA != c)
                .toArray(SafeColors[]::new);
        CRC32 crc = new CRC32();
        crc.update(sourceName.getBytes(StandardCharsets.UTF_8));

        double bucket = crc.getValue() / CRC_DIVIDE_TO_TEN_K_MAX;
        double bucketSize = 10_000d / colors.length;
        for (int i = 1; i <= colors.length; i++) {
            if (bucket < bucketSize * i) {
                return colors[i - 1].getColor();
            }
        }
        return colors[colors.length - 1].getColor();
    }

    private static String getSourceValue(IPrimitive p) {
        if (p.hasTag(SOURCE_KEY)) {
            return p.get(SOURCE_KEY);
        }
        if (p.hasTag(MAPWITHAI_SOURCE_KEY)) {
            return p.get(MAPWITHAI_SOURCE_KEY);
        }
        return null;
    }

}
