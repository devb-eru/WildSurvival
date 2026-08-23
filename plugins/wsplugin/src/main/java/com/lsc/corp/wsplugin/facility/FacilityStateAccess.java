package com.lsc.corp.wsplugin.facility;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

public final class FacilityStateAccess {
    private FacilityStateAccess() {
    }

    public static Map<String, RunSnapshot.FacilityInstanceState> instances(RunSnapshot run) {
        if (run.facilities == null) run.facilities = new LinkedHashMap<>();
        return run.facilities;
    }

    public static boolean active(RunSnapshot run, String facilityType) {
        return instances(run).values().stream().anyMatch(instance -> facilityType.equals(instance.facilityType)
                && "ACTIVE".equals(instance.state) && !expired(instance, System.currentTimeMillis()));
    }

    public static Optional<RunSnapshot.FacilityInstanceState> firstActive(RunSnapshot run, String facilityType) {
        long now = System.currentTimeMillis();
        return instances(run).values().stream().filter(instance -> facilityType.equals(instance.facilityType)
                && "ACTIVE".equals(instance.state) && !expired(instance, now)).findFirst();
    }

    public static boolean activeNear(RunSnapshot run, String facilityType, String world,
                                     double x, double y, double z, double range) {
        double rangeSquared = range * range;
        long now = System.currentTimeMillis();
        return instances(run).values().stream().anyMatch(instance -> facilityType.equals(instance.facilityType)
                && "ACTIVE".equals(instance.state) && !expired(instance, now) && world.equals(instance.world)
                && squared(instance.x + 0.5 - x, instance.y + 0.5 - y, instance.z + 0.5 - z) <= rangeSquared);
    }

    public static boolean everActivated(RunSnapshot run, String facilityType) {
        if (run.facilityTypesEverActivated == null) run.facilityTypesEverActivated = new LinkedHashSet<>();
        return run.facilityTypesEverActivated.contains(facilityType);
    }

    public static boolean expired(RunSnapshot.FacilityInstanceState instance, long now) {
        return instance.expiresAtEpochMs > 0L && instance.expiresAtEpochMs <= now;
    }

    private static double squared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }
}
