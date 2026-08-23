package com.lsc.corp.wsplugin.combat;

import java.util.Locale;

/** Pure slot-0 arbitration. Bukkit listeners translate events into these inputs. */
public final class CombatInputPolicy {
    private CombatInputPolicy() {
    }

    public static LeftDisposition leftClick(int heldSlot, boolean actionRestricted, boolean sneaking,
                                            String weaponId, boolean hostileTarget, boolean allowedBlock) {
        if (heldSlot != 0) {
            return LeftDisposition.VANILLA;
        }
        if (actionRestricted) {
            return LeftDisposition.RESTRICTED;
        }
        String normalizedWeapon = weaponId == null ? "UNARMED" : weaponId.toUpperCase(Locale.ROOT);
        if (hostileTarget) {
            return sneaking ? LeftDisposition.WEAPON_SKILL : LeftDisposition.BASIC_ATTACK;
        }
        if (allowedBlock && "PICKAXE".equals(normalizedWeapon) && !sneaking) {
            return LeftDisposition.VANILLA_MINING;
        }
        if (allowedBlock && "UNARMED".equals(normalizedWeapon) && sneaking) {
            return LeftDisposition.VANILLA_MINING;
        }
        return sneaking ? LeftDisposition.WEAPON_SKILL : LeftDisposition.BASIC_ATTACK;
    }

    public enum LeftDisposition {
        VANILLA,
        VANILLA_MINING,
        BASIC_ATTACK,
        WEAPON_SKILL,
        RESTRICTED;

        public boolean cancelsBlockDamage() {
            return this != VANILLA && this != VANILLA_MINING;
        }
    }
}
