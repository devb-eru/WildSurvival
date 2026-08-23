package com.lsc.corp.wsplugin.story;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;

public final class StoryTriggerPolicy {
    private StoryTriggerPolicy() { }

    public static boolean matches(ProductionContentCatalog.StorySceneEntry scene, String event, String reference) {
        return scene.triggerEvent().equals(event) && scene.triggerRef().equals(reference == null ? "" : reference);
    }

    public static int priority(String value) {
        return switch (value) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "NORMAL" -> 2;
            case "LOW" -> 3;
            default -> 4;
        };
    }
}
