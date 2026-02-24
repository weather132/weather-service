package com.github.yun531.climate.notification.domain.port;

import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopViewPair;

public interface PopViewReadPort {

    PopView loadCurrent(String regionId);

    PopView loadPrevious(String regionId);

    default PopViewPair loadCurrentPreviousPair(String regionId) {
        PopView cur = loadCurrent(regionId);
        PopView prv = loadPrevious(regionId);
        if (cur == null || prv == null) return null;
        return new PopViewPair(cur, prv);
    }
}