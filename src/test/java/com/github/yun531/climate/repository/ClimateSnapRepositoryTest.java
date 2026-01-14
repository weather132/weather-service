package com.github.yun531.climate.repository;

import com.github.yun531.climate.repository.dto.POPSnapDto;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
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

        // ====== 필요한 컬럼만 명시해서 insert (나머지는 NULL) ======
        "INSERT INTO climate_snap (" +
                "snap_id, region_id, report_time, " +
                "valid_at_a01, valid_at_a02, valid_at_a03, valid_at_a04, valid_at_a05, valid_at_a06, valid_at_a07, valid_at_a08, valid_at_a09, valid_at_a10, valid_at_a11, valid_at_a12, valid_at_a13, valid_at_a14, valid_at_a15, valid_at_a16, valid_at_a17, valid_at_a18, valid_at_a19, valid_at_a20, valid_at_a21, valid_at_a22, valid_at_a23, valid_at_a24, valid_at_a25, valid_at_a26, " +
                "pop_a01, pop_a02, pop_a03, pop_a04, pop_a05, pop_a06, pop_a07, pop_a08, pop_a09, pop_a10, pop_a11, pop_a12, pop_a13, pop_a14, pop_a15, pop_a16, pop_a17, pop_a18, pop_a19, pop_a20, pop_a21, pop_a22, pop_a23, pop_a24, pop_a25, pop_a26" +
                ") VALUES " +

                // snap_id=10 (CURRENT 가정)
                "(10, '11B10101', '2025-11-18 08:00:00', " +
                // valid_at_a01~a26 (report_time + 1h ~ +26h)
                "'2025-11-18 09:00:00','2025-11-18 10:00:00','2025-11-18 11:00:00','2025-11-18 12:00:00'," +
                "'2025-11-18 13:00:00','2025-11-18 14:00:00','2025-11-18 15:00:00','2025-11-18 16:00:00','2025-11-18 17:00:00'," +
                "'2025-11-18 18:00:00','2025-11-18 19:00:00','2025-11-18 20:00:00','2025-11-18 21:00:00','2025-11-18 22:00:00'," +
                "'2025-11-18 23:00:00','2025-11-19 00:00:00','2025-11-19 01:00:00','2025-11-19 02:00:00','2025-11-19 03:00:00'," +
                "'2025-11-19 04:00:00','2025-11-19 05:00:00','2025-11-19 06:00:00','2025-11-19 07:00:00','2025-11-19 08:00:00'," +
                "'2025-11-19 09:00:00','2025-11-19 10:00:00', " +
                // pop_a01~a26
                "50,40,50,60,60, 60,40,30,20,10, 0,0,10,20,20, 30,40,60,10,0, 20,40,70,40, 60,70" +
                ")," +

                // snap_id=1 (PREVIOUS 가정)
                "(1, '11B10101', '2025-11-18 11:00:00', " +
                "'2025-11-18 12:00:00','2025-11-18 13:00:00','2025-11-18 14:00:00','2025-11-18 15:00:00'," +
                "'2025-11-18 16:00:00','2025-11-18 17:00:00','2025-11-18 18:00:00','2025-11-18 19:00:00','2025-11-18 20:00:00'," +
                "'2025-11-18 21:00:00','2025-11-18 22:00:00','2025-11-18 23:00:00','2025-11-19 00:00:00','2025-11-19 01:00:00'," +
                "'2025-11-19 02:00:00','2025-11-19 03:00:00','2025-11-19 04:00:00','2025-11-19 05:00:00','2025-11-19 06:00:00'," +
                "'2025-11-19 07:00:00','2025-11-19 08:00:00','2025-11-19 09:00:00','2025-11-19 10:00:00','2025-11-19 11:00:00'," +
                "'2025-11-19 12:00:00','2025-11-19 13:00:00', " +
                "40,50,60,60,60, 60,30,20,10,0, 0,10,20,20,30, 60,60,60,0,20, 40,70,40,60, 70,80" +
                ")",

        "COMMIT"
})
class ClimateSnapRepositoryTest {

    @Autowired
    ClimateSnapRepository repo;

    @Test
    void findPopInfoBySnapIdsAndRegionId_DTO_프로젝션_검증_DB값기반() {
        // given
        String regionId = "11B10101";
        List<Integer> snapIds = List.of(
                SnapKindEnum.SNAP_CURRENT.getCode(),
                SnapKindEnum.SNAP_PREVIOUS.getCode()
        );

        // when
        List<POPSnapDto> result = repo.findPopInfoBySnapIdsAndRegionId(snapIds, regionId);

        // then
        assertThat(result).hasSize(2);

        POPSnapDto s1  = result.stream().filter(d -> d.getSnapId() == 1).findFirst().orElseThrow();
        POPSnapDto s10 = result.stream().filter(d -> d.getSnapId() == 10).findFirst().orElseThrow();

        // 공통 필드
        assertThat(s1.getRegionId()).isEqualTo(regionId);
        assertThat(s10.getRegionId()).isEqualTo(regionId);

        assertThat(s1.getReportTime()).isNotNull();
        assertThat(s10.getReportTime()).isNotNull();

        // POP 검증 (offset 1~26)
        // snap_id=1  → POP_A01=40, POP_A24=60
        assertThat(s1.getHourly().get(1)).isEqualTo(40);
        assertThat(s1.getHourly().get(24)).isEqualTo(60);

        // snap_id=10 → POP_A01=50, POP_A24=40
        assertThat(s10.getHourly().get(1)).isEqualTo(50);
        assertThat(s10.getHourly().get(24)).isEqualTo(40);

        // validAt 검증 (Point.validAt)
        assertThat(s1.getHourly().validAt(1)).isEqualTo(LocalDateTime.parse("2025-11-18T12:00:00"));
        assertThat(s10.getHourly().validAt(1)).isEqualTo(LocalDateTime.parse("2025-11-18T09:00:00"));
    }

    @Test
    void 기본_파인더_검증_DB값기반() {
        // given
        String regionId = "11B10101";

        // when & then
        // DB에는 동일 region_id 레코드가 2건(snap_id=1,10)
        assertThat(repo.findByRegionId(regionId)).hasSize(2);
        assertThat(repo.findBySnapIdIn(List.of(1, 10))).hasSize(2);
        assertThat(repo.findBySnapIdAndRegionId(1, regionId)).isNotNull();
    }
}