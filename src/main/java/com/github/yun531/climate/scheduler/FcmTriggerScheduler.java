package com.github.yun531.climate.scheduler;

import com.github.yun531.climate.service.fcm.FcmTopicPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmTriggerScheduler {

    private final FcmTopicPushService fcm;

    // 개발/검증 중에는 true로 두면 실제 발송 없이 검증만 수행 :contentReference[oaicite:5]{index=5}
    private static final boolean DRY_RUN = true;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void triggerEveryHour() {
        int hour = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).getHour();

        try {
            String hourlyId = fcm.sendHourlyTrigger(DRY_RUN);
            log.info("[FCM] hourly sent. hour={}, dryRun={}, messageId={}", hour, DRY_RUN, hourlyId);

            String dailyId = fcm.sendDailyTrigger(hour, DRY_RUN);
            log.info("[FCM] daily sent. hour={}, dryRun={}, messageId={}", hour, DRY_RUN, dailyId);
        } catch (Exception e) {
            log.error("[FCM] trigger failed. hour={}, dryRun={}", hour, DRY_RUN, e);
        }
    }
}