package com.github.yun531.climate.service.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FcmMessageFactory {

    public Message topicDataMessage(String topic, Map<String, String> data, long ttlMillis) {
        AndroidConfig android = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH) // 우선순위 설정 :contentReference[oaicite:4]{index=4}
                .setTtl(ttlMillis)
                .build();

        return Message.builder()
                .setTopic(topic)
                .putAllData(data)
                .setAndroidConfig(android)
                .build();
    }
}