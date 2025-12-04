package com.github.yun531.climate.controller;

import com.github.yun531.climate.dto.WarningKind;
import com.github.yun531.climate.service.notification.NotificationRequest;
import com.github.yun531.climate.service.notification.NotificationService;
import com.github.yun531.climate.service.notification.rule.AlertEvent;
import com.github.yun531.climate.service.notification.rule.AlertTypeEnum;
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

    private final NotificationService notificationService;

    @GetMapping("/climate/3hour")
    @Operation(
            summary = "일기예보 변동사항 알림",
            description = "3시간 마다 발표되는 24시간이내의 일기예보의 변동사항에 대한 알림"
    )
    public ResponseEntity<List<AlertEvent>> get3HourIntervalForecast(
            @RequestParam List<Integer> regionIds,
            @RequestParam(value = "maxHour", required = false) Integer maxHour // 0~23
    ) {
        // 간단 유효성 체크 >> 이상한 값이면 23 사용
        if (maxHour != null && (maxHour < 0 || maxHour > 23)) {
            maxHour = 23;
        }

        NotificationRequest req = new NotificationRequest(
                regionIds,
                null,   // since -> 서비스에서 now 로 보정
                null,   // enabledTypes -> 서비스에서 기본(RAIN_ONSET) 적용
                null,   // filterWarningKinds
                maxHour // rainHourLimit
        );
        List<AlertEvent> out = notificationService.generate(req);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/climate/day")
    @Operation(
            summary = "일기예보 요약 알림",
            description = "24시간 이내의 비오는 시간대와, 7일이내의 오전/오후 일기예보 알림"
    )
    public ResponseEntity<List<AlertEvent>> getDayForecast(
            @RequestParam List<Integer> regionIds
    ) {
        Set<AlertTypeEnum> types = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);

        NotificationRequest req = new NotificationRequest(
                regionIds,
                null,
                types,
                null,
                null
        );
        List<AlertEvent> out = notificationService.generate(req);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/warning")
    @Operation(
            summary = "기상특보 변동사항 알림",
            description = "1시간마다 발표되는 기상특보의 변동사항에 대한 알림"
    )
    public ResponseEntity<List<AlertEvent>> getWarning(
            @RequestParam List<Integer> regionIds,
            @RequestParam(value = "warningKinds", required = false) List<WarningKind> warningKinds
    ) {
        Set<AlertTypeEnum> types = EnumSet.of(AlertTypeEnum.WARNING_ISSUED);

        Set<WarningKind> filterKinds = toFilterKinds(warningKinds);

        NotificationRequest req = new NotificationRequest(
                regionIds,
                null,
                types,
                filterKinds,
                null
        );
        List<AlertEvent> out = notificationService.generate(req);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/summary")
    @Operation(
            summary = "단기 예보 + 기상특보 통합 알림",
            description = "3시간 단기예보 변동사항(RAIN_ONSET)과 기상특보 변동사항(WARNING_ISSUED)을 통합 조회"
    )
    public ResponseEntity<List<AlertEvent>> getSummbary(
            @RequestParam List<Integer> regionIds,
            @RequestParam(value = "warningKinds", required = false) List<WarningKind> warningKinds
    ) {
        Set<AlertTypeEnum> types = EnumSet.of(
                AlertTypeEnum.RAIN_ONSET,
                AlertTypeEnum.WARNING_ISSUED
        );

        Set<WarningKind> filterKinds = toFilterKinds(warningKinds);

        NotificationRequest req = new NotificationRequest(
                regionIds,
                null,
                types,
                filterKinds,
                null   // 여기서는 RAIN_ONSET 시간 제한은 두지 않음
        );
        List<AlertEvent> out = notificationService.generate(req);
        return ResponseEntity.ok(out);
    }

    private static Set<WarningKind> toFilterKinds(List<WarningKind> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(kinds);
    }
}