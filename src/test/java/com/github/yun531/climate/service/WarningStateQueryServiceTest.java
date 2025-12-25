package com.github.yun531.climate.service;


import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.WarningLevel;
import com.github.yun531.climate.service.query.dto.WarningStateDto;
import com.github.yun531.climate.entity.WarningState;
import com.github.yun531.climate.repository.WarningStateRepository;
import com.github.yun531.climate.service.query.WarningStateQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.github.yun531.climate.util.TimeUtil.nowMinutes;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(statements = {
        "SET FOREIGN_KEY_CHECKS = 0",
        "DELETE FROM warning_state",
        "SET FOREIGN_KEY_CHECKS = 1"
})
class WarningStateQueryServiceTest {

    @Autowired
    WarningStateQueryService service;
    @Autowired WarningStateRepository repo;
    @Autowired JdbcTemplate jdbc;

    @Test
    void findLatestByRegionAndKind_지역별_kind별_최신1건씩_반환() {
        int regionId01 = 3, regionId02 = 4;

        // R1: RAIN 2건(시간 다르게), HEAT 1건
        WarningState r1rainOld = repo.save(WarningState.builder().regionId(regionId01).kind(WarningKind.RAIN).level(WarningLevel.ADVISORY).build());
        WarningState r1rainNew = repo.save(WarningState.builder().regionId(regionId01).kind(WarningKind.RAIN).level(WarningLevel.WARNING).build());
        WarningState r1heat    = repo.save(WarningState.builder().regionId(regionId01).kind(WarningKind.HEAT).level(WarningLevel.ADVISORY).build());

        jdbc.update("UPDATE warning_state SET updated_at='2025-11-04 06:00:00' WHERE warning_id=?", r1rainOld.getWarningId());
        jdbc.update("UPDATE warning_state SET updated_at='2025-11-04 07:00:00' WHERE warning_id=?", r1rainNew.getWarningId());
        jdbc.update("UPDATE warning_state SET updated_at='2025-11-04 06:00:00' WHERE warning_id=?", r1heat.getWarningId());

        // R2: WIND 1건
        WarningState r2wind = repo.save(WarningState.builder().regionId(regionId02).kind(WarningKind.WIND).level(WarningLevel.ADVISORY).build());
        jdbc.update("UPDATE warning_state SET updated_at='2025-11-04 08:00:00' WHERE warning_id=?", r2wind.getWarningId());

        // when
        Map<Integer, Map<WarningKind, WarningStateDto>> map =
                service.findLatestByRegionAndKind(List.of(regionId01, regionId02));

        // then
        assertThat(map.keySet()).containsExactly(regionId01, regionId02);

        Map<WarningKind, WarningStateDto> r1 = map.get(regionId01);
        assertThat(r1.keySet()).containsExactlyInAnyOrder(WarningKind.RAIN, WarningKind.HEAT);
        assertThat(r1.get(WarningKind.RAIN).getLevel()).isEqualTo(WarningLevel.WARNING);   // 최신 선택
        assertThat(r1.get(WarningKind.HEAT).getLevel()).isEqualTo(WarningLevel.ADVISORY);

        Map<WarningKind, WarningStateDto> r2 = map.get(regionId02);
        assertThat(r2.keySet()).containsExactly(WarningKind.WIND);
        assertThat(r2.get(WarningKind.WIND).getLevel()).isEqualTo(WarningLevel.ADVISORY);
    }

    @Test
    void isNewlyIssuedSince_업데이트시각이_since보다_나중이면_true() {
        int regionId = 1;

        WarningStateDto dto = new WarningStateDto(
                regionId, WarningKind.RAIN, WarningLevel.ADVISORY,
                LocalDateTime.parse("2025-11-04T07:00:00")
        );
        LocalDateTime since = LocalDateTime.parse("2025-11-04T06:59:59");

        assertThat(service.isNewlyIssuedSince(dto, since)).isTrue();
    }

    @Test
    void isNewlyIssuedSince_null이나_동일이거나_이전이면_false() {
        int regionId = 1;

        WarningStateDto nullTime = new WarningStateDto(1, WarningKind.RAIN, WarningLevel.ADVISORY, null);
        assertThat(service.isNewlyIssuedSince(nullTime, nowMinutes())).isFalse();

        WarningStateDto same = new WarningStateDto(
                regionId, WarningKind.RAIN, WarningLevel.ADVISORY,
                LocalDateTime.parse("2025-11-04T07:00:00")
        );
        assertThat(service.isNewlyIssuedSince(same, LocalDateTime.parse("2025-11-04T07:00:00"))).isFalse();

        WarningStateDto older = new WarningStateDto(
                regionId, WarningKind.RAIN, WarningLevel.ADVISORY,
                LocalDateTime.parse("2025-11-04T06:00:00")
        );
        assertThat(service.isNewlyIssuedSince(older, LocalDateTime.parse("2025-11-04T07:00:00"))).isFalse();
    }
}