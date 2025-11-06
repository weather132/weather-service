package com.github.yun531.climate.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WarningLevel {
    ADVISORY("주의보"),
    WARNING("경보");

    private final String label;

    WarningLevel(String label) {
        this.label = label;
    }

    @JsonValue   // JSON 응답 시 '주의보', '경보' 형태로 직렬화
    public String getLabel() {
        return label;
    }

    @JsonCreator  // JSON 입력에서 한글 라벨을 Enum으로 변환할 때 (역직렬화)
    public static WarningLevel fromLabel(String label) {
        for (WarningLevel level : values()) {
            if (level.label.equals(label)) return level;
        }
        throw new IllegalArgumentException("Unknown level: " + label);
    }
}
