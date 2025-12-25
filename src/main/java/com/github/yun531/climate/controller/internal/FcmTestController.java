package com.github.yun531.climate.controller.internal;

import com.github.yun531.climate.controller.internal.dto.FcmTestRequest;
import com.github.yun531.climate.controller.internal.dto.FcmTestResponse;
import com.github.yun531.climate.service.fcm.FcmTopicPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/fcm")
public class FcmTestController {

    private final FcmTopicPushService fcm;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @PostMapping("/hourly")
    public ResponseEntity<FcmTestResponse> hourly(@RequestParam(defaultValue = "true") boolean dryRun) {
        try {
            String messageId = fcm.sendHourlyTrigger(dryRun);
            return ResponseEntity.ok(FcmTestResponse.success("hourly", dryRun, messageId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(FcmTestResponse.fail("hourly", dryRun, e.getMessage()));
        }
    }

    @PostMapping("/daily/{hour}")
    public ResponseEntity<FcmTestResponse> daily(@PathVariable int hour, @RequestParam(defaultValue = "true") boolean dryRun) {
        String topic = "daily_" + String.format("%02d", hour);
        try {
            String messageId = fcm.sendDailyTrigger(hour, dryRun);
            return ResponseEntity.ok(FcmTestResponse.success(topic, dryRun, messageId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(FcmTestResponse.fail(topic, dryRun, e.getMessage()));
        }
    }

    @PostMapping("/send")
    public ResponseEntity<FcmTestResponse> send(@RequestBody FcmTestRequest req) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", req.type());
            data.put("triggerAt", (req.triggerAt() == null || req.triggerAt().isBlank())
                    ? ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(ISO)
                    : req.triggerAt());
            if (req.hour() != null) data.put("hour", String.valueOf(req.hour()));

            String messageId = fcm.sendCustomTopic(req.topic(), data, req.dryRun());
            return ResponseEntity.ok(FcmTestResponse.success(req.topic(), req.dryRun(), messageId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(FcmTestResponse.fail(req.topic(), req.dryRun(), e.getMessage()));
        }
    }
}