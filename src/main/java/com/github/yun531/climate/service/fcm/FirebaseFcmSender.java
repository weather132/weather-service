package com.github.yun531.climate.service.fcm;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FirebaseFcmSender implements FcmSender {

    private final FirebaseApp firebaseApp;

    @Override
    public String send(Message message, boolean dryRun) throws FirebaseMessagingException {
        // send(message, dryRun) : dryRun=true면 검증만 하고 실제 발송은 안 함 :contentReference[oaicite:3]{index=3}
        return FirebaseMessaging.getInstance(firebaseApp).send(message, dryRun);
    }
}