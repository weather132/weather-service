package com.github.yun531.climate.service.notification.model;

import lombok.Getter;

@Getter
public enum WarningLevel {
    WATCH("예비특보"),
    ADVISORY("주의보"),
    WARNING("경보");

    private final String label;

    WarningLevel(String label) {
        this.label = label;
    }
}