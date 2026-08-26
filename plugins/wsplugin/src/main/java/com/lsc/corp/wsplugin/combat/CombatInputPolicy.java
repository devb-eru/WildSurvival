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

    public static SlotChangeDisposition slotChange(int previousSlot, int newSlot, boolean sneaking) {
        if (!sneaking || previousSlot != 0) {
            return SlotChangeDisposition.VANILLA;
        }
        if (newSlot >= 1 && newSlot <= 4) {
            return SlotChangeDisposition.COMMON_ACTIVE;
        }
        if (newSlot >= 5 && newSlot <= 8) {
            return SlotChangeDisposition.QUICK_ITEM;
        }
        return SlotChangeDisposition.VANILLA;
    }

    public static SwapHandDisposition swapHand(int heldSlot, boolean sneaking) {
        if (sneaking) {
            return SwapHandDisposition.PLAYER_MENU;
        }
        if (heldSlot == 0) {
            return SwapHandDisposition.SHORT_GUARD;
        }
        return SwapHandDisposition.FIXED_OFFHAND_REJECTED;
    }

    public enum LeftDisposition {
        VANILLA,
        VANILLA_MINING,
        BASIC_ATTACK,
        WEAPON_SKILL,
        QUICK_ITEM_CANCELLED,
        RESTRICTED;

        public boolean cancelsBlockDamage() {
            return this != VANILLA && this != VANILLA_MINING;
        }
    }

    public enum SlotChangeDisposition {
        VANILLA,
        COMMON_ACTIVE,
        QUICK_ITEM;

        public boolean returnsToCombatStance() {
            return this != VANILLA;
        }
    }

    public enum SwapHandDisposition {
        PLAYER_MENU,
        SHORT_GUARD,
        FIXED_OFFHAND_REJECTED
    }
}
