package com.github.yun531.climate.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * ClimateSnap 엔티티의 "복합키"를 표현하는 ID 클래스.
 * - PRIMARY KEY (snap_id, region_id) 를 사용하고 있으므로,
 *   JPA 에서도 동일한 키 구조를 표현해야 한다.
 * - 이 클래스는 @IdClass(ClimateSnapId.class) 와 함께 사용
 */
public class ClimateSnapId implements Serializable {
    private Integer snapId;
    private String regionId;

    public ClimateSnapId() {}

    /** 코드에서 명시적으로 복합키를 만들 때 사용하는 생성자 */
    public ClimateSnapId(Integer snapId, String regionId) {
        this.snapId = snapId;
        this.regionId = regionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClimateSnapId other)) return false;
        return Objects.equals(snapId, other.snapId) &&
                Objects.equals(regionId, other.regionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapId, regionId);
    }
}