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

    // 서비스(FcmTopicPushService)와 동일하게 LocalDateTime 기반 포맷으로 통일
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
            log.info("[FCM-TEST] daily ok topic={} dryRun={} messageId={}", topic, dryRun, messageId);
            return ResponseEntity.ok(FcmTestResponse.success(topic, dryRun, messageId));
        } catch (FirebaseMessagingException e) {
            log.error("[FCM-TEST] daily firebase fail topic={} dryRun={}", topic, dryRun, e);
            return ResponseEntity.internalServerError()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getMessage()));
        } catch (Exception e) {
            log.error("[FCM-TEST] daily fail topic={} dryRun={}", topic, dryRun, e);
            return ResponseEntity.internalServerError()
                    .body(FcmTestResponse.fail(topic, dryRun, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @PostMapping("/send")
    public ResponseEntity<FcmTestResponse> send(@RequestBody FcmTestRequest req) {
        // topic 기본 검증
        String topic = (req.topic() == null) ? "" : req.topic().trim();
        boolean dryRun = req.dryRun();

        if (topic.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(FcmTestResponse.fail("(blank)", dryRun, "topic must not be blank"));
        }

        try {
            Map<String, String> data = new HashMap<>();

            // type 기본값 처리(원하면 "CUSTOM" 같은 값으로 통일해도 됨)
            String type = (req.type() == null || req.type().isBlank()) ? "CUSTOM" : req.type().trim();
            data.put("type", type);

            // triggerAt: 클라에서 내려주는 값이 없으면 서버 현재시각(분 단위)으로
            // - ISO_LOCAL_DATE_TIME 우선
            // - ISO_OFFSET_DATE_TIME도 들어올 수 있어서 받아서 Local로 변환
            String triggerAtLocal = normalizeTriggerAtLocal(req.triggerAt());
            data.put("triggerAtLocal", triggerAtLocal);

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
     * triggerAt 입력값을 "ISO_LOCAL_DATE_TIME"로 정규화.
     * - null/blank면: 서버 현재시각(TimeUtil.nowMinutes()) 사용
     * - ISO_LOCAL_DATE_TIME이면: 그대로 사용
     * - ISO_OFFSET_DATE_TIME이면: offset 제거 후 local로 변환해서 사용
     */
    private String normalizeTriggerAtLocal(String triggerAt) {
        if (triggerAt == null || triggerAt.isBlank()) {
            LocalDateTime now = TimeUtil.nowMinutes();
            return now.format(ISO_LOCAL);
        }

        String s = triggerAt.trim();

        // 1) ISO_LOCAL_DATE_TIME 시도
        try {
            LocalDateTime.parse(s, ISO_LOCAL);
            return s;
        } catch (DateTimeParseException ignored) {
        }

        // 2) ISO_OFFSET_DATE_TIME 시도 → Local 변환
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDateTime()
                    .format(ISO_LOCAL);
        } catch (DateTimeParseException ignored) {
        }

        // 그 외 포맷은 명확히 실패 처리(테스트 컨트롤러니까 입력을 엄격히 잡는 편이 디버깅이 쉬움)
        throw new IllegalArgumentException("triggerAt must be ISO_LOCAL_DATE_TIME or ISO_OFFSET_DATE_TIME");
    }
}