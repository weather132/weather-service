package com.github.yun531.climate.service.fcm;

import com.github.yun531.climate.config.fcm.FcmTriggerProperties;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FcmTopicPushService {

    private final FcmSender sender;
    private final FcmMessageFactory factory;
    private final FcmTriggerProperties props;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public String sendHourlyTrigger(boolean dryRun) throws FirebaseMessagingException {
        ZonedDateTime now = ZonedDateTime.now(KST);
        int hour = now.getHour();

        Map<String, String> data = new HashMap<>();
        data.put("type", "HOURLY_TRIGGER");
        data.put("triggerAt", now.format(ISO));
        data.put("hour", String.valueOf(hour));

        long ttlMillis = props.ttlSeconds() * 1000L;
        Message msg = factory.topicDataMessage(props.hourlyTopic(), data, ttlMillis);
        return sender.send(msg, dryRun);
    }

    public String sendDailyTrigger(int hour, boolean dryRun) throws FirebaseMessagingException {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour must be 0..23");

        ZonedDateTime now = ZonedDateTime.now(KST);
        String topic = props.dailyTopicPrefix() + String.format("%02d", hour);

        Map<String, String> data = new HashMap<>();
        data.put("type", "DAILY_TRIGGER");
        data.put("triggerAt", now.format(ISO));
        data.put("hour", String.valueOf(hour));

        long ttlMillis = props.ttlSeconds() * 1000L;
        Message msg = factory.topicDataMessage(topic, data, ttlMillis);
        return sender.send(msg, dryRun);
    }

    public String sendCustomTopic(String topic, Map<String, String> data, boolean dryRun) throws FirebaseMessagingException {
        long ttlMillis = props.ttlSeconds() * 1000L;
        Message msg = factory.topicDataMessage(topic, data, ttlMillis);
        return sender.send(msg, dryRun);
    }
}