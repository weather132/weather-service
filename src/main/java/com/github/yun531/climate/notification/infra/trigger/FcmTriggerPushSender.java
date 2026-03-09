package com.github.yun531.climate.notification.infra.trigger;

import com.github.yun531.climate.fcm.domain.TopicPushMessage;
import com.github.yun531.climate.fcm.domain.TopicPushSender;
import com.github.yun531.climate.notification.application.trigger.TriggerPushSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * TriggerPushSender → TopicPushSender 어댑터.
 * - "notification이 요구하는 인터페이스"를 "fcm이 제공하는 인터페이스"로 변환
 * - topic 이름, 데이터 구조, TTL 등 전송 세부사항을 여기서 조립
 */
@Component
@RequiredArgsConstructor
public class FcmTriggerPushSender implements TriggerPushSender {

    private final TopicPushSender pushSender;
    private final TriggerProperties props;

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String KEY_TYPE = "type";
    private static final String KEY_TRIGGER_AT_LOCAL = "triggerAtLocal";
    private static final String KEY_HOUR = "hour";

    @Override
    public String sendHourly(LocalDateTime firedAt, int hour, boolean dryRun) {
        Map<String, String> data = buildData("HOURLY_TRIGGER", firedAt, hour);
        TopicPushMessage message = new TopicPushMessage(
                props.hourlyTopic(), data, props.ttlMillis());
        return pushSender.push(message, dryRun);
    }

    @Override
    public String sendDaily(LocalDateTime firedAt, int hour, boolean dryRun) {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour must be 0..23");

        String topic = props.dailyTopicPrefix() + String.format("%02d", hour);
        Map<String, String> data = buildData("DAILY_TRIGGER", firedAt, hour);
        TopicPushMessage message = new TopicPushMessage(
                topic, data, props.ttlMillis());
        return pushSender.push(message, dryRun);
    }

    private Map<String, String> buildData(String type, LocalDateTime firedAt, int hour) {
        Map<String, String> data = new HashMap<>();
        data.put(KEY_TYPE, type);
        data.put(KEY_TRIGGER_AT_LOCAL, firedAt.format(ISO_LOCAL));
        data.put(KEY_HOUR, String.valueOf(hour));
        return data;
    }
}
