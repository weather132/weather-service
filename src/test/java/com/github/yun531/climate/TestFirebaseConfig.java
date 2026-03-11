package com.github.yun531.climate;

import com.github.yun531.climate.fcm.domain.TopicPushSender;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * 통합 테스트에서 Firebase 의존성을 Mock으로 대체.
 * FirebaseAdminConfig는 @Profile("!test")로 제외되므로 빈 충돌 없음.
 */
@TestConfiguration
public class TestFirebaseConfig {

    @Bean
    @Primary
    public FirebaseMessaging firebaseMessaging() {
        return mock(FirebaseMessaging.class);
    }

    @Bean
    @Primary
    public TopicPushSender topicPushSender() {
        return mock(TopicPushSender.class);
    }
}