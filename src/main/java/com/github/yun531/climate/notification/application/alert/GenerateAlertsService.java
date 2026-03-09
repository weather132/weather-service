package com.github.yun531.climate.notification.application.alert;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.reader.WarningStateReader;
import com.github.yun531.climate.notification.domain.adjust.RainForecastAdjuster;
import com.github.yun531.climate.notification.domain.adjust.RainOnsetAdjuster;
import com.github.yun531.climate.notification.domain.evaluator.RainForecastEvaluator;
import com.github.yun531.climate.notification.domain.evaluator.RainOnsetEvaluator;
import com.github.yun531.climate.notification.domain.evaluator.WarningIssuedEvaluator;
import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopViewReader;
import com.github.yun531.climate.shared.time.TimeUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.*;

import static com.github.yun531.climate.shared.time.TimeUtil.nowTruncatedToMinute;

/**
 * 흐름: 정규화 → 타입별 분기 → Port 로드 → Computer 계산 → Adjuster 후처리 → dedup/sort
 */
public class GenerateAlertsService {

    private final PopViewReader popViewReader;
    private final WarningStateReader warningStateReader;
    private final RainOnsetEvaluator rainOnsetEvaluator;
    private final RainForecastEvaluator rainForecastEvaluator;
    private final WarningIssuedEvaluator warningIssuedEvaluator;
    private final RainOnsetAdjuster rainOnsetAdjuster;
    private final RainForecastAdjuster rainForecastAdjuster;
    private final int maxRegionCount;
    private final int defaultSinceHours;

    private static final Comparator<AlertEvent> EVENT_ORDER = Comparator
            .comparing(AlertEvent::type, Comparator.nullsLast(Comparator.comparingInt(Enum::ordinal)))
            .thenComparing(AlertEvent::regionId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AlertEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()));

    public GenerateAlertsService(
            PopViewReader popViewReader,
            WarningStateReader warningStateReader,
            RainOnsetEvaluator rainOnsetEvaluator,
            RainForecastEvaluator rainForecastEvaluator,
            WarningIssuedEvaluator warningIssuedEvaluator,
            RainOnsetAdjuster rainOnsetAdjuster,
            RainForecastAdjuster rainForecastAdjuster,
            int maxRegionCount,
            int defaultSinceHours
    ) {
        this.popViewReader = popViewReader;
        this.warningStateReader = warningStateReader;
        this.rainOnsetEvaluator = rainOnsetEvaluator;
        this.rainForecastEvaluator = rainForecastEvaluator;
        this.warningIssuedEvaluator = warningIssuedEvaluator;
        this.rainOnsetAdjuster = rainOnsetAdjuster;
        this.rainForecastAdjuster = rainForecastAdjuster;
        this.maxRegionCount = Math.max(0, maxRegionCount);
        this.defaultSinceHours = Math.max(1, defaultSinceHours);
    }

    public List<AlertEvent> generate(GenerateAlertsCommand command) {
        return generate(command, nowTruncatedToMinute());
    }

    public List<AlertEvent> generate(GenerateAlertsCommand command, @Nullable LocalDateTime now) {
        if (command == null) return List.of();

        Set<AlertTypeEnum> enabledTypes = command.enabledTypes();
        if (enabledTypes == null || enabledTypes.isEmpty()) return List.of();

        LocalDateTime effectiveNow = normalizeNow(now);
        LocalDateTime since = normalizeSince(command.sinceHours(), effectiveNow);
        List<String> regionIds = normalizeRegionIds(command.regionIds());
        if (regionIds.isEmpty()) return List.of();

        List<AlertEvent> collected = collectEvents(
                enabledTypes, regionIds, since,
                command.warningKinds(), command.rainHourLimit(), effectiveNow
        );
        if (collected.isEmpty()) return List.of();

        List<AlertEvent> deduped = deduplicate(collected);
        deduped.sort(EVENT_ORDER);
        return deduped;
    }

    // -- 타입별 분기 + 지역 순회 (정규화된 값만 받음) --

    private List<AlertEvent> collectEvents(
            Set<AlertTypeEnum> types, List<String> regionIds, LocalDateTime since,
            @Nullable Set<WarningKind> warningKinds, @Nullable Integer rainHourLimit,
            LocalDateTime now
    ) {
        ArrayList<AlertEvent> out = new ArrayList<>(16);

        for (String regionId : regionIds) {
            if (types.contains(AlertTypeEnum.RAIN_ONSET))
                out.addAll(evalRainOnset(regionId, rainHourLimit, now));

            if (types.contains(AlertTypeEnum.RAIN_FORECAST))
                out.addAll(evalRainForecast(regionId, now));

            if (types.contains(AlertTypeEnum.WARNING_ISSUED))
                out.addAll(evalWarning(regionId, since, warningKinds, now));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    // -- RAIN_ONSET: load pair → compute → adjust(validAt window) --

    private List<AlertEvent> evalRainOnset(
            String regionId, @Nullable Integer rainHourLimit, LocalDateTime now
    ) {
        PopView.Pair pair = popViewReader.loadCurrentPreviousPair(regionId);
        if (pair == null) return List.of();

        List<AlertEvent> raw = rainOnsetEvaluator.compute(regionId, pair, now);
        if (raw.isEmpty()) return List.of();

        return rainOnsetAdjuster.adjust(raw, now, rainHourLimit);
    }

    // -- RAIN_FORECAST: load current → compute → adjust (time shift + clipping) --

    private List<AlertEvent> evalRainForecast(String regionId, LocalDateTime now) {
        PopView view = popViewReader.loadCurrent(regionId);
        if (view == null) return List.of();

        AlertEvent raw = rainForecastEvaluator.compute(regionId, view, now);
        if (raw == null) return List.of();

        AlertEvent adjusted = rainForecastAdjuster.adjust(raw, raw.occurredAt(), now);
        return (adjusted == null) ? List.of() : List.of(adjusted);
    }

    // -- WARNING_ISSUED: load states → compute --

    private List<AlertEvent> evalWarning(
            String regionId, LocalDateTime since,
            @Nullable Set<WarningKind> warningKinds, LocalDateTime now
    ) {
        var states = warningStateReader.loadLatestByKind(regionId);
        if (states == null || states.isEmpty()) return List.of();

        return warningIssuedEvaluator.compute(regionId, states, since, warningKinds, now);
    }

    // -- 정규화 헬퍼 --

    private LocalDateTime normalizeNow(@Nullable LocalDateTime now) {
        return (now == null) ? nowTruncatedToMinute() : TimeUtil.truncateToMinutes(now);
    }

    /** sinceHours(정수) → LocalDateTime 변환. null 이면 기본값(defaultSinceHours) 적용 */
    private LocalDateTime normalizeSince(@Nullable Integer sinceHours, LocalDateTime now) {
        int hours = (sinceHours != null && sinceHours > 0) ? sinceHours : defaultSinceHours;
        return now.minusHours(hours);
    }

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

    private List<AlertEvent> deduplicate(List<AlertEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        LinkedHashSet<AlertEvent> set = new LinkedHashSet<>(Math.max(16, events.size()));
        for (AlertEvent e : events) { if (e != null) set.add(e); }
        return new ArrayList<>(set);
    }
}