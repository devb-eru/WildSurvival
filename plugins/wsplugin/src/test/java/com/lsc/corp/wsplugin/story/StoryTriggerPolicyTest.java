package com.lsc.corp.wsplugin.story;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import org.junit.jupiter.api.Test;

class StoryTriggerPolicyTest {
    @Test
    void requiresExactEventAndReference() {
        var scene = new ProductionContentCatalog.StorySceneEntry("S", "CHAPTER", "DISCOVERY:C27:DISCOVERED",
                "DISCOVERY", "C27:DISCOVERED", "HIGH", "story.key", "", "QUEUE", true, false);
        assertTrue(StoryTriggerPolicy.matches(scene, "DISCOVERY", "C27:DISCOVERED"));
        assertFalse(StoryTriggerPolicy.matches(scene, "DISCOVERY", "C27:COMPLETE"));
        assertFalse(StoryTriggerPolicy.matches(scene, "BOSS_STATE", "C27:DISCOVERED"));
    }
}
