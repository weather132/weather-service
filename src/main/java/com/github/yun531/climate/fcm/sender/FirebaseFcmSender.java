package com.github.yun531.climate.fcm.sender;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FirebaseFcmSender implements FcmSender {

    private final FirebaseMessaging messaging;

    @Override
    public String send(Message message, boolean dryRun) throws FirebaseMessagingException {
        return messaging.send(message, dryRun);
    }
}