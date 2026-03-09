package com.github.yun531.climate.notification.infra.alert;

import com.github.yun531.climate.snapshot.domain.reader.SnapshotReader;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
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
        WeatherSnapshot snap = snapshotReader.loadCurrent(regionId);
        return mapper.toPopView(snap);
    }

    @Override
    public PopView loadPrevious(String regionId) {
        WeatherSnapshot snap = snapshotReader.loadPrevious(regionId);
        return mapper.toPopView(snap);
    }

    @Override
    public PopView.Pair loadCurrentPreviousPair(String regionId) {
        WeatherSnapshot cur = snapshotReader.loadCurrent(regionId);
        WeatherSnapshot prv = snapshotReader.loadPrevious(regionId);
        return mapper.toPair(cur, prv);
    }
}