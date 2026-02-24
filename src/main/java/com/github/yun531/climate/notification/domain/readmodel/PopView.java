package com.github.yun531.climate.notification.domain.readmodel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 알림/판정 전용 POP Projection(읽기 모델).
 * - ForecastSnap(원본 스냅샷)에서 POP만 뽑아 26시간/7일 규격으로 정규화한다.
 */
public record PopView(
        HourlyPopSeries26 hourly,
        DailyPopSeries7 daily,
        LocalDateTime reportTime
) {
    public static final int HOURLY_SIZE = 26;
    public static final int DAILY_SIZE = 7;

    public PopView {
        hourly = (hourly == null) ? HourlyPopSeries26.empty26() : hourly;
        daily  = (daily  == null) ? DailyPopSeries7.empty7() : daily;
    }

    /** ======================= Hourly ======================= */
    public record HourlyPopSeries26(List<Point> points) {
        public record Point(LocalDateTime validAt, int pop) {}

        public HourlyPopSeries26 {
            points = (points == null) ? List.of() : List.copyOf(points);
            if (points.size() != HOURLY_SIZE) {
                throw new IllegalArgumentException("HourlyPopSeries26 must have " + HOURLY_SIZE + " points");
            }
        }

        public static HourlyPopSeries26 empty26() {
            List<Point> out = new ArrayList<>(HOURLY_SIZE);
            for (int i = 0; i < HOURLY_SIZE; i++) out.add(new Point(null, 0));
            return new HourlyPopSeries26(out);
        }

        /** 1..26 */
        public int popAt(int offsetHour1to26) {
            return points.get(offsetHour1to26 - 1).pop();
        }

        /** 1..26 */
        public LocalDateTime validAt(int offsetHour1to26) {
            return points.get(offsetHour1to26 - 1).validAt();
        }
    }

    /** ======================= Daily ======================= */
    public record DailyPopSeries7(List<DailyPop> days) {
        public record DailyPop(int am, int pm) {}

        public DailyPopSeries7 {
            days = (days == null) ? List.of() : List.copyOf(days);
            if (days.size() != DAILY_SIZE) {
                throw new IllegalArgumentException("DailyPopSeries7 must have " + DAILY_SIZE + " days");
            }
        }

        public static DailyPopSeries7 empty7() {
            List<DailyPop> out = new ArrayList<>(DAILY_SIZE);
            for (int i = 0; i < DAILY_SIZE; i++) out.add(new DailyPop(0, 0));
            return new DailyPopSeries7(out);
        }

        public DailyPop get(int dayOffset0to6) {
            return days.get(dayOffset0to6);
        }
    }
}