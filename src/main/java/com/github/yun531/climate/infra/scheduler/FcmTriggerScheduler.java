package com.github.yun531.climate.infra.scheduler;

import com.github.yun531.climate.infra.fcm.FcmTopicPushService;
import com.github.yun531.climate.util.time.TimeUtil;
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
    public void triggerHourlyBetween09And22() {
        var now = TimeUtil.nowMinutes();
        int hour = now.getHour();

        try {
            String hourlyId = fcm.sendHourlyTrigger(DRY_RUN);
            log.info("[FCM] hourly sent. hour={}, dryRun={}, messageId={}", hour, DRY_RUN, hourlyId);
        } catch (Exception e) {
            log.error("[FCM] hourly failed. hour={}, dryRun={}", hour, DRY_RUN, e);
        }
    }

    // 매 시간(00~23) + 5분 마다 daily 전송
    @Scheduled(cron = "0 5 * * * *")
    public void triggerDailyEveryHour() {
        var now = TimeUtil.nowMinutes();
        int hour = now.getHour();

        try {
            String dailyId = fcm.sendDailyTrigger(hour, DRY_RUN);
            log.info("[FCM] daily sent. hour={}, dryRun={}, messageId={}", hour, DRY_RUN, dailyId);
        } catch (Exception e) {
            log.error("[FCM] daily failed. hour={}, dryRun={}", hour, DRY_RUN, e);
        }
    }
}