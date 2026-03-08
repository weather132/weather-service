package com.github.yun531.climate.kernel.warning.reader;

import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import com.github.yun531.climate.kernel.warning.model.WarningKind;

import java.util.Map;

public interface WarningStateReader {
    Map<WarningKind, WarningStateView> loadLatestByKind(String regionId);
}