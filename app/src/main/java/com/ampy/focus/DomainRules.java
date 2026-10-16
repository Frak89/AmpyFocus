package com.ampy.focus;

import java.net.IDN;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** For AmpybiOS FocusBridge v2 only. Owner uses optional curated family DNS. */
public final class DomainRules {
    private DomainRules() {}

    public static Set<String> parse(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        for (String raw : text.split("[\\s,;]+")) {
            String item = raw.trim().toLowerCase(Locale.ROOT);
            if (item.startsWith("https://")) item = item.substring(8);
            if (item.startsWith("http://")) item = item.substring(7);
            int slash = item.indexOf('/');
            if (slash >= 0) item = item.substring(0, slash);
            if (item.startsWith("*.")) item = item.substring(2);
            if (item.endsWith(".")) item = item.substring(0, item.length() - 1);
            if (item.isEmpty() || item.indexOf(':') >= 0) continue;
            try {
                String ascii = IDN.toASCII(item, IDN.USE_STD3_ASCII_RULES);
                if (ascii.length() > 253 || ascii.indexOf('.') <= 0) continue;
                if (!ascii.matches("[a-z0-9.-]+")) continue;
                out.add(ascii);
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    public static boolean matches(String host, Set<String> domains) {
        if (host == null || domains == null) return false;
        String lower;
        try {
            lower = IDN.toASCII(host.toLowerCase(Locale.ROOT)
                    .replaceAll("\\.$", ""), IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException ex) { return false; }
        for (String item : domains) {
            if (lower.equals(item) || lower.endsWith("." + item)) return true;
        }
        return false;
    }
}
