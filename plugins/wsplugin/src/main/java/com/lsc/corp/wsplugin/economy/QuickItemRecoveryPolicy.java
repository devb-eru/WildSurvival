package com.lsc.corp.wsplugin.economy;

/** Decides how a durable quick-item count repairs a lagging Bukkit player inventory. */
public final class QuickItemRecoveryPolicy {
    public static final int MAX_COUNT = 1_000_000;

    private QuickItemRecoveryPolicy() { }

    public static Decision reconcile(Integer durableCount, int inventoryCount) {
        validateInventoryCount(inventoryCount);
        boolean initialize = durableCount == null;
        int target = initialize ? inventoryCount : Math.max(0, Math.min(MAX_COUNT, durableCount));
        return new Decision(target, Math.max(0, target - inventoryCount),
                Math.max(0, inventoryCount - target), initialize);
    }

    /** Records an intentional inventory transfer instead of treating it as a player-data rewind. */
    public static Observation observeLegitimateInventory(Integer durableCount, int inventoryCount) {
        validateInventoryCount(inventoryCount);
        int previous = durableCount == null ? 0 : Math.max(0, Math.min(MAX_COUNT, durableCount));
        return new Observation(inventoryCount, durableCount == null || previous != inventoryCount);
    }

    private static void validateInventoryCount(int inventoryCount) {
        if (inventoryCount < 0 || inventoryCount > MAX_COUNT) {
            throw new IllegalArgumentException("Inventory quick-item count must be 0 to " + MAX_COUNT);
        }
    }

    public record Decision(int targetCount, int grantCount, int removeCount,
                           boolean initializeDurableCount) {
        public boolean inventoryChanged() {
            return grantCount > 0 || removeCount > 0;
        }
    }

    public record Observation(int durableCount, boolean changed) { }
}
