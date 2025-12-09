package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.dto.PopForecastSeries;
import com.github.yun531.climate.dto.PopDailySeries7;
import com.github.yun531.climate.dto.PopSeries24;
import com.github.yun531.climate.dto.SnapKindEnum;
import com.github.yun531.climate.service.ClimateService;
import com.github.yun531.climate.service.notification.NotificationRequest;
import com.github.yun531.climate.util.CacheEntry;
import com.github.yun531.climate.util.RegionCache;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

import static com.github.yun531.climate.util.TimeUtil.nowMinutes;

@Component
@RequiredArgsConstructor
public class RainForecastRule implements AlertRule {

    private final ClimateService climateService;

    private static final int SNAP_CURRENT_CODE = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int RAIN_THRESHOLD    = RainThresholdEnum.RAIN.getThreshold();

    /** since 기준으로 이 분(분 단위)보다 오래된 계산 결과면 재계산 */
    private static final int RECOMPUTE_THRESHOLD_MINUTES = 165;

    /** payload 키 상수 */
    private static final String PAYLOAD_SRC_RULE_KEY      = "_srcRule";
    private static final String PAYLOAD_SRC_RULE_NAME     = "RainForecastRule";
    private static final String PAYLOAD_HOURLY_PARTS_KEY  = "hourlyParts";
    private static final String PAYLOAD_DAY_PARTS_KEY     = "dayParts";

    private static final int MAX_HOURLY_HOURS = 24;

    /** 지역별 캐시: RAIN_FORECAST AlertEvent 리스트 + 계산시각 */
    private final RegionCache<List<AlertEvent>> cache = new RegionCache<>();

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_FORECAST;
    }

    @Override
    public List<AlertEvent> evaluate(NotificationRequest request) {
        List<Integer> regionIds   = request.regionIds();
        LocalDateTime since       = request.since();   // null 가능 (RegionCache 쪽에서 처리)

        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        List<AlertEvent> result = new ArrayList<>();

        for (int regionId : regionIds) {
            CacheEntry<List<AlertEvent>> entry =
                    cache.getOrComputeSinceBased(
                            regionId,
                            since,
                            RECOMPUTE_THRESHOLD_MINUTES,
                            () -> computeForRegion(regionId)
                    );

            List<AlertEvent> eventsForRegion = extractEvents(entry);
            if (!eventsForRegion.isEmpty()) {
                result.addAll(eventsForRegion);
            }
        }
        return result;
    }

    /**
     * CacheEntry에서 이벤트 리스트를 꺼낸다.
     * entry가 null이거나, value(List<AlertEvent>)가 null 또는 비어 있으면 빈 리스트를 반환한다.
     */
    private List<AlertEvent> extractEvents(@Nullable CacheEntry<List<AlertEvent>> entry) {
        if (entry == null || entry.value() == null || entry.value().isEmpty()) {
            return List.of();
        }
        return entry.value();
    }

    // 한 지역에 대한 비 예보 계산 → CacheEntry로 반환
    private CacheEntry<List<AlertEvent>> computeForRegion(int regionId) {
        PopForecastSeries series = loadForecastSeries(regionId);
        if (series == null) {
            return new CacheEntry<>(List.of(), null);
        }

        List<List<Integer>> hourlyParts = buildHourlyParts(series);
        List<List<Integer>> dayParts    = buildDayParts(series);

        Map<String, Object> payload = createPayload(hourlyParts, dayParts);

        LocalDateTime computedAt = nowMinutes();
        AlertEvent event = new AlertEvent(
                AlertTypeEnum.RAIN_FORECAST,
                regionId,
                computedAt,
                payload
        );

        return new CacheEntry<>(List.of(event), computedAt);
    }

    private PopForecastSeries loadForecastSeries(int regionId) {
        PopForecastSeries series = climateService.loadForecastSeries(regionId, SNAP_CURRENT_CODE);
        if (series == null) {
            return null;
        }
        if (series.hourly() == null && series.daily() == null) {
            return null;
        }
        return series;
    }

    /** 시간대별 POP 24시간에서 연속으로 비가 오는 구간들을 [startIdx, endIdx] 형태로 리턴. */
    private List<List<Integer>> buildHourlyParts(PopForecastSeries series) {
        PopSeries24 hourly = series.hourly();
        if (hourly == null) {
            return List.of();
        }

        int size = Math.min(hourly.size(), MAX_HOURLY_HOURS);
        if (size <= 0) {
            return List.of();
        }

        List<List<Integer>> parts = new ArrayList<>();

        int hourIdx = 0;
        while (hourIdx < size) {
            int startIdx = findNextRainStart(hourly, hourIdx, size);
            if (startIdx == -1) {
                break;
            }

            int endIdx = findRainEnd(hourly, startIdx, size);
            parts.add(List.of(startIdx, endIdx));

            hourIdx = endIdx + 1;
        }

        return parts.isEmpty() ?
                    List.of() : List.copyOf(parts);
    }

    /** 다음 강수 시작 인덱스를 찾고, 없으면 -1 */
    private int findNextRainStart(PopSeries24 series, int start, int size) {
        int idx = start;
        while (idx < size && series.get(idx) < RAIN_THRESHOLD) {
            idx++;
        }
        return (idx >= size) ?
                    -1 : idx;
    }

    /** start 인덱스에서 시작하는 연속 강수 구간의 끝 인덱스 */
    private int findRainEnd(PopSeries24 series, int start, int size) {
        int idx = start;
        while (idx + 1 < size && series.get(idx + 1) >= RAIN_THRESHOLD) {
            idx++;
        }
        return idx;
    }

    /**
     * D+0 ~ (N-1)일에 대해 각 일자를 [amFlag, pmFlag] 로 표현한 2차원 리스트를 리턴.
     * amFlag: 오전 POP >= TH 이면 1, 아니면 0
     * pmFlag: 오후 POP >= TH 이면 1, 아니면 0
     */
    private List<List<Integer>> buildDayParts(PopForecastSeries fs) {
        PopDailySeries7 daily = fs.daily();
        if (daily == null || daily.days() == null || daily.days().isEmpty()) {
            return List.of();
        }

        List<PopDailySeries7.DailyPop> days = daily.days();
        List<List<Integer>> parts = new ArrayList<>(days.size());

        for (PopDailySeries7.DailyPop day : days) {
            parts.add(createDayFlagRow(day));
        }

        return parts.isEmpty() ?
                    List.of() : List.copyOf(parts);
    }

    /** 한 일자의 [amFlag, pmFlag] 생성 */
    private List<Integer> createDayFlagRow(PopDailySeries7.DailyPop p) {
        int amFlag = (p.am() >= RAIN_THRESHOLD) ? 1 : 0;
        int pmFlag = (p.pm() >= RAIN_THRESHOLD) ? 1 : 0;
        return List.of(amFlag, pmFlag);
    }

    private Map<String, Object> createPayload(List<List<Integer>> hourlyParts,
                                              List<List<Integer>> dayParts) {
        return Map.of(
                PAYLOAD_SRC_RULE_KEY,     PAYLOAD_SRC_RULE_NAME,
                PAYLOAD_HOURLY_PARTS_KEY, hourlyParts,
                PAYLOAD_DAY_PARTS_KEY,    dayParts
        );
    }

    /** 캐시 무효화 */
    public void invalidate(int regionId) { cache.invalidate(regionId); }
    public void invalidateAll() { cache.invalidateAll(); }
}