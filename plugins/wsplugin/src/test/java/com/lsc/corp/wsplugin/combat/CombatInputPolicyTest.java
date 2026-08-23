package com.lsc.corp.wsplugin.combat;

import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.BASIC_ATTACK;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.RESTRICTED;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.VANILLA;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.VANILLA_MINING;
import static com.lsc.corp.wsplugin.combat.CombatInputPolicy.LeftDisposition.WEAPON_SKILL;
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
}
