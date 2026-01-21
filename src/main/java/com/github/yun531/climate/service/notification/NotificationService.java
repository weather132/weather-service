package com.github.yun531.climate.service.notification;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.rule.AlertRule;
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

    private final List<AlertRule> rules; // @Component 룰 자동 주입

    /**
     * NotificationRequest 기반 공용 엔트리.
     */
    public List<AlertEvent> generate(NotificationRequest request) {
        if (request == null || isEmptyRegions(request)) {
            return List.of();
        }

        // 1) Request 정규화 (since, regionIds 등)
        NotificationRequest normalized = normalize(request);
        if (normalized.enabledTypes().isEmpty()) {
            return List.of();    // enabledTypes가 비어있으면 아무 것도 실행하지 않음
        }

        // 2) 룰 실행 및 이벤트 수집
        List<AlertEvent> events = collectEvents(normalized);

        // 3) 중복 제거 및 정렬
        List<AlertEvent> deduped = deduplicate(events);
        sortEvents(deduped);

        return deduped;
    }

    private boolean isEmptyRegions(NotificationRequest request) {
        List<String> regionIds = request.regionIds();
        return regionIds == null || regionIds.isEmpty();
    }

    /**
     * raw NotificationRequest 를
     * - since: null → now
     * - regionIds: 최대 3개로 제한
     * 로 정규화한 새 Request 로 변환.
     */
    private NotificationRequest normalize(NotificationRequest raw) {
        LocalDateTime effectiveSince = sinceOrNow(raw.since());
        List<String> targetRegions = limitRegions(raw.regionIds());

        Set<WarningKind> filterKinds = raw.filterWarningKinds();
        Integer rainHourLimit = raw.rainHourLimit();

        return new NotificationRequest(
                targetRegions,
                effectiveSince,
                raw.enabledTypes(),   // 이미 non-null + EnumSet 정규화됨
                filterKinds,
                rainHourLimit
        );
    }

    /** 룰 실행 / 이벤트 수집 */
    private List<AlertEvent> collectEvents(NotificationRequest request) {
        var enabledTypes = request.enabledTypes();

        return rules.stream()
                .filter(r -> enabledTypes.contains(r.supports()))
                .flatMap(r -> {
                    List<AlertEvent> out = r.evaluate(request);
                    return (out == null ? List.<AlertEvent>of() : out).stream();
                })
                .toList();
    }

    /** 중복 제거 */
    private List<AlertEvent> deduplicate(List<AlertEvent> events) {
        if (events == null || events.isEmpty()) return List.of();

        Map<String, AlertEvent> map = new LinkedHashMap<>();
        for (AlertEvent event : events) {
            if (event == null) continue;
            String key = keyOf(event);
            map.putIfAbsent(key, event);
        }
        return new ArrayList<>(map.values());
    }

    /** <type>|<regionId>|<occurredAt>|<payload> 형태로 키 생성 */
    private String keyOf(AlertEvent event) {
        String type = (event.type() == null) ? "?" : event.type().name();
        String region = String.valueOf(event.regionId());
        String ts = (event.occurredAt() == null) ? "?" : event.occurredAt().toString();

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

    /** event 정렬: 타입(ordinal) → 지역 ID → 발생시각 순 */
    private void sortEvents(List<AlertEvent> events) {
        if (events == null || events.isEmpty()) return;

        events.sort(Comparator
                .comparing(AlertEvent::type,
                        Comparator.nullsLast(Comparator.comparingInt(Enum::ordinal)))
                .thenComparing(AlertEvent::regionId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AlertEvent::occurredAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /** since 가 null 이면 현재 시각을 사용 */
    private LocalDateTime sinceOrNow(@Nullable LocalDateTime since) {
        return (since != null) ? since : nowMinutes();
    }

    /** 지역 최대 3개 제한 */
    private List<String> limitRegions(List<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return List.of();
        return regionIds.stream().limit(MAX_REGION_COUNT).toList();
    }
}