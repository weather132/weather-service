package com.github.yun531.climate.warning.domain.reader;

import com.github.yun531.climate.warning.domain.readmodel.WarningStateView;
import com.github.yun531.climate.warning.domain.model.WarningKind;

import java.util.Map;

public interface WarningStateReader {
    Map<WarningKind, WarningStateView> loadLatestByKind(String regionId);
}