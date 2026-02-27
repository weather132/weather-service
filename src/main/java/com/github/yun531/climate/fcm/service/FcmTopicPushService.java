package com.github.yun531.climate.fcm.service;

import com.github.yun531.climate.fcm.config.FcmTriggerProperties;
import com.github.yun531.climate.fcm.sender.FcmSender;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FcmTopicPushService {

    private final FcmSender sender;
    private final FcmTriggerProperties props;
    private final FcmMessageFactory factory;

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String KEY_TYPE = "type";
    private static final String KEY_TRIGGER_AT_LOCAL = "triggerAtLocal";
    private static final String KEY_HOUR = "hour";

    private static final String TYPE_HOURLY = "HOURLY_TRIGGER";
    private static final String TYPE_DAILY = "DAILY_TRIGGER";

    public String sendHourlyTrigger(LocalDateTime now, int hour, boolean dryRun) throws FirebaseMessagingException {
        Map<String, String> data = new HashMap<>();
        data.put(KEY_TYPE, TYPE_HOURLY);
        data.put(KEY_TRIGGER_AT_LOCAL, now.format(ISO_LOCAL));
        data.put(KEY_HOUR, String.valueOf(hour));

        long ttlMillis = Math.multiplyExact(props.ttlSeconds(), 1000L);
        Message msg = factory.topicDataMessage(props.hourlyTopic(), data, ttlMillis);

        return sender.send(msg, dryRun);
    }

    public String sendDailyTrigger(LocalDateTime now, int hour, boolean dryRun) throws FirebaseMessagingException {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour must be 0..23");

        String topic = props.dailyTopicPrefix() + String.format("%02d", hour);

        Map<String, String> data = new HashMap<>();
        data.put(KEY_TYPE, TYPE_DAILY);
        data.put(KEY_TRIGGER_AT_LOCAL, now.format(ISO_LOCAL));
        data.put(KEY_HOUR, String.valueOf(hour));

        long ttlMillis = Math.multiplyExact(props.ttlSeconds(), 1000L);
        Message msg = factory.topicDataMessage(topic, data, ttlMillis);

        return sender.send(msg, dryRun);
    }

    public String sendCustomTopic(String topic, Map<String, String> data, boolean dryRun) throws FirebaseMessagingException {
        long ttlMillis = Math.multiplyExact(props.ttlSeconds(), 1000L);
        Message msg = factory.topicDataMessage(topic, data, ttlMillis);
        return sender.send(msg, dryRun);
    }
}