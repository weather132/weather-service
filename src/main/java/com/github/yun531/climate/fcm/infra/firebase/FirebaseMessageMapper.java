package com.github.yun531.climate.fcm.infra.firebase;

import com.github.yun531.climate.fcm.domain.TopicPushMessage;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * TopicPushMessage -> Firebase Message 변환.
 */
@Component
public class FirebaseMessageMapper {

    public Message toFirebaseMessage(TopicPushMessage pushMessage) {
        AndroidConfig android = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(pushMessage.ttlMillis())
                .build();

        return Message.builder()
                .setTopic(pushMessage.topic())
                .putAllData(pushMessage.data())
                .setAndroidConfig(android)
                .build();
    }
}
