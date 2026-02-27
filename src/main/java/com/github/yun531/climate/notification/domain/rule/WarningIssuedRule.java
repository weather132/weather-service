package com.github.yun531.climate.notification.domain.rule;

import com.github.yun531.climate.notification.domain.model.*;
import com.github.yun531.climate.notification.domain.payload.WarningIssuedPayload;
import com.github.yun531.climate.notification.domain.port.WarningStateReadPort;
import com.github.yun531.climate.notification.domain.readmodel.WarningStateView;
import com.github.yun531.climate.notification.application.command.GenerateAlertsCommand;
import com.github.yun531.climate.util.cache.CacheEntry;
import com.github.yun531.climate.util.time.TimeUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.*;

public class WarningIssuedRule
        extends AbstractCachedRegionAlertRule<Map<WarningKind, WarningStateView>> {

    private final WarningStateReadPort warningStateReadPort;

    private final int cacheTtlMinutes;        // 캐시 TTL (분)
    private final int sinceAdjustMinutes;     // 특보 발효 시각과의 시차 보정을 위한 since 보정 (분)

    public WarningIssuedRule(
            WarningStateReadPort warningStateReadPort,
            int cacheTtlMinutes,
            int sinceAdjustMinutes
    ) {
        this.warningStateReadPort = warningStateReadPort;
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

    /**
     * WarningIssuedRule은 TTL을 "지금" 기준으로 굴림
     * - sinceForCache의 since 자리에 now를 넣어 TTL 기반으로만 재계산되게 함
     */
    @Override
    protected LocalDateTime sinceForCache(GenerateAlertsCommand command, LocalDateTime now) {
        return now; // TTL을 now 기준으로만 굴림
    }

    /** 한 지역에 대한 최신 특보 상태를 로드하고 CacheEntry로 래핑 */
    @Override
    protected CacheEntry<Map<WarningKind, WarningStateView>> computeForRegion(String regionId, LocalDateTime now) {
        Map<WarningKind, WarningStateView> byKind = warningStateReadPort.loadLatestByKind(regionId);
        LocalDateTime computedAt = TimeUtil.truncateToMinutes(now);
        return new CacheEntry<>((byKind == null) ? Map.of() : byKind, computedAt);
    }

    /**
     * 캐시값(Map<kind, state>)을 AlertEvent 리스트로 변환.
     * - filterKinds 적용
     * - since는 특보 발효 시각 보정을 위해 sinceAdjustMinutes 만큼 당겨(adjust) 판단
     */
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

    /**
     * 특보 발효 시각과의 시차 보정을 위해 since를 일정 분(sinceAdjustMinutes) 당겨서 사용
     * 요청 시점에서부터 보정 분 이전까지의 특보 정보를 새로 발효된 것으로 간주
     */
    private LocalDateTime adjustSince(@Nullable LocalDateTime since) {
        if (since == null) return null;
        return since.minusMinutes(sinceAdjustMinutes); // todo: 특보알림 로직 변경시 수정해야 함
    }

    /** 필터 kind 집합이 비어 있지 않다면, 해당 kind가 포함되는지 검사 */
    private boolean matchesFilter(WarningKind kind, @Nullable Set<WarningKind> filterKinds) {
        if (filterKinds == null || filterKinds.isEmpty()) return true;
        return filterKinds.contains(kind);
    }

    /** 새로 발효된 특보인지 판단 */
    private boolean isNewWarning(@Nullable WarningStateView state, @Nullable LocalDateTime adjustedSince) {
        if (state == null) return false;

        // adjustedSince가 null 이면 “전부 새로 발효된 것으로 간주”
        if (adjustedSince == null) return true;

        return warningStateReadPort.isNewlyIssuedSince(state, adjustedSince);
    }

    /** View → AlertEvent 변환 */
    private AlertEvent toAlertEvent(String regionId, WarningStateView state, LocalDateTime now) {
        LocalDateTime occurredAt = (state.updatedAt() != null) ? state.updatedAt() : now;
        occurredAt = TimeUtil.truncateToMinutes(occurredAt);

        // Map payload 대신 typed payload
        WarningIssuedPayload payload = new WarningIssuedPayload(
                RuleId.WARNING_ISSUED.id(),
                state.kind(),   // WarningKind enum
                state.level()   // WarningLevel enum
        );

        return new AlertEvent(
                AlertTypeEnum.WARNING_ISSUED,
                regionId,
                occurredAt,
                payload
        );
    }
}