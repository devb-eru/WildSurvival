package com.lsc.corp.wsplugin.death;

public final class GravePolicy {
    private GravePolicy() {
    }

    public static int lostConsumables(int stackAmount, boolean consumable) {
        if (stackAmount <= 0 || !consumable) return 0;
        return (int) Math.floor(stackAmount * 0.25);
    }

    public static int remainingAmount(int stackAmount, boolean consumable) {
        return Math.max(0, stackAmount - lostConsumables(stackAmount, consumable));
    }
}
