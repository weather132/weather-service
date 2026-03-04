package com.github.yun531.climate.notification.infra;

import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.port.SnapshotPort;
import com.github.yun531.climate.kernel.snapshot.readmodel.WeatherSnapshot;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 스냅샷 로드 + PopView 변환 담당 */
@Component
@RequiredArgsConstructor
public class PopViewReadAdapter implements PopViewReadPort {

    private final SnapshotPort snapshotPort;
    private final SnapshotToPopViewMapper mapper;

    @Override
    public PopView loadCurrent(String regionId) {
        WeatherSnapshot snap = snapshotPort.load(regionId, SnapKind.CURRENT);
        return mapper.toPopView(snap);
    }

    @Override
    public PopView loadPrevious(String regionId) {
        WeatherSnapshot snap = snapshotPort.load(regionId, SnapKind.PREVIOUS);
        return mapper.toPopView(snap);
    }

    @Override
    public PopView.Pair loadCurrentPreviousPair(String regionId) {
        WeatherSnapshot cur = snapshotPort.load(regionId, SnapKind.CURRENT);
        WeatherSnapshot prv = snapshotPort.load(regionId, SnapKind.PREVIOUS);
        return mapper.toPair(cur, prv);
    }
}