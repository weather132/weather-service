package com.github.yun531.climate.service.notification.rule.adjust;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.util.time.OffsetShiftUtil;
import io.micrometer.common.lang.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HourOffsetEventAdjuster {

    private final String hourKey;
    private final int maxShiftHours;

    public HourOffsetEventAdjuster(String hourKey, int maxShiftHours) {
        this.hourKey = hourKey;
        this.maxShiftHours = maxShiftHours;
    }

    /**
     * - baseTime(=entry.computedAt)을 now 기준으로 diffHours 시프트
     * - hourOffset <= diffHours 인 이벤트는 이미 지난 이벤트 → 제거
     * - 나머지는 hourOffset -= diffHours
     * - occurredAt(여기서는 event.occurredAt 역할)을 baseTime + diffHours로 통일
     */
    public List<AlertEvent> adjust(List<AlertEvent> events,
                                   @Nullable LocalDateTime baseTime,
                                   LocalDateTime now) {
        if (baseTime == null || events == null || events.isEmpty()) {
            return events == null ? List.of() : events;
        }

        OffsetShiftUtil.OffsetShift shift = OffsetShiftUtil.compute(baseTime, now, maxShiftHours);
        if (shift.diffHours() <= 0) {
            return events;
        }

        int diffHours = shift.diffHours();
        LocalDateTime shiftedTime = shift.shiftedBaseTime();

        List<AlertEvent> out = new ArrayList<>(events.size());

        for (AlertEvent e : events) {
            Integer hour = readHour(e);

            // hourOffset이 없으면: 시간만 시프트해서 유지
            if (hour == null) {
                out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, e.payload()));
                continue;
            }

            if (hour <= diffHours) {
                continue; // 이미 지난 이벤트
            }

            int newHour = hour - diffHours;

            Map<String, Object> oldPayload = e.payload();
            Map<String, Object> newPayload = (oldPayload == null) ? new HashMap<>() : new HashMap<>(oldPayload);
            newPayload.put(hourKey, newHour);

            out.add(new AlertEvent(e.type(), e.regionId(), shiftedTime, Map.copyOf(newPayload)));
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    @Nullable
    private Integer readHour(AlertEvent e) {
        Map<String, Object> payload = e.payload();
        if (payload == null) return null;

        Object v = payload.get(hourKey);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return null;
    }
}