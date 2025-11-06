package com.github.yun531.climate.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WarningKind {
    RAIN("호우"),
    HEAT("폭염"),
    WIND("강풍"),
    TYPHOON("태풍");

    private final String label;
    WarningKind(String label){ this.label = label; }

    @JsonValue   // JSON 응답에 한글 라벨을 내보내고 싶을 때 (직렬화)
    public String getLabel(){ return label; }

    @JsonCreator  // JSON 입력에서 한글 라벨을 Enum으로 변환할 때 (역직렬화)
    public static WarningKind fromLabel(String label){
        for (var k : values()) if (k.label.equals(label)) return k;
        throw new IllegalArgumentException("Unknown kind: " + label);
    }
}
