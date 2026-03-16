package com.github.yun531.climate.notification.application.alert;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.reader.WarningStateReader;
import com.github.yun531.climate.notification.domain.adjust.RainForecastAdjuster;
import com.github.yun531.climate.notification.domain.adjust.RainOnsetAdjuster;
import com.github.yun531.climate.notification.domain.detect.RainForecastDetector;
import com.github.yun531.climate.notification.domain.detect.RainOnsetDetector;
import com.github.yun531.climate.notification.domain.detect.WarningIssuedDetector;
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
 * 알림 생성 서비스.
 * 흐름: 정규화 -> 타입별 분기 -> Port 로드 -> Detector 감지 -> Adjuster 보정 -> dedup/sort
 */
public class GenerateAlertsService {

    private final PopViewReader popViewReader;
    private final WarningStateReader warningStateReader;
    private final RainOnsetDetector rainOnsetDetector;
    private final RainForecastDetector rainForecastDetector;
    private final WarningIssuedDetector warningIssuedDetector;
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
            RainOnsetDetector rainOnsetDetector,
            RainForecastDetector rainForecastDetector,
            WarningIssuedDetector warningIssuedDetector,
            RainOnsetAdjuster rainOnsetAdjuster,
            RainForecastAdjuster rainForecastAdjuster,
            int maxRegionCount,
            int defaultSinceHours
    ) {
        this.popViewReader = popViewReader;
        this.warningStateReader = warningStateReader;
        this.rainOnsetDetector = rainOnsetDetector;
        this.rainForecastDetector = rainForecastDetector;
        this.warningIssuedDetector = warningIssuedDetector;
        this.rainOnsetAdjuster = rainOnsetAdjuster;
        this.rainForecastAdjuster = rainForecastAdjuster;
        this.maxRegionCount = Math.max(0, maxRegionCount);
        this.defaultSinceHours = Math.max(1, defaultSinceHours);
    }

    public List<AlertEvent> generate(GenerateAlertsCommand command) {
        return generate(command, nowTruncatedToMinute());
    }

    public List<AlertEvent> generate(GenerateAlertsCommand command, @Nullable LocalDateTime now) {
        if (command == null || command.hasNoTypes()) return List.of();

        Set<AlertTypeEnum> enabledTypes = command.enabledTypes();
        if (enabledTypes == null || enabledTypes.isEmpty()) return List.of();

        LocalDateTime effectiveNow = normalizeNow(now);
        LocalDateTime since = normalizeSince(command.sinceHours(), effectiveNow);
        List<String> regionIds = normalizeRegionIds(command.regionIds());
        if (regionIds.isEmpty()) return List.of();

        List<AlertEvent> collected = collectEvents(command, regionIds, since, effectiveNow);
        if (collected.isEmpty()) return List.of();

        List<AlertEvent> deduped = deduplicate(collected);
        deduped.sort(EVENT_ORDER);
        return deduped;
    }

    // =====================================================================
    //  타입별 분기 + 지역 순회
    // =====================================================================

    private List<AlertEvent> collectEvents(
            GenerateAlertsCommand cmd, List<String> regionIds,
            LocalDateTime since, LocalDateTime now
    ) {
        ArrayList<AlertEvent> out = new ArrayList<>(16);

        for (String regionId : regionIds) {
            if (cmd.isEnabled(AlertTypeEnum.RAIN_ONSET))
                out.addAll(detectRainOnset(regionId, cmd.withinHours(), now));

            if (cmd.isEnabled(AlertTypeEnum.RAIN_FORECAST))
                out.addAll(detectRainForecast(regionId, now));

            if (cmd.isEnabled(AlertTypeEnum.WARNING_ISSUED))
                out.addAll(detectWarningIssued(regionId, since, cmd.warningKinds()));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /** load pair -> detect onset -> adjust(validAt window) */
    private List<AlertEvent> detectRainOnset(
            String regionId, @Nullable Integer withinHours, LocalDateTime now
    ) {
        PopView.Pair pair = popViewReader.loadCurrentPreviousPair(regionId);
        if (pair == null) return List.of();

        List<AlertEvent> raw = rainOnsetDetector.detect(regionId, pair, now);
        if (raw.isEmpty()) return List.of();

        return rainOnsetAdjuster.adjust(raw, now, withinHours);
    }

    /** load current -> detect forecast -> adjust (time shift + clipping) */
    private List<AlertEvent> detectRainForecast(String regionId, LocalDateTime now) {
        PopView view = popViewReader.loadCurrent(regionId);
        if (view == null) return List.of();

        AlertEvent raw = rainForecastDetector.detect(regionId, view, now);
        if (raw == null) return List.of();

        AlertEvent adjusted = rainForecastAdjuster.adjust(raw, raw.occurredAt(), now);
        return (adjusted == null) ? List.of() : List.of(adjusted);
    }

    /** load states -> detect issued warnings */
    private List<AlertEvent> detectWarningIssued(
            String regionId, LocalDateTime since,
            @Nullable Set<WarningKind> warningKinds
    ) {
        var warningsByKind = warningStateReader.loadLatestByKind(regionId);
        if (warningsByKind == null || warningsByKind.isEmpty()) return List.of();

        return warningIssuedDetector.detect(regionId, warningsByKind, since, warningKinds);
    }

    // -- 정규화 헬퍼 --

    private LocalDateTime normalizeNow(@Nullable LocalDateTime now) {
        return (now == null) ? nowTruncatedToMinute() : TimeUtil.truncateToMinutes(now);
    }

    /** sinceHours -> LocalDateTime 변환. null 이면 기본값(defaultSinceHours) 적용 */
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