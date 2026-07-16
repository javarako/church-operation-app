package com.church.operation.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MaintenanceModeService {
    private final AtomicBoolean active = new AtomicBoolean();

    public boolean isActive() {
        return active.get();
    }

    public void enable() {
        active.set(true);
    }

    public void disable() {
        active.set(false);
    }
}
