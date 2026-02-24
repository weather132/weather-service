package com.github.yun531.climate.notification.domain.model;

import lombok.Getter;

@Getter
public enum WarningKind {
    HEAT("폭염"),
    COLDWAVE("한파"),
    HEAVY_SNOW("대설"),
    RAIN("호우"),
    DRY("건조"),
    WIND("강풍"),
    HIGH_WAVE("풍랑"),
    TYPHOON("태풍"),
    TSUNAMI("해일"),
    EARTHQUAKE_TSUNAMI("지진해일");

    private final String label;

    WarningKind(String label) {
        this.label = label;
    }

}