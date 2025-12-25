package com.github.yun531.climate.config.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class FirebaseAdminConfig {

    private final FirebaseProperties props;

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        List<FirebaseApp> existing = FirebaseApp.getApps();
        if (existing != null && !existing.isEmpty()) {
            return existing.get(0);
        }

        try (FileInputStream fis = new FileInputStream(props.serviceAccountPath())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(fis))
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }
}