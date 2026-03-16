package com.github.yun531.climate.notification.infra.trigger;

import com.github.yun531.climate.fcm.domain.TopicPushMessage;
import com.github.yun531.climate.fcm.domain.TopicPushSender;
import com.github.yun531.climate.notification.application.trigger.TriggerPushSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * TriggerPushSender -> TopicPushSender 어댑터.
 * - "notification이 요구하는 인터페이스"를 "fcm이 제공하는 인터페이스"로 변환
 * - topic 이름, 데이터 구조, TTL 등 전송 세부사항을 여기서 조립
 */
@Component
@RequiredArgsConstructor
public class FcmTriggerPushSender implements TriggerPushSender {

    private final TopicPushSender pushSender;
    private final TriggerProperties props;

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String sendHourly(LocalDateTime firedAt, int hour, boolean dryRun) {
        return send(props.hourlyTopic(), "HOURLY_TRIGGER", firedAt, hour, dryRun);
    }

    @Override
    public String sendDaily(LocalDateTime firedAt, int hour, boolean dryRun) {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour must be 0..23");

        return send(props.dailyTopic(hour), "DAILY_TRIGGER", firedAt, hour, dryRun);
    }

    private String send(String topic, String type, LocalDateTime firedAt, int hour, boolean dryRun) {
        Map<String, String> data = Map.of(
                "type", type,
                "triggerAtLocal", firedAt.format(ISO_LOCAL),
                "hour", String.valueOf(hour)
        );
        TopicPushMessage message = new TopicPushMessage(topic, data, props.ttlMillis());
        return pushSender.push(message, dryRun);
    }
}
