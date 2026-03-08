package com.github.yun531.climate.notification.domain.evaluator;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.WarningIssuedPayload;
import com.github.yun531.climate.shared.time.TimeUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기상특보 상태 Map → "신규 발령" AlertEvent 목록 계산.
 * - since(절대시각)를 기준으로, 그 이후에 발령/갱신된 특보만 알림 대상으로 판정한다.
 * - since 값은 Service가 sinceHours(정수) → LocalDateTime 으로 변환해서 넘긴다.
 * -  특보.updatedAt > since → 알림 대상
 * -  특보.updatedAt <= since → 제외
 */
public class WarningIssuedEvaluator {

    public List<AlertEvent> compute(
            String regionId,
            Map<WarningKind, IssuedWarning> byKind,     // kind별 최신 특보 상태 (Port 에서 이미 로드된 데이터)
            LocalDateTime since,                           // 기준 시각
            Set<WarningKind> warningKinds,                 // 조회할 특보 종류 (null/empty면 전체)
            LocalDateTime now
    ) {
        if (regionId == null || regionId.isBlank()) return List.of();
        if (byKind == null || byKind.isEmpty()) return List.of();
        if (now == null) return List.of();

        Iterable<IssuedWarning> candidates = selectCandidates(byKind, warningKinds);

        ArrayList<AlertEvent> out = new ArrayList<>(8);
        for (IssuedWarning warning : candidates) {
            if (isIssuedAfter(warning, since)) {
                out.add(toAlertEvent(regionId, warning, now));
            }
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    // -- 후보 선택: warningKinds가 있으면 해당 kind만, 없으면 전체 --

    private Iterable<IssuedWarning> selectCandidates(
            Map<WarningKind, IssuedWarning> byKind, Set<WarningKind> warningKinds
    ) {
        if (warningKinds == null || warningKinds.isEmpty()) return byKind.values();

        ArrayList<IssuedWarning> filtered = new ArrayList<>(warningKinds.size());
        for (WarningKind kind : warningKinds) {
            if (kind == null) continue;
            IssuedWarning warning = byKind.get(kind);
            if (warning != null) filtered.add(warning);
        }
        return filtered;
    }

    // -- 판정: since 이후에 발령/갱신된 특보인지 --

    private boolean isIssuedAfter(IssuedWarning warning, LocalDateTime since) {
        if (warning == null || warning.updatedAt() == null) return false;
        if (since == null) return true;
        return warning.updatedAt().isAfter(since);
    }

    // -- AlertEvent 조립 --

    private AlertEvent toAlertEvent(String regionId, IssuedWarning warning, LocalDateTime now) {
        LocalDateTime occurredAt = (warning.updatedAt() != null) ? warning.updatedAt() : now;
        occurredAt = TimeUtil.truncateToMinutes(occurredAt);

        WarningIssuedPayload payload = new WarningIssuedPayload(
                AlertTypeEnum.WARNING_ISSUED, warning.kind(), warning.level()
        );

        return new AlertEvent(AlertTypeEnum.WARNING_ISSUED, regionId, occurredAt, payload);
    }
}