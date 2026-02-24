package com.github.yun531.climate.notification.infra.assembler;

import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopViewPair;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotDailyPoint;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotForecast;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotHourlyPoint;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SnapshotForecast(원본 스냅샷) -> PopView(알림/판정 전용 POP Projection) 변환기.
 * - POP만 뽑아 26시간/7일 규격으로 정규화한다.
 * - 누락된 값은 방어적으로 채운다:
 *   - hourly: 부족하면 (validAt=null, pop=0)로 패딩하여 26개 보장
 *   - daily : 0~6 offset을 기준으로 없으면 (am=0, pm=0)
 */
@Component
public class PopViewAssembler {

    public PopView toPopView(SnapshotForecast snap) {
        if (snap == null) return null;

        PopView.HourlyPopSeries26 hourly = toHourly(snap.hourly());
        PopView.DailyPopSeries7 daily = toDaily(snap.daily());

        return new PopView(hourly, daily, snap.reportTime());
    }

    public PopViewPair toPair(SnapshotForecast cur, SnapshotForecast prev) {
        if (cur == null || prev == null) return null;

        PopView a = toPopView(cur);
        PopView b = toPopView(prev);
        if (a == null || b == null) return null;

        return new PopViewPair(a, b);
    }

    private PopView.HourlyPopSeries26 toHourly(List<SnapshotHourlyPoint> hourly) {
        List<SnapshotHourlyPoint> src = (hourly == null) ? List.of() : hourly;

        // validAt 기준 정렬(없으면 뒤로)
        List<SnapshotHourlyPoint> sorted = src.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        SnapshotHourlyPoint::validAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();

        List<PopView.HourlyPopSeries26.Point> out = new ArrayList<>(PopView.HOURLY_SIZE);

        // 최대 26개까지 채우기
        for (int i = 0; i < sorted.size() && out.size() < PopView.HOURLY_SIZE; i++) {
            SnapshotHourlyPoint p = sorted.get(i);
            out.add(new PopView.HourlyPopSeries26.Point(p.validAt(), n(p.pop())));
        }

        // 부족하면 패딩
        while (out.size() < PopView.HOURLY_SIZE) {
            out.add(new PopView.HourlyPopSeries26.Point(null, 0));
        }

        return new PopView.HourlyPopSeries26(out);
    }

    private PopView.DailyPopSeries7 toDaily(List<SnapshotDailyPoint> daily) {
        List<SnapshotDailyPoint> src = (daily == null) ? List.of() : daily;

        // dayOffset 기반으로 0..6 고정 채움
        PopView.DailyPopSeries7.DailyPop[] arr = new PopView.DailyPopSeries7.DailyPop[PopView.DAILY_SIZE];
        for (int i = 0; i < PopView.DAILY_SIZE; i++) {
            arr[i] = new PopView.DailyPopSeries7.DailyPop(0, 0);
        }

        for (SnapshotDailyPoint d : src) {
            if (d == null) continue;

            int off = d.dayOffset();
            if (off < 0 || off >= PopView.DAILY_SIZE) continue;

            arr[off] = new PopView.DailyPopSeries7.DailyPop(n(d.amPop()), n(d.pmPop()));
        }

        return new PopView.DailyPopSeries7(List.of(arr));
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}