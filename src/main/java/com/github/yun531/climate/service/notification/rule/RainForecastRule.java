package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.*;
import com.github.yun531.climate.service.notification.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainForecastComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.util.cache.CacheEntry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

@Component
@RequiredArgsConstructor
public class RainForecastRule extends AbstractCachedRegionAlertRule<List<AlertEvent>> {

    private static final Logger log = LoggerFactory.getLogger(RainForecastPartsAdjuster.class);

    private final SnapshotQueryService snapshotQueryService;

    private static final int SNAP_CURRENT_CODE = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int RAIN_THRESHOLD    = RainThresholdEnum.RAIN.getThreshold();
    private static final int RECOMPUTE_THRESHOLD_MINUTES = 165;

    private static final String PAYLOAD_SRC_RULE_KEY      = "_srcRule";
    private static final String PAYLOAD_SRC_RULE_NAME     = "RainForecastRule";
    private static final String PAYLOAD_HOURLY_PARTS_KEY  = "hourlyParts";
    private static final String PAYLOAD_DAY_PARTS_KEY     = "dayParts";

    private static final int MAX_HOURLY_HOURS = 26;
    private static final int MAX_SHIFT_HOURS  = 2; // 3시간 스냅샷을 0/1/2시간 재사용

    private final RainForecastComputer computer =
            new RainForecastComputer(RAIN_THRESHOLD, MAX_HOURLY_HOURS);

    private final RainForecastPartsAdjuster partsAdjuster =
            new RainForecastPartsAdjuster(
                    PAYLOAD_HOURLY_PARTS_KEY,
                    PAYLOAD_DAY_PARTS_KEY,
                    MAX_SHIFT_HOURS,
                    MAX_HOURLY_HOURS
            );

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_FORECAST;
    }

    @Override
    protected int thresholdMinutes() {
        return RECOMPUTE_THRESHOLD_MINUTES;
    }

    @Override
    protected CacheEntry<List<AlertEvent>> computeForRegion(String regionId) {
        PopForecastSeries series = snapshotQueryService.loadForecastSeries(regionId, SNAP_CURRENT_CODE);
        if (series == null || (series.hourly() == null && series.daily() == null)) {
            return new CacheEntry<>(List.of(), null);
        }

        RainForecastParts parts = computer.compute(series);


        var pts = (series.hourly() == null) ? List.<PopSeries24.Point>of() : series.hourly().getPoints();
        long nullValidAt = pts.stream().filter(p -> p.validAt() == null).count();
        long rainCnt24 = pts.stream()
                .sorted(Comparator.comparing(PopSeries24.Point::validAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(24)
                .filter(p -> p.validAt() != null && p.pop() >= RAIN_THRESHOLD)
                .count();

        log.info("[RAIN] points={}, nullValidAt={}, rainCntInFirst24={}, parts={}",
                pts.size(), nullValidAt, rainCnt24, parts.hourlyParts().size());

        // ---- payload 포맷을 "직렬화 안정적인 단순 구조"로 고정 ----
        // hourlyParts: [[startIso, endIso], ...]
        List<List<String>> hourlyParts2d =
                parts.hourlyParts().stream()
                        // HourlyPart의 필드명이 start()/end()가 아니라 from()/to()라면 여기만 바꿔주면 됨
                        .map(p -> List.of(p.start().toString(), p.end().toString()))
                        .toList();

        // dayParts: dayOffset 순서 유지 -> [[amInt, pmInt], ...] (1/0)
        List<List<Integer>> dayParts2d =
                parts.dayParts().stream()
                        .sorted(Comparator.comparing(RainForecastParts.DayPart::dayOffset))
                        .map(d -> List.of(d.am() ? 1 : 0, d.pm() ? 1 : 0))
                        .toList();

        Map<String, Object> payload = Map.of(
                PAYLOAD_SRC_RULE_KEY, PAYLOAD_SRC_RULE_NAME,
                PAYLOAD_HOURLY_PARTS_KEY, hourlyParts2d,
                PAYLOAD_DAY_PARTS_KEY, dayParts2d
        );
        // -------------------------------------------------------

        // PopForecastSeries 자체에 reportTime이 없으므로 "계산 시각"을 기준 시각으로 저장
        LocalDateTime computedAt = nowMinutes();
        AlertEvent event = new AlertEvent(AlertTypeEnum.RAIN_FORECAST, regionId, computedAt, payload);

        return new CacheEntry<>(List.of(event), computedAt);
    }

    @Override
    protected List<AlertEvent> buildEvents(String regionId,
                                           List<AlertEvent> events,
                                           LocalDateTime computedAt,
                                           LocalDateTime now,
                                           NotificationRequest request) {
        if (events == null || events.isEmpty()) return List.of();

        // 반환 직전에 parts(시간축) 보정
        List<AlertEvent> adjusted = partsAdjuster.adjust(events, computedAt, now);
        return (adjusted == null || adjusted.isEmpty()) ? List.of() : adjusted;
    }
}