package com.github.yun531.climate.notification.application.trigger;

import com.github.yun531.climate.shared.time.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerScheduler {

    private final TriggerPushSender sender;

    // 개발/검증 중에는 true로 두면 실제 발송 없이 검증만 수행
    private static final boolean DRY_RUN = false;

    // 08~23시(3시간 간격)의 5분에만 hourly 전송
    @Scheduled(cron = "0 5 8-23/3 * * *")
    public void triggerHourly() {
        var now = TimeUtil.nowTruncatedToMinute();
        int hour = now.getHour();

        try {
            String messageId = sender.sendHourly(now, hour, DRY_RUN);
            log.info("[TRIGGER] hourly sent. hour={} dryRun={} messageId={}",
                    hour, DRY_RUN, messageId);
        } catch (Exception e) {
            log.error("[TRIGGER] hourly failed. hour={} dryRun={}", hour, DRY_RUN, e);
        }
    }

    // 매 시간(00~23) + 5분 마다 daily 전송
    @Scheduled(cron = "0 5 * * * *")
    public void triggerDaily() {
        var now = TimeUtil.nowTruncatedToMinute();
        int hour = now.getHour();

        try {
            String messageId = sender.sendDaily(now, hour, DRY_RUN);
            log.info("[TRIGGER] daily sent. hour={} dryRun={} messageId={}",
                    hour, DRY_RUN, messageId);
        } catch (Exception e) {
            log.error("[TRIGGER] daily failed. hour={} dryRun={}", hour, DRY_RUN, e);
        }
    }
}
