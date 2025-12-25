package com.github.yun531.climate.repository;


import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.repository.dto.POPSnapDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(statements = {
        "SET FOREIGN_KEY_CHECKS = 0",
        "DELETE FROM climate_snap",
        "SET FOREIGN_KEY_CHECKS = 1",
        "insert into climate_snap values " +
                "(10, 1, '2025-11-18 08:00:00'," +
                    " 1,2,3,4,5,6,7,8,9,10,11,12,13,12,11,10,9,8,7,6,5,4,3,2,1,5," +
                    " 5,5, 7,7, 8,8, 7,7, 6,6, 5,5, 6,6," +
                    " 50,40,50,60,60, 60,40,30,20,10, 0,0,10,20,20, 30,40,60,10,0, 20,40,70,40, 60, 70," +
                    " 70,40, 40,40, 30,30, 40,40, 30,60, 50,50, 40,40)," +
                "(1, 1, '2025-11-18 11:00:00'," +
                    " 1,2,3,4,5,6,7,8,9,10,11,12,13,12,11,10,9,8,7,6,5,4,3,2,1,5," +
                    " 5,5, 7,7, 8,8, 7,7, 6,6, 5,5, 6,6," +
                    " 40,50,60,60,60, 60,30,20,10,0, 0,10,20,20,30, 60,60,60,0,20, 40,70,40,60, 70, 80," +
                    " 30,30, 40,40, 30,60, 50,50, 40,40, 20,20, 0,0)"
})
class ClimateSnapRepositoryTest {

    @Autowired
    ClimateSnapRepository repo;

    @Test
    void findPopInfoBySnapIdsAndRegionId_DTO_프로젝션_검증_DB값기반() {
        // given
        int regionId = 1;
        List<Integer> snapIds = List.of(SnapKindEnum.SNAP_CURRENT.getCode(),
                                        SnapKindEnum.SNAP_PREVIOUS.getCode());

        // when
        List<POPSnapDto> result = repo.findPopInfoBySnapIdsAndRegionId(snapIds, regionId);

        // then
        assertThat(result).hasSize(2);

        POPSnapDto s1  = result.stream().filter(d -> d.getSnapId() == 1L).findFirst().orElseThrow();
        POPSnapDto s10 = result.stream().filter(d -> d.getSnapId() == 10L).findFirst().orElseThrow();

        // 공통 필드
        assertThat(s1.getRegionId()).isEqualTo(regionId);
        assertThat(s10.getRegionId()).isEqualTo(regionId);

        assertThat(s1.getReportTime()).isNotNull();
        assertThat(s10.getReportTime()).isNotNull();

        // 스키마/데이터에 기초한 POP 검증
        // snap_id=1  → POP_A01=40, POP_A24=60
        assertThat(s1.getHourly().get(1)).isEqualTo(40);
        assertThat(s1.getHourly().get(24)).isEqualTo(60);

        // snap_id=10 → POP_A01=50, POP_A24=40
        assertThat(s10.getHourly().get(1)).isEqualTo(50);
        assertThat(s10.getHourly().get(24)).isEqualTo(40);
    }

    @Test
    void 기본_파인더_검증_DB값기반() {
        // given
        int regionId = 1;

        // when & then
        // DB에는 동일 region_id=1 레코드가 2건(snap_id=1,10)
        assertThat(repo.findByRegionId(regionId)).hasSize(2);
        assertThat(repo.findBySnapIdIn(List.of(1, 10))).hasSize(2);
        assertThat(repo.findBySnapIdAndRegionId(1, regionId)).isNotNull();
    }
}