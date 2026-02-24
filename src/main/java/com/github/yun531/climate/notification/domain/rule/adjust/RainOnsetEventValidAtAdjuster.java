package com.github.yun531.climate.notification.domain.rule.adjust;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.List;

public class RainOnsetEventValidAtAdjuster extends ValidAtEventAdjuster {

    public RainOnsetEventValidAtAdjuster(int windowHours) {
        super(windowHours);
    }

    public List<AlertEvent> adjust(List<AlertEvent> events, LocalDateTime now, @Nullable Integer hourLimitInclusive) {
        return super.adjust(events, now, hourLimitInclusive);
    }
}