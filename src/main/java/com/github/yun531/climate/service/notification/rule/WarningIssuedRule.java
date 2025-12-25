package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.query.dto.WarningStateDto;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.query.WarningStateQueryService;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.util.CacheEntry;
import com.github.yun531.climate.util.RegionCache;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Set;

import static com.github.yun531.climate.util.TimeUtil.nowMinutes;

@Component
@RequiredArgsConstructor
public class WarningIssuedRule implements AlertRule {

    private final WarningStateQueryService warningStateQueryService;

    /** 캐시 TTL (분 단위) */
    private static final int CACHE_TTL_MINUTES = 45;
    /** payload 키 상수 */
    private static final String PAYLOAD_SRC_RULE_KEY  = "_srcRule";
    private static final String PAYLOAD_SRC_RULE_NAME = "WarningIssuedRule";
    private static final String PAYLOAD_KIND_KEY      = "kind";
    private static final String PAYLOAD_LEVEL_KEY     = "level";

    /** 지역별 캐시: kind → WarningStateDto 맵 + 계산시각 */
    private final RegionCache<Map<WarningKind, WarningStateDto>> cache = new RegionCache<>();

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.WARNING_ISSUED;
    }

    @Override
    public List<AlertEvent> evaluate(NotificationRequest request) {
        List<Integer> regionIds        = request.regionIds();
        LocalDateTime since            = request.since();
        Set<WarningKind> filterKinds   = request.filterWarningKinds();

        return evaluateInternal(regionIds, filterKinds, since);
    }

    private List<AlertEvent> evaluateInternal(List<Integer> regionIds,
                                              Set<WarningKind> filterKinds,
                                              LocalDateTime since) {
        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        // 캐시 TTL 기준 시각: "지금"
        LocalDateTime cacheSince    = nowMinutes();
        // 특보 발효 시각 판정용 since (90분 보정)
        LocalDateTime adjustedSince = adjustSince(since);

        List<AlertEvent> out = new ArrayList<>();

        for (int regionId : regionIds) {
            CacheEntry<Map<WarningKind, WarningStateDto>> entry =
                    cache.getOrComputeSinceBased(
                            regionId,
                            cacheSince,          // now 기준 TTL
                            CACHE_TTL_MINUTES,
                            () -> loadLatestForRegion(regionId)
                    );

            Map<WarningKind, WarningStateDto> eventByKind = extractEvents(entry);

            collectEventsForRegion(regionId, eventByKind, filterKinds, adjustedSince, out);
        }
        return out;
    }

    /**
     * CacheEntry에서 이벤트 리스트를 꺼낸다.
     * entry가 null이거나, value(Map<WarningKind, WarningStateDto>)가 null 또는 비어 있으면 빈 리스트를 반환한다.
     */
    private Map<WarningKind, WarningStateDto> extractEvents(
            @Nullable CacheEntry<Map<WarningKind, WarningStateDto>> entry ) {

        if (entry == null || entry.value() == null || entry.value().isEmpty()) {
            return Map.of();
        }
        return entry.value();
    }

    /**
     * 특보 발효 시각과의 시차 보정을 위해 since 를 90분 당겨서 사용
     * 요청 시점에서부터 90분 이전까지의 특보 정보를 새로 발효된 것으로 간주
     */
    private LocalDateTime adjustSince(LocalDateTime since) {
        if (since == null) {
            return null;
        }
        return since.minusMinutes(90);   // todo: 특보알림 로직 변경시 수정해야 함
    }

    /** 한 지역에 대한 최신 특보 상태를 DB에서 로드하고 CacheEntry 로 래핑 */
    private CacheEntry<Map<WarningKind, WarningStateDto>> loadLatestForRegion(int regionId) {
        Map<Integer, Map<WarningKind, WarningStateDto>> latestByRegion =
                warningStateQueryService.findLatestByRegionAndKind(List.of(regionId));

        Map<WarningKind, WarningStateDto> byKind =
                latestByRegion.getOrDefault(regionId, Map.of());

        return new CacheEntry<>(byKind, nowMinutes());
    }

    // 한 지역에 대한 이벤트 수집 (필요 시 특정 kind 필터링)
    private void collectEventsForRegion(int regionId,
                                        Map<WarningKind, WarningStateDto> byKind,
                                        Set<WarningKind> filterKinds,
                                        LocalDateTime adjustedSince,
                                        List<AlertEvent> out) {
        if (byKind == null || byKind.isEmpty()) {
            return;
        }

        for (Map.Entry<WarningKind, WarningStateDto> entry : byKind.entrySet()) {
            WarningKind kind = entry.getKey();
            if (!matchesFilter(kind, filterKinds)) {
                continue;
            }

            WarningStateDto state = entry.getValue();
            if (!isNewWarning(state, adjustedSince)) {
                continue;
            }

            out.add(toAlertEvent(regionId, state));
        }
    }

    /** 필터 kind 집합이 비어 있지 않다면, 해당 kind 가 포함되는지 검사 */
    private boolean matchesFilter(WarningKind kind,
                                  @Nullable Set<WarningKind> filterKinds) {

        if (filterKinds == null || filterKinds.isEmpty()) {
            return true;
        }
        return filterKinds.contains(kind);
    }

    /** 새로 발효된 특보인지 판단 */
    private boolean isNewWarning(WarningStateDto state,
                                 LocalDateTime adjustedSince) {
        if (state == null) {
            return false;
        }
        // adjustedSince 가 null 이면 “전부 새로 발효된 것으로 간주”
        if (adjustedSince == null) {
            return true;
        }
        return warningStateQueryService.isNewlyIssuedSince(state, adjustedSince);
    }

    /** DTO → AlertEvent 변환 */
    private AlertEvent toAlertEvent(int regionId, WarningStateDto state) {
        LocalDateTime occurredAt =
                (state.getUpdatedAt() != null) ? state.getUpdatedAt() : nowMinutes();

        Map<String, Object> payload = Map.of(
                PAYLOAD_SRC_RULE_KEY,  PAYLOAD_SRC_RULE_NAME,
                PAYLOAD_KIND_KEY,      state.getKind(),   // WarningKind enum
                PAYLOAD_LEVEL_KEY,     state.getLevel()   // WarningLevel enum
        );

        return new AlertEvent(
                AlertTypeEnum.WARNING_ISSUED,
                regionId,
                occurredAt,
                payload
        );
    }

    /** 캐시 무효화 */
    public void invalidate(int regionId) { cache.invalidate(regionId); }
    public void invalidateAll() { cache.invalidateAll(); }
}