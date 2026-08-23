package com.lsc.corp.wsplugin.combat;

public final class PlayerLifePolicy {
    private PlayerLifePolicy() {
    }

    public static DamageDisposition evaluate(String lifeState, double health, double finalDamage) {
        if ("DOWNED".equals(lifeState) || "DEAD".equals(lifeState)) {
            return DamageDisposition.BLOCK;
        }
        if (!"ACTIVE".equals(lifeState)) {
            return DamageDisposition.BLOCK;
        }
        if (Double.isFinite(finalDamage) && finalDamage > 0.0 && finalDamage + 1.0e-6 >= Math.max(0.0, health)) {
            return DamageDisposition.ENTER_DOWNED;
        }
        if (Double.isInfinite(finalDamage) && finalDamage > 0.0) {
            return DamageDisposition.ENTER_DOWNED;
        }
        return DamageDisposition.ALLOW;
    }

    public enum DamageDisposition {
        ALLOW,
        ENTER_DOWNED,
        BLOCK
    }
}
