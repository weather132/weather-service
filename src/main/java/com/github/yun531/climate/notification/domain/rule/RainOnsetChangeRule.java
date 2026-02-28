package com.github.yun531.climate.notification.domain.rule;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.model.RuleId;
import com.github.yun531.climate.notification.domain.payload.RainOnsetPayload;
import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import com.github.yun531.climate.notification.domain.readmodel.PopViewPair;
import com.github.yun531.climate.notification.domain.rule.adjust.RainOnsetEventValidAtAdjuster;
import com.github.yun531.climate.notification.domain.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.notification.domain.rule.criteria.AlertCriteria;
import com.github.yun531.climate.shared.time.TimeUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RainOnsetChangeRule implements AlertRule {

    private final PopViewReadPort popViewReadPort;
    private final RainOnsetEventValidAtAdjuster windowAdjuster;
    private final RainOnsetEventComputer computer;

    public RainOnsetChangeRule(
            PopViewReadPort popViewReadPort,
            RainOnsetEventValidAtAdjuster windowAdjuster,
            RainOnsetEventComputer computer
    ) {
        this.popViewReadPort = popViewReadPort;
        this.windowAdjuster = windowAdjuster;
        this.computer = computer;
    }

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_ONSET;
    }

    @Override
    public List<AlertEvent> evaluate(String regionId, AlertCriteria criteria, LocalDateTime now) {
        if (regionId == null || regionId.isBlank()) return List.of();

        LocalDateTime effectiveNow = (now == null)
                ? TimeUtil.nowMinutes()
                : TimeUtil.truncateToMinutes(now);

        PopViewPair pair = popViewReadPort.loadCurrentPreviousPair(regionId);
        if (pair == null || pair.current() == null || pair.previous() == null) return List.of();

        // 캐시 computedAt: 가능하면 "발표 시각(reportTime)"을 사용
        LocalDateTime rt = pair.current().reportTime();
        LocalDateTime computedAt = (rt != null)
                ? TimeUtil.truncateToMinutes(rt)
                : effectiveNow;

        List<RainOnsetEventComputer.Hit> hits = computer.detect(pair);
        if (hits == null || hits.isEmpty()) return List.of();

        ArrayList<AlertEvent> events = new ArrayList<>(hits.size());
        for (RainOnsetEventComputer.Hit h : hits) {
            RainOnsetPayload payload = new RainOnsetPayload(
                    RuleId.RAIN_ONSET_CHANGE.id(),
                    h.validAt(),
                    h.pop()
            );
            events.add(new AlertEvent(AlertTypeEnum.RAIN_ONSET, regionId, computedAt, payload));
        }

        Integer maxHour = (criteria == null) ? null : criteria.rainHourLimit();
        List<AlertEvent> adjusted = windowAdjuster.adjust(events, effectiveNow, maxHour);

        return (adjusted == null || adjusted.isEmpty()) ? List.of() : List.copyOf(adjusted);
    }
}