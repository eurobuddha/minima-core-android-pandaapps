package com.eurobuddha.pandaapps;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps catalog categories onto the store's display groups, in display order.
 *
 * Every catalog entry already carries a `category`; the store owns only the grouping of those
 * categories and the order they appear in. Security and Utilities hold one app each, so they fold
 * into TOOLS rather than rendering as lone headings, and the non-Android Desktop builds sit last.
 * An unrecognised future category lands in MORE instead of vanishing from the list.
 */
public final class Groups {

    public static final String MORE = "MORE";

    /** Front-page order. MORE stays last so new catalog categories surface at the bottom. */
    private static final String[] ORDER = {
            "GET STARTED",
            "WALLETS",
            "FINANCE & TRADING",
            "SHOP & SELL",
            "SOCIAL",
            "GAMES",
            "TOOLS",
            "DEVELOPER",
            "DESKTOP",
            MORE
    };

    private static final Map<String, String> MAP = new HashMap<>();
    static {
        MAP.put("core",      "GET STARTED");
        MAP.put("store",     "GET STARTED"); // the stores themselves lead the list (catalog order)
        MAP.put("wallet",    "WALLETS");
        MAP.put("finance",   "FINANCE & TRADING");
        MAP.put("shopping",  "SHOP & SELL");
        MAP.put("social",    "SOCIAL");
        MAP.put("games",     "GAMES");
        MAP.put("tools",     "TOOLS");
        MAP.put("utilities", "TOOLS");      // one app (Entropy) — no heading of its own
        MAP.put("security",  "TOOLS");      // one app (KeyUses) — no heading of its own
        MAP.put("developer", "DEVELOPER");
        MAP.put("desktop",   "DESKTOP");
    }

    private Groups() {}

    /** The group heading this app belongs under. */
    public static String groupFor(AppEntry a) {
        String c = a.category == null ? "" : a.category.trim().toLowerCase(Locale.US);
        String g = MAP.get(c);
        return g == null ? MORE : g;
    }

    public static String[] order() { return ORDER.clone(); }
}
