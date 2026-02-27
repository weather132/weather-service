package com.github.yun531.climate.kernel.warning.port;

import com.github.yun531.climate.kernel.warning.readmodel.WarningStateView;
import com.github.yun531.climate.kernel.warning.model.WarningKind;

import java.util.Map;

public interface WarningStateReadPort {
    Map<WarningKind, WarningStateView> loadLatestByKind(String regionId);
}