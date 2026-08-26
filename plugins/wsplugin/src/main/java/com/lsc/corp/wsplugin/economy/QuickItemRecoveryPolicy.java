package com.lsc.corp.wsplugin.economy;

/** Decides how a durable quick-item count repairs a lagging Bukkit player inventory. */
public final class QuickItemRecoveryPolicy {
    public static final int MAX_COUNT = 1_000_000;

    private QuickItemRecoveryPolicy() { }

    public static Decision reconcile(Integer durableCount, int inventoryCount) {
        if (inventoryCount < 0 || inventoryCount > MAX_COUNT) {
            throw new IllegalArgumentException("Inventory quick-item count must be 0 to " + MAX_COUNT);
        }
        boolean initialize = durableCount == null;
        int target = initialize ? inventoryCount : Math.max(0, Math.min(MAX_COUNT, durableCount));
        return new Decision(target, Math.max(0, target - inventoryCount),
                Math.max(0, inventoryCount - target), initialize);
    }

    public record Decision(int targetCount, int grantCount, int removeCount,
                           boolean initializeDurableCount) {
        public boolean inventoryChanged() {
            return grantCount > 0 || removeCount > 0;
        }
    }
}
