package com.church.operation.config;

import com.church.operation.service.RuntimeOperationalSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.function.Supplier;

@Component
public class ChurchTimeZoneProperties {
    private final Supplier<ZoneId> zoneId;

    @Autowired
    public ChurchTimeZoneProperties(RuntimeOperationalSettings settings) {
        this(settings::timeZone);
    }

    public ChurchTimeZoneProperties(String timeZone) {
        this(() -> ZoneId.of(timeZone));
    }

    private ChurchTimeZoneProperties(Supplier<ZoneId> zoneId) {
        this.zoneId = zoneId;
    }

    public ZoneId zoneId() {
        return zoneId.get();
    }
}
