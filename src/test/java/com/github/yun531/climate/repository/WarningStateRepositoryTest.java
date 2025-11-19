package com.github.yun531.climate.repository;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.domain.WarningLevel;
import com.github.yun531.climate.entity.WarningState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(statements = {
        "SET FOREIGN_KEY_CHECKS = 0",
        "TRUNCATE TABLE warning_state",
        "SET FOREIGN_KEY_CHECKS = 1"
})
class WarningStateRepositoryTest {

    @Autowired WarningStateRepository repo;
    @Autowired JdbcTemplate jdbc;

    @Test
    void findByRegionIdIn_여러지역조회() {
        WarningState a = repo.save(WarningState.builder()
                .regionId(101).kind(WarningKind.RAIN).level(WarningLevel.ADVISORY).build());
        WarningState b = repo.save(WarningState.builder()
                .regionId(202).kind(WarningKind.HEAT).level(WarningLevel.WARNING).build());
        WarningState c = repo.save(WarningState.builder()
                .regionId(303).kind(WarningKind.WIND).level(WarningLevel.ADVISORY).build());

        // when
        List<WarningState> list = repo.findByRegionIdIn(List.of(101, 202));

        // then
        assertThat(list).extracting("regionId").containsExactlyInAnyOrder(101, 202);
        assertThat(list).doesNotContain(c);
    }

    @Test
    void findTopByRegionIdOrderByUpdatedAtDesc_최신한건_반환() {
        // given: 같은 regionId 에 두 건 저장
        WarningState ws1 = repo.save(WarningState.builder()
                .regionId(777).kind(WarningKind.RAIN).level(WarningLevel.ADVISORY).build());
        WarningState ws2 = repo.save(WarningState.builder()
                .regionId(777).kind(WarningKind.RAIN).level(WarningLevel.WARNING).build());

        // updated_at을 직접 갱신(정밀도 보장용)
        jdbc.update("UPDATE warning_state SET updated_at = '2025-11-04 05:00:00' WHERE warning_id = ?", ws1.getWarningId());
        jdbc.update("UPDATE warning_state SET updated_at = '2025-11-04 06:00:00' WHERE warning_id = ?", ws2.getWarningId());

        // when
        Optional<WarningState> top = repo.findTopByRegionIdOrderByUpdatedAtDesc(777);

        // then
        assertThat(top).isPresent();
        assertThat(top.get().getWarningId()).isEqualTo(ws2.getWarningId()); // 더 늦은 시간
        assertThat(top.get().getLevel()).isEqualTo(WarningLevel.WARNING);
    }
}