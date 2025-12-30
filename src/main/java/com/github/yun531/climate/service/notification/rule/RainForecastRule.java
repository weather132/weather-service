package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.model.PopForecastSeries;
import com.github.yun531.climate.service.notification.model.PopDailySeries7;
import com.github.yun531.climate.service.notification.model.PopSeries24;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.RainThresholdEnum;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.util.cache.CacheEntry;
import com.github.yun531.climate.util.cache.RegionCache;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

@Component
@RequiredArgsConstructor
public class RainForecastRule implements AlertRule {

    private final SnapshotQueryService snapshotQueryService;

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
        List<String> regionIds   = request.regionIds();
        LocalDateTime since       = request.since();   // null 가능 (RegionCache 쪽에서 처리)

        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        List<AlertEvent> result = new ArrayList<>();

        for (String regionId : regionIds) {
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
    private CacheEntry<List<AlertEvent>> computeForRegion(String regionId) {
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

    private PopForecastSeries loadForecastSeries(String regionId) {
        PopForecastSeries series = snapshotQueryService.loadForecastSeries(regionId, SNAP_CURRENT_CODE);
        if (series == null) {
            return null;
        }
        if (series.hourly() == null && series.daily() == null) {
            return null;
        }
        return series;
    }

    /**
     * 시간대별 POP 24시간에서 연속으로 비가 오는 구간들을 [startOffset, endOffset] 형태로 반환.
     * - startOffset, endOffset 은 "스냅샷 기준 몇 시간 후인지"를 나타내는 offset (1~24)
     *   예: [3, 5] → 스냅 기준 3~5시간 후 구간에서 비가 연속으로 온다는 의미
     */
    private List<List<Integer>> buildHourlyParts(PopForecastSeries series) {
        PopSeries24 hourly = series.hourly();
        if (hourly == null) {
            return List.of();
        }

        // PopSeries24.size()는 24, offset은 1..24
        int maxOffset = Math.min(hourly.size(), MAX_HOURLY_HOURS); // 보통 24

        if (maxOffset <= 0) {
            return List.of();
        }

        List<List<Integer>> parts = new ArrayList<>();

        int offset = 1; // offset은 1부터 시작
        while (offset <= maxOffset) {
            int startOffset = findNextRainStart(hourly, offset, maxOffset);
            if (startOffset == -1) {
                break;
            }

            int endOffset = findRainEnd(hourly, startOffset, maxOffset);
            parts.add(List.of(startOffset, endOffset));

            offset = endOffset + 1;
        }

        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    /** 다음 강수 시작 offset(1..maxOffset)을 찾고, 없으면 -1 */
    private int findNextRainStart(PopSeries24 series, int startOffset, int maxOffset) {
        int offset = startOffset;
        while (offset <= maxOffset && series.get(offset) < RAIN_THRESHOLD) {
            offset++;
        }
        return (offset > maxOffset) ? -1 : offset;
    }

    /** startOffset에서 시작하는 연속 강수 구간의 끝 offset */
    private int findRainEnd(PopSeries24 series, int startOffset, int maxOffset) {
        int offset = startOffset;
        while (offset + 1 <= maxOffset && series.get(offset + 1) >= RAIN_THRESHOLD) {
            offset++;
        }
        return offset;
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
    public void invalidate(String regionId) { cache.invalidate(regionId); }
    public void invalidateAll() { cache.invalidateAll(); }
}