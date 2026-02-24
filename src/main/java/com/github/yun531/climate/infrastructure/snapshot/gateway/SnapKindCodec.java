package com.github.yun531.climate.infrastructure.snapshot.gateway;

import com.github.yun531.climate.kernel.snapshot.SnapKind;

public final class SnapKindCodec {
    private SnapKindCodec() {}

    public static int toCode(SnapKind kind) {
        return switch (kind) {
            case CURRENT -> 1;
            case PREVIOUS -> 10;
        };
    }

    public static SnapKind fromCode(int code) {
        return switch (code) {
            case 1 -> SnapKind.CURRENT;
            case 10 -> SnapKind.PREVIOUS;
            default -> null;
        };
    }
}