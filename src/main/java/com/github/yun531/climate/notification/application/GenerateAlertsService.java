package com.github.yun531.climate.notification.application;

import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.port.WarningStateReadPort;
import com.github.yun531.climate.notification.domain.adjust.RainForecastAdjuster;
import com.github.yun531.climate.notification.domain.adjust.RainOnsetAdjuster;
import com.github.yun531.climate.notification.domain.compute.RainForecastComputer;
import com.github.yun531.climate.notification.domain.compute.RainOnsetComputer;
import com.github.yun531.climate.notification.domain.compute.WarningIssuedComputer;
import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import com.github.yun531.climate.shared.time.TimeUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.*;

import static com.github.yun531.climate.shared.time.TimeUtil.nowTruncatedToMinute;

/**
 * 흐름: 정규화 → 타입별 분기 → Port 로드 → Computer 계산 → Adjuster 후처리 → dedup/sort
 */
public class GenerateAlertsService {

    private final PopViewReadPort popViewReadPort;
    private final WarningStateReadPort warningStateReadPort;
    private final RainOnsetComputer rainOnsetComputer;
    private final RainForecastComputer rainForecastComputer;
    private final WarningIssuedComputer warningIssuedComputer;
    private final RainOnsetAdjuster rainOnsetAdjuster;
    private final RainForecastAdjuster rainForecastAdjuster;
    private final int maxRegionCount;
    private final int defaultSinceHours;

    private static final Comparator<AlertEvent> EVENT_ORDER = Comparator
            .comparing(AlertEvent::type, Comparator.nullsLast(Comparator.comparingInt(Enum::ordinal)))
            .thenComparing(AlertEvent::regionId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AlertEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()));

    public GenerateAlertsService(
            PopViewReadPort popViewReadPort,
            WarningStateReadPort warningStateReadPort,
            RainOnsetComputer rainOnsetComputer,
            RainForecastComputer rainForecastComputer,
            WarningIssuedComputer warningIssuedComputer,
            RainOnsetAdjuster rainOnsetAdjuster,
            RainForecastAdjuster rainForecastAdjuster,
            int maxRegionCount,
            int defaultSinceHours
    ) {
        this.popViewReadPort = popViewReadPort;
        this.warningStateReadPort = warningStateReadPort;
        this.rainOnsetComputer = rainOnsetComputer;
        this.rainForecastComputer = rainForecastComputer;
        this.warningIssuedComputer = warningIssuedComputer;
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
        PopView.Pair pair = popViewReadPort.loadCurrentPreviousPair(regionId);
        if (pair == null) return List.of();

        List<AlertEvent> raw = rainOnsetComputer.compute(regionId, pair, now);
        if (raw.isEmpty()) return List.of();

        return rainOnsetAdjuster.adjust(raw, now, rainHourLimit);
    }

    // -- RAIN_FORECAST: load current → compute → adjust (time shift + clipping) --

    private List<AlertEvent> evalRainForecast(String regionId, LocalDateTime now) {
        PopView view = popViewReadPort.loadCurrent(regionId);
        if (view == null) return List.of();

        AlertEvent raw = rainForecastComputer.compute(regionId, view, now);
        if (raw == null) return List.of();

        AlertEvent adjusted = rainForecastAdjuster.adjust(raw, raw.occurredAt(), now);
        return (adjusted == null) ? List.of() : List.of(adjusted);
    }

    // -- WARNING_ISSUED: load states → compute --

    private List<AlertEvent> evalWarning(
            String regionId, LocalDateTime since,
            @Nullable Set<WarningKind> warningKinds, LocalDateTime now
    ) {
        var states = warningStateReadPort.loadLatestByKind(regionId);
        if (states == null || states.isEmpty()) return List.of();

        return warningIssuedComputer.compute(regionId, states, since, warningKinds, now);
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