package com.github.yun531.climate.notification.domain.rule;

import com.github.yun531.climate.notification.application.command.GenerateAlertsCommand;
import com.github.yun531.climate.notification.domain.model.*;
import com.github.yun531.climate.notification.domain.payload.WarningIssuedPayload;
import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.kernel.warning.port.WarningIssuedJudgePort;
import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.time.TimeUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.*;

public class WarningIssuedRule
        extends AbstractCachedRegionAlertRule<Map<WarningKind, WarningStateView>> {

    private final WarningStateReadPort warningStateReadPort;
    private final WarningIssuedJudgePort warningIssuedJudgePort;

    private final int cacheTtlMinutes;
    private final int sinceAdjustMinutes;

    public WarningIssuedRule(
            WarningStateReadPort warningStateReadPort,
            WarningIssuedJudgePort warningIssuedJudgePort,
            int cacheTtlMinutes,
            int sinceAdjustMinutes
    ) {
        this.warningStateReadPort = warningStateReadPort;
        this.warningIssuedJudgePort = warningIssuedJudgePort;
        this.cacheTtlMinutes = Math.max(0, cacheTtlMinutes);
        this.sinceAdjustMinutes = Math.max(0, sinceAdjustMinutes);
    }

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.WARNING_ISSUED;
    }

    @Override
    protected int thresholdMinutes() {
        return cacheTtlMinutes;
    }

    @Override
    protected LocalDateTime sinceForCache(GenerateAlertsCommand command, LocalDateTime now) {
        return now;
    }

    @Override
    protected CacheEntry<Map<WarningKind, WarningStateView>> computeForRegion(String regionId, LocalDateTime now) {
        Map<WarningKind, WarningStateView> byKind = warningStateReadPort.loadLatestByKind(regionId);
        LocalDateTime computedAt = TimeUtil.truncateToMinutes(now);
        return new CacheEntry<>((byKind == null) ? Map.of() : byKind, computedAt);
    }

    @Override
    protected List<AlertEvent> buildEvents(
            String regionId,
            Map<WarningKind, WarningStateView> byKind,
            @Nullable LocalDateTime computedAt,
            LocalDateTime now,
            GenerateAlertsCommand command
    ) {
        if (byKind == null || byKind.isEmpty()) return List.of();

        Set<WarningKind> filterKinds = command.filterWarningKinds();
        LocalDateTime adjustedSince = adjustSince(command.since());

        ArrayList<AlertEvent> out = new ArrayList<>(byKind.size());

        for (Map.Entry<WarningKind, WarningStateView> e : byKind.entrySet()) {
            WarningKind kind = e.getKey();
            if (!matchesFilter(kind, filterKinds)) continue;

            WarningStateView state = e.getValue();
            if (!isNewWarning(state, adjustedSince)) continue;

            out.add(toAlertEvent(regionId, state, now));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private LocalDateTime adjustSince(@Nullable LocalDateTime since) {
        if (since == null) return null;
        return since.minusMinutes(sinceAdjustMinutes);
    }

    private boolean matchesFilter(WarningKind kind, @Nullable Set<WarningKind> filterKinds) {
        if (filterKinds == null || filterKinds.isEmpty()) return true;
        return filterKinds.contains(kind);
    }

    private boolean isNewWarning(@Nullable WarningStateView state, @Nullable LocalDateTime adjustedSince) {
        if (state == null) return false;
        if (adjustedSince == null) return true;

        return warningIssuedJudgePort.isNewlyIssuedSince(state, adjustedSince);
    }

    private AlertEvent toAlertEvent(String regionId, WarningStateView state, LocalDateTime now) {
        LocalDateTime occurredAt = (state.updatedAt() != null) ? state.updatedAt() : now;
        occurredAt = TimeUtil.truncateToMinutes(occurredAt);

        WarningIssuedPayload payload = new WarningIssuedPayload(
                RuleId.WARNING_ISSUED.id(),
                state.kind(),
                state.level()
        );

        return new AlertEvent(
                AlertTypeEnum.WARNING_ISSUED,
                regionId,
                occurredAt,
                payload
        );
    }
}