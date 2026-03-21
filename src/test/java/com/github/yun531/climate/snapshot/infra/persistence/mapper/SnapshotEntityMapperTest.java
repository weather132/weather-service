package com.github.yun531.climate.snapshot.infra.persistence.mapper;

import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.persistence.entity.SnapshotEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotEntityMapperTest {

    private final SnapshotEntityMapper mapper = new SnapshotEntityMapper();

    private static final LocalDateTime ANNOUNCE_TIME     = LocalDateTime.of(2026, 1, 22, 5, 0);
    private static final LocalDateTime SERIES_START_TIME = ANNOUNCE_TIME.plusHours(1);

    @Test
    @DisplayName("정상 엔티티 -> WeatherSnapshot 변환: hourly 26개, daily 7개")
    void normalEntity_producesCorrectSnapshot() throws Exception {
        SnapshotEntity entity = buildEntity();

        WeatherSnapshot snap = mapper.toSnapshot(entity);

        assertThat(snap.regionId()).isEqualTo("11B10101");
        assertThat(snap.announceTime()).isEqualTo(ANNOUNCE_TIME);
        assertThat(snap.hourly()).hasSize(26);
        assertThat(snap.daily()).hasSize(7);
    }

    @Test
    @DisplayName("hourly validAt은 seriesStartTime 기반 1시간 간격")
    void hourlyValidAt_basedOnSeriesStart() throws Exception {
        SnapshotEntity entity = buildEntity();

        WeatherSnapshot snap = mapper.toSnapshot(entity);

        assertThat(snap.hourly().get(0).effectiveTime()).isEqualTo(SERIES_START_TIME);
        assertThat(snap.hourly().get(1).effectiveTime()).isEqualTo(SERIES_START_TIME.plusHours(1));
        assertThat(snap.hourly().get(25).effectiveTime()).isEqualTo(SERIES_START_TIME.plusHours(25));
    }

    @Test
    @DisplayName("daily dayOffset는 0~6 순서")
    void dailyDayOffset_0to6() throws Exception {
        SnapshotEntity entity = buildEntity();

        WeatherSnapshot snap = mapper.toSnapshot(entity);

        for (int i = 0; i < 7; i++) {
            assertThat(snap.daily().get(i).daysAhead()).isEqualTo(i);
        }
    }

    // -- 엔티티 빌더 (리플렉션 사용: @NoArgsConstructor(access=PROTECTED) 대응) --

    private SnapshotEntity buildEntity() throws Exception {
        SnapshotEntity entity = createEntityViaReflection();
        setField(entity, "snapId", 1);
        setField(entity, "regionId", "11B10101");
        setField(entity, "announceTime", ANNOUNCE_TIME);
        setField(entity, "seriesStartTime", SERIES_START_TIME);

        // temp A01~A26
        for (int i = 1; i <= 26; i++) {
            setField(entity, String.format("tempA%02d", i), i);
        }
        // POP A01~A26
        for (int i = 1; i <= 26; i++) {
            setField(entity, String.format("popA%02d", i), i * 3);
        }
        // daily temp min/max
        for (int d = 0; d <= 6; d++) {
            setField(entity, String.format("tempA%ddMin", d), -d);
            setField(entity, String.format("tempA%ddMax", d), d + 10);
        }
        // daily POP am/pm
        for (int d = 0; d <= 6; d++) {
            setField(entity, String.format("popA%ddAm", d), d * 10);
            setField(entity, String.format("popA%ddPm", d), d * 10 + 5);
        }

        return entity;
    }

    private SnapshotEntity createEntityViaReflection() throws Exception {
        var constructor = SnapshotEntity.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
