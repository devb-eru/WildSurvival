package com.lsc.corp.wsplugin.combat;

import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.BASIC_ATTACK;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.RESTRICTED;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.VANILLA;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.VANILLA_MINING;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.WEAPON_SKILL;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.SlotChangeDisposition.COMMON_ACTIVE;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.SlotChangeDisposition.QUICK_ITEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatInputPolicyTest {
    @Test
    void nonCombatSlotsAlwaysRemainVanilla() {
        assertEquals(VANILLA, CombatInputPolicy.leftClick(4, false, true, "PICKAXE", true, true));
    }

    @Test
    void pickaxeMinesOnlyWithoutAHostileTargetAndWithoutSneaking() {
        assertEquals(VANILLA_MINING, CombatInputPolicy.leftClick(0, false, false, "PICKAXE", false, true));
        assertEquals(BASIC_ATTACK, CombatInputPolicy.leftClick(0, false, false, "PICKAXE", true, true));
        assertEquals(WEAPON_SKILL, CombatInputPolicy.leftClick(0, false, true, "PICKAXE", false, true));
    }

    @Test
    void crouchedUnarmedInputCanBreakAnAllowedBlock() {
        assertEquals(VANILLA_MINING, CombatInputPolicy.leftClick(0, false, true, "UNARMED", false, true));
        assertEquals(BASIC_ATTACK, CombatInputPolicy.leftClick(0, false, false, "UNARMED", false, true));
    }

    @Test
    void restrictionAndCancellationAreExplicit() {
        assertEquals(RESTRICTED, CombatInputPolicy.leftClick(0, true, false, "SWORD", false, true));
        assertTrue(BASIC_ATTACK.cancelsBlockDamage());
        assertFalse(VANILLA_MINING.cancelsBlockDamage());
    }

    @Test
    void shiftNumberIsInterceptedOnlyWhenLeavingCombatStance() {
        assertEquals(COMMON_ACTIVE, CombatInputPolicy.slotChange(0, 1, true));
        assertEquals(COMMON_ACTIVE, CombatInputPolicy.slotChange(0, 4, true));
        assertEquals(QUICK_ITEM, CombatInputPolicy.slotChange(0, 5, true));
        assertEquals(QUICK_ITEM, CombatInputPolicy.slotChange(0, 8, true));
        assertTrue(COMMON_ACTIVE.returnsToCombatStance());
        assertTrue(QUICK_ITEM.returnsToCombatStance());
    }

    @Test
    void nonCombatSlotAndOrdinaryNumberSelectionRemainVanilla() {
        assertEquals(CombatInputPolicy.SlotChangeDisposition.VANILLA,
                CombatInputPolicy.slotChange(1, 2, true));
        assertEquals(CombatInputPolicy.SlotChangeDisposition.VANILLA,
                CombatInputPolicy.slotChange(0, 2, false));
        assertFalse(CombatInputPolicy.SlotChangeDisposition.VANILLA.returnsToCombatStance());
    }

    @Test
    void fixedOffhandNeverUsesVanillaHandSwapDuringARun() {
        assertEquals(CombatInputPolicy.SwapHandDisposition.SHORT_GUARD,
                CombatInputPolicy.swapHand(0, false));
        for (int slot = 1; slot <= 8; slot++) {
            assertEquals(CombatInputPolicy.SwapHandDisposition.FIXED_OFFHAND_REJECTED,
                    CombatInputPolicy.swapHand(slot, false));
        }
        assertEquals(CombatInputPolicy.SwapHandDisposition.PLAYER_MENU,
                CombatInputPolicy.swapHand(0, true));
        assertEquals(CombatInputPolicy.SwapHandDisposition.PLAYER_MENU,
                CombatInputPolicy.swapHand(8, true));
    }
}
