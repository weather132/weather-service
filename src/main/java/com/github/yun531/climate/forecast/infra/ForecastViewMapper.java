package com.github.yun531.climate.forecast.infra;

import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyView;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyPoint;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyPoint;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;
import com.github.yun531.climate.snapshot.domain.readmodel.DailyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * WeatherSnapshot -> forecast 읽기모델 변환.
 */
@Component
public class ForecastViewMapper {

    public ForecastHourlyView toHourlyView(WeatherSnapshot snap) {
        if (snap == null) return null;

        List<ForecastHourlyPoint> points = mapHourlyPoints(snap.hourly());
        return new ForecastHourlyView(snap.regionId(), snap.announceTime(), points);
    }

    public ForecastDailyView toDailyView(WeatherSnapshot snap) {
        if (snap == null) return null;

        List<ForecastDailyPoint> points = mapDailyPoints(snap.daily());
        return new ForecastDailyView(snap.regionId(), snap.announceTime(), points);
    }


    /**  도메인 중립 포인트를 HourlyPoint 모델로 변환 후 effectiveTime 기준 정렬 */
    private List<ForecastHourlyPoint> mapHourlyPoints(List<HourlyPoint> hourlyPoints) {
        if (hourlyPoints == null || hourlyPoints.isEmpty()) return List.of();
        return hourlyPoints.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        HourlyPoint::effectiveTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new ForecastHourlyPoint(p.effectiveTime(), p.temp(), p.pop()))
                .toList();
    }

        /**  도메인 중립 포인트를 DailyPoint 모델로 변환 후 daysAhead 기준 정렬 */
    private List<ForecastDailyPoint> mapDailyPoints(List<DailyPoint> dailyPoints) {   //todo: 7일 기준으로 짜르는거 추가
        if (dailyPoints == null || dailyPoints.isEmpty()) return List.of();
        return dailyPoints.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(DailyPoint::daysAhead))
                .map(d -> new ForecastDailyPoint(d.daysAhead(), d.minTemp(), d.maxTemp(), d.amPop(), d.pmPop()))
                .toList();
    }
}