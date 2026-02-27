package com.github.yun531.climate.snapshot.infra.adapter;

import com.github.yun531.climate.kernel.snapshot.model.SnapKind;

public final class SnapKindCodec {
    private SnapKindCodec() {}

    public static int toCode(SnapKind kind) {
        return switch (kind) {
            case CURRENT -> 1;
            case PREVIOUS -> 10;
        };
    }
}