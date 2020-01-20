// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * Access tag related utilities
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:access">Key:access</a>
 *
 * @author Taylor Smock
 * @since xxx
 */
public final class Access {
    /**
     * Holds access tags to avoid typos
     */
    public enum AccessTags {
        /** Air, land, and sea */
        ALL_TRANSPORT_TYPE("all"),

        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:access">Key:access</a>
         */
        ACCESS_KEY("access", ALL_TRANSPORT_TYPE),

        // Access tag values
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Dyes">Tag:access%3Dyes</a>
         */
        YES("yes"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Dofficial">Tag:access%3Dofficial</a>
         */
        OFFICIAL("official"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Ddesignated">Tag:access%3Ddesignated</a>
         */
        DESIGNATED("designated"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Ddestination">Tag:access%3Ddestination</a>
         */
        DESTINATION("destination"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Ddelivery">Tag:access%3Ddelivery</a>
         */
        DELIVERY("delivery"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Dcustomers">Tag:access%3Dcustomers</a>
         */
        CUSTOMERS("customers"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Dpermissive">Tag:access%3Dpermissive</a>
         */
        PERMISSIVE("permissive"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Dagricultural">Tag:access%3Dagricultural</a>
         */
        AGRICULTURAL("agricultural"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Dforestry">Tag:access%3Dforestry</a>
         */
        FORESTRY("forestry"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Dprivate">Tag:access%3Dprivate</a>
         */
        PRIVATE("private"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Dno">Tag:access%3Dno</a>
         */
        NO("no"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Ddiscouraged">Tag:access%3Ddiscouraged</a>
         */
        DISCOURAGED("discouraged"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Duse_sidepath">Tag:access%3Duse_sidepath</a>
         */
        USE_SIDEPATH("use_sidepath"),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Tag:access%3Ddismount">Tag:access%3Ddismount</a>
         */
        DISMOUNT("dismount"),
        // Land
        /** Land transport types */
        LAND_TRANSPORT_TYPE("land", ALL_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:vehicle">Key:vehicle</a>
         */
        VEHICLE("vehicle", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:motor_vehicle">Key:motor_vehicle</a>
         */
        MOTOR_VEHICLE("motor_vehicle", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:trailer">Key:trailer</a>
         */
        TRAILER("trailer", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:foot">Key:foot</a> */
        FOOT("foot", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:ski">Key:ski</a> */
        SKI("ski", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:inline_skates">Key:inline_skates</a>
         */
        INLINE_SKATES("inline_skates", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:ice_skates">Key:ice_skates</a>
         */
        ICE_SKATES("ice_skates", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:horse">Key:horse</a>
         */
        HORSE("horse", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:bicycle">Key:bicycle</a>
         */
        BICYCLE("bicycle", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:carriage">Key:carriage</a>
         */
        CARRIAGE("carriage", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:caravan">Key:caravan</a>
         */
        CARAVAN("caravan", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:motorcycle">Key:motorcycle</a>
         */
        MOTORCYCLE("motorcycle", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:moped">Key:moped</a>
         */
        MOPED("moped", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:mofa">Key:mofa</a> */
        MOFA("mofa", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:motorcar">Key:motorcar</a>
         */
        MOTORCAR("motorcar", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:motorhome">Key:motorhome</a>
         */
        MOTORHOME("motorhome", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:psv">Key:psv</a> */
        PSV("psv", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:bus">Key:bus</a> */
        BUS("bus", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:taxi">Key:taxi</a> */
        TAXI("taxi", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:tourist_bus">Key:tourist_bus</a>
         */
        TOURIST_BUS("tourist_bus", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:goods">Key:goods</a>
         */
        GOODS("goods", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:hgv">Key:hgv</a> */
        HGV("hgv", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:atv">Key:atv</a> */
        ATV("atv", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:snowmobile">Key:snowmobile</a>
         */
        SNOWMOBILE("snowmobile", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:hgv_articulated">Key:hgv_articulated</a>
         */
        HGV_ARTICULATED("hgv_articulated", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:ski">Key:ski</a> */
        SKI_NORDIC("ski:nordic", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:ski">Key:ski</a> */
        SKI_ALPINE("ski:alpine", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:ski">Key:ski</a> */
        SKI_TELEMARK("ski:telemark", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:coach">Key:coach</a>
         */
        COACH("coach", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:golf_cart">Key:golf_cart</a>
         */
        GOLF_CART("golf_cart", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:minibus">Key:minibus</a>
         */
        MINIBUS("minibus", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:share_taxi">Key:share_taxi</a>
         */
        SHARE_TAXI("share_taxi", LAND_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:hov">Key:hov</a> */
        HOV("hov", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:car_sharing">Key:car_sharing</a>
         */
        CAR_SHARING("car_sharing", LAND_TRANSPORT_TYPE),
        /**
         * Routers should default to {@code yes}, regardless of higher access rules,
         * assuming it is navigatible by vehicle
         *
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:emergency">Key:emergency</a>
         */
        EMERGENCY("emergency", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:hazmat">Key:hazmat</a>
         */
        HAZMAT("hazmat", LAND_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:disabled">Key:disabled</a>
         */
        DISABLED("disabled", LAND_TRANSPORT_TYPE),

        // Water
        /** Water transport type */
        WATER_TRANSPORT_TYPE("water", ALL_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:swimming">Key:swimming</a>
         */
        SWIMMING("swimming", WATER_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:boat">Key:boat</a> */
        BOAT("boat", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:fishing_vessel">Key:fishing_vessel</a>
         */
        FISHING_VESSEL("fishing_vessel", WATER_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:ship">Key:ship</a> */
        SHIP("ship", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:motorboat">Key:motorboat</a>
         */
        MOTORBOAT("motorboat", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:sailboat">Key:sailboat</a>
         */
        SAILBOAT("sailboat", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:canoe">Key:canoe</a>
         */
        CANOE("canoe", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:passenger">Key:passenger</a>
         */
        PASSENGER("passenger", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:cargo">Key:cargo</a>
         */
        CARGO("cargo", WATER_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:isps">Key:isps</a> */
        ISPS("isps", WATER_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:bulk">Key:bulk</a> */
        BULK("bulk", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:tanker">Key:tanker</a>
         */
        TANKER("tanker", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href=
         *      "https://wiki.openstreetmap.org/wiki/Key:container">Key:container</a>
         */
        CONTAINER("container", WATER_TRANSPORT_TYPE),
        /** @see <a href="https://wiki.openstreetmap.org/wiki/Key:imdg">Key:imdg</a> */
        IMDG("imdg", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:tanker">Key:tanker</a>
         */
        TANKER_GAS("tanker:gas", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:tanker">Key:tanker</a>
         */
        TANKER_OIL("tanker:oil", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:tanker">Key:tanker</a>
         */
        TANKER_CHEMICAL("tanker:chemical", WATER_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:tanker">Key:tanker</a>
         */
        TANKER_SINGLEHULL("tanker:singlehull", WATER_TRANSPORT_TYPE),

        // Trains
        /** Rail transport type */
        RAIL_TRANSPORT_TYPE("rail", ALL_TRANSPORT_TYPE),
        /**
         * @see <a href="https://wiki.openstreetmap.org/wiki/Key:train">Key:train</a>
         */
        TRAIN("train", RAIL_TRANSPORT_TYPE);

        private final String key;
        private final AccessTags type;

        AccessTags(String key) {
            this.key = key;
            this.type = null;
        }

        AccessTags(String key, AccessTags type) {
            this.key = key;
            this.type = type;
        }

        /**
         * @return The key for the enum
         */
        public String getKey() {
            return key;
        }

        /**
         * @return The AccessTags transport type
         *         (RAIL_TRANSPORT_TYPE/WATER_TRANSPORT_TYPE/etc)
         */
        public AccessTags getTransportType() {
            return type;
        }

        /**
         * Check if this is a parent transport type (air/sea/water/all)
         *
         * @param potentialDescendant The AccessTags that we want to check
         * @return true if valueOf is a child transport type of this
         */
        public boolean parentOf(AccessTags potentialDescendant) {
            AccessTags tmp = potentialDescendant;
            while (tmp != null && tmp != this) {
                tmp = tmp.getTransportType();
            }
            return tmp == this;
        }

        /**
         * Get the enum that matches the mode
         *
         * @param childrenMode The mode to get the access tag
         * @return The AccessTags enum that matches the childrenMode, or null
         */
        public static AccessTags get(String childrenMode) {
            for (AccessTags value : values()) {
                if (value.getKey().equalsIgnoreCase(childrenMode)) {
                    return value;
                }
            }
            return null;
        }

        /**
         * Get access tags that match a certain type
         *
         * @param type {@link AccessTags#WATER_TRANSPORT_TYPE},
         *             {@link AccessTags#LAND_TRANSPORT_TYPE},
         *             {@link AccessTags#RAIL_TRANSPORT_TYPE}, or
         *             {@link AccessTags#ALL_TRANSPORT_TYPE}
         * @return A collection of access tags that match the given transport type
         */
        public static Collection<AccessTags> getByTransportType(AccessTags type) {
            return Arrays.stream(values()).filter(type::parentOf).collect(Collectors.toList());
        }
    }

    /**
     * The key for children modes for the map, see {@link Access#getAccessMethods}
     */
    public static final String CHILDREN = "children";
    /** The key for parent modes for the map, see {@link Access#getAccessMethods} */
    public static final String PARENT = "parent";
    /** This set has keys that indicate that access is possible */
    private static final Set<String> POSITIVE_ACCESS = new HashSet<>(
            Arrays.asList(AccessTags.YES, AccessTags.OFFICIAL, AccessTags.DESIGNATED, AccessTags.DESTINATION,
                    AccessTags.DELIVERY, AccessTags.CUSTOMERS, AccessTags.PERMISSIVE, AccessTags.AGRICULTURAL,
                    AccessTags.FORESTRY).stream().map(AccessTags::getKey).collect(Collectors.toSet()));
    /** This set has all basic restriction values (yes/no/permissive/private/...) */
    private static final Set<String> RESTRICTION_VALUES = new HashSet<>(Arrays.asList(AccessTags.PRIVATE, AccessTags.NO)
            .stream().map(AccessTags::getKey).collect(Collectors.toSet()));
    /** This set has transport modes (access/foot/ski/motor_vehicle/vehicle/...) */
    private static final Set<String> TRANSPORT_MODES = new HashSet<>(Arrays.asList(AccessTags.ACCESS_KEY,
            AccessTags.FOOT, AccessTags.SKI, AccessTags.INLINE_SKATES, AccessTags.ICE_SKATES, AccessTags.HORSE,
            AccessTags.VEHICLE, AccessTags.BICYCLE, AccessTags.CARRIAGE, AccessTags.TRAILER, AccessTags.CARAVAN,
            AccessTags.MOTOR_VEHICLE, AccessTags.MOTORCYCLE, AccessTags.MOPED, AccessTags.MOFA, AccessTags.MOTORCAR,
            AccessTags.MOTORHOME, AccessTags.PSV, AccessTags.BUS, AccessTags.TAXI, AccessTags.TOURIST_BUS,
            AccessTags.GOODS, AccessTags.HGV, AccessTags.AGRICULTURAL, AccessTags.ATV, AccessTags.SNOWMOBILE,
            AccessTags.HGV_ARTICULATED, AccessTags.SKI_NORDIC, AccessTags.SKI_ALPINE, AccessTags.SKI_TELEMARK,
            AccessTags.COACH, AccessTags.GOLF_CART
    /*
     * ,"minibus","share_taxi","hov","car_sharing","emergency","hazmat","disabled"
     */).stream().map(AccessTags::getKey).collect(Collectors.toSet()));

    /** Map<Access Method, Map<Parent/Child, List<Access Methods>> */
    private static final Map<String, Map<String, List<String>>> accessMethods = new HashMap<>();
    static {
        RESTRICTION_VALUES.addAll(POSITIVE_ACCESS);
        defaultInheritance();
    }

    private Access() {
        // Hide the constructor
    }

    /**
     * Create the default access inheritance, as defined at
     * {@link "https://wiki.openstreetmap.org/wiki/Key:access#Transport_mode_restrictions"}
     */
    private static void defaultInheritance() {
        addMode(null, AccessTags.ACCESS_KEY);

        // Land
        addModes(AccessTags.ACCESS_KEY, AccessTags.FOOT, AccessTags.SKI, AccessTags.INLINE_SKATES,
                AccessTags.ICE_SKATES, AccessTags.HORSE, AccessTags.VEHICLE);
        addModes(AccessTags.SKI, AccessTags.SKI_NORDIC, AccessTags.SKI_ALPINE, AccessTags.SKI_TELEMARK);
        addModes(AccessTags.VEHICLE, AccessTags.BICYCLE, AccessTags.CARRIAGE, AccessTags.TRAILER,
                AccessTags.MOTOR_VEHICLE);
        addModes(AccessTags.TRAILER, AccessTags.CARAVAN);
        addModes(AccessTags.MOTOR_VEHICLE, AccessTags.MOTORCYCLE, AccessTags.MOPED, AccessTags.MOFA,
                AccessTags.MOTORCAR, AccessTags.MOTORHOME, AccessTags.TOURIST_BUS, AccessTags.COACH, AccessTags.GOODS,
                AccessTags.HGV, AccessTags.AGRICULTURAL, AccessTags.GOLF_CART, AccessTags.ATV, AccessTags.SNOWMOBILE,
                AccessTags.PSV, AccessTags.HOV, AccessTags.CAR_SHARING, AccessTags.EMERGENCY, AccessTags.HAZMAT,
                AccessTags.DISABLED);
        addMode(AccessTags.HGV, AccessTags.HGV_ARTICULATED);
        addModes(AccessTags.PSV, AccessTags.BUS, AccessTags.MINIBUS, AccessTags.SHARE_TAXI, AccessTags.TAXI);

        // Water
        addModes(AccessTags.ACCESS_KEY, AccessTags.SWIMMING, AccessTags.BOAT, AccessTags.FISHING_VESSEL,
                AccessTags.SHIP);
        addModes(AccessTags.BOAT, AccessTags.MOTORBOAT, AccessTags.SAILBOAT, AccessTags.CANOE);
        addModes(AccessTags.SHIP, AccessTags.PASSENGER, AccessTags.CARGO, AccessTags.ISPS);
        addModes(AccessTags.CARGO, AccessTags.BULK, AccessTags.TANKER, AccessTags.CONTAINER, AccessTags.IMDG);
        addModes(AccessTags.TANKER, AccessTags.TANKER_GAS, AccessTags.TANKER_OIL, AccessTags.TANKER_CHEMICAL,
                AccessTags.TANKER_SINGLEHULL);

        // Rail
        addModes(AccessTags.ACCESS_KEY, AccessTags.TRAIN);
    }

    /**
     * Add multiple modes with a common parent
     *
     * @param parent The parent of all the modes
     * @param modes  The modes to add
     */
    public static void addModes(AccessTags parent, AccessTags... modes) {
        for (AccessTags mode : modes) {
            addMode(parent, mode);
        }
    }

    /**
     * Add modes to a list, modifying parents as needed
     *
     * @param mode   The mode to be added
     * @param parent The parent of the mode
     */
    public static void addMode(AccessTags parent, AccessTags mode) {
        Objects.requireNonNull(mode, "Mode must not be null");
        if (parent != null) {
            Map<String, List<String>> parentMap = accessMethods.getOrDefault(parent.getKey(), new HashMap<>());
            accessMethods.putIfAbsent(parent.getKey(), parentMap);
            List<String> parentChildren = parentMap.getOrDefault(CHILDREN, new ArrayList<>());
            if (!parentChildren.contains(mode.getKey())) {
                parentChildren.add(mode.getKey());
            }
            parentMap.putIfAbsent(CHILDREN, parentChildren);
        }
        Map<String, List<String>> modeMap = accessMethods.getOrDefault(mode.getKey(), new HashMap<>());
        accessMethods.putIfAbsent(mode.getKey(), modeMap);
        List<String> modeParent = modeMap.getOrDefault(PARENT,
                Collections.singletonList(parent == null ? null : parent.getKey()));
        modeMap.putIfAbsent(PARENT, modeParent);
    }

    /**
     * Get the number of parents a mode has
     *
     * @param mode The mode with parents
     * @return The number of parents the mode has
     */
    public static int depth(String mode) {
        String tempMode = mode;
        int maxCount = accessMethods.size();
        while (tempMode != null && maxCount > 0) {
            tempMode = accessMethods.getOrDefault(tempMode, Collections.emptyMap())
                    .getOrDefault(PARENT, Collections.emptyList()).get(0);
            if (tempMode != null) {
                maxCount--;
            }
        }
        return accessMethods.size() - maxCount;
    }

    /**
     * Expand access modes to cover the children of that access mode (currently only
     * supports the default hierarchy)
     *
     * @param mode   The transport mode
     * @param access The access value (the children transport modes inherit this
     *               value)
     * @return A map of the mode and its children (does not include parents)
     */
    public static Map<String, String> expandAccessMode(String mode, String access) {
        return expandAccessMode(mode, access, AccessTags.ALL_TRANSPORT_TYPE);
    }

    /**
     * Expand access modes to cover the children of that access mode (currently only
     * supports the default hierarchy)
     *
     * @param mode          The transport mode
     * @param access        The access value (the children transport modes inherit
     *                      this value)
     * @param transportType {@link AccessTags#ALL_TRANSPORT_TYPE},
     *                      {@link AccessTags#LAND_TRANSPORT_TYPE},
     *                      {@link AccessTags#WATER_TRANSPORT_TYPE},
     *                      {@link AccessTags#RAIL_TRANSPORT_TYPE}
     * @return A map of the mode and its children (does not include parents)
     */
    public static Map<String, String> expandAccessMode(String mode, String access, AccessTags transportType) {
        Map<String, String> accessModes = new HashMap<>();
        accessModes.put(mode, access);
        if (accessMethods.containsKey(mode)) {
            for (String childrenMode : accessMethods.getOrDefault(mode, Collections.emptyMap()).getOrDefault(CHILDREN,
                    Collections.emptyList())) {
                if (transportType.parentOf(AccessTags.get(childrenMode))) {
                    accessModes.putAll(expandAccessMode(childrenMode, access, transportType));
                }
            }
        }
        return accessModes;
    }

    /**
     * Merge two access maps (more specific wins)
     *
     * @param map1 A map with access values (see {@link Access#expandAccessMode})
     * @param map2 A map with access values (see {@link Access#expandAccessMode})
     * @return The merged map
     */
    public static Map<String, String> mergeMaps(Map<String, String> map1, Map<String, String> map2) {
        Map<String, String> merged;
        if (map1.keySet().containsAll(map2.keySet())) {
            merged = new HashMap<>(map1);
            merged.putAll(map2);
        } else { // if they don't overlap or if map2 contains all of map1
            merged = new HashMap<>(map2);
            merged.putAll(map1);
        }
        return merged;
    }

    /**
     * Get the set of values that can generally be considered to be accessible
     *
     * @return A set of values that can be used for routing purposes (unmodifiable)
     */
    public static Set<String> getPositiveAccessValues() {
        return Collections.unmodifiableSet(POSITIVE_ACCESS);
    }

    /**
     * Get the valid restriction values ({@code unknown} is not included). See
     *
     * @return Valid values for restrictions (unmodifiable)
     * @see <a href=
     *      "https://wiki.openstreetmap.org/wiki/Key:access#List_of_possible_values">Key:access#List_of_possible_values</a>
     */
    public static Set<String> getRestrictionValues() {
        return Collections.unmodifiableSet(RESTRICTION_VALUES);
    }

    /**
     * Get the valid transport modes. See
     *
     * @return Value transport modes (unmodifiable)
     * @see <a href=
     *      "https://wiki.openstreetmap.org/wiki/Key:access#Transport_mode_restrictions">Key:access#Transport_mode_restrictions</a>
     */
    public static Set<String> getTransportModes() {
        return Collections.unmodifiableSet(TRANSPORT_MODES);
    }

    /**
     * Get the access method hierarchy.
     *
     * @return The hierarchy for access modes (unmodifiable)
     * @see <a href=
     *      "https://wiki.openstreetmap.org/wiki/Key:access#Transport_mode_restrictions">Key:access#Transport_mode_restrictions</a>
     */
    public static Map<String, Map<String, List<String>>> getAccessMethods() {
        Map<String, Map<String, List<String>>> map = new HashMap<>();
        for (Entry<String, Map<String, List<String>>> entry : map.entrySet()) {
            Map<String, List<String>> tMap = new HashMap<>();
            entry.getValue().forEach((key, list) -> tMap.put(key, Collections.unmodifiableList(list)));
            map.put(entry.getKey(), Collections.unmodifiableMap(tMap));
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Get the implied access values for a primitive
     *
     * @param primitive     A primitive with access values
     * @param transportType {@link AccessTags#ALL_TRANSPORT_TYPE},
     *                      {@link AccessTags#LAND_TRANSPORT_TYPE},
     *                      {@link AccessTags#WATER_TRANSPORT_TYPE},
     *                      {@link AccessTags#RAIL_TRANSPORT_TYPE}
     * @return The implied access values (for example, "hgv=designated" adds
     *         "hgv_articulated=designated")
     */
    public static Map<String, String> getAccessValues(OsmPrimitive primitive, AccessTags transportType) {
        Map<String, String> accessValues = new HashMap<>();
        TRANSPORT_MODES.stream().filter(primitive::hasKey)
                .map(mode -> expandAccessMode(mode, primitive.get(mode), transportType)).forEach(modeAccess -> {
                    Map<String, String> tMap = mergeMaps(accessValues, modeAccess);
                    accessValues.clear();
                    accessValues.putAll(tMap);
                });
        return accessValues;
    }

    /**
     * Expand a map of access values
     *
     * @param accessValues A map of mode, access type values
     * @return The expanded access values
     */
    public static Map<String, String> expandAccessValues(Map<String, String> accessValues) {
        Map<String, String> modes = new HashMap<>();
        List<Map<String, String>> list = accessValues.entrySet().stream()
                .map(entry -> expandAccessMode(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(Map::size)).collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(list);
        for (Map<String, String> access : list) {
            modes = mergeMaps(modes, access);
        }
        return modes;
    }
}
