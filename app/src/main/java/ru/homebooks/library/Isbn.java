package ru.homebooks.library;

import java.util.Locale;

public final class Isbn {
    private Isbn() {}
    /** Validates checksum and returns canonical ISBN-13, or null. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s\\-–]", "").toUpperCase(Locale.ROOT);
        if (s.matches("[0-9]{9}[0-9X]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) sum += (10 - i) * (s.charAt(i) == 'X' ? 10 : s.charAt(i) - '0');
            if (sum % 11 != 0) return null;
            String prefix = "978" + s.substring(0, 9);
            return prefix + checksum(prefix);
        }
        if (!s.matches("97[89][0-9]{10}")) return null;
        return checksum(s.substring(0, 12)) == s.charAt(12) - '0' ? s : null;
    }
    private static int checksum(String prefix) {
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (prefix.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (10 - sum % 10) % 10;
    }
}
