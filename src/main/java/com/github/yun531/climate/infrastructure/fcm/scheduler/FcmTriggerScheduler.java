package com.github.yun531.climate.infrastructure.fcm.scheduler;

import com.github.yun531.climate.infrastructure.fcm.service.FcmTopicPushService;
import com.github.yun531.climate.shared.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmTriggerScheduler {

    private final FcmTopicPushService fcm;

    /** 개발/검증 중에는 true로 두면 실제 발송 없이 검증만 수행 */
    private static final boolean DRY_RUN = false;

    // 08~23시(3시간 간격)의 5분에만 hourly 전송
    @Scheduled(cron = "0 5 8-23/3 * * *")
    public void triggerHourly() {
        var now = TimeUtil.nowMinutes();
        int hour = now.getHour();

        run("hourly", hour, () -> fcm.sendHourlyTrigger(now, hour, DRY_RUN));
    }

    // 매 시간(00~23) + 5분 마다 daily 전송
    @Scheduled(cron = "0 5 * * * *")
    public void triggerDaily() {
        var now = TimeUtil.nowMinutes();
        int hour = now.getHour();

        run("daily", hour, () -> fcm.sendDailyTrigger(now, hour, DRY_RUN));
    }

    private void run(String kind, int hour, FcmCall call) {
        try {
            String messageId = call.call();
            log.info("[FCM] {} sent. hour={}, dryRun={}, messageId={}", kind, hour, DRY_RUN, messageId);
        } catch (Exception e) {
            log.error("[FCM] {} failed. hour={}, dryRun={}", kind, hour, DRY_RUN, e);
        }
    }

    @FunctionalInterface
    private interface FcmCall {
        String call() throws Exception;
    }
}