package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.WarningIssuedPayload;
import com.github.yun531.climate.shared.time.TimeUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기상특보 상태 Map -> "신규 발령" AlertEvent 목록 계산.
 * since(절대시각) 이후에 발령/갱신된 특보만 알림 대상으로 판정한다.
 * since는 Service가 sinceHours -> LocalDateTime 으로 변환해서 전달한다.
 */
public class WarningIssuedDetector {

    /** warningsByKind 에서 since 이후 발령된 특보를 AlertEvent로 변환 */
    public List<AlertEvent> detect(
            String regionId,
            Map<WarningKind, IssuedWarning> warningsByKind,
            @Nullable LocalDateTime since,
            @Nullable Set<WarningKind> warningKinds
    ) {
        if (regionId == null || regionId.isBlank()) return List.of();
        if (warningsByKind == null || warningsByKind.isEmpty()) return List.of();

        List<AlertEvent> out = new ArrayList<>(8);

        for (IssuedWarning warning : selectCandidates(warningsByKind, warningKinds)) {
            if (isIssuedAfter(warning, since)) {
                out.add(toAlertEvent(regionId, warning));
            }
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /** warningKinds가 지정되면 해당 kind만, 없으면 전체 */
    private Iterable<IssuedWarning> selectCandidates(
            Map<WarningKind, IssuedWarning> warningsByKind,
            @Nullable Set<WarningKind> warningKinds
    ) {
        if (warningKinds == null || warningKinds.isEmpty()) return warningsByKind.values();

        List<IssuedWarning> filtered = new ArrayList<>(warningKinds.size());
        for (WarningKind kind : warningKinds) {
            if (kind == null) continue;
            IssuedWarning warning = warningsByKind.get(kind);
            if (warning != null) filtered.add(warning);
        }
        return filtered;
    }

    private boolean isIssuedAfter(IssuedWarning warning, @Nullable LocalDateTime since) {
        if (warning == null || warning.updatedAt() == null) return false;
        if (since == null) return true;
        return warning.updatedAt().isAfter(since);
    }

    private AlertEvent toAlertEvent(String regionId, IssuedWarning warning) {
        LocalDateTime occurredAt = TimeUtil.truncateToMinutes(warning.updatedAt());

        WarningIssuedPayload payload = new WarningIssuedPayload(
                AlertTypeEnum.WARNING_ISSUED, warning.kind(), warning.level());

        return new AlertEvent(AlertTypeEnum.WARNING_ISSUED, regionId, occurredAt, payload);
    }
}