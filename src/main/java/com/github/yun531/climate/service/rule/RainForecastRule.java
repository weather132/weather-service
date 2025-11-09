package com.github.yun531.climate.service.rule;

import com.github.yun531.climate.service.ClimateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.yun531.climate.support.PopArrays.n;

@Component
@RequiredArgsConstructor
public class RainForecastRule implements AlertRule {

    private final ClimateService climateService;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int TH = 60;
    private long currentSnapId = 1L;  // todo: snapId 임시 하드코딩

    @Override public AlertTypeEnum supports() { return AlertTypeEnum.RAIN_FORECAST; }

    @Override
    public List<AlertEvent> evaluate(List<Long> regionIds, Instant since) {
        if (regionIds == null || regionIds.isEmpty()) return List.of();

        List<AlertEvent> out = new ArrayList<>(regionIds.size());
        ZonedDateTime now = ZonedDateTime.now(ZONE);

        for (Long regionId : regionIds) {
            ClimateService.ForecastSeries fs = climateService.loadForecastSeries(regionId, currentSnapId);

            List<String> hourlyParts = buildHourlyParts(fs, now); // 오늘/내일 시간대 상세
            List<String> dayParts    = buildDayParts(fs);         // D+0~6 오전/오후

            Map<String, Object> payload = new HashMap<>();
            payload.put("_srcRule", "RainForecastRule");
            payload.put("hourlyParts", hourlyParts);
            payload.put("dayParts", dayParts);

            out.add(new AlertEvent(
                    AlertTypeEnum.RAIN_FORECAST,
                    regionId,
                    Instant.now(),
                    payload
            ));
        }
        return out;
    }

    /** 시간대(rolling 24h) 상세 구간을 오늘/내일 라벨로 압축 */
    private List<String> buildHourlyParts(ClimateService.ForecastSeries fs, ZonedDateTime now) {
        if (fs == null || fs.hourly24() == null) return List.of();

        int[] pop24 = fs.hourly24();
        List<String> parts = new ArrayList<>();

        // 연속 '비' 구간 시작점 및 연속구간 탐색
        int hourIdx = 0;
        while (hourIdx < 24) {
            while (hourIdx < 24 && pop24[hourIdx] < TH) hourIdx++;
            if (hourIdx >= 24) break;

            ZonedDateTime startTs = now.plusHours(hourIdx);
            LocalDate startDay = startTs.toLocalDate();
            long dayDiff = ChronoUnit.DAYS.between(now.toLocalDate(), startDay);
            if (dayDiff > 1) break;
            if (dayDiff < 0) { hourIdx++; continue; }

            int endIdx = hourIdx;
            while (endIdx + 1 < 24
                    && pop24[endIdx + 1] >= TH
                    && now.plusHours(endIdx + 1).toLocalDate().equals(startDay)) {
                endIdx++;
            }

            String dayLabel = (dayDiff == 0) ? "오늘" : "내일";
            int sh = startTs.getHour();
            int eh = now.plusHours(endIdx).getHour();
            parts.add(dayLabel + " " + hourRange(sh, eh));

            hourIdx = endIdx + 1;
        }
        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    /** D+0~6 오전/오후 요약 생성 */
    private List<String> buildDayParts(ClimateService.ForecastSeries fs) {
        if (fs == null || fs.ampm7x2() == null) return List.of();

        Byte[][] a = fs.ampm7x2();
        List<String> parts = new ArrayList<>(14);
        for (int d = 0; d <= 6; d++) {
            String dl = toDayLabel(d);
            if (n(a[d][0]) >= TH) parts.add(dl + " 오전");
            if (n(a[d][1]) >= TH) parts.add(dl + " 오후");
        }
        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    private String toDayLabel(int d) {
        return switch (d) {
            case 0 -> "오늘";
            case 1 -> "내일";
            case 2 -> "모레";
            default -> d + "일 후"; };
    }

    /** 시간 범위 표기: 단일 시면 "HH시", 구간이면 "HH~HH시" */
    private String hourRange(int startHour, int endHour) {
        return (startHour == endHour)
                ? String.format("%02d시", startHour)
                : String.format("%02d~%02d시", startHour, endHour);
    }
}