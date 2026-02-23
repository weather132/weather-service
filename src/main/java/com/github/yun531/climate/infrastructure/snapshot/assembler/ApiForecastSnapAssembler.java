package com.github.yun531.climate.infrastructure.snapshot.assembler;

import com.github.yun531.climate.infrastructure.snapshot.dto.DailyForecastItem;
import com.github.yun531.climate.infrastructure.snapshot.dto.DailyForecastResponse;
import com.github.yun531.climate.infrastructure.snapshot.dto.GridPoint;
import com.github.yun531.climate.infrastructure.snapshot.dto.HourlySnapshotResponse;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 외부 Snapshot API 응답 DTO(HourlySnapshotResponse, DailyForecastResponse)를
 *    서비스 도메인 모델(ForecastSnap, HourlyPoint, DailyPoint)로 변환/조립  */
@Component
public class ApiForecastSnapAssembler {

    /** 시간별 스냅샷 + 일별 포인트를 합쳐 도메인 모델(ForecastSnap)을 생성 */
    public ForecastSnap buildForecastSnap(
            String regionId,
            HourlySnapshotResponse hourly,
            List<DailyPoint> dailyPoints
    ) {
        List<HourlyPoint> hourlyPoints = buildHourlyPoints(hourly);
        return new ForecastSnap(regionId, hourly.announceTime(), hourlyPoints, dailyPoints);
    }

    /** HourlySnapshotResponse 에서 HourlyPoint 리스트(최대 26개)를 생성
     * - effectiveTime 기준으로 정렬(null은 뒤로) */
    public List<HourlyPoint> buildHourlyPoints(HourlySnapshotResponse hourly) {
        List<GridPoint> src = (hourly == null || hourly.gridForecastData() == null)
                ? List.of()
                : hourly.gridForecastData();

        List<GridPoint> sorted = new ArrayList<>(src);
        sorted.sort(Comparator.comparing(
                GridPoint::effectiveTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        int n = Math.min(26, sorted.size());
        List<HourlyPoint> out = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            GridPoint p = sorted.get(i);
            out.add(new HourlyPoint(p.effectiveTime(), p.temp(), p.pop()));
        }

        return List.copyOf(out);
    }

    /**
     * DailyForecastResponse 에서 dayOffset 0..6의 DailyPoint 7개를 생성
     * - baseDate를 기준으로 effectiveTime의 날짜 차이를 dayOffset 으로 환산
     * - temp: minTemp는 최소값, maxTemp는 최대값
     * - pop : AM(0~11시), PM(12~23시)로 나누어 각 구간의 최대 POP  */
    public List<DailyPoint> buildDailyPoints(LocalDate baseDate, DailyForecastResponse daily) {
        if (baseDate == null || daily == null || daily.forecasts() == null) {
            return emptyDailyPoints();
        }
        return aggregateDailyPoints(baseDate, daily.forecasts());
    }

    /** daily forecast item 들을 dayOffset 별로 집계해서 DailyPoint 7개를 생성 */
    private List<DailyPoint> aggregateDailyPoints(LocalDate baseDate, List<DailyForecastItem> items) {
        Map<Integer, DayAcc> acc = new HashMap<>();

        for (DailyForecastItem it : items) {
            if (it == null || it.effectiveTime() == null) continue;

            int offset = (int) ChronoUnit.DAYS.between(baseDate, it.effectiveTime().toLocalDate());
            if (offset < 0 || offset > 6) continue;

            DayAcc dayAcc = acc.computeIfAbsent(offset, k -> new DayAcc());

            // temp: min/max 누적
            Integer temp = it.temp();
            if (temp != null) {
                dayAcc.minTemp = (dayAcc.minTemp == null) ? temp : Math.min(dayAcc.minTemp, temp);
                dayAcc.maxTemp = (dayAcc.maxTemp == null) ? temp : Math.max(dayAcc.maxTemp, temp);
            }

            // pop: AM/PM 별 최대값
            Integer pop = it.pop();
            if (pop != null) {
                if (isAm(it.effectiveTime())) {
                    dayAcc.amPop = (dayAcc.amPop == null) ? pop : Math.max(dayAcc.amPop, pop);
                } else {
                    dayAcc.pmPop = (dayAcc.pmPop == null) ? pop : Math.max(dayAcc.pmPop, pop);
                }
            }
        }

        // dayOffset 0..6: 존재하지 않는 offset은 null로 채움
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

    private boolean isAm(LocalDateTime t) {
        return t.getHour() < 12;
    }

    private List<DailyPoint> emptyDailyPoints() {
        List<DailyPoint> out = new ArrayList<>(7);
        for (int d = 0; d < 7; d++) {
            out.add(new DailyPoint(d, null, null, null, null));
        }
        return List.copyOf(out);
    }

    /** dayOffset별 집계용 누적자 */
    private static final class DayAcc {
        Integer minTemp;
        Integer maxTemp;
        Integer amPop;
        Integer pmPop;
    }
}