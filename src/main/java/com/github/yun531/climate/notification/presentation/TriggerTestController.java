package com.github.yun531.climate.notification.presentation;

import com.github.yun531.climate.notification.application.trigger.TriggerPushSender;
import com.github.yun531.climate.shared.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 스케줄러의 트리거를 원하는 타이밍에 수동으로 발화하기 위한 내부 API.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/trigger")
@ConditionalOnProperty(prefix = "notification.internal-api", name = "enabled", havingValue = "true")
public class TriggerTestController {

    private final TriggerPushSender sender;

    @PostMapping("/hourly")
    public ResponseEntity<Map<String, Object>> hourly(
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        var now = TimeUtil.nowTruncatedToMinute();
        int hour = now.getHour();

        try {
            String messageId = sender.sendHourly(now, hour, dryRun);
            log.info("[TRIGGER-TEST] hourly ok hour={} dryRun={} messageId={}",
                    hour, dryRun, messageId);
            return ResponseEntity.ok(Map.of(
                    "ok", true, "hour", hour,
                    "dryRun", dryRun, "messageId", messageId));
        } catch (Exception e) {
            log.error("[TRIGGER-TEST] hourly fail hour={} dryRun={}", hour, dryRun, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false, "hour", hour,
                    "dryRun", dryRun, "error", e.getMessage()));
        }
    }

    @PostMapping("/daily/{hour}")
    public ResponseEntity<Map<String, Object>> daily(
            @PathVariable int hour,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        if (hour < 0 || hour > 23) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false, "error", "hour must be 0..23"));
        }

        var now = TimeUtil.nowTruncatedToMinute();

        try {
            String messageId = sender.sendDaily(now, hour, dryRun);
            log.info("[TRIGGER-TEST] daily ok hour={} dryRun={} messageId={}",
                    hour, dryRun, messageId);
            return ResponseEntity.ok(Map.of(
                    "ok", true, "hour", hour,
                    "dryRun", dryRun, "messageId", messageId));
        } catch (Exception e) {
            log.error("[TRIGGER-TEST] daily fail hour={} dryRun={}", hour, dryRun, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false, "hour", hour,
                    "dryRun", dryRun, "error", e.getMessage()));
        }
    }
}