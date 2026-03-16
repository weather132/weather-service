package com.github.yun531.climate.snapshot.domain.policy;

import com.github.yun531.climate.snapshot.domain.model.SnapKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PublishSchedulePolicyTest {

    // 발표시각: 02, 05, 08, 11, 14, 17, 20, 23 (3시간 간격)
    private final PublishSchedulePolicy policy = new PublishSchedulePolicy(10);

    @Nested
    @DisplayName("latestAvailableAnnounceTime")
    class LatestAvailable {

        @Test
        @DisplayName("05:15 -> 05:00 발표 접근 가능 (delay 10분 경과)")
        void after0510_returns0500() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 22, 5, 15);

            assertThat(policy.latestAvailableAnnounceTime(now))
                    .isEqualTo(LocalDateTime.of(2026, 1, 22, 5, 0));
        }

        @Test
        @DisplayName("05:05 -> 05:00 접근 불가 (delay 미경과) -> 02:00 반환")
        void at0505_returns0200() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 22, 5, 5);

            assertThat(policy.latestAvailableAnnounceTime(now))
                    .isEqualTo(LocalDateTime.of(2026, 1, 22, 2, 0));
        }

        @Test
        @DisplayName("02:05 -> 02:00 접근 불가 -> 전날 23:00 반환")
        void at0205_returnsPreviousDay2300() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 22, 2, 5);

            assertThat(policy.latestAvailableAnnounceTime(now))
                    .isEqualTo(LocalDateTime.of(2026, 1, 21, 23, 0));
        }

        @Test
        @DisplayName("null 이면 null 반환")
        void nullNow_returnsNull() {
            assertThat(policy.latestAvailableAnnounceTime(null)).isNull();
        }
    }

    @Nested
    @DisplayName("announceTimeFor")
    class AnnounceTimeFor {

        @Test
        @DisplayName("CURRENT -> 최신 접근 가능 발표시각")
        void current_returnsLatest() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 22, 5, 15);

            assertThat(policy.announceTimeFor(now, SnapKind.CURRENT))
                    .isEqualTo(LocalDateTime.of(2026, 1, 22, 5, 0));
        }

        @Test
        @DisplayName("PREVIOUS -> CURRENT - 3시간")
        void previous_returnsCurrentMinus3h() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 22, 5, 15);

            assertThat(policy.announceTimeFor(now, SnapKind.PREVIOUS))
                    .isEqualTo(LocalDateTime.of(2026, 1, 22, 2, 0));
        }

        @Test
        @DisplayName("null now -> null")
        void nullNow_returnsNull() {
            assertThat(policy.announceTimeFor(null, SnapKind.CURRENT)).isNull();
        }
    }

    @Nested
    @DisplayName("isAccessible")
    class IsAccessible {

        @Test
        @DisplayName("delay 경과 후 접근 가능")
        void afterDelay_accessible() {
            LocalDateTime now          = LocalDateTime.of(2026, 1, 22, 5, 11);
            LocalDateTime announceTime = LocalDateTime.of(2026, 1, 22, 5, 0);

            assertThat(policy.isAccessible(now, announceTime)).isTrue();
        }

        @Test
        @DisplayName("delay 미경과 -> 접근 불가")
        void beforeDelay_notAccessible() {
            LocalDateTime now          = LocalDateTime.of(2026, 1, 22, 5, 9);
            LocalDateTime announceTime = LocalDateTime.of(2026, 1, 22, 5, 0);

            assertThat(policy.isAccessible(now, announceTime)).isFalse();
        }
    }
}
