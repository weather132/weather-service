package com.github.yun531.climate.notification.domain.rule;

import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.port.WarningIssuedJudgePort;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.model.RuleId;
import com.github.yun531.climate.notification.domain.payload.WarningIssuedPayload;
import com.github.yun531.climate.notification.domain.rule.criteria.AlertCriteria;
import com.github.yun531.climate.shared.time.TimeUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WarningIssuedRule implements AlertRule {

    private final WarningStateReadPort warningStateReadPort;
    private final WarningIssuedJudgePort warningIssuedJudgePort;

    /** since 판단을 완화하기 위한 보정(분) */
    private final int sinceAdjustMinutes;

    public WarningIssuedRule(
            WarningStateReadPort warningStateReadPort,
            WarningIssuedJudgePort warningIssuedJudgePort,
            int sinceAdjustMinutes
    ) {
        this.warningStateReadPort = warningStateReadPort;
        this.warningIssuedJudgePort = warningIssuedJudgePort;
        this.sinceAdjustMinutes = Math.max(0, sinceAdjustMinutes);
    }

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.WARNING_ISSUED;
    }

    @Override
    public List<AlertEvent> evaluate(String regionId, AlertCriteria criteria, LocalDateTime now) {
        if (regionId == null || regionId.isBlank()) return List.of();

        // 기준 시각(분 단위 고정)
        LocalDateTime effectiveNow = (now == null)
                ? TimeUtil.nowMinutes()
                : TimeUtil.truncateToMinutes(now);

        // criteria 정규화
        Set<WarningKind> filterKinds = (criteria == null) ? null : criteria.filterWarningKinds();
        LocalDateTime adjustedSince = adjustSince((criteria == null) ? null : criteria.since());

        // 포트 조회 (TTL 캐시가 포트 데코레이터에서 적용됨)
        Map<WarningKind, WarningStateView> byKind = warningStateReadPort.loadLatestByKind(regionId);
        if (byKind == null || byKind.isEmpty()) return List.of();

        ArrayList<AlertEvent> out = new ArrayList<>(Math.min(8, byKind.size()));

        // filterKinds가 있으면 해당 kind만 조회 (전체 순회 방지)
        if (filterKinds != null && !filterKinds.isEmpty()) {
            for (WarningKind kind : filterKinds) {
                if (kind == null) continue;

                WarningStateView state = byKind.get(kind);
                if (!isNewWarning(state, adjustedSince)) continue;

                out.add(toAlertEvent(regionId, state, effectiveNow));
            }
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }

        // 필터가 없으면 전체 순회
        for (Map.Entry<WarningKind, WarningStateView> e : byKind.entrySet()) {
            if (e == null) continue;

            WarningStateView state = e.getValue();
            if (!isNewWarning(state, adjustedSince)) continue;

            out.add(toAlertEvent(regionId, state, effectiveNow));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private LocalDateTime adjustSince(LocalDateTime since) {
        if (since == null) return null;
        return TimeUtil.truncateToMinutes(since.minusMinutes(sinceAdjustMinutes));
    }

    private boolean isNewWarning(WarningStateView state, LocalDateTime adjustedSince) {
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