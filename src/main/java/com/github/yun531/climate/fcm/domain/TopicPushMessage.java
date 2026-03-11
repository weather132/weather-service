package com.github.yun531.climate.fcm.domain;

import java.util.Map;

/**
 * 토픽 푸시 메시지 값 객체.
 * - "어떤 토픽에, 어떤 데이터를, 얼마간의 TTL로 보낼 것인가" 표현
 */
public record TopicPushMessage(
        String topic,
        Map<String, String> data,
        long ttlMillis
) {
    public TopicPushMessage {
        if (topic == null || topic.isBlank()){
            throw new IllegalArgumentException("topic must not be blank");
        }
        data = (data == null) ? Map.of() : Map.copyOf(data);
        if (ttlMillis < 0) ttlMillis = 0;
    }
}
