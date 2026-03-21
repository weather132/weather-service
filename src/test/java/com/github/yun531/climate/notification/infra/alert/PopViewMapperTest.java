package com.github.yun531.climate.notification.infra.alert;

import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.snapshot.domain.readmodel.DailyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PopViewMapperTest {

    private final PopViewMapper mapper = new PopViewMapper();

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);

    @Test
    @DisplayName("정상 스냅샷 -> PopView 변환: hourly 26개, daily 7개 규격")
    void normalSnapshot_correctSizes() {
        WeatherSnapshot snap = buildSnapshot(26, 7);

        PopView view = mapper.toPopView(snap);

        assertThat(view.hourly().pops()).hasSize(26);
        assertThat(view.daily().pops()).hasSize(7);
        assertThat(view.reportTime()).isEqualTo(ANNOUNCE_TIME);
    }

    @Test
    @DisplayName("hourly가 26개 미만이면 패딩으로 채움 (null)")
    void shortHourly_paddedTo26() {
        WeatherSnapshot snap = buildSnapshot(10, 7);

        PopView view = mapper.toPopView(snap);

        assertThat(view.hourly().pops()).hasSize(26);
        // 10번째 이후는 패딩 (effectiveTime=null, pop=null)
        assertThat(view.hourly().pops().get(10).validAt()).isNull();
        assertThat(view.hourly().pops().get(10).pop()).isNull();
    }

    @Test
    @DisplayName("null 스냅샷 -> null 반환")
    void nullSnapshot_returnsNull() {
        assertThat(mapper.toPopView(null)).isNull();
    }

    @Test
    @DisplayName("toPair — 둘 다 정상 -> Pair 생성")
    void toPair_bothValid() {
        WeatherSnapshot cur  = buildSnapshot(26, 7);
        WeatherSnapshot prev = buildSnapshot(26, 7);

        PopView.Pair pair = mapper.toPair(cur, prev);

        assertThat(pair).isNotNull();
        assertThat(pair.current()).isNotNull();
        assertThat(pair.previous()).isNotNull();
    }

    @Test
    @DisplayName("toPair — 하나라도 null이면 null")
    void toPair_oneNull_returnsNull() {
        assertThat(mapper.toPair(null, buildSnapshot(26, 7))).isNull();
        assertThat(mapper.toPair(buildSnapshot(26, 7), null)).isNull();
    }

    @Test
    @DisplayName("POP null 값 -> null 유지 (0으로 대체하지 않음)")
    void nullPop_staysNull() {
        List<HourlyPoint> hourly = new ArrayList<>(26);
        for (int i = 0; i < 26; i++) {
            hourly.add(new HourlyPoint(ANNOUNCE_TIME.plusHours(i + 1), null, null));
        }
        WeatherSnapshot snap = new WeatherSnapshot("R1", ANNOUNCE_TIME, hourly, List.of());

        PopView view = mapper.toPopView(snap);

        assertThat(view.hourly().pops().get(0).pop()).isNull();
    }

    @Test
    @DisplayName("daily 데이터 없음 -> null로 채워진 DailyPop")
    void emptyDaily_nullPadded() {
        WeatherSnapshot snap = new WeatherSnapshot("R1", ANNOUNCE_TIME,
                buildHourlyPoints(26), List.of());

        PopView view = mapper.toPopView(snap);

        assertThat(view.daily().pops()).hasSize(7);
        assertThat(view.daily().pops().get(0).am()).isNull();
        assertThat(view.daily().pops().get(0).pm()).isNull();
    }

    @Test
    @DisplayName("daily 실제 데이터 -> Integer 값 유지")
    void dailyWithData_valuesPreserved() {
        List<DailyPoint> daily = new ArrayList<>(7);
        daily.add(new DailyPoint(0, -5, 5, 30, 60));
        for (int dayOffset = 1; dayOffset < 7; dayOffset++) {
            daily.add(new DailyPoint(dayOffset, null, null, null, null));
        }

        WeatherSnapshot snap = new WeatherSnapshot("R1", ANNOUNCE_TIME, buildHourlyPoints(26), daily);

        PopView view = mapper.toPopView(snap);

        assertThat(view.daily().pops().get(0).am()).isEqualTo(30);
        assertThat(view.daily().pops().get(0).pm()).isEqualTo(60);
    }

    // -- 헬퍼 --

    private WeatherSnapshot buildSnapshot(int hourlyCount, int dailyCount) {
        List<DailyPoint> daily = new ArrayList<>(dailyCount);
        for (int d = 0; d < dailyCount; d++) {
            daily.add(new DailyPoint(d, -d, d + 10, d * 10, d * 10 + 5));
        }
        return new WeatherSnapshot("R1", ANNOUNCE_TIME, buildHourlyPoints(hourlyCount), daily);
    }

    private List<HourlyPoint> buildHourlyPoints(int count) {
        List<HourlyPoint> hourly = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hourly.add(new HourlyPoint(ANNOUNCE_TIME.plusHours(i + 1), i * 2, i * 3));
        }
        return hourly;
    }
}