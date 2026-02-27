package com.github.yun531.climate.warning.infra.persistence.entity;

import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.model.WarningLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

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
    private Integer warningId;                 // PK

    @Column(name = "region_id", nullable = false)
    private String regionId;                  // 지역코드

    @Enumerated(EnumType.STRING)   // Enumerated 지원 안하는 DB 있음, 주의
    @Column(name = "kind", length = 16)
    private WarningKind kind;                    // 호우 / 폭염 / 강풍 / 태풍 ...

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 16)
    private WarningLevel level;                   // 예비특보 / 주의보 / 경보


    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;              // 특보 발효/갱신 시각
}
