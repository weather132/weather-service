package com.github.yun531.climate.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.yun531.climate.dto.WarningStateDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class WarningEnumJsonTest {

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void warningKind_직렬화_역직렬화_라벨_검증() throws Exception {
        assertThat(serialize(WarningKind.RAIN)).isEqualTo("\"호우\"");
        assertThat(serialize(WarningKind.HEAT)).isEqualTo("\"폭염\"");

        // 역직렬화: 한글 라벨 → Enum
        assertThat(om.readValue("\"호우\"", WarningKind.class)).isEqualTo(WarningKind.RAIN);
        assertThat(om.readValue("\"태풍\"", WarningKind.class)).isEqualTo(WarningKind.TYPHOON);

        // 알 수 없는 값이면 예외
        assertThatThrownBy(() -> om.readValue("\"???\"", WarningKind.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void warningLevel_직렬화_역직렬화_라벨_검증() throws Exception {
        assertThat(serialize(WarningLevel.ADVISORY)).isEqualTo("\"주의보\"");
        assertThat(serialize(WarningLevel.WARNING)).isEqualTo("\"경보\"");

        // 역직렬화: 한글 라벨 → Enum
        assertThat(om.readValue("\"주의보\"", WarningLevel.class)).isEqualTo(WarningLevel.ADVISORY);
        assertThat(om.readValue("\"경보\"", WarningLevel.class)).isEqualTo(WarningLevel.WARNING);

        // 알 수 없는 값이면 예외
        assertThatThrownBy(() -> om.readValue("\"???\"", WarningLevel.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void warningStateDto_JSON_직렬화_라벨_출력() throws Exception {
        WarningStateDto dto = new WarningStateDto(
                1L, WarningKind.RAIN, WarningLevel.WARNING, Instant.parse("2025-11-04T05:00:00Z")
        );
        String json = om.writeValueAsString(dto);

        // kind/level 이 한글 라벨로 나갔는지 확인
        assertThat(json).contains("\"kind\":\"호우\"");
        assertThat(json).contains("\"level\":\"경보\"");
    }

    // ---- helpers ----
    private String serialize(Object v) throws JsonProcessingException {
        return om.writeValueAsString(v);   //직렬화 메서드 (자바 객체를 JSON 문자열로 변환)
    }
}