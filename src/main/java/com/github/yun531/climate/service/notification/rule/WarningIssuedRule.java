package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.query.WarningStateQueryService;
import com.github.yun531.climate.service.query.dto.WarningStateDto;
import com.github.yun531.climate.util.cache.CacheEntry;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

@Component
@RequiredArgsConstructor
public class WarningIssuedRule
        extends AbstractCachedRegionAlertRule<Map<WarningKind, WarningStateDto>> {

    private final WarningStateQueryService warningStateQueryService;

    /** 캐시 TTL (분 단위) */
    private static final int CACHE_TTL_MINUTES = 45;

    /** payload 키 상수 */
    private static final String PAYLOAD_SRC_RULE_KEY  = "_srcRule";
    private static final String PAYLOAD_SRC_RULE_NAME = "WarningIssuedRule";
    private static final String PAYLOAD_KIND_KEY      = "kind";
    private static final String PAYLOAD_LEVEL_KEY     = "level";

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.WARNING_ISSUED;
    }

    @Override
    protected int thresholdMinutes() {
        return CACHE_TTL_MINUTES;
    }

    /**
     * WarningIssuedRule은 TTL을 "지금" 기준으로 굴림 (기존 코드의 cacheSince=nowMinutes() 유지)
     * - getOrComputeSinceBased의 since 자리에 now를 넣어 TTL 기반으로만 재계산되게 함
     */
    @Override
    protected LocalDateTime sinceForCache(NotificationRequest request, LocalDateTime now) {
        return now;
    }

    /** 한 지역에 대한 최신 특보 상태를 DB 에서 로드하고 CacheEntry로 래핑 */
    @Override
    protected CacheEntry<Map<WarningKind, WarningStateDto>> computeForRegion(String regionId) {
        Map<String, Map<WarningKind, WarningStateDto>> latestByRegion =
                warningStateQueryService.findLatestByRegionAndKind(List.of(regionId));

        Map<WarningKind, WarningStateDto> byKind =
                latestByRegion.getOrDefault(regionId, Map.of());

        return new CacheEntry<>(byKind, nowMinutes());
    }

    /**
     * 캐시값(Map<kind, state>)을 AlertEvent 리스트로 변환.
     * - filterKinds 적용
     * - since는 특보 발효 시각 보정을 위해 90분 당겨(adjust) 판단 (기존 로직 유지)
     */
    @Override
    protected List<AlertEvent> buildEvents(String regionId,
                                           Map<WarningKind, WarningStateDto> byKind,
                                           @Nullable LocalDateTime computedAt,
                                           LocalDateTime now,
                                           NotificationRequest request) {

        if (byKind == null || byKind.isEmpty()) return List.of();

        Set<WarningKind> filterKinds = request.filterWarningKinds();
        LocalDateTime adjustedSince = adjustSince(request.since());

        List<AlertEvent> out = new ArrayList<>();

        for (Map.Entry<WarningKind, WarningStateDto> e : byKind.entrySet()) {
            WarningKind kind = e.getKey();
            if (!matchesFilter(kind, filterKinds)) continue;

            WarningStateDto state = e.getValue();
            if (!isNewWarning(state, adjustedSince)) continue;

            out.add(toAlertEvent(regionId, state, now));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /**
     * 특보 발효 시각과의 시차 보정을 위해 since를 90분 당겨서 사용
     * 요청 시점에서부터 90분 이전까지의 특보 정보를 새로 발효된 것으로 간주
     */
    private LocalDateTime adjustSince(LocalDateTime since) {
        if (since == null) return null;
        return since.minusMinutes(90); // todo: 특보알림 로직 변경시 수정해야 함
    }

    /** 필터 kind 집합이 비어 있지 않다면, 해당 kind가 포함되는지 검사 */
    private boolean matchesFilter(WarningKind kind,
                                  @Nullable Set<WarningKind> filterKinds) {
        if (filterKinds == null || filterKinds.isEmpty()) return true;
        return filterKinds.contains(kind);
    }

    /** 새로 발효된 특보인지 판단 */
    private boolean isNewWarning(WarningStateDto state,
                                 LocalDateTime adjustedSince) {
        if (state == null) return false;

        // adjustedSince가 null 이면 “전부 새로 발효된 것으로 간주”
        if (adjustedSince == null) return true;

        return warningStateQueryService.isNewlyIssuedSince(state, adjustedSince);
    }

    /** DTO → AlertEvent 변환 */
    private AlertEvent toAlertEvent(String regionId, WarningStateDto state, LocalDateTime now) {
        LocalDateTime occurredAt =
                (state.updatedAt() != null) ? state.updatedAt() : now;

        Map<String, Object> payload = Map.of(
                PAYLOAD_SRC_RULE_KEY,  PAYLOAD_SRC_RULE_NAME,
                PAYLOAD_KIND_KEY,      state.kind(),   // WarningKind enum
                PAYLOAD_LEVEL_KEY,     state.level()   // WarningLevel enum
        );

        return new AlertEvent(
                AlertTypeEnum.WARNING_ISSUED,
                regionId,
                occurredAt,
                payload
        );
    }
}