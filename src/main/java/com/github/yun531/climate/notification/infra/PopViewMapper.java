package com.github.yun531.climate.notification.infra;

import com.github.yun531.climate.kernel.snapshot.readmodel.DailyPoint;
import com.github.yun531.climate.kernel.snapshot.readmodel.HourlyPoint;
import com.github.yun531.climate.kernel.snapshot.readmodel.WeatherSnapshot;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopView.HourlyPopSeries26;
import com.github.yun531.climate.notification.domain.readmodel.PopView.DailyPopSeries7;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * WeatherSnapshot → PopView 변환기.
 * POP만 뽑아 26시간/7일 규격으로 정규화한다.
 */
@Component
public class PopViewMapper {

    private static final DailyPopSeries7.DailyPop ZERO_POP = new DailyPopSeries7.DailyPop(0, 0);
    private static final HourlyPopSeries26.Point EMPTY_POINT = new HourlyPopSeries26.Point(null, 0);

    public PopView toPopView(WeatherSnapshot snap) {
        if (snap == null) return null;

        PopView.HourlyPopSeries26 hourly = toHourly(snap.hourly());
        PopView.DailyPopSeries7 daily = toDaily(snap.daily());
        return new PopView(hourly, daily, snap.reportTime());
    }

    public PopView.Pair toPair(WeatherSnapshot cur, WeatherSnapshot prev) {
        if (cur == null || prev == null) return null;

        PopView popViewCur = toPopView(cur);
        PopView popViewPrev = toPopView(prev);
        return new PopView.Pair(popViewCur, popViewPrev);
    }

    // -- Hourly: validAt 정렬 → 최대 26개 + 부족분 패딩 --

    private HourlyPopSeries26 toHourly(List<HourlyPoint> hourly) {
        List<HourlyPoint> sorted = sortByValidAt(hourly);

        List<HourlyPopSeries26.Point> out = new ArrayList<>(PopView.HOURLY_SIZE);
        for (int i = 0; i < PopView.HOURLY_SIZE; i++) {
            if (i < sorted.size()) {
                HourlyPoint p = sorted.get(i);
                out.add(new HourlyPopSeries26.Point(p.validAt(), orZero(p.pop())));
            } else {
                out.add(EMPTY_POINT);
            }
        }
        return new HourlyPopSeries26(out);
    }

    // validAt 기준 정렬(없으면 뒤로)
    private List<HourlyPoint> sortByValidAt(List<HourlyPoint> hourly) {
        if (hourly == null || hourly.isEmpty()) return List.of();

        return hourly.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        HourlyPoint::validAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(PopView.HOURLY_SIZE)
                .toList();
    }

    // -- Daily: dayOffset(0~6) 기준으로 슬롯 채움 --

    private DailyPopSeries7 toDaily(List<DailyPoint> daily) {
        DailyPopSeries7.DailyPop[] slots = new DailyPopSeries7.DailyPop[PopView.DAILY_SIZE];
        Arrays.fill(slots, ZERO_POP);

        if (daily != null) {
            for (DailyPoint d : daily) {
                if (d == null) continue;
                int off = d.dayOffset();
                if (off < 0 || off >= PopView.DAILY_SIZE) continue;
                slots[off] = new DailyPopSeries7.DailyPop(orZero(d.amPop()), orZero(d.pmPop()));
            }
        }

        return new DailyPopSeries7(List.of(slots));
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }
}