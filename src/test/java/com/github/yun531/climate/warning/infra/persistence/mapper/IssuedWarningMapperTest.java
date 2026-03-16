package com.github.yun531.climate.warning.infra.persistence.mapper;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.model.WarningLevel;
import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import com.github.yun531.climate.warning.infra.persistence.entity.WarningStateEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IssuedWarningMapperTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 22, 5, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 1, 22, 5, 10);

    @Test
    @DisplayName("같은 kind 중 updatedAt이 최신인 엔티티만 선택")
    void sameKind_picksLatest() {
        WarningStateEntity old   = buildEntity(1, "R1", WarningKind.RAIN, WarningLevel.ADVISORY, T1);
        WarningStateEntity newer = buildEntity(2, "R1", WarningKind.RAIN, WarningLevel.WARNING, T2);

        Map<WarningKind, IssuedWarning> result =
                IssuedWarningMapper.mapLatestByKind("R1", List.of(old, newer));

        assertThat(result).containsKey(WarningKind.RAIN);
        assertThat(result.get(WarningKind.RAIN).level()).isEqualTo(WarningLevel.WARNING);
        assertThat(result.get(WarningKind.RAIN).updatedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName("다른 kind -> 각각 독립적으로 선택")
    void differentKinds_bothSelected() {
        WarningStateEntity rain = buildEntity(1, "R1", WarningKind.RAIN, WarningLevel.ADVISORY, T1);
        WarningStateEntity heat = buildEntity(2, "R1", WarningKind.HEAT, WarningLevel.WARNING, T2);

        Map<WarningKind, IssuedWarning> result =
                IssuedWarningMapper.mapLatestByKind("R1", List.of(rain, heat));

        assertThat(result).hasSize(2);
        assertThat(result).containsKeys(WarningKind.RAIN, WarningKind.HEAT);
    }

    @Test
    @DisplayName("다른 regionId의 엔티티는 제외")
    void differentRegion_excluded() {
        WarningStateEntity other = buildEntity(1, "R2", WarningKind.RAIN, WarningLevel.ADVISORY, T1);

        Map<WarningKind, IssuedWarning> result =
                IssuedWarningMapper.mapLatestByKind("R1", List.of(other));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("빈 리스트 -> 빈 맵")
    void emptyList_emptyMap() {
        assertThat(IssuedWarningMapper.mapLatestByKind("R1", List.of())).isEmpty();
    }

    @Test
    @DisplayName("null regionId -> 빈 맵")
    void nullRegion_emptyMap() {
        WarningStateEntity entity = buildEntity(1, "R1", WarningKind.RAIN, WarningLevel.ADVISORY, T1);
        assertThat(IssuedWarningMapper.mapLatestByKind(null, List.of(entity))).isEmpty();
    }

    @Test
    @DisplayName("toView — 엔티티 -> IssuedWarning 변환")
    void fromEntity_correctMapping() {
        WarningStateEntity entity = buildEntity(1, "R1", WarningKind.RAIN, WarningLevel.WARNING, T1);

        IssuedWarning view = IssuedWarningMapper.fromEntity(entity);

        assertThat(view.regionId()).isEqualTo("R1");
        assertThat(view.kind()).isEqualTo(WarningKind.RAIN);
        assertThat(view.level()).isEqualTo(WarningLevel.WARNING);
        assertThat(view.updatedAt()).isEqualTo(T1);
    }

    // -- 헬퍼 --

    private WarningStateEntity buildEntity(int id, String regionId, WarningKind kind,
                                           WarningLevel level, LocalDateTime updatedAt) {
        return WarningStateEntity.builder()
                .warningId(id)
                .regionId(regionId)
                .kind(kind)
                .level(level)
                .updatedAt(updatedAt)
                .build();
    }
}
