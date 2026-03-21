package com.github.yun531.climate.snapshot.infra.reader;

import com.github.yun531.climate.TestFirebaseConfig;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JpaSnapshotReader 통합 테스트.
 * H2 + schema-h2.sql + data-h2.sql 기반으로 DB -> WeatherSnapshot 변환 체인 검증.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestFirebaseConfig.class)
class JpaSnapshotReaderIntegrationTest {

    @Autowired
    private JpaSnapshotReader reader;

    @Test
    @DisplayName("loadCurrent(11B10101) -> DB snap_id=1 조회 -> WeatherSnapshot 변환")
    void loadCurrent_region1_success() {
        WeatherSnapshot snap = reader.loadCurrent("11B10101");

        assertThat(snap).isNotNull();
        assertThat(snap.regionId()).isEqualTo("11B10101");
        assertThat(snap.hourly()).hasSize(26);
        assertThat(snap.daily()).hasSize(7);
    }

    @Test
    @DisplayName("loadPrevious(11B10101) -> DB snap_id=10 조회")
    void loadPrevious_region1_success() {
        WeatherSnapshot snap = reader.loadPrevious("11B10101");

        assertThat(snap).isNotNull();
        assertThat(snap.regionId()).isEqualTo("11B10101");
    }

    @Test
    @DisplayName("loadCurrent(11B20201) -> 두 번째 지역 조회")
    void loadCurrent_region2_success() {
        WeatherSnapshot snap = reader.loadCurrent("11B20201");

        assertThat(snap).isNotNull();
        assertThat(snap.regionId()).isEqualTo("11B20201");
        assertThat(snap.hourly()).hasSize(26);
    }

    @Test
    @DisplayName("loadCurrent / loadPrevious -> 서로 다른 announceTime (다른 snap_id 확인)")
    void currentAndPrevious_differentReportTime() {
        WeatherSnapshot current = reader.loadCurrent("11B10101");
        WeatherSnapshot previous = reader.loadPrevious("11B10101");

        assertThat(current).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(current.announceTime()).isNotEqualTo(previous.announceTime());
    }

    @Test
    @DisplayName("존재하지 않는 regionId -> null 반환")
    void nonExistentRegion_returnsNull() {
        WeatherSnapshot snap = reader.loadCurrent("99999999");

        assertThat(snap).isNull();
    }

    @Test
    @DisplayName("POP 값이 DB 에서 정확히 로드된다")
    void popValues_matchDbData() {
        WeatherSnapshot snap = reader.loadCurrent("11B10101");

        assertThat(snap).isNotNull();
        // data-h2.sql: snap_id=1, 11B10101, POP_A01=40, POP_A04=80
        assertThat(snap.hourly().get(0).pop()).isEqualTo(40);
        assertThat(snap.hourly().get(3).pop()).isEqualTo(80);
    }
}
