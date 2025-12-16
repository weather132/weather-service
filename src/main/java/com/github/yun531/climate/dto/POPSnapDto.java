package com.github.yun531.climate.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class POPSnapDto {

    private int snapId;
    private int regionId;
    private LocalDateTime reportTime;

    private PopSeries24 hourly;         // ---- 시간대별 POP (0~23시) ---- //
    private PopDailySeries7 daily;      // ---- 일자별 (0~6일) 오전/오후 POP ----

    public POPSnapDto(
            Integer snapId,
            Integer regionId,
            LocalDateTime reportTime,
            Integer popA01, Integer popA02, Integer popA03, Integer popA04,
            Integer popA05, Integer popA06, Integer popA07, Integer popA08, Integer popA09,
            Integer popA10, Integer popA11, Integer popA12, Integer popA13, Integer popA14,
            Integer popA15, Integer popA16, Integer popA17, Integer popA18, Integer popA19,
            Integer popA20, Integer popA21, Integer popA22, Integer popA23, Integer popA24, Integer popA25, Integer popA26,
            Integer popA0dAm, Integer popA0dPm,
            Integer popA1dAm, Integer popA1dPm,
            Integer popA2dAm, Integer popA2dPm,
            Integer popA3dAm, Integer popA3dPm,
            Integer popA4dAm, Integer popA4dPm,
            Integer popA5dAm, Integer popA5dPm,
            Integer popA6dAm, Integer popA6dPm
    ) {
        this.snapId = snapId;
        this.regionId = regionId;
        this.reportTime = reportTime;

        // ---- 시간대별 POP 24개 → PopSeries24 ----
        this.hourly = new PopSeries24(List.of(
                n(popA01), n(popA02), n(popA03), n(popA04),
                n(popA05), n(popA06), n(popA07), n(popA08), n(popA09),
                n(popA10), n(popA11), n(popA12), n(popA13), n(popA14),
                n(popA15), n(popA16), n(popA17), n(popA18), n(popA19),
                n(popA20), n(popA21), n(popA22), n(popA23), n(popA24), n(popA25), n(popA26)
        ));

        // ---- 일자별 AM/PM POP → PopDailySeries7 ----
        this.daily = new PopDailySeries7(List.of(
                new PopDailySeries7.DailyPop(n(popA0dAm), n(popA0dPm)),
                new PopDailySeries7.DailyPop(n(popA1dAm), n(popA1dPm)),
                new PopDailySeries7.DailyPop(n(popA2dAm), n(popA2dPm)),
                new PopDailySeries7.DailyPop(n(popA3dAm), n(popA3dPm)),
                new PopDailySeries7.DailyPop(n(popA4dAm), n(popA4dPm)),
                new PopDailySeries7.DailyPop(n(popA5dAm), n(popA5dPm)),
                new PopDailySeries7.DailyPop(n(popA6dAm), n(popA6dPm))
        ));
    }

    /** Integer → int 변환 + null → 0 치환 */
    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}