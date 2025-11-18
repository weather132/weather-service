package com.github.yun531.climate.service.rule;

import com.github.yun531.climate.domain.PopDailySeries7;
import com.github.yun531.climate.domain.PopSeries24;
import com.github.yun531.climate.service.ClimateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RainForecastRule implements AlertRule {

    private final ClimateService climateService;

    private static final int TH = 60;
    private long SNAP_CURRENT_DEFAULT = 1L;  // todo: snapId 임시 하드코딩

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_FORECAST;
    }

    @Override
    public List<AlertEvent> evaluate(List<Long> regionIds, Instant since) {
        if (regionIds == null || regionIds.isEmpty()) return List.of();

        List<AlertEvent> out = new ArrayList<>(regionIds.size());

        for (Long regionId : regionIds) {
            ClimateService.ForecastSeries fs =
                    climateService.loadForecastSeries(regionId, SNAP_CURRENT_DEFAULT);

            // 순수 인덱스/플래그 정보만 제공
            List<List<Integer>> hourlyParts = buildHourlyParts(fs); // [startIdx, endIdx]
            List<List<Integer>> dayParts    = buildDayParts(fs);    // [amFlag, pmFlag] x N일

            Map<String, Object> payload = new HashMap<>();
            payload.put("_srcRule", "RainForecastRule");
            payload.put("hourlyParts", hourlyParts);
            payload.put("dayParts", dayParts);

            out.add(new AlertEvent(
                    AlertTypeEnum.RAIN_FORECAST,
                    regionId,
                    Instant.now(),
                    payload
            ));
        }
        return out;
    }

    /**
     * 시간대별 POP 24시간에서
     * 연속으로 비가 오는 구간들을 [startIdx, endIdx] 형태로 리턴.
     *
     * 예) POP >= TH 인 구간이
     *    3~5, 10~12 라면
     *    [[3,5], [10,12]] 형태.
     */
    private List<List<Integer>> buildHourlyParts(ClimateService.ForecastSeries fs) {
        if (fs == null || fs.hourly() == null) return List.of();

        PopSeries24 series = fs.hourly();
        int size = Math.min(series.size(), 24);
        if (size == 0) return List.of();

        List<List<Integer>> parts = new ArrayList<>();

        int hourIdx = 0;
        while (hourIdx < size) {
            while (hourIdx < size && series.get(hourIdx) < TH) {
                hourIdx++;
            }
            if (hourIdx >= size) break;

            int startIdx = hourIdx;
            while (hourIdx + 1 < size && series.get(hourIdx + 1) >= TH) {
                hourIdx++;
            }
            int endIdx = hourIdx;

            parts.add(List.of(startIdx, endIdx));
            hourIdx = endIdx + 1;
        }

        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    /**
     * D+0 ~ (N-1)일에 대해
     * 각 일자를 [amFlag, pmFlag] 로 표현한 2차원 리스트를 리턴.
     * 오전 POP >= TH 이면 1, 아니면 0
     *
     * 예) 첫째날 오전/오후 비 안 옴, 둘째날 오전만 비 옴
     *    -> [[0,0], [1,0], ...]
     */
    private List<List<Integer>> buildDayParts(ClimateService.ForecastSeries fs) {
        if (fs == null || fs.daily() == null) return List.of();

        PopDailySeries7 daily = fs.daily();
        if (daily.getDays() == null || daily.getDays().isEmpty()) return List.of();

        List<List<Integer>> parts = new ArrayList<>(daily.getDays().size());

        for (PopDailySeries7.DailyPop p : daily.getDays()) {
            int amFlag = (p.getAm() >= TH) ? 1 : 0;
            int pmFlag = (p.getPm() >= TH) ? 1 : 0;
            parts.add(List.of(amFlag, pmFlag));
        }

        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }
}