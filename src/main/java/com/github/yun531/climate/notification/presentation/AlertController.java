package com.github.yun531.climate.notification.presentation;

import com.github.yun531.climate.notification.application.GenerateAlertsService;
import com.github.yun531.climate.notification.application.command.GenerateAlertsCommand;
import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.model.WarningKind;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/change")
public class AlertController {

    private final GenerateAlertsService service;

    @GetMapping("/climate/3hour")
    @Operation(
            summary = "일기예보 변동사항 알림",
            description = "3시간 마다 발표되는 24시간이내의 일기예보의 변동사항에 대한 알림"
    )
    public ResponseEntity<List<AlertEvent>> get3HourIntervalForecast(
            @RequestParam List<String> regionIds,
            @RequestParam(value = "maxHour", required = false) Integer maxHour // 1~24
    ) {
        // 간단 유효성 체크 >> 이상한 값이면 24 사용
        if (maxHour != null && (maxHour < 1 || maxHour > 24)) {
            maxHour = 24;
        }
        Set<AlertTypeEnum> types = EnumSet.of(AlertTypeEnum.RAIN_ONSET);

        GenerateAlertsCommand cmd = new GenerateAlertsCommand(
                regionIds,
                null,   // since -> 서비스에서 now 로 보정
                types,   // enabledTypes
                null,    // filterWarningKinds
                maxHour  // rainHourLimit
        );

        return ResponseEntity.ok(service.generate(cmd));
    }

    @GetMapping("/climate/day")
    @Operation(
            summary = "일기예보 요약 알림",
            description = "24시간 이내의 비오는 시간대와, 7일이내의 오전/오후 일기예보 알림"
    )
    public ResponseEntity<List<AlertEvent>> getDayForecast(
            @RequestParam List<String> regionIds
    ) {
        Set<AlertTypeEnum> types = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);

        GenerateAlertsCommand cmd = new GenerateAlertsCommand(
                regionIds,
                null,
                types,
                null,
                null
        );

        return ResponseEntity.ok(service.generate(cmd));
    }

    @GetMapping("/warning")
    @Operation(
            summary = "기상특보 변동사항 알림",
            description = "1시간마다 발표되는 기상특보의 변동사항에 대한 알림"
    )
    public ResponseEntity<List<AlertEvent>> getWarning(
            @RequestParam List<String> regionIds,
            @RequestParam(value = "warningKinds", required = false) List<WarningKind> warningKinds
    ) {
        Set<AlertTypeEnum> types = EnumSet.of(AlertTypeEnum.WARNING_ISSUED);
        Set<WarningKind> filterKinds = toFilterKinds(warningKinds);

        GenerateAlertsCommand cmd = new GenerateAlertsCommand(
                regionIds,
                null,
                types,
                filterKinds,
                null
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
            @RequestParam(value = "warningKinds", required = false) List<WarningKind> warningKinds
    ) {
        Set<AlertTypeEnum> types = EnumSet.of(
                AlertTypeEnum.RAIN_ONSET,
                AlertTypeEnum.WARNING_ISSUED
        );

        Set<WarningKind> filterKinds = toFilterKinds(warningKinds);

        GenerateAlertsCommand cmd = new GenerateAlertsCommand(
                regionIds,
                null,
                types,
                filterKinds,
                null
        );

        return ResponseEntity.ok(service.generate(cmd));
    }

    private static Set<WarningKind> toFilterKinds(List<WarningKind> kinds) {
        if (kinds == null || kinds.isEmpty()) return null;
        return EnumSet.copyOf(kinds);
    }
}