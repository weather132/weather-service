package com.github.yun531.climate.repository;

import com.github.yun531.climate.entity.ClimateSnap;
import com.github.yun531.climate.entity.ClimateSnapId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(statements = {
        "SET FOREIGN_KEY_CHECKS = 0",
        "DELETE FROM climate_snap",
        "SET FOREIGN_KEY_CHECKS = 1",

        // 필요한 컬럼만 명시해서 insert (나머지는 NULL)
        "INSERT INTO climate_snap (" +
                "snap_id, region_id, report_time, series_start_time, " +
                "temp_A01, temp_A26, " +
                "POP_A01, POP_A26" +
                ") VALUES " +

                // region 11B10101: snap_id=10 (이전 스냅)
                "(10, '11B10101', '2025-11-18 08:00:00', '2025-11-18 09:00:00', " +
                "1, 5, " +
                "50, 70" +
                ")," +

                // region 11B10101: snap_id=1 (현재 스냅)
                "(1, '11B10101', '2025-11-18 11:00:00', '2025-11-18 12:00:00', " +
                "2, 6, " +
                "40, 80" +
                ")," +

                // 다른 region 하나 추가( snap_id=1이 여러 region에 존재할 수 있음을 검증용 )
                "(1, '11B20201', '2025-11-18 11:00:00', '2025-11-18 12:00:00', " +
                "11, 15, " +
                "30, 60" +
                ")",

        "COMMIT"
})
class ClimateSnapRepositoryTest {

    @Autowired
    ClimateSnapRepository repo;

    @Test
    void 기본_파인더_검증_DB값기반() {
        // given
        String regionId = "11B10101";

        // when & then
        // DB에는 동일 region_id 레코드가 2건(snap_id=1,10)
        assertThat(repo.findByRegionId(regionId)).hasSize(2);

        // snap_id=1은 region이 2개(11B10101, 11B20201)라서 총 3건
        assertThat(repo.findBySnapIdIn(List.of(1, 10))).hasSize(3);

        assertThat(repo.findBySnapIdAndRegionId(1, regionId)).isNotNull();
    }

    @Test
    void snapId는_여러_region에_존재할수있다_findBySnapIdIn_검증() {
        // snap_id=1은 region 2개로 저장됨
        List<ClimateSnap> list = repo.findBySnapIdIn(List.of(1));
        assertThat(list).hasSize(2);
        assertThat(list).extracting(ClimateSnap::getRegionId)
                .containsExactlyInAnyOrder("11B10101", "11B20201");
    }

    @Test
    void 복합키_findById_조회된다() {
        ClimateSnapId id = new ClimateSnapId(10, "11B10101");

        var opt = repo.findById(id);
        assertThat(opt).isPresent();

        ClimateSnap snap = opt.get();
        assertThat(snap.getSnapId()).isEqualTo(10);
        assertThat(snap.getRegionId()).isEqualTo("11B10101");
        assertThat(snap.getReportTime()).isEqualTo(LocalDateTime.parse("2025-11-18T08:00:00"));
        assertThat(snap.getSeriesStartTime()).isEqualTo(LocalDateTime.parse("2025-11-18T09:00:00"));
    }

    @Test
    void seriesStartTime으로_시간축을_재구성할수있다() {
        ClimateSnap snap = repo.findBySnapIdAndRegionId(10, "11B10101");
        assertThat(snap).isNotNull();

        LocalDateTime base = snap.getSeriesStartTime();

        // A01의 effectiveTime = seriesStartTime (+0h)
        LocalDateTime t01 = base.plusHours(0);
        // A26의 effectiveTime = seriesStartTime (+25h)
        LocalDateTime t26 = base.plusHours(25);

        assertThat(t01).isEqualTo(LocalDateTime.parse("2025-11-18T09:00:00"));
        assertThat(t26).isEqualTo(LocalDateTime.parse("2025-11-19T10:00:00"));
    }

    @Test
    void 일부_hourly컬럼만_넣어도_NULL로_정상조회된다() {
        // 우리가 insert에서 temp/pop은 A01, A26만 넣었고 나머지는 NULL이다.
        ClimateSnap snap = repo.findBySnapIdAndRegionId(10, "11B10101");
        assertThat(snap).isNotNull();

        assertThat(snap.getTempA01()).isEqualTo(1);
        assertThat(snap.getTempA26()).isEqualTo(5);

        assertThat(snap.getPopA01()).isEqualTo(50);
        assertThat(snap.getPopA26()).isEqualTo(70);

        // 중간 값은 insert하지 않았으니 NULL이어야 한다(대표로 A02 확인)
        assertThat(snap.getTempA02()).isNull();
        assertThat(snap.getPopA02()).isNull();
    }
}