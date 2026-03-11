package com.github.yun531.climate.warning.infra.reader;

import com.github.yun531.climate.TestFirebaseConfig;
import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.model.WarningLevel;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JpaIssuedWarningReader 통합 테스트.
 * H2 + data-h2.sql 기반으로 DB -> IssuedWarning 변환 체인 검증.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestFirebaseConfig.class)
class JpaIssuedWarningReaderIntegrationTest {

    @Autowired
    private JpaIssuedWarningReader reader;

    @Test
    @DisplayName("11B10101 -> RAIN ADVISORY 조회")
    void region1_rainAdvisory() {
        Map<WarningKind, IssuedWarning> result = reader.loadLatestByKind("11B10101");

        assertThat(result).containsKey(WarningKind.RAIN);

        IssuedWarning rain = result.get(WarningKind.RAIN);
        assertThat(rain.level()).isEqualTo(WarningLevel.ADVISORY);
        assertThat(rain.regionId()).isEqualTo("11B10101");
    }

    @Test
    @DisplayName("11B20201 -> HEAT WARNING 조회")
    void region2_heatWarning() {
        Map<WarningKind, IssuedWarning> result = reader.loadLatestByKind("11B20201");

        assertThat(result).containsKey(WarningKind.HEAT);
        assertThat(result.get(WarningKind.HEAT).level()).isEqualTo(WarningLevel.WARNING);
    }

    @Test
    @DisplayName("존재하지 않는 regionId -> 빈 맵")
    void nonExistentRegion_emptyMap() {
        Map<WarningKind, IssuedWarning> result = reader.loadLatestByKind("99999999");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("blank regionId -> 빈 맵")
    void blankRegionId_emptyMap() {
        assertThat(reader.loadLatestByKind("")).isEmpty();
        assertThat(reader.loadLatestByKind(null)).isEmpty();
    }
}
