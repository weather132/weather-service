package com.github.yun531.climate.notification.infra.adapter;

import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopViewPair;
import com.github.yun531.climate.notification.infra.assembler.PopViewAssembler;
import com.github.yun531.climate.kernel.snapshot.SnapKind;
import com.github.yun531.climate.kernel.snapshot.port.SnapshotReadPort;
import com.github.yun531.climate.kernel.snapshot.readmodel.SnapshotForecast;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 스냅샷 로드 + PopView 변환 담당 */
@Component
@RequiredArgsConstructor
public class PopViewReadAdapter implements PopViewReadPort {

    private final SnapshotReadPort snapshotReadPort;
    private final PopViewAssembler mapper;

    /** 기본 POP 예보 요약: 현재 SNAP 기준 */
    @Override
    public PopView loadCurrent(String regionId) {
        SnapshotForecast snap = snapshotReadPort.load(regionId, SnapKind.CURRENT);
        return mapper.toPopView(snap);
    }

    /** 기본 POP 예보 요약: 이전 SNAP 기준 */
    @Override
    public PopView loadPrevious(String regionId) {
        SnapshotForecast snap = snapshotReadPort.load(regionId, SnapKind.PREVIOUS);
        return mapper.toPopView(snap);
    }

    /** 비(POP) 판정에 필요한 시계열을 로드 (현재*이전 스냅샷) - SnapKind(의미) 기반 */
    @Override
    public PopViewPair loadCurrentPreviousPair(String regionId) {
        SnapshotForecast cur = snapshotReadPort.load(regionId, SnapKind.CURRENT);
        SnapshotForecast prv = snapshotReadPort.load(regionId, SnapKind.PREVIOUS);
        return mapper.toPair(cur, prv);
    }
}