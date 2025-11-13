package com.github.yun531.climate.dto;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.domain.WarningLevel;
import com.github.yun531.climate.entity.WarningState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WarningStateDto {

    private long regionId;          // 지역 코드  // todo 빌더 고류, 디폴트 인자
    private WarningKind kind;       // 호우 / 폭염 / 강풍 / 태풍 ...
    private WarningLevel level;     // 예비특보 / 주의보 / 경보
    private Instant updatedAt;      // 특보가 발효되거나 갱신된 시각

    public static WarningStateDto from(WarningState e) {  // todo 생성자
        return new WarningStateDto(
                e.getRegionId(),
                e.getKind(),
                e.getLevel(),
                e.getUpdatedAt()
        );
    }
}
