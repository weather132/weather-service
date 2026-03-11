package com.github.yun531.climate.notification.infra.alert;

import com.github.yun531.climate.snapshot.domain.readmodel.DailyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopView.HourlySeries;
import com.github.yun531.climate.notification.domain.readmodel.PopView.DailySeries;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * WeatherSnapshot → PopView 변환기.
 * POP만 뽑아 26시간/7일 규격으로 정규화한다.
 * - 데이터 없음(null) → null 유지 (센티넬 값 사용하지 않음)
 */
@Component
public class PopViewMapper {

    private static final DailySeries.DailyPop EMPTY_DAILY = new DailySeries.DailyPop(null, null);
    private static final HourlySeries.Point EMPTY_POINT = new HourlySeries.Point(null, null);

    public PopView toPopView(WeatherSnapshot snap) {
        if (snap == null) return null;

        HourlySeries hourly = toHourly(snap.hourly());
        DailySeries daily = toDaily(snap.daily());
        return new PopView(hourly, daily, snap.reportTime());
    }

    public PopView.Pair toPair(WeatherSnapshot cur, WeatherSnapshot prev) {
        if (cur == null || prev == null) return null;

        PopView popViewCur = toPopView(cur);
        PopView popViewPrev = toPopView(prev);
        return new PopView.Pair(popViewCur, popViewPrev);
    }

    // -- Hourly: validAt 정렬 → 최대 26개 + 부족분 패딩 --

    private HourlySeries toHourly(List<HourlyPoint> hourly) {
        List<HourlyPoint> sorted = sortByValidAt(hourly);

        List<HourlySeries.Point> out = new ArrayList<>(PopView.HOURLY_SIZE);
        for (int i = 0; i < PopView.HOURLY_SIZE; i++) {
            if (i < sorted.size()) {
                HourlyPoint p = sorted.get(i);
                out.add(new HourlySeries.Point(p.validAt(), p.pop()));
            } else {
                out.add(EMPTY_POINT);
            }
        }
        return new HourlySeries(out);
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

    private DailySeries toDaily(List<DailyPoint> daily) {
        DailySeries.DailyPop[] slots = new DailySeries.DailyPop[PopView.DAILY_SIZE];
        Arrays.fill(slots, EMPTY_DAILY);

        if (daily != null) {
            for (DailyPoint d : daily) {
                if (d == null) continue;
                int off = d.dayOffset();
                if (off < 0 || off >= PopView.DAILY_SIZE) continue;
                slots[off] = new DailySeries.DailyPop(d.amPop(), d.pmPop());
            }
        }

        return new DailySeries(List.of(slots));
    }
}