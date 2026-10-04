package com.game.cookingspree;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GameVersionPolicyTest {
    @Test
    public void semanticVersionsUseEqualMajorAsCompatibilityBoundary() {
        assertEquals(GameVersionPolicy.Compatibility.COMPATIBLE,
                GameVersionPolicy.compatibility("1.0.0", "1.8.12"));
        assertEquals(GameVersionPolicy.Compatibility.INCOMPATIBLE,
                GameVersionPolicy.compatibility("0.9.9", "1.0.0"));
        assertEquals(GameVersionPolicy.Compatibility.INCOMPATIBLE,
                GameVersionPolicy.compatibility("2.0.0", "1.0.0"));
    }

    @Test
    public void missingAndMalformedVersionsAreIncompatibleAndDisplayUnknown() {
        for (String value : new String[]{null, "", "1.0", "v1.0.0", "01.0.0", "1..0", "1.0.0-beta"}) {
            assertEquals(GameVersionPolicy.Compatibility.INCOMPATIBLE,
                    GameVersionPolicy.compatibility(value, "1.0.0"));
            assertEquals("unknown", GameVersionPolicy.displayVersion(value));
        }
        assertEquals(GameVersionPolicy.Compatibility.COMPATIBLE,
                GameVersionPolicy.compatibility("1.999999999999999999999.0", "1.0.0"));
    }
}
