package com.github.yun531.climate.entity;

import com.github.yun531.climate.domain.WarningKind;
import com.github.yun531.climate.domain.WarningLevel;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "warning_state")
public class WarningState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "warning_id")
    private long warningId;                 // PK

    @Column(name = "region_id", nullable = false)
    private long regionId;                  // 지역코드

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 16)
    private WarningKind kind;                    // 예: 호우 / 폭염 / 강풍 / 태풍 ...

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 16)
    private WarningLevel level;                   // 예: 주의보 / 경보

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @org.hibernate.annotations.Generated(org.hibernate.annotations.GenerationTime.ALWAYS)
    private Instant updatedAt;              // 특보 발효/갱신 시각
}
