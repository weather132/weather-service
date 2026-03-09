package com.github.yun531.climate.fcm.domain;

import com.github.yun531.climate.fcm.domain.TopicPushMessage;

/**
 * 토픽 푸시 전송 계약.
 * - PushFailedException(unchecked)으로 실패를 전파.
 */
public interface TopicPushSender {

    String push(TopicPushMessage message, boolean dryRun);
}
