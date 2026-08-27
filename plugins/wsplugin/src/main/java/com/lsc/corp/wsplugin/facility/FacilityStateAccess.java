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

    public static int removeWork(RunSnapshot run, String instanceId, String workId) {
        RunSnapshot.FacilityInstanceState instance = instances(run).get(instanceId);
        if (instance == null || instance.queue == null || workId == null) return 0;
        int before = instance.queue.size();
        instance.queue.removeIf(work -> work != null && workId.equals(work.workId));
        return before - instance.queue.size();
    }

    public static boolean corruptionProtected(RunSnapshot run, String world, double x, double y, double z) {
        long now = System.currentTimeMillis();
        for (RunSnapshot.FacilityInstanceState instance : instances(run).values()) {
            if (!"ACTIVE".equals(instance.state) || expired(instance, now) || !world.equals(instance.world)) continue;
            double range = switch (instance.facilityType) {
                case "FAC-P05" -> 5.0;
                case "FAC-S13" -> 6.0 + Math.max(1, instance.level) * 2.0;
                case "FAC-S15" -> 8.0 + Math.max(1, instance.level) * 2.0;
                case "FAC-S14" -> relayPowered(run, instance) ? 8.0 + Math.max(1, instance.level) * 3.0 : 0.0;
                default -> 0.0;
            };
            if (range > 0.0 && squared(instance.x + 0.5 - x, instance.y + 0.5 - y,
                    instance.z + 0.5 - z) <= range * range) return true;
        }
        return false;
    }

    private static boolean relayPowered(RunSnapshot run, RunSnapshot.FacilityInstanceState relay) {
        if (relay.networkId == null || relay.networkId.isBlank()) return false;
        long now = System.currentTimeMillis();
        return instances(run).values().stream().anyMatch(instance -> "FAC-S13".equals(instance.facilityType)
                && "ACTIVE".equals(instance.state) && !expired(instance, now)
                && relay.networkId.equals(instance.networkId));
    }

    public static boolean expired(RunSnapshot.FacilityInstanceState instance, long now) {
        return instance.expiresAtEpochMs > 0L && instance.expiresAtEpochMs <= now;
    }

    private static double squared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }
}
