package com.github.yun531.climate.service.notification;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.rule.AlertRule;
import com.github.yun531.climate.util.time.TimeUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.*;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

/**
 * NotificationConfig(@Bean)에서 생성되는 서비스.
 * - 설정값(maxRegionCount)은 Config 에서 주입받아 생성자 파라미터로 전달받음
 * - payload는 AlertPayload(다형성)로 고정되어 있으므로, 중복 제거는 AlertEvent(record) equals/hashCode로 처리
 */
public class NotificationService {

    /** 룰 목록(대개 @Component로 등록된 구현체들이 주입/전달됨) */
    private final List<AlertRule> rules;

    /** 지역 최대 개수 제한(설정값) */
    private final int maxRegionCount;

    /**
     * event 정렬: 타입(ordinal) → 지역 ID → 발생시각 순
     * - (todo: 추후 타입 우선순위가 필요하면 ordinal 대신 AlertTypeEnum에 priority를 두는 방식 가능)
     */
    private static final Comparator<AlertEvent> EVENT_ORDER = Comparator
            .comparing(AlertEvent::type,
                    Comparator.nullsLast(Comparator.comparingInt(Enum::ordinal)))
            .thenComparing(AlertEvent::regionId,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AlertEvent::occurredAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));

    public NotificationService(List<AlertRule> rules, int maxRegionCount) {
        this.rules = (rules == null) ? List.of() : List.copyOf(rules);
        this.maxRegionCount = Math.max(0, maxRegionCount);
    }

    /** NotificationRequest 기반 공용 엔트리 (내부에서 now 생성) */
    public List<AlertEvent> generate(NotificationRequest request) {
        return generate(request, nowMinutes());
    }

    /** 결정적 엔트리: now를 외부에서 주입 */
    public List<AlertEvent> generate(NotificationRequest request, @Nullable LocalDateTime now) {
        if (request == null) return List.of();

        Set<AlertTypeEnum> enabledTypes = request.enabledTypes();
        if (enabledTypes == null || enabledTypes.isEmpty()) {
            return List.of(); // enabledTypes가 비어있으면 아무 것도 실행하지 않음
        }

        // 1) Request 정규화 (now/since, regionIds 등)
        LocalDateTime effectiveNow = normalizeNow(now);
        NotificationRequest normalized = normalize(request, effectiveNow);
        if (normalized.regionIds().isEmpty()) return List.of();

        // 2) 룰 실행 및 이벤트 수집
        List<AlertEvent> collected = collectEvents(normalized);
        if (collected.isEmpty()) return List.of();

        // 3) 중복 제거 및 정렬
        List<AlertEvent> deduped = deduplicate(collected);
        deduped.sort(EVENT_ORDER);

        return deduped;
    }

    /**
     * raw NotificationRequest 를 정규화한 새 Request 로 변환.
     * - since: null → now (분 단위 고정)
     * - regionIds: null/blank 제거 + trim + 순서보존 중복제거 + 최대 maxRegionCount개 제한
     */
    private NotificationRequest normalize(NotificationRequest raw, LocalDateTime now) {
        LocalDateTime effectiveSince = normalizeSince(raw.since(), now);
        List<String> targetRegions = normalizeRegionIds(raw.regionIds());

        Set<WarningKind> filterKinds = raw.filterWarningKinds();
        Integer rainHourLimit = raw.rainHourLimit();

        return new NotificationRequest(
                targetRegions,
                effectiveSince,
                raw.enabledTypes(),
                filterKinds,
                rainHourLimit
        );
    }

    private LocalDateTime normalizeNow(@Nullable LocalDateTime now) {
        return (now == null) ? nowMinutes() : TimeUtil.truncateToMinutes(now);
    }

    /** since 가 null 이면 현재(now) 시각을 사용 (분 단위 고정) */
    private LocalDateTime normalizeSince(@Nullable LocalDateTime since, LocalDateTime now) {
        return (since == null) ? now : TimeUtil.truncateToMinutes(since);
    }

    /** 지역 최대 maxRegionCount개 제한 + 값 정제(null/blank 제거, trim, 중복 제거, 순서 보존) */
    private List<String> normalizeRegionIds(@Nullable List<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return List.of();
        if (maxRegionCount == 0) return List.of();

        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String id : regionIds) {
            if (id == null) continue;
            String trimmed = id.trim();
            if (trimmed.isEmpty()) continue;

            set.add(trimmed);
            if (set.size() == maxRegionCount) break;
        }
        return List.copyOf(set);
    }

    /** 룰 실행 / 이벤트 수집 */
    private List<AlertEvent> collectEvents(NotificationRequest request) {
        Set<AlertTypeEnum> enabledTypes = request.enabledTypes();
        if (enabledTypes == null || enabledTypes.isEmpty()) return List.of();
        if (rules.isEmpty()) return List.of();

        ArrayList<AlertEvent> out = new ArrayList<>(16);

        for (AlertRule rule : rules) {
            if (rule == null) continue;
            if (!enabledTypes.contains(rule.supports())) continue;

            List<AlertEvent> produced = rule.evaluate(request);
            if (produced == null || produced.isEmpty()) continue;

            for (AlertEvent e : produced) {
                if (e != null) out.add(e);
            }
        }
        return out;
    }

    /**
     * 중복 제거
     * - AlertEvent가 record이므로 equals/hashCode가 (type, regionId, occurredAt, payload) 기준으로 자동 생성됨
     * - payload 또한 record/값 객체 기반이면 안정적으로 dedup 가능
     * - LinkedHashSet으로 "첫 등장 이벤트 유지"
     */
    private List<AlertEvent> deduplicate(List<AlertEvent> events) {
        if (events == null || events.isEmpty()) return List.of();

        LinkedHashSet<AlertEvent> set = new LinkedHashSet<>(Math.max(16, events.size()));
        for (AlertEvent e : events) {
            if (e != null) set.add(e);
        }
        return new ArrayList<>(set);
    }
}