package com.github.yun531.climate.notification.presentation;

import com.github.yun531.climate.notification.application.alert.GenerateAlertsService;
import com.github.yun531.climate.notification.application.alert.GenerateAlertsCommand;
import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.warning.domain.model.WarningKind;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/alerts")
public class AlertController {

    private final GenerateAlertsService service;

    @GetMapping("/rain-onset")
    @Operation(
            summary = "일기예보 변동사항 알림",
            description = "3시간 마다 발표되는 24시간이내의 일기예보의 변동사항에 대한 알림"
    )
    public ResponseEntity<List<AlertEvent>> get3HourIntervalForecast(
            @RequestParam List<String> regionIds,
            @RequestParam(value = "withinHours", required = false) Integer withinHours
    ) {
        if (withinHours != null && (withinHours < 1 || withinHours > 24)) withinHours = 24;

        var cmd = new GenerateAlertsCommand(
                regionIds, null, EnumSet.of(AlertTypeEnum.RAIN_ONSET), null, withinHours
        );
        return ResponseEntity.ok(service.generate(cmd));
    }

    @GetMapping("/rain-forecast")
    @Operation(
            summary = "일기예보 요약 알림",
            description = "24시간 이내의 비오는 시간대와, 7일이내의 오전/오후 일기예보 알림"
    )
    public ResponseEntity<List<AlertEvent>> getDayForecast(
            @RequestParam List<String> regionIds
    ) {
        var cmd = new GenerateAlertsCommand(
                regionIds, null, EnumSet.of(AlertTypeEnum.RAIN_FORECAST), null, null
        );
        return ResponseEntity.ok(service.generate(cmd));
    }

    @GetMapping("/warning-issued")
    @Operation(
            summary = "기상특보 변동사항 알림",
            description = "1시간마다 발표되는 기상특보의 변동사항에 대한 알림. sinceHours: 최근 N시간 이내 발령 특보만 조회 (기본값 서버 설정)"
    )
    public ResponseEntity<List<AlertEvent>> getWarning(
            @RequestParam List<String> regionIds,
            @RequestParam(value = "sinceHours", required = false) Integer sinceHours,
            @RequestParam(value = "warningKinds", required = false) List<WarningKind> warningKinds
    ) {
        var cmd = new GenerateAlertsCommand(
                regionIds, sinceHours, EnumSet.of(AlertTypeEnum.WARNING_ISSUED),
                toEnumSet(warningKinds), null
        );
        return ResponseEntity.ok(service.generate(cmd));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "단기 예보 + 기상특보 통합 알림",
            description = "3시간 단기예보 변동사항(RAIN_ONSET)과 기상특보 변동사항(WARNING_ISSUED)을 통합 조회"
    )
    public ResponseEntity<List<AlertEvent>> getSummary(
            @RequestParam List<String> regionIds,
            @RequestParam(value = "withinHours", required = false) Integer withinHours,
            @RequestParam(value = "sinceHours", required = false) Integer sinceHours,
            @RequestParam(value = "warningKinds", required = false) List<WarningKind> warningKinds
    ) {
        if (withinHours != null && (withinHours < 1 || withinHours > 24)) withinHours = 24;

        var cmd = new GenerateAlertsCommand(
                regionIds, sinceHours,
                EnumSet.of(AlertTypeEnum.RAIN_ONSET, AlertTypeEnum.WARNING_ISSUED),
                toEnumSet(warningKinds), withinHours
        );
        return ResponseEntity.ok(service.generate(cmd));
    }

    private static Set<WarningKind> toEnumSet(List<WarningKind> kinds) {
        if (kinds == null || kinds.isEmpty()) return null;
        return EnumSet.copyOf(kinds);
    }
}