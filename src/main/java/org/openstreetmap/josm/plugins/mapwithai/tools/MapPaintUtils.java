// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
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

import org.openstreetmap.josm.data.Version;
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
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.Logging;

public class MapPaintUtils {
    /** The default url for the MapWithAI paint style */
    public static final String DEFAULT_PAINT_STYLE_RESOURCE_URL = "https://josm.openstreetmap.de/josmfile?page=Styles/MapWithAI&zip=1";

    private static String paintStyleResourceUrl = DEFAULT_PAINT_STYLE_RESOURCE_URL;
    private static final Pattern TEST_PATTERN = Pattern
            .compile("^https?:\\/\\/(www\\.)?localhost[:0-9]*\\/josmfile\\?page=Styles\\/MapWithAI&zip=1$");

    private static final String SOURCE_KEY = "source";
    private static final String MAPWITHAI_SOURCE_KEY = "mapwithai:" + SOURCE_KEY;

    private static final String MAPWITHAI_MAPCSS_ZIP_NAME = "Styles_MapWithAI-style.mapcss";
    private static final double CRC_DIVIDE_TO_TEN_K_MAX = 429496.7296;

    static enum SafeColors {
        RED(Color.RED), ORANGE(Color.ORANGE), GOLD(ColorHelper.html2color("#ffd700")),
        LIME(ColorHelper.html2color("#00ff00")), CYAN(Color.CYAN), DODGER_BLUE(ColorHelper.html2color("#1e90ff")),
        AI_MAGENTA(ColorHelper.html2color("#ff26d4")), PINK(Color.PINK), LIGHT_GREY(ColorHelper.html2color("#d3d3d3")),
        LINEN(ColorHelper.html2color("#faf0e6"));

        private final Color color;

        SafeColors(Color color) {
            this.color = new Color(color.getRGB());
        }

        /**
         * Get the safe color
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
     */
    public static void addMapWithAIPaintStyles() {
        // Remove old url's that were automatically added -- remove after Jan 01, 2020
        final List<Pattern> oldUrls = Arrays.asList(Pattern.compile(
                "https://gitlab.com/(gokaart/JOSM_MapWithAI|smocktaylor/rapid)/raw/master/src/resources/styles/standard/(mapwithai|rapid).mapcss"),
                TEST_PATTERN, Pattern.compile("resource://styles/standard/mapwithai.mapcss"));
        new ArrayList<>(MapPaintStyles.getStyles().getStyleSources()).parallelStream()
                .filter(style -> oldUrls.stream().anyMatch(p -> p.matcher(style.url).matches()))
                .forEach(MapPaintStyles::removeStyle);

        if (!checkIfMapWithAIPaintStyleExists()) {
            final MapCSSStyleSource style = new MapCSSStyleSource(paintStyleResourceUrl, MapWithAIPlugin.NAME,
                    "MapWithAI");
            MapPaintStyles.addStyle(style);
        }
    }

    public static boolean checkIfMapWithAIPaintStyleExists() {
        return MapPaintStyles.getStyles().getStyleSources().parallelStream().filter(MapCSSStyleSource.class::isInstance)
                .map(MapCSSStyleSource.class::cast).anyMatch(source -> paintStyleResourceUrl.equals(source.url)
                        || TEST_PATTERN.matcher(source.url).matches());
    }

    /**
     * Remove MapWithAI paint styles
     */
    public static void removeMapWithAIPaintStyles() {
        new ArrayList<>(MapPaintStyles.getStyles().getStyleSources()).parallelStream().filter(
                source -> paintStyleResourceUrl.equals(source.url) || TEST_PATTERN.matcher(source.url).matches())
                .forEach(style -> GuiHelper.runInEDT(() -> MapPaintStyles.removeStyle(style)));
    }

    /**
     * Get any MapWithAI paint style
     *
     * @return get the MapWithAI Paint style
     */
    public static StyleSource getMapWithAIPaintStyle() {
        return MapPaintStyles.getStyles().getStyleSources().parallelStream().filter(
                source -> paintStyleResourceUrl.equals(source.url) || TEST_PATTERN.matcher(source.url).matches())
                .findAny().orElse(null);
    }

    /**
     * Set the URL for the MapWithAI paint style
     *
     * @param paintUrl The paint style for MapWithAI
     */
    public static void setPaintStyleUrl(String paintUrl) {
        paintStyleResourceUrl = paintUrl;
    }

    /**
     * Get the url for the paint style for MapWithAI
     *
     * @return The url for the paint style
     */
    public static String getPaintStyleUrl() {
        return paintStyleResourceUrl;
    }

    /**
     * Add sources to the paint style
     *
     * @param ds The dataset to add sources to
     */
    public static synchronized void addSourcesToPaintStyle(DataSet ds) {

        List<String> sources = ds.allPrimitives().stream().map(MapPaintUtils::getSourceValue).filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        StyleSource styleSource = getMapWithAIPaintStyle();
        /* TODO Depends upon JOSM-19547 */
        if ((Version.getInstance().getVersion() < 20_000
                && Version.getInstance().getVersion() == Version.JOSM_UNKNOWN_VERSION) || styleSource == null) {
            return;
        }
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
            try {
                ZipFile zipFile = new ZipFile(file.getAbsolutePath());
                writeZipData(zipFile, group, sources);
            } catch (ZipException e) {
                // Assume that it is a standard file, not a zip file.
                OutputStream out = new FileOutputStream(file.getName() + ".tmp");
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                writeData(out, bufferedReader, group, sources);
                bufferedReader.close();
                out.close();
                Files.move(new File(file.getName() + ".tmp").toPath(), new File(file.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }

            styleSource.loadStyleSource();
        } catch (IOException e) {
            Logging.error(e);
        }
    }

    private static void writeZipData(ZipFile file, String group, List<String> sources) throws IOException {
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file.getName() + ".tmp"));
        for (Enumeration<? extends ZipEntry> e = file.entries(); e.hasMoreElements();) {
            ZipEntry current = e.nextElement();
            // For the entry we are overwriting, we cannot use the current zipentry, we must
            // make a new one.
            if (!current.getName().equalsIgnoreCase(MAPWITHAI_MAPCSS_ZIP_NAME)) {
                out.putNextEntry(current);
                InputStream is = file.getInputStream(current);
                byte[] buf = new byte[1024];
                int len;
                while ((len = is.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                is.close();
                out.closeEntry();
                continue;
            }
            out.putNextEntry(new ZipEntry(MAPWITHAI_MAPCSS_ZIP_NAME));
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(current), StandardCharsets.UTF_8));
            writeData(out, bufferedReader, group, sources);
            bufferedReader.close();
            out.closeEntry();
        }
        out.close();
        Files.move(new File(file.getName() + ".tmp").toPath(), new File(file.getName()).toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void writeData(OutputStream out, BufferedReader bufferedReader, String group, List<String> sources)
            throws IOException {
        String line = bufferedReader.readLine();
        while (!line.contains("End Settings for the paint style")) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            line = bufferedReader.readLine();
        }
        /* Finish writing the comment */
        while (!line.endsWith("*/")) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            line = bufferedReader.readLine();
        }
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));

        for (String source : sources) {
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            String simpleSource = source.replaceAll("[() /\\${}:]", "_");
            StringBuilder sb = new StringBuilder("setting::").append(simpleSource).append("{").append("type:color;")
                    .append("default:").append(simpleSource).append(ColorHelper.color2html(getRandomColor(source)))
                    .append(";label:tr(\"{0} color\",\"").append(source).append("\");");
            if (group != null) {
                sb.append("group:\"").append(group).append("\";");
            }
            sb.append("}");
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
        if (Arrays.asList("mapwithai", "maxar", "digitalglobe", "microsoft/").stream()
                .anyMatch(i -> sourceName.toLowerCase().contains(i.toLowerCase()))) {
            return SafeColors.AI_MAGENTA.getColor();
        }
        SafeColors[] colors = Stream.of(SafeColors.values()).filter(c -> SafeColors.AI_MAGENTA != c)
                .toArray(SafeColors[]::new);
        CRC32 crc = new CRC32();
        crc.update(sourceName.getBytes(StandardCharsets.UTF_8));

        double bucket = crc.getValue() / CRC_DIVIDE_TO_TEN_K_MAX;
        double bucket_size = 10_000 / colors.length;
        for (int i = 1; i <= colors.length; i++) {
            if (bucket < bucket_size * i) {
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
