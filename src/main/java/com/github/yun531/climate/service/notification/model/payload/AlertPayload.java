package com.github.yun531.climate.service.notification.model.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * REST 에서도 payload를 "타입" 그대로 내보내기 위한 다형성 payload 루트.
 * JSON 예시:
 *  "payload": { "payloadType":"RAIN_ONSET", "srcRule":"...", ... }
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "payloadType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RainOnsetPayload.class, name = "RAIN_ONSET"),
        @JsonSubTypes.Type(value = RainForecastPayload.class, name = "RAIN_FORECAST"),
        @JsonSubTypes.Type(value = WarningIssuedPayload.class, name = "WARNING_ISSUED")
})
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface AlertPayload
        permits RainOnsetPayload, RainForecastPayload, WarningIssuedPayload {

    /** 디버깅/추적용 */
    String srcRule();

    /**
     * FCM data payload용(전부 String)
     * - REST는 타입 payload 그대로 나가고,
     * - FCM은 필요할 때만 이걸로 평탄화해서 보낼 수 있게 유지
     */
    default Map<String, String> toFcmData() {
        return Map.of();
    }
}