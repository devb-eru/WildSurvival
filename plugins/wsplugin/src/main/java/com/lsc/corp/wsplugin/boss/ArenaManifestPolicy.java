package com.lsc.corp.wsplugin.boss;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure, deterministic geometry gate that runs before any boss-call item is reserved. */
public final class ArenaManifestPolicy {
    private ArenaManifestPolicy() {
    }

    public static Selection select(String callItemId, List<Stake> source) {
        Spec spec = spec(callItemId);
        List<Stake> stakes = source == null ? List.of() : source.stream()
                .filter(ArenaManifestPolicy::usable)
                .sorted(Comparator.comparing(Stake::world).thenComparing(Stake::instanceId))
                .toList();
        if (stakes.size() < 3) return Selection.rejected("INSUFFICIENT_STAKES");
        for (int first = 0; first < stakes.size() - 2; first++) {
            for (int second = first + 1; second < stakes.size() - 1; second++) {
                for (int third = second + 1; third < stakes.size(); third++) {
                    List<Stake> triple = List.of(stakes.get(first), stakes.get(second), stakes.get(third));
                    Candidate candidate = candidate(spec, triple);
                    if (candidate != null) return Selection.accepted(candidate);
                }
            }
        }
        return Selection.rejected("NO_VALID_STAKE_TRIANGLE");
    }

    public static Spec spec(String callItemId) {
        return switch (callItemId) {
            case "WSI-CALL-D10" -> new Spec(callItemId, "BOSS-D10", 20.0, 42.0,
                    0.0, Double.POSITIVE_INFINITY, 32, 48, List.of(0, 8, 16));
            case "WSI-CALL-D20" -> new Spec(callItemId, "BOSS-D20", 24.0, 46.0,
                    0.0, Double.POSITIVE_INFINITY, 36, 52, List.of(0, 8, 16, 24));
            case "WSI-CALL-D30" -> new Spec(callItemId, "BOSS-D30", 0.0, Double.POSITIVE_INFINITY,
                    0.0, Double.POSITIVE_INFINITY, 40, 58, List.of(0, 8, 16, 24));
            case "WSI-CALL-D40" -> new Spec(callItemId, "BOSS-D40", 0.0, Double.POSITIVE_INFINITY,
                    12.0, 20.0, 44, 62, List.of(0, 8, 16, 24));
            default -> throw new IllegalArgumentException("Unknown boss call item " + callItemId);
        };
    }

    private static Candidate candidate(Spec spec, List<Stake> stakes) {
        String world = stakes.getFirst().world();
        if (stakes.stream().anyMatch(value -> !world.equals(value.world()))) return null;
        for (int left = 0; left < stakes.size(); left++) {
            for (int right = left + 1; right < stakes.size(); right++) {
                double distance = horizontalDistance(stakes.get(left), stakes.get(right));
                if (distance + 1.0e-6 < spec.minimumPairDistance()
                        || distance - 1.0e-6 > spec.maximumPairDistance()) return null;
            }
        }
        double x = stakes.stream().mapToDouble(Stake::x).average().orElseThrow();
        double y = stakes.stream().mapToDouble(Stake::y).average().orElseThrow();
        double z = stakes.stream().mapToDouble(Stake::z).average().orElseThrow();
        for (Stake stake : stakes) {
            double radial = Math.hypot(stake.x() - x, stake.z() - z);
            if (radial + 1.0e-6 < spec.minimumRadiusFromCenter()
                    || radial - 1.0e-6 > spec.maximumRadiusFromCenter()) return null;
        }
        List<String> ids = stakes.stream().map(Stake::instanceId).sorted().toList();
        String fingerprint = Integer.toUnsignedString(String.join("|", ids).hashCode(), 36).toUpperCase(Locale.ROOT);
        return new Candidate(spec.callItemId(), spec.bossId(), world, x, y, z,
                spec.arenaRadius(), spec.recoveryRadius(), ids, fingerprint, spec.offsetRings());
    }

    private static boolean usable(Stake stake) {
        return stake != null && stake.instanceId() != null && !stake.instanceId().isBlank()
                && stake.world() != null && !stake.world().isBlank() && "ACTIVE".equals(stake.state())
                && Double.isFinite(stake.x()) && Double.isFinite(stake.y()) && Double.isFinite(stake.z());
    }

    private static double horizontalDistance(Stake left, Stake right) {
        return Math.hypot(left.x() - right.x(), left.z() - right.z());
    }

    public record Stake(String instanceId, String world, double x, double y, double z, String state) {
    }

    public record Spec(String callItemId, String bossId, double minimumPairDistance,
                       double maximumPairDistance, double minimumRadiusFromCenter,
                       double maximumRadiusFromCenter, int arenaRadius, int recoveryRadius,
                       List<Integer> offsetRings) {
        public Spec {
            offsetRings = List.copyOf(offsetRings);
        }
    }

    public record Candidate(String callItemId, String bossId, String world, double x, double y, double z,
                            int arenaRadius, int recoveryRadius, List<String> stakeInstanceIds,
                            String fingerprint, List<Integer> offsetRings) {
        public Candidate {
            stakeInstanceIds = List.copyOf(stakeInstanceIds);
            offsetRings = List.copyOf(offsetRings);
        }
    }

    public record Selection(boolean accepted, String reason, Candidate candidate) {
        private static Selection accepted(Candidate candidate) {
            return new Selection(true, "OK", candidate);
        }

        private static Selection rejected(String reason) {
            return new Selection(false, reason, null);
        }
    }
}
