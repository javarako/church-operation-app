package com.church.operation.config;

import com.church.operation.service.RuntimeOperationalSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

@Component
public class FiscalYearProperties {
    private final IntSupplier startMonth;

    @Autowired
    public FiscalYearProperties(RuntimeOperationalSettings settings) {
        this(settings::fiscalYearStartMonth);
    }

    public FiscalYearProperties(int startMonth) {
        this(() -> startMonth);
    }

    private FiscalYearProperties(IntSupplier startMonth) {
        this.startMonth = startMonth;
    }

    public int startMonth() {
        return startMonth.getAsInt();
    }
}
