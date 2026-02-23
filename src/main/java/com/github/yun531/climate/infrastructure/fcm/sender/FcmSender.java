package com.github.yun531.climate.infrastructure.fcm.sender;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

public interface FcmSender {
    String send(Message message, boolean dryRun) throws FirebaseMessagingException;
}