package com.github.yun531.climate.notification.domain.readmodel;

public interface PopViewReader {

    PopView loadCurrent(String regionId);

    PopView loadPrevious(String regionId);

    default PopView.Pair loadCurrentPreviousPair(String regionId) {
        PopView cur = loadCurrent(regionId);
        PopView prv = loadPrevious(regionId);
        if (cur == null || prv == null) return null;
        return new PopView.Pair(cur, prv);
    }
}