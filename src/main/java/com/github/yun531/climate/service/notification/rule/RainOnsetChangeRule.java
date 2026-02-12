package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.PopViewPair;
import com.github.yun531.climate.service.notification.model.RuleId;
import com.github.yun531.climate.service.notification.model.payload.RainOnsetPayload;
import com.github.yun531.climate.service.notification.rule.adjust.RainOnsetEventValidAtAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.util.cache.CacheEntry;
import com.github.yun531.climate.util.time.TimeUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RainOnsetChangeRule extends AbstractCachedRegionAlertRule<List<AlertEvent>> {

    private final SnapshotQueryService snapshotQueryService;
    private final RainOnsetEventValidAtAdjuster windowAdjuster;
    private final RainOnsetEventComputer computer;

    private final int recomputeThresholdMinutes;

    public RainOnsetChangeRule(
            SnapshotQueryService snapshotQueryService,
            RainOnsetEventValidAtAdjuster windowAdjuster,
            RainOnsetEventComputer computer,
            int recomputeThresholdMinutes
    ) {
        this.snapshotQueryService = snapshotQueryService;
        this.windowAdjuster = windowAdjuster;
        this.computer = computer;
        this.recomputeThresholdMinutes = Math.max(0, recomputeThresholdMinutes);
    }

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_ONSET;
    }

    @Override
    protected int thresholdMinutes() {
        return recomputeThresholdMinutes;
    }

    @Override
    protected CacheEntry<List<AlertEvent>> computeForRegion(String regionId, LocalDateTime now) {
        PopViewPair pair = snapshotQueryService.loadDefaultPopViewPair(regionId);
        if (pair == null || pair.current() == null || pair.previous() == null) {
            return new CacheEntry<>(List.of(), null);
        }

        // 캐시 computedAt: 가능하면 "발표 시각(reportTime)"을 사용
        LocalDateTime rt = pair.current().reportTime();
        LocalDateTime computedAt =
                (rt != null) ? TimeUtil.truncateToMinutes(rt) : TimeUtil.truncateToMinutes(now);

        List<RainOnsetEventComputer.Hit> hits = computer.detect(pair);
        if (hits.isEmpty()) return new CacheEntry<>(List.of(), computedAt);

        List<AlertEvent> events = new ArrayList<>(hits.size());
        for (RainOnsetEventComputer.Hit h : hits) {
            RainOnsetPayload payload = new RainOnsetPayload(RuleId.RAIN_ONSET_CHANGE.id(), h.validAt(), h.pop());
            events.add(new AlertEvent(AlertTypeEnum.RAIN_ONSET, regionId, computedAt, payload));
        }

        return new CacheEntry<>(List.copyOf(events), computedAt);
    }

    @Override
    protected List<AlertEvent> buildEvents(
            String regionId,
            List<AlertEvent> events,
            LocalDateTime computedAt,
            LocalDateTime now,
            NotificationRequest request
    ) {
        if (events == null || events.isEmpty()) return List.of();

        // now 기준 윈도우(+1~+windowHours)로 제한 + occurredAt은 nowHour로 통일
        // 윈도우(+1~+windowHours) + rainHourLimit을 adjust 에서 한번에 반영
        Integer maxHour = request.rainHourLimit();
        List<AlertEvent> adjusted = windowAdjuster.adjust(events, now, maxHour);

        return (adjusted == null || adjusted.isEmpty()) ? List.of() : List.copyOf(adjusted);
    }
}