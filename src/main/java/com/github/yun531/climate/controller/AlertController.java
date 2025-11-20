package com.github.yun531.climate.controller;

import com.github.yun531.climate.service.NotificationService;
import com.github.yun531.climate.service.rule.AlertEvent;
import com.github.yun531.climate.service.rule.AlertTypeEnum;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/change")
public class AlertController {

    private final NotificationService notificationService;

    @GetMapping("/climate/3hour")
    @Operation(summary = "일기예보 변동사항 알림", description = "3시간 마다 발표되는 24시간이내의 일기예보의 변동사항에 대한 알림")
    public ResponseEntity<List<AlertEvent>> get3HourIntervalForecast(@RequestParam List<Integer> regionIds) {
        List<AlertEvent> out = notificationService.generate(regionIds, null, null);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/climate/day")
    @Operation(summary = "일기예보 요약 알림", description = "24시간 이내의 비오는 시간대와, 7일이내의 오전/오후 일기예보 알림")
    public ResponseEntity<List<AlertEvent>> getDayForecast(@RequestParam List<Integer> regionIds) {
        Set<AlertTypeEnum> types = EnumSet.of(AlertTypeEnum.RAIN_FORECAST);
        List<AlertEvent> out = notificationService.generate(regionIds, types, null);

        return ResponseEntity.ok(out);
    }

}
