package com.github.yun531.climate.fcm.infra.firebase;

import com.github.yun531.climate.fcm.domain.PushFailedException;
import com.github.yun531.climate.fcm.domain.TopicPushMessage;
import com.github.yun531.climate.fcm.domain.TopicPushSender;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Firebase Admin SDK를 사용한 TopicPushSender 구현.
 *
 * FirebaseMessagingException -> PushFailedException 으로 변환하여
 * 소비자가 Firebase SDK에 의존하지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class FirebaseTopicPushSender implements TopicPushSender {

    private final FirebaseMessaging messaging;
    private final FirebaseMessageMapper mapper;

    @Override
    public String push(TopicPushMessage message, boolean dryRun) {
        try {
            var firebaseMessage = mapper.toFirebaseMessage(message);
            return messaging.send(firebaseMessage, dryRun);
        } catch (FirebaseMessagingException e) {
            throw new PushFailedException(
                    "FCM push failed: topic=" + message.topic(), e);
        }
    }
}
