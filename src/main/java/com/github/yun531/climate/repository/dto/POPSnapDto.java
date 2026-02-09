package com.github.yun531.climate.repository.dto;

import com.github.yun531.climate.service.notification.model.PopView;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class POPSnapDto {

    private int snapId;
    private String regionId;
    private LocalDateTime reportTime;

    private PopView pop;

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
        this.snapId = (snapId == null) ? 0 : snapId;
        this.regionId = regionId;
        this.reportTime = reportTime;

        // 시간대별: (validAt, pop) 26개를 PopView.HourlyPopSeries26로 묶기
        // hourly 26개
        List<PopView.HourlyPopSeries26.Point> pts = new ArrayList<>(PopView.HOURLY_SIZE);
        add(pts, validAtA01, popA01); add(pts, validAtA02, popA02);
        add(pts, validAtA03, popA03); add(pts, validAtA04, popA04);
        add(pts, validAtA05, popA05); add(pts, validAtA06, popA06);
        add(pts, validAtA07, popA07); add(pts, validAtA08, popA08);
        add(pts, validAtA09, popA09); add(pts, validAtA10, popA10);
        add(pts, validAtA11, popA11); add(pts, validAtA12, popA12);
        add(pts, validAtA13, popA13); add(pts, validAtA14, popA14);
        add(pts, validAtA15, popA15); add(pts, validAtA16, popA16);
        add(pts, validAtA17, popA17); add(pts, validAtA18, popA18);
        add(pts, validAtA19, popA19); add(pts, validAtA20, popA20);
        add(pts, validAtA21, popA21); add(pts, validAtA22, popA22);
        add(pts, validAtA23, popA23); add(pts, validAtA24, popA24);
        add(pts, validAtA25, popA25); add(pts, validAtA26, popA26);

        PopView.HourlyPopSeries26 hourly = new PopView.HourlyPopSeries26(pts);

        // 일자별: AM/PM 7개는 기존대로
        // daily 7개
        PopView.DailyPopSeries7 daily = new PopView.DailyPopSeries7(List.of(
                new PopView.DailyPopSeries7.DailyPop(n(popA0dAm), n(popA0dPm)),
                new PopView.DailyPopSeries7.DailyPop(n(popA1dAm), n(popA1dPm)),
                new PopView.DailyPopSeries7.DailyPop(n(popA2dAm), n(popA2dPm)),
                new PopView.DailyPopSeries7.DailyPop(n(popA3dAm), n(popA3dPm)),
                new PopView.DailyPopSeries7.DailyPop(n(popA4dAm), n(popA4dPm)),
                new PopView.DailyPopSeries7.DailyPop(n(popA5dAm), n(popA5dPm)),
                new PopView.DailyPopSeries7.DailyPop(n(popA6dAm), n(popA6dPm))
        ));

        this.pop = new PopView(hourly, daily, reportTime);
    }

    private static void add(List<PopView.HourlyPopSeries26.Point> out, LocalDateTime validAt, Integer pop) {
        out.add(new PopView.HourlyPopSeries26.Point(validAt, n(pop)));
    }

    /** Integer → int 변환 + null → 0 치환 */
    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}