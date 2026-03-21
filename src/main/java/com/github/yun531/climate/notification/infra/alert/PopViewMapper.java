package com.github.yun531.climate.notification.infra.alert;

import com.github.yun531.climate.snapshot.domain.readmodel.DailyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopView.Hourly;
import com.github.yun531.climate.notification.domain.readmodel.PopView.Daily;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * WeatherSnapshot -> PopView 변환기.
 * POP만 뽑아 26시간/7일 규격으로 정규화한다.
 * - 데이터 없음(null) -> null 유지 (센티넬 값 사용하지 않음)
 */
@Component
public class PopViewMapper {

    private static final Daily.Pop EMPTY_DAILY = new Daily.Pop(null, null);
    private static final Hourly.Pop EMPTY_POP = new Hourly.Pop(null, null);

    public PopView toPopView(WeatherSnapshot snap) {
        if (snap == null) return null;

        Hourly hourly = toHourly(snap.hourly());
        Daily daily = toDaily(snap.daily());
        return new PopView(hourly, daily, snap.announceTime());
    }

    public PopView.Pair toPair(WeatherSnapshot cur, WeatherSnapshot prev) {
        if (cur == null || prev == null) return null;

        PopView popViewCur = toPopView(cur);
        PopView popViewPrev = toPopView(prev);
        return new PopView.Pair(popViewCur, popViewPrev);
    }

    // -- Hourly: effectiveTime 정렬 -> 최대 26개 + 부족분 패딩 --

    private Hourly toHourly(List<HourlyPoint> hourlyPoints) {
        List<HourlyPoint> sorted = sortByValidAt(hourlyPoints);

        List<Hourly.Pop> out = new ArrayList<>(PopView.HOURLY_SIZE);
        for (int i = 0; i < PopView.HOURLY_SIZE; i++) {
            if (i < sorted.size()) {
                HourlyPoint p = sorted.get(i);
                out.add(new Hourly.Pop(p.effectiveTime(), p.pop()));
            } else {
                out.add(EMPTY_POP);
            }
        }
        return new Hourly(out);
    }

    // effectiveTime 기준 정렬(없으면 뒤로)
    private List<HourlyPoint> sortByValidAt(List<HourlyPoint> hourly) {
        if (hourly == null || hourly.isEmpty()) return List.of();

        return hourly.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        HourlyPoint::effectiveTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(PopView.HOURLY_SIZE)
                .toList();
    }

    // -- Daily: daysAhead(0~6) 기준으로 슬롯 채움 --

    private Daily toDaily(List<DailyPoint> dailyPoints) {
        Daily.Pop[] slots = new Daily.Pop[PopView.DAILY_SIZE];
        Arrays.fill(slots, EMPTY_DAILY);

        if (dailyPoints != null) {
            for (DailyPoint d : dailyPoints) {
                if (d == null) continue;
                int off = d.daysAhead();
                if (off < 0 || off >= PopView.DAILY_SIZE) continue;
                slots[off] = new Daily.Pop(d.amPop(), d.pmPop());
            }
        }

        return new Daily(List.of(slots));
    }
}