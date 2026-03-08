package com.github.yun531.climate.notification.infra;

import com.github.yun531.climate.kernel.snapshot.model.SnapKind;
import com.github.yun531.climate.kernel.snapshot.reader.SnapshotReader;
import com.github.yun531.climate.kernel.snapshot.readmodel.WeatherSnapshot;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopViewReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 스냅샷 로드 + PopView 변환 담당 */
@Component
@RequiredArgsConstructor
public class SnapshotPopViewReader implements PopViewReader {

    private final SnapshotReader snapshotReader;
    private final PopViewMapper mapper;

    @Override
    public PopView loadCurrent(String regionId) {
        WeatherSnapshot snap = snapshotReader.load(regionId, SnapKind.CURRENT);
        return mapper.toPopView(snap);
    }

    @Override
    public PopView loadPrevious(String regionId) {
        WeatherSnapshot snap = snapshotReader.load(regionId, SnapKind.PREVIOUS);
        return mapper.toPopView(snap);
    }

    @Override
    public PopView.Pair loadCurrentPreviousPair(String regionId) {
        WeatherSnapshot cur = snapshotReader.load(regionId, SnapKind.CURRENT);
        WeatherSnapshot prv = snapshotReader.load(regionId, SnapKind.PREVIOUS);
        return mapper.toPair(cur, prv);
    }
}