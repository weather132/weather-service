package com.github.yun531.climate.service;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.domain.WarningLevel;
import com.github.yun531.climate.service.rule.AlertEvent;
import com.github.yun531.climate.service.rule.AlertRule;
import com.github.yun531.climate.service.rule.AlertTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationGeneratorService {

    private final List<AlertRule> rules; // @Component 룰 자동 주입 (RAIN_ONSET, WARNING_ISSUED 등)

    private static final String PAYLOAD_SRC_RULE = "_srcRule";

    /**
     * 간편 버전: 특보 알림 포함 여부로 룰 선택
     */
    public List<String> generate(List<Long> regionIds,
                                 boolean receiveWarnings,
                                 @Nullable Instant since) {

        Instant effectiveSince = (since != null) ? since : Instant.now().minus(90, ChronoUnit.MINUTES);
        Set<AlertTypeEnum> enabled = EnumSet.of(AlertTypeEnum.RAIN_ONSET);
        if (receiveWarnings)
            enabled.add(AlertTypeEnum.WARNING_ISSUED);

        return generate(regionIds, enabled, effectiveSince, Locale.KOREA);
    }
    public List<String> generate(List<Long> regionIds,
                                 @Nullable Set<AlertTypeEnum> enabledTypes,
                                 @Nullable Instant since) {

        Instant effectiveSince = (since != null) ? since : Instant.now().minus(90, ChronoUnit.MINUTES);
        return generate(regionIds, enabledTypes, effectiveSince, Locale.KOREA);
    }

    /**
     * 고급 버전: 실행할 룰 타입 지정
     */
    public List<String> generate(List<Long> regionIds,
                                 @Nullable Set<AlertTypeEnum> enabledTypes,
                                 @Nullable Instant since,
                                 Locale locale) {

        if (regionIds == null || regionIds.isEmpty())
            return List.of();

        // 지역 최대 3개 제한 (초과 시 앞에서 3개만 사용)
        List<Long> targetRegions = regionIds.size() > 3 ? regionIds.subList(0, 3) : regionIds;

        // 실행할 타입 결정 (기본은 RAIN_ONSET만)
        final Set<AlertTypeEnum> selected = (enabledTypes == null || enabledTypes.isEmpty())
                ? EnumSet.of(AlertTypeEnum.RAIN_ONSET)
                : EnumSet.copyOf(enabledTypes);

        // 룰 실행
        List<AlertEvent> events = rules.stream()
                .filter(r -> selected.contains(r.supports()))
                .flatMap(r -> r.evaluate(targetRegions, since).stream())
                .collect(Collectors.toList());

        // 중복 제거 (type|region|occurredAt 기준)
        List<AlertEvent> deduped = deduplicate(events);

        // 정렬 (지역, 타입명, 발생시각 asc)
        deduped.sort(Comparator
                .comparing(AlertEvent::type,
                        Comparator.nullsLast(Comparator.comparingInt(Enum::ordinal)))
                .thenComparing(AlertEvent::regionId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> e.type().name())
                .thenComparing(AlertEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())));



        // 문자열 변환
        return deduped.stream()
                .map(e -> format(e, locale))
                .toList();
    }

    private List<AlertEvent> deduplicate(List<AlertEvent> events) {
        Map<String, AlertEvent> map = new LinkedHashMap<>();
        for (AlertEvent e : events) {
            String key = keyOf(e);
            map.putIfAbsent(key, e);
        }
        return new ArrayList<>(map.values());
    }

    /** <type>|<regionId>|<occurredAt> 형태로 생성 */
    private String keyOf(AlertEvent e) {
        return (e.type() == null ? "?" : e.type().name())
                + "|" + (e.regionId() == null ? "?" : e.regionId())
                + "|" + (e.occurredAt() == null ? "?" : e.occurredAt().toString());
    }

    /** 간단 문자열 포맷터 (필요 시 외부 클래스로 분리 가능) */
    private String format(AlertEvent e, Locale locale) {
        String ts = e.occurredAt() == null ? ""
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withLocale(locale)
                .withZone(ZoneId.of("Asia/Seoul"))
                .format(e.occurredAt());

        switch (e.type()) {
            case RAIN_ONSET -> {
                Object hourObj = e.payload().get("hour");
                Object popObj  = e.payload().get("pop");
                String hourStr = hourObj == null ? "?" : hourObj.toString();
                String popStr  = popObj == null ? "?" : popObj.toString();
                return "지역 %d | %s | %s시 비 시작 (POP %s%%)"
                        .formatted(e.regionId(), ts, hourStr, popStr);
            }
            case WARNING_ISSUED -> {
                Object kindObj = e.payload().get("kind");
                Object levelObj = e.payload().get("level");

                String kindLabel = kindObj instanceof WarningKind k ? k.getLabel() : String.valueOf(kindObj);
                String levelLabel = levelObj instanceof WarningLevel l ? l.getLabel() : String.valueOf(levelObj);

                return "지역 %d | %s | %s %s 발효"
                        .formatted(e.regionId(), ts, kindLabel, levelLabel);
            }
            default -> {
                return "지역 %d | %s | %s"
                        .formatted(e.regionId(), ts, e.type() == null ? "UNKNOWN" : e.type().name());
            }
        }
    }
}
