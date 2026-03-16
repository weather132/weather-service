package com.github.yun531.climate.notification.domain.adjust;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainOnsetPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RainOnsetAdjusterTest {

    private final RainOnsetAdjuster adjuster = new RainOnsetAdjuster(24, 1);

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 22, 5, 0);

    //  --- null / 빈 입력 가드 ---

    @Nested
    @DisplayName("null/빈 입력 가드")
    class NullEmptyGuards {

        @Test
        @DisplayName("null 리스트 → 빈 리스트")
        void nullInput_emptyOutput() {
            assertThat(adjuster.adjust(null, NOW, null)).isEmpty();
        }

        @Test
        @DisplayName("빈 리스트 → 빈 리스트")
        void emptyInput_emptyOutput() {
            assertThat(adjuster.adjust(List.of(), NOW, null)).isEmpty();
        }

        @Test
        @DisplayName("null now → 원본 이벤트 복사 반환 (필터링 없음)")
        void nullNow_returnsEventsCopy() {
            AlertEvent event = makeEvent(NOW.plusHours(2));

            List<AlertEvent> result = adjuster.adjust(List.of(event), null, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(event);
        }
    }

    //  --- 윈도우 필터링 ---

    @Nested
    @DisplayName("윈도우 필터링")
    class WindowFiltering {

        @Test
        @DisplayName("윈도우 내 이벤트만 남김 (now+1 ~ now+24)")
        void withinWindow_kept() {
            AlertEvent inside  = makeEvent(NOW.plusHours(3));
            AlertEvent outside = makeEvent(NOW.plusHours(25));

            List<AlertEvent> result = adjuster.adjust(List.of(inside, outside), NOW, null);

            assertThat(result).hasSize(1);
            assertThat(((RainOnsetPayload) result.get(0).payload()).validAt()).isEqualTo(NOW.plusHours(3));
        }

        @Test
        @DisplayName("now+1 미만(즉시 시간대) 이벤트 제외")
        void beforeWindowStart_excluded() {
            AlertEvent tooSoon = makeEvent(NOW.plusMinutes(30));

            List<AlertEvent> result = adjuster.adjust(List.of(tooSoon), NOW, null);

            assertThat(result).isEmpty();
        }

        @Nested
        @DisplayName("윈도우 경계 포함/제외")
        class WindowBoundary {

            @Test
            @DisplayName("validAt == windowStart(now+1) → 포함")
            void validAtEqualsWindowStart_included() {
                AlertEvent atStart = makeEvent(NOW.plusHours(1));

                List<AlertEvent> result = adjuster.adjust(List.of(atStart), NOW, null);

                assertThat(result).hasSize(1);
            }

            @Test
            @DisplayName("validAt == windowEnd(now+24) → 포함")
            void validAtEqualsWindowEnd_included() {
                AlertEvent atEnd = makeEvent(NOW.plusHours(24));

                List<AlertEvent> result = adjuster.adjust(List.of(atEnd), NOW, null);

                assertThat(result).hasSize(1);
            }

            @Test
            @DisplayName("validAt == windowStart - 1분 → 제외")
            void validAtJustBeforeStart_excluded() {
                AlertEvent justBefore = makeEvent(NOW.plusMinutes(59)); // 05:59 < 06:00

                List<AlertEvent> result = adjuster.adjust(List.of(justBefore), NOW, null);

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("validAt == windowEnd + 1분 → 제외")
            void validAtJustAfterEnd_excluded() {
                AlertEvent justAfter = makeEvent(NOW.plusHours(24).plusMinutes(1));

                List<AlertEvent> result = adjuster.adjust(List.of(justAfter), NOW, null);

                assertThat(result).isEmpty();
            }
        }
    }

    //  --- hourLimit ---

    @Nested
    @DisplayName("hourLimit")
    class HourLimit {

        @Test
        @DisplayName("hourLimit 적용 시 윈도우 축소")
        void hourLimit_shrinksWindow() {
            AlertEvent at3h = makeEvent(NOW.plusHours(3));
            AlertEvent at5h = makeEvent(NOW.plusHours(5));

            List<AlertEvent> result = adjuster.adjust(List.of(at3h, at5h), NOW, 4);

            assertThat(result).hasSize(1); // at3h만 (now+1 ~ now+4)
        }

        @Test
        @DisplayName("withinHours가 startOffset보다 작으면 빈 리스트 (유효 윈도우 없음)")
        void withinHoursLessThanStartOffset_empty() {
            // startOffsetHours=1, withinHours=0 → endOffset = min(24, 0) = 0 < 1 → 빈 리스트
            AlertEvent event = makeEvent(NOW.plusHours(2));

            List<AlertEvent> result = adjuster.adjust(List.of(event), NOW, 0);

            assertThat(result).isEmpty();
        }
    }

    //  --- maxCount 절단 ---

    @Nested
    @DisplayName("maxCount 절단")
    class MaxCount {

        @Test
        @DisplayName("윈도우 내 이벤트가 maxCount를 초과하면 절단")
        void exceedsMaxCount_truncated() {
            // horizonHours=3, startOffset=1 → maxCount = 3 - 1 + 1 = 3
            RainOnsetAdjuster smallAdjuster = new RainOnsetAdjuster(3, 1);

            List<AlertEvent> events = List.of(
                    makeEvent(NOW.plusHours(1)),
                    makeEvent(NOW.plusHours(1).plusMinutes(30)),
                    makeEvent(NOW.plusHours(2)),
                    makeEvent(NOW.plusHours(2).plusMinutes(30)),
                    makeEvent(NOW.plusHours(3))
            );

            List<AlertEvent> result = smallAdjuster.adjust(events, NOW, null);

            assertThat(result).hasSize(3); // maxCount=3으로 절단
        }
    }

    //  --- 정렬 검증 ---

    @Nested
    @DisplayName("정렬 검증")
    class Sorting {

        @Test
        @DisplayName("역순 입력 → validAt 기준 오름차순 정렬")
        void reverseOrder_sortedByValidAt() {
            AlertEvent late  = makeEvent(NOW.plusHours(5));
            AlertEvent early = makeEvent(NOW.plusHours(2));
            AlertEvent mid   = makeEvent(NOW.plusHours(3));

            List<AlertEvent> result = adjuster.adjust(List.of(late, early, mid), NOW, null);

            assertThat(result).hasSize(3);
            assertThat(((RainOnsetPayload) result.get(0).payload()).validAt()).isEqualTo(NOW.plusHours(2));
            assertThat(((RainOnsetPayload) result.get(1).payload()).validAt()).isEqualTo(NOW.plusHours(3));
            assertThat(((RainOnsetPayload) result.get(2).payload()).validAt()).isEqualTo(NOW.plusHours(5));
        }
    }

    //  --- occurredAt 계산 ---

    @Nested
    @DisplayName("occurredAt 계산")
    class OccurredAt {

        @Test
        @DisplayName("occurredAt이 nowHour로 통일됨")
        void occurredAt_normalizedToNowHour() {
            AlertEvent event = makeEvent(NOW.plusHours(2));

            List<AlertEvent> result = adjuster.adjust(List.of(event), NOW, null);

            assertThat(result.get(0).occurredAt()).isEqualTo(NOW); // truncatedTo(HOURS)
        }
    }

    //  --- 헬퍼 ---

    private AlertEvent makeEvent(LocalDateTime validAt) {
        return new AlertEvent(
                AlertTypeEnum.RAIN_ONSET, "R1", NOW,
                new RainOnsetPayload(AlertTypeEnum.RAIN_ONSET, validAt, 80)
        );
    }
}