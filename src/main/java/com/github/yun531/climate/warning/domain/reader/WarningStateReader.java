package com.github.yun531.climate.warning.domain.reader;

import com.github.yun531.climate.warning.domain.readmodel.IssuedWarning;
import com.github.yun531.climate.warning.domain.model.WarningKind;

import java.util.Map;

public interface WarningStateReader {
    Map<WarningKind, IssuedWarning> loadLatestByKind(String regionId);
}