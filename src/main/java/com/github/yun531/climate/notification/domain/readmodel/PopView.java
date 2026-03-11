package com.github.yun531.climate.notification.domain.readmodel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 알림/판정 전용 POP Projection(읽기 모델).
 * - ForecastSnap(원본 스냅샷)에서 POP만 뽑아 26시간/7일 규격으로 정규화한다.
 */
public record PopView(
        HourlySeries hourly,
        DailySeries daily,
        LocalDateTime reportTime
) {
    public static final int HOURLY_SIZE = 26;
    public static final int DAILY_SIZE = 7;

    public PopView {
        hourly = (hourly == null) ? HourlySeries.empty() : hourly;
        daily  = (daily  == null) ? DailySeries.empty() : daily;
    }

    /** ======================= Pair ======================= */
    public record Pair(PopView current, PopView previous) {}

    /** ======================= Hourly ======================= */
    public record HourlySeries(List<Point> points) {

        /** @param pop null 이면 "데이터 없음" */
        public record Point(LocalDateTime validAt, Integer pop) {}

        public HourlySeries {
            points = (points == null) ? List.of() : List.copyOf(points);
            if (points.size() != HOURLY_SIZE) {
                throw new IllegalArgumentException(
                        "HourlySeries must have " + HOURLY_SIZE + " points");
            }
        }

        public static HourlySeries empty() {
            List<Point> out = new ArrayList<>(HOURLY_SIZE);
            for (int i = 0; i < HOURLY_SIZE; i++) out.add(new Point(null, null));
            return new HourlySeries(out);
        }
    }

    /** ======================= Daily ======================= */
    public record DailySeries(List<DailyPop> days) {

        /** @param am null 이면 "데이터 없음", @param pm null 이면 "데이터 없음" */
        public record DailyPop(Integer am, Integer pm) {}

        public DailySeries {
            days = (days == null) ? List.of() : List.copyOf(days);
            if (days.size() != DAILY_SIZE) {
                throw new IllegalArgumentException(
                        "DailySeries must have " + DAILY_SIZE + " dailyPoints");
            }
        }

        public static DailySeries empty() {
            List<DailyPop> out = new ArrayList<>(DAILY_SIZE);
            for (int i = 0; i < DAILY_SIZE; i++) out.add(new DailyPop(null, null));
            return new DailySeries(out);
        }

        public DailyPop get(int dayOffset0to6) {
            return days.get(dayOffset0to6);
        }
    }
}