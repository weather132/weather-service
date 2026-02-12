package com.github.yun531.climate.service.notification.model;

import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 알림/판정 전용 POP Projection(읽기 모델).
 * - ForecastSnap(원본 스냅샷)에서 POP만 뽑아 26시간/7일 규격으로 정규화한다.
 * - 누락된 값은 방어적으로 채운다:
 *   - hourly: 부족하면 (validAt=null, pop=0)로 패딩하여 26개 보장
 *   - daily : 0~6 offset을 기준으로 없으면 (am=0, pm=0)
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

    public static PopView fromSnap(ForecastSnap snap) {
        if (snap == null) return null;

        HourlyPopSeries26 h = HourlyPopSeries26.fromHourlyPoints(snap.hourly());
        DailyPopSeries7 d = DailyPopSeries7.fromDailyPoints(snap.daily());

        return new PopView(h, d, snap.reportTime());
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

        public static HourlyPopSeries26 fromHourlyPoints(List<HourlyPoint> hourly) {
            List<HourlyPoint> src = (hourly == null) ? List.of() : hourly;

            // validAt 기준 정렬(없으면 뒤로)
            List<HourlyPoint> sorted = src.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(
                            HourlyPoint::validAt,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .toList();

            List<Point> out = new ArrayList<>(HOURLY_SIZE);

            // 최대 26개까지 채우기
            for (int i = 0; i < sorted.size() && out.size() < HOURLY_SIZE; i++) {
                HourlyPoint p = sorted.get(i);
                out.add(new Point(p.validAt(), n(p.pop())));
            }

            // 부족하면 패딩
            while (out.size() < HOURLY_SIZE) {
                out.add(new Point(null, 0));
            }

            return new HourlyPopSeries26(out);
        }

        /** 1..26 */
        public int popAt(int offsetHour) {
            return points.get(offsetHour - 1).pop();
        }

        /** 1..26 */
        public LocalDateTime validAt(int offsetHour) {
            return points.get(offsetHour - 1).validAt();
        }

        private static int n(Integer v) {
            return v == null ? 0 : v;
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

        public static DailyPopSeries7 fromDailyPoints(List<DailyPoint> daily) {
            List<DailyPoint> src = (daily == null) ? List.of() : daily;

            // dayOffset 기반으로 0..6 고정 채움
            DailyPop[] arr = new DailyPop[DAILY_SIZE];
            for (int i = 0; i < DAILY_SIZE; i++) arr[i] = new DailyPop(0, 0);

            for (DailyPoint d : src) {
                if (d == null) continue;
                int off = d.dayOffset();
                if (off < 0 || off >= DAILY_SIZE) continue;
                arr[off] = new DailyPop(n(d.amPop()), n(d.pmPop()));
            }

            return new DailyPopSeries7(List.of(arr));
        }

        public DailyPop get(int dayOffset) {
            return days.get(dayOffset);
        }

        private static int n(Integer v) {
            return v == null ? 0 : v;
        }
    }
}