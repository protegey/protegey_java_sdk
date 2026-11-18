package com.protegey.sdk;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Sanctions / PEP screening — {@code /partner-api/sanctions/screen}. Not sandbox-scoped. */
public class SanctionsModule {
    private final Client http;

    SanctionsModule(Client http) {
        this.http = http;
    }

    /** Screens a name with no type hint. */
    public Map<String, Object> screen(String name) {
        return screen(name, null);
    }

    /**
     * Screens a name. {@code type} is {@code "person"} | {@code "business"} | null (the backend
     * only accepts lowercase; this method lowercases it for you).
     *
     * <p>Returns {@code {decision (BLOCKED|REVIEW|CLEAR), score, pepMatch, matches[], provider,
     * providers, defaultListChecked, degraded, fallbackReason?}}. Each match has {@code source}
     * (lowercase: {@code nigsac}, {@code ofac}, {@code eu}, {@code un}, {@code au}, {@code dfat},
     * {@code custom}, {@code dow_jones}), {@code matchedOn} ({@code name}|{@code alias}) and
     * {@code isPep}.
     */
    public Map<String, Object> screen(String name, String type) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (type != null) body.put("type", type.toLowerCase(Locale.ROOT));
        return http.post("/partner-api/sanctions/screen", body);
    }
}
