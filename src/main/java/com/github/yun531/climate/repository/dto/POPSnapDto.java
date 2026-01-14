package com.github.yun531.climate.repository.dto;

import com.github.yun531.climate.service.notification.model.PopDailySeries7;
import com.github.yun531.climate.service.notification.model.PopSeries24;
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
    private String regionId;
    private LocalDateTime reportTime;

    private PopSeries24 hourly;                  // ---- 시간대별 POP (1~26시) ---- //
    private PopDailySeries7 daily;               // ---- 일자별 (0~6일) 오전/오후 POP ----

    public POPSnapDto(
            Integer snapId,
            String regionId,
            LocalDateTime reportTime,

            LocalDateTime validAtA01, LocalDateTime validAtA02, LocalDateTime validAtA03, LocalDateTime validAtA04, LocalDateTime validAtA05,
            LocalDateTime validAtA06, LocalDateTime validAtA07, LocalDateTime validAtA08, LocalDateTime validAtA09, LocalDateTime validAtA10,
            LocalDateTime validAtA11, LocalDateTime validAtA12, LocalDateTime validAtA13, LocalDateTime validAtA14, LocalDateTime validAtA15,
            LocalDateTime validAtA16, LocalDateTime validAtA17, LocalDateTime validAtA18, LocalDateTime validAtA19, LocalDateTime validAtA20,
            LocalDateTime validAtA21, LocalDateTime validAtA22, LocalDateTime validAtA23, LocalDateTime validAtA24, LocalDateTime validAtA25, LocalDateTime validAtA26,

            Integer popA01, Integer popA02, Integer popA03, Integer popA04, Integer popA05,
            Integer popA06, Integer popA07, Integer popA08, Integer popA09, Integer popA10,
            Integer popA11, Integer popA12, Integer popA13, Integer popA14, Integer popA15,
            Integer popA16, Integer popA17, Integer popA18, Integer popA19, Integer popA20,
            Integer popA21, Integer popA22, Integer popA23, Integer popA24, Integer popA25, Integer popA26,

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

        // 시간대별: (validAt, pop) 26개를 PopSeries24로 묶기
        this.hourly = new PopSeries24(List.of(
                new PopSeries24.Point(validAtA01, n(popA01)), new PopSeries24.Point(validAtA02, n(popA02)),
                new PopSeries24.Point(validAtA03, n(popA03)), new PopSeries24.Point(validAtA04, n(popA04)),
                new PopSeries24.Point(validAtA05, n(popA05)), new PopSeries24.Point(validAtA06, n(popA06)),
                new PopSeries24.Point(validAtA07, n(popA07)), new PopSeries24.Point(validAtA08, n(popA08)),
                new PopSeries24.Point(validAtA09, n(popA09)), new PopSeries24.Point(validAtA10, n(popA10)),
                new PopSeries24.Point(validAtA11, n(popA11)), new PopSeries24.Point(validAtA12, n(popA12)),
                new PopSeries24.Point(validAtA13, n(popA13)), new PopSeries24.Point(validAtA14, n(popA14)),
                new PopSeries24.Point(validAtA15, n(popA15)), new PopSeries24.Point(validAtA16, n(popA16)),
                new PopSeries24.Point(validAtA17, n(popA17)), new PopSeries24.Point(validAtA18, n(popA18)),
                new PopSeries24.Point(validAtA19, n(popA19)), new PopSeries24.Point(validAtA20, n(popA20)),
                new PopSeries24.Point(validAtA21, n(popA21)), new PopSeries24.Point(validAtA22, n(popA22)),
                new PopSeries24.Point(validAtA23, n(popA23)), new PopSeries24.Point(validAtA24, n(popA24)),
                new PopSeries24.Point(validAtA25, n(popA25)), new PopSeries24.Point(validAtA26, n(popA26))
        ));

        // 일자별: AM/PM 7개는 기존대로
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