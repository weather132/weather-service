package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.WarningIssuedPayload;
import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.model.WarningLevel;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarningIssuedDetectorTest {

    private final WarningIssuedDetector detector = new WarningIssuedDetector();

    private static final LocalDateTime NOW   = LocalDateTime.of(2026, 1, 22, 5, 15);
    private static final LocalDateTime SINCE = NOW.minusHours(2); // 03:15

    @Test
    @DisplayName("since 이후 발령된 특보 → AlertEvent 생성")
    void issuedAfterSince_detected() {
        IssuedWarning rain = new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW);
        Map<WarningKind, IssuedWarning> warningsByKind = Map.of(WarningKind.RAIN, rain);

        List<AlertEvent> events = detector.detect("R1", warningsByKind, SINCE, null, NOW);

        assertThat(events).hasSize(1);
        AlertEvent e = events.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.WARNING_ISSUED);

        WarningIssuedPayload payload = (WarningIssuedPayload) e.payload();
        assertThat(payload.kind()).isEqualTo(WarningKind.RAIN);
        assertThat(payload.level()).isEqualTo(WarningLevel.ADVISORY);
    }

    @Test
    @DisplayName("since 이전 발령 → 제외")
    void issuedBeforeSince_excluded() {
        IssuedWarning old = new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.WARNING,
                SINCE.minusHours(1));
        Map<WarningKind, IssuedWarning> warningsByKind = Map.of(WarningKind.RAIN, old);

        List<AlertEvent> events = detector.detect("R1", warningsByKind, SINCE, null, NOW);

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("warningKinds 필터 적용 — RAIN만 요청하면 HEAT 제외")
    void kindFilter_onlyMatchingKinds() {
        Map<WarningKind, IssuedWarning> warningsByKind = Map.of(
                WarningKind.RAIN, new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW),
                WarningKind.HEAT, new IssuedWarning("R1", WarningKind.HEAT, WarningLevel.WARNING, NOW)
        );

        List<AlertEvent> events = detector.detect(
                "R1", warningsByKind, SINCE, EnumSet.of(WarningKind.RAIN), NOW);

        assertThat(events).hasSize(1);
        assertThat(((WarningIssuedPayload) events.get(0).payload()).kind()).isEqualTo(WarningKind.RAIN);
    }

    @Test
    @DisplayName("빈 맵 → 빈 리스트")
    void emptyMap_empty() {
        assertThat(detector.detect("R1", Map.of(), SINCE, null, NOW)).isEmpty();
    }

    @Test
    @DisplayName("null regionId → 빈 리스트")
    void nullRegion_empty() {
        Map<WarningKind, IssuedWarning> warningsByKind = Map.of(
                WarningKind.RAIN, new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW));
        assertThat(detector.detect(null, warningsByKind, SINCE, null, NOW)).isEmpty();
    }
}
