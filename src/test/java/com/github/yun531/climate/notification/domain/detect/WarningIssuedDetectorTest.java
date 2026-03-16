package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.WarningIssuedPayload;
import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.model.WarningLevel;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    //  --- 정상 탐지 ---

    @Nested
    @DisplayName("정상 탐지")
    class NormalDetection {

        @Test
        @DisplayName("since 이후 발령된 특보 -> AlertEvent 생성")
        void issuedAfterSince_detected() {
            IssuedWarning rain = new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW);
            Map<WarningKind, IssuedWarning> warningsByKind = Map.of(WarningKind.RAIN, rain);

            List<AlertEvent> events = detector.detect("R1", warningsByKind, SINCE, null);

            assertThat(events).hasSize(1);
            AlertEvent e = events.get(0);
            assertThat(e.type()).isEqualTo(AlertTypeEnum.WARNING_ISSUED);

            WarningIssuedPayload payload = (WarningIssuedPayload) e.payload();
            assertThat(payload.kind()).isEqualTo(WarningKind.RAIN);
            assertThat(payload.level()).isEqualTo(WarningLevel.ADVISORY);
        }

        @Test
        @DisplayName("since 이전 발령 -> 제외")
        void issuedBeforeSince_excluded() {
            IssuedWarning old = new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.WARNING,
                    SINCE.minusHours(1));
            Map<WarningKind, IssuedWarning> warningsByKind = Map.of(WarningKind.RAIN, old);

            List<AlertEvent> events = detector.detect("R1", warningsByKind, SINCE, null);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("여러 종류 특보가 모두 since 이후 -> 전부 반환")
        void multipleKinds_allAfterSince_allReturned() {
            Map<WarningKind, IssuedWarning> warningsByKind = Map.of(
                    WarningKind.RAIN, new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW),
                    WarningKind.HEAT, new IssuedWarning("R1", WarningKind.HEAT, WarningLevel.WARNING, NOW)
            );

            List<AlertEvent> events = detector.detect("R1", warningsByKind, SINCE, null);

            assertThat(events).hasSize(2);
        }
    }

    //  --- 필터링 ---

    @Nested
    @DisplayName("필터링")
    class Filtering {

        @Test
        @DisplayName("warningKinds 필터 적용 — RAIN만 요청하면 HEAT 제외")
        void kindFilter_onlyMatchingKinds() {
            Map<WarningKind, IssuedWarning> warningsByKind = Map.of(
                    WarningKind.RAIN, new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW),
                    WarningKind.HEAT, new IssuedWarning("R1", WarningKind.HEAT, WarningLevel.WARNING, NOW)
            );

            List<AlertEvent> events = detector.detect(
                    "R1", warningsByKind, SINCE, EnumSet.of(WarningKind.RAIN));

            assertThat(events).hasSize(1);
            assertThat(((WarningIssuedPayload) events.get(0).payload()).kind()).isEqualTo(WarningKind.RAIN);
        }

        @Test
        @DisplayName("warningKinds에 빈 EnumSet -> 전체 특보 대상")
        void emptyWarningKinds_includesAll() {
            Map<WarningKind, IssuedWarning> warningsByKind = Map.of(
                    WarningKind.RAIN, new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW));

            List<AlertEvent> events = detector.detect(
                    "R1", warningsByKind, SINCE, EnumSet.noneOf(WarningKind.class));

            assertThat(events).hasSize(1);
        }

        @Test
        @DisplayName("warningKinds에 없는 종류 요청 -> 빈 결과")
        void warningKindNotInMap_empty() {
            Map<WarningKind, IssuedWarning> warningsByKind = Map.of(
                    WarningKind.RAIN, new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW));

            List<AlertEvent> events = detector.detect(
                    "R1", warningsByKind, SINCE, EnumSet.of(WarningKind.HEAT));

            assertThat(events).isEmpty();
        }
    }

    //  --- null / blank 가드 ---

    @Nested
    @DisplayName("null/blank 가드")
    class NullBlankGuards {

        @Test
        @DisplayName("빈 맵 -> 빈 리스트")
        void emptyMap_empty() {
            assertThat(detector.detect("R1", Map.of(), SINCE, null)).isEmpty();
        }

        @Test
        @DisplayName("null regionId -> 빈 리스트")
        void nullRegion_empty() {
            Map<WarningKind, IssuedWarning> warningsByKind = Map.of(
                    WarningKind.RAIN, new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW));

            assertThat(detector.detect(null, warningsByKind, SINCE, null)).isEmpty();
        }

        @Test
        @DisplayName("blank regionId -> 빈 리스트")
        void blankRegionId_empty() {
            Map<WarningKind, IssuedWarning> warningsByKind = Map.of(
                    WarningKind.RAIN, new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, NOW));

            assertThat(detector.detect("", warningsByKind, SINCE, null)).isEmpty();
            assertThat(detector.detect("   ", warningsByKind, SINCE, null)).isEmpty();
        }

        @Test
        @DisplayName("null warningsByKind -> 빈 리스트")
        void nullWarningsByKind_empty() {
            assertThat(detector.detect("R1", null, SINCE, null)).isEmpty();
        }
    }

    //  --- isIssuedAfter 분기 ---

    @Nested
    @DisplayName("isIssuedAfter 분기")
    class IsIssuedAfter {

        @Test
        @DisplayName("since가 null -> 모든 특보 포함")
        void nullSince_includesAll() {
            IssuedWarning rain = new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY,
                    LocalDateTime.of(2020, 1, 1, 0, 0));

            List<AlertEvent> events = detector.detect(
                    "R1", Map.of(WarningKind.RAIN, rain), null, null);

            assertThat(events).hasSize(1);
        }

        @Test
        @DisplayName("updatedAt이 null인 특보 -> 제외")
        void nullUpdatedAt_excluded() {
            IssuedWarning noDate = new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, null);

            List<AlertEvent> events = detector.detect(
                    "R1", Map.of(WarningKind.RAIN, noDate), SINCE, null);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("updatedAt이 since와 정확히 같으면 -> 제외 (isAfter는 strictly after)")
        void updatedAtEqualsSince_excluded() {
            IssuedWarning exact = new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, SINCE);

            List<AlertEvent> events = detector.detect(
                    "R1", Map.of(WarningKind.RAIN, exact), SINCE, null);

            assertThat(events).isEmpty();
        }
    }

    //  --- occurredAt 계산 ---

    @Nested
    @DisplayName("occurredAt 계산")
    class OccurredAt {

        @Test
        @DisplayName("occurredAt은 updatedAt을 truncateToMinutes 한 값")
        void occurredAt_basedOnUpdatedAt() {
            LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 22, 5, 30, 45);
            IssuedWarning warning = new IssuedWarning("R1", WarningKind.RAIN, WarningLevel.ADVISORY, updatedAt);

            List<AlertEvent> events = detector.detect(
                    "R1", Map.of(WarningKind.RAIN, warning), SINCE, null);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).occurredAt()).isEqualTo(
                    LocalDateTime.of(2026, 1, 22, 5, 30));
        }
    }
}