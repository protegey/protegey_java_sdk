package com.protegey.sdk;

import java.util.Map;

/** Device intelligence events — {@code /partner-api/device-events}. Sandbox-scoped. */
public class DeviceEventsModule {
    private final Client http;

    DeviceEventsModule(Client http) {
        this.http = http;
    }

    /**
     * Records a device event. Required keys: {@code eventId} (idempotency key, globally unique —
     * prefer a UUID), {@code visitorId}. Optional: {@code externalCustomerId}, {@code phoneNumber},
     * {@code deviceAttributes} — a map with any of {@code platform}, {@code osVersion},
     * {@code deviceModel}, {@code userAgent}, {@code screenWidth}, {@code screenHeight},
     * {@code timezone}, {@code language}, {@code appVersion}, {@code isRooted},
     * {@code isEmulator}, {@code connectionType}, {@code devicePixelRatio}, {@code manufacturer},
     * {@code cpuCores}, {@code totalMemoryMb}, {@code totalStorageMb}, {@code freeStorageMb},
     * {@code batteryLevel}, {@code isCharging}, {@code isVpnActive} (unknown keys are dropped),
     * {@code location} — GPS position, a map with {@code latitude} and {@code longitude}
     * (required), {@code accuracyMeters}, {@code capturedAt} (ISO 8601), {@code isMocked}; a
     * transaction of the same customer within ~15 minutes reuses it.
     *
     * <p>Returns {@code {recorded, action (ALLOW|SOFT_CHALLENGE|HARD_CHALLENGE|BLOCK), riskScore}}.
     */
    public Map<String, Object> record(Map<String, Object> event) {
        return http.post("/partner-api/device-events", event);
    }
}
