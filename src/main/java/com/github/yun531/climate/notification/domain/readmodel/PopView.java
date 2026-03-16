package com.github.yun531.climate.notification.domain.readmodel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 알림/판정 전용 POP Projection(읽기 모델).
 * - ForecastSnap(원본 스냅샷)에서 POP만 뽑아 26시간/7일 규격으로 정규화한다.
 */
public record PopView(
        Hourly hourly,
        Daily daily,
        LocalDateTime reportTime
) {
    public static final int HOURLY_SIZE = 26;
    public static final int DAILY_SIZE = 7;

    public PopView {
        Objects.requireNonNull(hourly, "hourly must not be null");
        Objects.requireNonNull(daily, "daily must not be null");
    }

    /** ======================= Pair ======================= */
    public record Pair(PopView current, PopView previous) {}

    /** ======================= Hourly ======================= */
    public record Hourly(List<Pop> pops) {

        /** @param pop null 이면 "데이터 없음" */
        public record Pop(LocalDateTime validAt, Integer pop) {}

        public Hourly {
            pops = (pops == null) ? List.of() : List.copyOf(pops);
            if (pops.size() != HOURLY_SIZE) {
                throw new IllegalArgumentException(
                        "HourlySeries must have " + HOURLY_SIZE + " points");
            }
        }
    }

    /** ======================= Daily ======================= */
    public record Daily(List<Pop> pops) {

        /** @param am null 이면 "데이터 없음", @param pm null 이면 "데이터 없음" */
        public record Pop(Integer am, Integer pm) {}

        public Daily {
            pops = (pops == null) ? List.of() : List.copyOf(pops);
            if (pops.size() != DAILY_SIZE) {
                throw new IllegalArgumentException(
                        "DailySeries must have " + DAILY_SIZE + " dailyPoints");
            }
        }

        public Pop get(int dayOffset0to6) {
            return pops.get(dayOffset0to6);
        }
    }
}