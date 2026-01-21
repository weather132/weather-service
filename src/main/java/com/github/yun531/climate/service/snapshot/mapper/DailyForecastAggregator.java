package com.github.yun531.climate.service.snapshot.mapper;

import com.github.yun531.climate.infra.snapshotapi.dto.DailyForecastItem;
import com.github.yun531.climate.service.forecast.model.DailyPoint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class DailyForecastAggregator {

    public List<DailyPoint> aggregate(LocalDate baseDate, List<DailyForecastItem> items) {
        Map<Integer, DayAcc> acc = new HashMap<>();

        if (items != null) {
            for (DailyForecastItem it : items) {
                if (it == null || it.effectiveTime() == null) continue;

                int offset = (int) ChronoUnit.DAYS.between(baseDate, it.effectiveTime().toLocalDate());
                if (offset < 0 || offset > 6) continue;

                DayAcc dayAcc = acc.computeIfAbsent(offset, k -> new DayAcc());

                Integer temp = it.temp();
                if (temp != null) {
                    dayAcc.minTemp = (dayAcc.minTemp == null) ? temp : Math.min(dayAcc.minTemp, temp);
                    dayAcc.maxTemp = (dayAcc.maxTemp == null) ? temp : Math.max(dayAcc.maxTemp, temp);
                }

                Integer pop = it.pop();
                if (pop != null) {
                    if (isAm(it.effectiveTime())) {
                        dayAcc.amPop = (dayAcc.amPop == null) ? pop : Math.max(dayAcc.amPop, pop);
                    } else {
                        dayAcc.pmPop = (dayAcc.pmPop == null) ? pop : Math.max(dayAcc.pmPop, pop);
                    }
                }
            }
        }

        List<DailyPoint> out = new ArrayList<>(7);
        for (int d = 0; d < 7; d++) {
            DayAcc dayAcc = acc.get(d);
            out.add(new DailyPoint(
                    d,
                    dayAcc == null ? null : dayAcc.minTemp,
                    dayAcc == null ? null : dayAcc.maxTemp,
                    dayAcc == null ? null : dayAcc.amPop,
                    dayAcc == null ? null : dayAcc.pmPop
            ));
        }
        return List.copyOf(out);
    }

    /** 일반화: hour < 12면 AM, 그 외 PM */
    private boolean isAm(LocalDateTime t) {
        return t.getHour() < 12;
    }

    private static final class DayAcc {
        Integer minTemp;
        Integer maxTemp;
        Integer amPop;
        Integer pmPop;
    }
}