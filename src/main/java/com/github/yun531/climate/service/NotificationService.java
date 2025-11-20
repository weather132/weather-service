package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.WarningKind;
import com.github.yun531.climate.dto.WarningLevel;
import com.github.yun531.climate.service.rule.AlertEvent;
import com.github.yun531.climate.service.rule.AlertRule;
import com.github.yun531.climate.service.rule.AlertTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<AlertRule> rules; // @Component 룰 자동 주입 (RAIN_ONSET, WARNING_ISSUED 등)

    /** default: RAIN_ONSET만 제공 */
    public List<AlertEvent> generate(List<Integer> regionIds,
                                 boolean receiveWarnings,
                                 @Nullable LocalDateTime since) {

        LocalDateTime effectiveSince = resolveSince(since);
        Set<AlertTypeEnum> enabled   = defaultEnabledTypes(receiveWarnings);

        return generateInternal(regionIds, enabled, effectiveSince);
    }

    public List<AlertEvent> generate(List<Integer> regionIds,
                                 @Nullable Set<AlertTypeEnum> enabledTypes,
                                 @Nullable LocalDateTime since) {

        LocalDateTime effectiveSince = resolveSince(since);
        Set<AlertTypeEnum> enabled   = normalizeEnabledTypes(enabledTypes);

        return generateInternal(regionIds, enabled, effectiveSince);
    }

    private List<AlertEvent> generateInternal(List<Integer> regionIds,
                                          Set<AlertTypeEnum> enabledTypes,
                                          LocalDateTime since) {

        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        List<Integer> targetRegions = limitRegions(regionIds);
        List<AlertEvent> events     = collectEvents(targetRegions, enabledTypes, since);
        List<AlertEvent> deduped    = deduplicate(events);
        sortEvents(deduped);

        return deduped;
    }

    /** since 가 null이면 현재 시각을 사용 */
    private LocalDateTime resolveSince(@Nullable LocalDateTime since) {
        return (since != null) ? since : LocalDateTime.now();
    }

    /** receiveWarnings 플래그 기반 기본 타입 셋 */
    private Set<AlertTypeEnum> defaultEnabledTypes(boolean receiveWarnings) {
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        if (receiveWarnings) {
            enabled.add(AlertTypeEnum.WARNING_ISSUED);
        }
        return enabled;
    }

    /** null/빈 값이면 기본(RAIN_ONSET)으로 교체, 아니면 복사 */
    private Set<AlertTypeEnum> normalizeEnabledTypes(@Nullable Set<AlertTypeEnum> enabledTypes) {
        if (enabledTypes == null || enabledTypes.isEmpty()) {
            return EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        }
        return EnumSet.copyOf(enabledTypes);
    }

    /** 지역 최대 3개 제한 */
    private List<Integer> limitRegions(List<Integer> regionIds) {
        if (regionIds.size() <= 3) {
            return regionIds;
        }
        return regionIds.subList(0, 3);
    }

    /** 룰 실행 / 이벤트 수집 */
    private List<AlertEvent> collectEvents(List<Integer> targetRegions,
                                           Set<AlertTypeEnum> enabledTypes,
                                           LocalDateTime since) {

        return rules.stream()
                .filter(r -> enabledTypes.contains(r.supports()))
                .flatMap(r -> r.evaluate(targetRegions, since).stream())
                .collect(Collectors.toList());
    }

    /** 중복 제거 / 정렬 / 문자열 변환 */
    private List<AlertEvent> deduplicate(List<AlertEvent> events) {
        Map<String, AlertEvent> map = new LinkedHashMap<>();
        for (AlertEvent event : events) {
            String key = keyOf(event);
            map.putIfAbsent(key, event);
        }
        return new ArrayList<>(map.values());
    }

    /** <type>|<regionId>|<occurredAt>|<payLoad> 형태로 생성 */
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

        // 키 이름 정렬 → 불규칙한 Map 순서 문제 해결
        return payload.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + stringify(e.getValue()))
                .reduce((a, b) -> a + "," + b)
                .orElse("-");
    }

    private String stringify(Object v) {
        if (v == null) return "null";
        return String.valueOf(v);
    }

    private void sortEvents(List<AlertEvent> events) {
        events.sort(Comparator
                .comparing(AlertEvent::type,
                        Comparator.nullsLast(Comparator.comparingInt(Enum::ordinal)))
                .thenComparing(AlertEvent::regionId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(event -> event.type().name())
                .thenComparing(AlertEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())));
    }


//    /** 포맷팅 부분: 타입별 */
//    private List<String> toMessages(List<AlertEvent> events) {
//        return events.stream()
//                .map(this::format)
//                .toList();
//    }
//
//    private String format(AlertEvent event) {
//        return switch (event.type()) {
//            case RAIN_ONSET   -> formatRainOnset(event);
//            case WARNING_ISSUED -> formatWarningIssued(event);
//            case RAIN_FORECAST  -> formatRainForecast(event);
//            default              -> formatDefault(event);
//        };
//    }
//
//    private String formatRainOnset(AlertEvent event) {
//        String ts = formatTimestamp(event);
//
//        Object hourObj = event.payload().get("hour");
//        Object popObj  = event.payload().get("pop");
//        String hourStr = hourObj == null ? "?" : hourObj.toString();
//        String popStr  = popObj == null ? "?" : popObj.toString();
//
//        return "지역 %d | %s | %s시 비 시작 (POP %s%%)"
//                .formatted(event.regionId(), ts, hourStr, popStr);
//    }
//
//    private String formatWarningIssued(AlertEvent event) {
//        String ts = formatTimestamp(event);
//
//        Object kindObj  = event.payload().get("kind");
//        Object levelObj = event.payload().get("level");
//
//        String kindLabel  = (kindObj instanceof WarningKind k) ? k.getLabel() : String.valueOf(kindObj);
//        String levelLabel = (levelObj instanceof WarningLevel l) ? l.getLabel() : String.valueOf(levelObj);
//
//        return "지역 %d | %s | %s %s 발효"
//                .formatted(event.regionId(), ts, kindLabel, levelLabel);
//    }
//
//    private String formatRainForecast(AlertEvent event) {
//        String hourlyJoined = joinParts(event.payload().get("hourlyParts"));
//        String dayJoined    = joinParts(event.payload().get("dayParts"));
//
//        if (!hourlyJoined.isEmpty() || !dayJoined.isEmpty()) {
//            List<String> pieces = new ArrayList<>(2);
//            if (!hourlyJoined.isEmpty()) pieces.add(hourlyJoined);
//            if (!dayJoined.isEmpty())    pieces.add(dayJoined);
//
//            String body = String.join(", ", pieces) + " 비 예보";
//            return "지역 %d | %s".formatted(event.regionId(), body);
//        }
//
//        Object text = event.payload().get("text");
//        if (text != null && !text.toString().isBlank()) {
//            return "지역 %d | %s".formatted(event.regionId(), text.toString().trim());
//        }
//
//        return "지역 %d | 비 예보 없음".formatted(event.regionId());
//    }
//
//    private String formatDefault(AlertEvent event) {
//        String ts = formatTimestamp(event);
//        String typeName = (event.type() == null) ? "UNKNOWN" : event.type().name();
//        return "지역 %d | %s | %s"
//                .formatted(event.regionId(), ts, typeName);
//    }
//
//    private String formatTimestamp(AlertEvent event) {
//        if (event.occurredAt() == null) {
//            return "";
//        }
//        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
//                .withZone(ZoneId.of("Asia/Seoul"))
//                .format(event.occurredAt());
//    }
//
//    /** payload에서 리스트 계열(hourlyParts/dayParts 등)을 안전하게 조인 */
//    @SuppressWarnings("unchecked")
//    private String joinParts(Object raw) {
//        if (raw == null) return "";
//        if (raw instanceof String s) return s.trim();
//
//        if (raw instanceof Collection<?> col) {
//            List<String> items = new ArrayList<>(col.size());
//            for (Object o : col) {
//                if (o == null) continue;
//                String s = o.toString().trim();
//                if (!s.isEmpty()) items.add(s);
//            }
//            return String.join(", ", items);
//        }
//
//        return raw.toString().trim();
//    }
}
