package com.github.yun531.climate.controller.internal;

import com.github.yun531.climate.config.fcm.FcmTriggerProperties;
import com.github.yun531.climate.controller.internal.dto.FcmTestRequest;
import com.github.yun531.climate.controller.internal.dto.FcmTestResponse;
import com.github.yun531.climate.infra.fcm.FcmTopicPushService;
import com.github.yun531.climate.util.time.TimeUtil;
import com.google.firebase.messaging.FirebaseMessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/fcm")
public class FcmTestController {

    private final FcmTopicPushService fcm;
    private final FcmTriggerProperties props;

    /** 서비스(FcmTopicPushService)와 동일하게 LocalDateTime(ISO_LOCAL_DATE_TIME) 포맷으로 통일 */
    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @PostMapping("/hourly")
    public ResponseEntity<FcmTestResponse> hourly(@RequestParam(defaultValue = "true") boolean dryRun) {
        String topic = props.hourlyTopic();
        try {
            String messageId = fcm.sendHourlyTrigger(dryRun);
            log.info("[FCM-TEST] hourly ok topic={} dryRun={} messageId={}", topic, dryRun, messageId);
            return ResponseEntity.ok(FcmTestResponse.success(topic, dryRun, messageId));
        } catch (FirebaseMessagingException e) {
            log.error("[FCM-TEST] hourly firebase fail topic={} dryRun={}", topic, dryRun, e);
            return ResponseEntity.internalServerError()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getMessage()));
        } catch (Exception e) {
            log.error("[FCM-TEST] hourly fail topic={} dryRun={}", topic, dryRun, e);
            return ResponseEntity.internalServerError()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @PostMapping("/daily/{hour}")
    public ResponseEntity<FcmTestResponse> daily(@PathVariable int hour,
                                                 @RequestParam(defaultValue = "true") boolean dryRun) {
        if (hour < 0 || hour > 23) {
            String topic = props.dailyTopicPrefix() + "??";
            return ResponseEntity.badRequest()
                    .body(FcmTestResponse.fail(topic, dryRun, "hour must be 0..23"));
        }

        String topic = props.dailyTopicPrefix() + String.format("%02d", hour);
        try {
            String messageId = fcm.sendDailyTrigger(hour, dryRun);
            log.info("[FCM-TEST] daily ok topic={} hour={} dryRun={} messageId={}", topic, hour, dryRun, messageId);
            return ResponseEntity.ok(FcmTestResponse.success(topic, dryRun, messageId));
        } catch (FirebaseMessagingException e) {
            log.error("[FCM-TEST] daily firebase fail topic={} hour={} dryRun={}", topic, hour, dryRun, e);
            return ResponseEntity.internalServerError()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getMessage()));
        } catch (Exception e) {
            log.error("[FCM-TEST] daily fail topic={} hour={} dryRun={}", topic, hour, dryRun, e);
            return ResponseEntity.internalServerError()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @PostMapping("/send")
    public ResponseEntity<FcmTestResponse> send(@RequestBody FcmTestRequest req) {
        String topic = (req.topic() == null) ? "" : req.topic().trim();
        boolean dryRun = req.dryRun();

        if (topic.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(FcmTestResponse.fail("(blank)", dryRun, "topic must not be blank"));
        }

        try {
            Map<String, String> data = new HashMap<>();

            // type 기본값
            String type = (req.type() == null || req.type().isBlank()) ? "CUSTOM" : req.type().trim();
            data.put("type", type);

            // triggerAtLocal: "서버/클라가 공통으로 쓰는 로컬 시각(문자열)"로 정규화해서 보냄
            // - 미입력: 서버 nowMinutes()
            // - 입력: ISO_LOCAL_DATE_TIME 또는 ISO_OFFSET_DATE_TIME 허용
            data.put("triggerAtLocal", normalizeTriggerAtLocal(req.triggerAt()));

            // hour(선택)
            if (req.hour() != null) data.put("hour", String.valueOf(req.hour()));

            String messageId = fcm.sendCustomTopic(topic, data, dryRun);
            log.info("[FCM-TEST] send ok topic={} dryRun={} type={} messageId={}", topic, dryRun, type, messageId);
            return ResponseEntity.ok(FcmTestResponse.success(topic, dryRun, messageId));

        } catch (IllegalArgumentException e) {
            log.warn("[FCM-TEST] send bad-request topic={} dryRun={} msg={}", topic, dryRun, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getMessage()));
        } catch (FirebaseMessagingException e) {
            log.error("[FCM-TEST] send firebase fail topic={} dryRun={}", topic, dryRun, e);
            return ResponseEntity.internalServerError()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getMessage()));
        } catch (Exception e) {
            log.error("[FCM-TEST] send fail topic={} dryRun={}", topic, dryRun, e);
            return ResponseEntity.internalServerError()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /**
     * triggerAt 입력을 "ISO_LOCAL_DATE_TIME" 문자열로 정규화한다.
     *
     * 규칙:
     * - null/blank: 서버 현재시각(TimeUtil.nowMinutes()) 사용
     * - ISO_LOCAL_DATE_TIME: 그대로 사용
     * - ISO_OFFSET_DATE_TIME: 오프셋 포함 시각을 LocalDateTime으로 변환해 사용
     *
     * 예)
     * - "2026-01-14T20:10:00"         -> 그대로
     * - "2026-01-14T20:10:00+09:00"  -> "2026-01-14T20:10:00"
     */
    private String normalizeTriggerAtLocal(String triggerAt) {
        if (triggerAt == null || triggerAt.isBlank()) {
            LocalDateTime now = TimeUtil.nowMinutes();
            return now.format(ISO_LOCAL);
        }

        String s = triggerAt.trim();

        // 1) ISO_LOCAL_DATE_TIME
        try {
            LocalDateTime.parse(s, ISO_LOCAL);
            return s;
        } catch (DateTimeParseException ignored) {
        }

        // 2) ISO_OFFSET_DATE_TIME -> Local 변환 후 ISO_LOCAL로 출력
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDateTime()
                    .format(ISO_LOCAL);
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException("triggerAt must be ISO_LOCAL_DATE_TIME or ISO_OFFSET_DATE_TIME");
    }
}