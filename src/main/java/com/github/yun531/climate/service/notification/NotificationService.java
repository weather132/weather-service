package com.github.yun531.climate.service.notification;

import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.rule.AlertRule;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_REGION_COUNT = 3;
    private static final Set<AlertTypeEnum> DEFAULT_ENABLED_TYPES =
            EnumSet.of(AlertTypeEnum.RAIN_ONSET);

    private final List<AlertRule> rules; // @Component 룰 자동 주입

    /**
     * NotificationRequest 기반 공용 엔트리.
     */
    public List<AlertEvent> generate(NotificationRequest request) {
        if (request == null || isEmptyRegions(request)) {
            return List.of();
        }

        // 1) Request 정규화 (since, enabledTypes, regionIds 등)
        NotificationRequest normalized = normalize(request);
        // 2) 룰 실행 및 이벤트 수집
        List<AlertEvent> events = collectEvents(normalized);
        // 3) 중복 제거 및 정렬
        List<AlertEvent> deduped = deduplicate(events);
        sortEvents(deduped);

        return deduped;
    }

    private boolean isEmptyRegions(NotificationRequest request) {
        List<Integer> regionIds = request.regionIds();
        return regionIds == null || regionIds.isEmpty();
    }

    /**
     * raw NotificationRequest 를
     * - since: null → now
     * - enabledTypes: null/empty → 기본값
     * - regionIds: 최대 3개로 제한
     * 으로 정규화한 새 Request 로 변환.
     */
    private NotificationRequest normalize(NotificationRequest raw) {
        LocalDateTime effectiveSince    = sinceOrNow(raw.since());
        Set<AlertTypeEnum> enabled      = normalizeEnabledTypes(raw.enabledTypes());
        List<Integer> targetRegions     = limitRegions(raw.regionIds());
        Set<WarningKind> filterKinds    = raw.filterWarningKinds();
        Integer rainHourLimit           = raw.rainHourLimit();

        return new NotificationRequest(
                targetRegions,
                effectiveSince,
                enabled,
                filterKinds,
                rainHourLimit
        );
    }

    /** 룰 실행 / 이벤트 수집 */
    private List<AlertEvent> collectEvents(NotificationRequest request) {
        Set<AlertTypeEnum> enabledTypes = request.enabledTypes();

        return rules.stream()
                .filter(r -> enabledTypes.contains(r.supports()))
                .flatMap(r -> r.evaluate(request).stream())
                .toList();
    }

    /** 중복 제거 */
    private List<AlertEvent> deduplicate(List<AlertEvent> events) {
        Map<String, AlertEvent> map = new LinkedHashMap<>();
        for (AlertEvent event : events) {
            String key = keyOf(event);
            map.putIfAbsent(key, event);
        }
        return new ArrayList<>(map.values());
    }

    /** <type>|<regionId>|<occurredAt>|<payload> 형태로 키 생성 */
    private String keyOf(AlertEvent event) {
        String type   = (event.type() == null) ?
                            "?" : event.type().name();
        String region = String.valueOf(event.regionId());
        String ts     = (event.occurredAt() == null) ?
                            "?" : event.occurredAt().toString();

        String payloadKey = normalizePayload(event.payload());
        return type + "|" + region + "|" + ts + "|" + payloadKey;
    }

    private String normalizePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "-";
        }

        return payload.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + stringify(e.getValue()))
                .collect(Collectors.joining(","));
    }

    private String stringify(@Nullable Object v) {
        return String.valueOf(v);
    }

    /** event 정렬: 타입(ordinal) → 지역ID → 타입 이름 → 발생시각 순 */
    private void sortEvents(List<AlertEvent> events) {
        events.sort(Comparator
                .comparing(AlertEvent::type,
                        Comparator.nullsLast(Comparator.comparingInt(Enum::ordinal)))
                .thenComparing(AlertEvent::regionId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AlertEvent::occurredAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /** since 가 null이면 현재 시각을 사용 */
    private LocalDateTime sinceOrNow(@Nullable LocalDateTime since) {
        return (since != null) ? since : nowMinutes();
    }

    /** null/빈 값이면 기본(RAIN_ONSET)으로 교체, 아니면 복사 */
    private Set<AlertTypeEnum> normalizeEnabledTypes(@Nullable Set<AlertTypeEnum> enabledTypes) {
        if (enabledTypes == null || enabledTypes.isEmpty()) {
            return EnumSet.copyOf(DEFAULT_ENABLED_TYPES);
        }
        return EnumSet.copyOf(enabledTypes);
    }

    /** 지역 최대 3개 제한 */
    private List<Integer> limitRegions(List<Integer> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }
        return regionIds.stream()
                .limit(MAX_REGION_COUNT)
                .toList();
    }
}
