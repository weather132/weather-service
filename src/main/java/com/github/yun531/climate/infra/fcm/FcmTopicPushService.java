package com.github.yun531.climate.infra.fcm;

import com.github.yun531.climate.config.fcm.FcmTriggerProperties;
import com.github.yun531.climate.util.time.TimeUtil;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FcmTopicPushService {

    private final FcmSender sender;
    private final FcmMessageFactory factory;
    private final FcmTriggerProperties props;

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public String sendHourlyTrigger(boolean dryRun) throws FirebaseMessagingException {
        LocalDateTime now = TimeUtil.nowMinutes();
        int hour = now.getHour();

        Map<String, String> data = new HashMap<>();
        data.put("type", "HOURLY_TRIGGER");
        data.put("triggerAtLocal", now.format(ISO_LOCAL));
        data.put("hour", String.valueOf(hour));

        long ttlMillis = props.ttlSeconds() * 1000L;
        Message msg = factory.topicDataMessage(props.hourlyTopic(), data, ttlMillis);
        return sender.send(msg, dryRun);
    }

    public String sendDailyTrigger(int hour, boolean dryRun) throws FirebaseMessagingException {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour must be 0..23");

        LocalDateTime now = TimeUtil.nowMinutes();
        String topic = props.dailyTopicPrefix() + String.format("%02d", hour);

        Map<String, String> data = new HashMap<>();
        data.put("type", "DAILY_TRIGGER");
        data.put("triggerAtLocal", now.format(ISO_LOCAL));
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