package com.game.cookingspree;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigInteger;

/** Owns semantic game-version parsing and the save compatibility boundary. */
final class GameVersionPolicy {
    private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    enum Compatibility { COMPATIBLE, INCOMPATIBLE }

    private GameVersionPolicy() {}

    static Compatibility compatibility(String savedVersion, String currentVersion) {
        BigInteger savedMajor = major(savedVersion);
        BigInteger currentMajor = major(currentVersion);
        return savedMajor != null && currentMajor != null && savedMajor.equals(currentMajor)
                ? Compatibility.COMPATIBLE : Compatibility.INCOMPATIBLE;
    }

    static String displayVersion(String version) {
        return major(version) == null ? "unknown" : version;
    }

    private static BigInteger major(String version) {
        if (version == null) return null;
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.matches()) return null;
        return new BigInteger(matcher.group(1));
    }
}
