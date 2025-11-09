package com.github.yun531.climate.support;

import com.github.yun531.climate.dto.POPSnapDto;
import org.springframework.lang.Nullable;

public final class PopArrays {
    private PopArrays() {}

    public static int[] hourly24(POPSnapDto d) {
        return new int[]{
                n(d.getPopA00()), n(d.getPopA01()), n(d.getPopA02()), n(d.getPopA03()), n(d.getPopA04()),
                n(d.getPopA05()), n(d.getPopA06()), n(d.getPopA07()), n(d.getPopA08()), n(d.getPopA09()),
                n(d.getPopA10()), n(d.getPopA11()), n(d.getPopA12()), n(d.getPopA13()), n(d.getPopA14()),
                n(d.getPopA15()), n(d.getPopA16()), n(d.getPopA17()), n(d.getPopA18()), n(d.getPopA19()),
                n(d.getPopA20()), n(d.getPopA21()), n(d.getPopA22()), n(d.getPopA23())
        };
    }

    /** [7][2] = [d][0=AM,1=PM] */
    public static Byte[][] ampm7x2(POPSnapDto d) {
        return new Byte[][]{
                new Byte[]{d.getPopA0dAm(), d.getPopA0dPm()},
                new Byte[]{d.getPopA1dAm(), d.getPopA1dPm()},
                new Byte[]{d.getPopA2dAm(), d.getPopA2dPm()},
                new Byte[]{d.getPopA3dAm(), d.getPopA3dPm()},
                new Byte[]{d.getPopA4dAm(), d.getPopA4dPm()},
                new Byte[]{d.getPopA5dAm(), d.getPopA5dPm()},
                new Byte[]{d.getPopA6dAm(), d.getPopA6dPm()}
        };
    }

    /** 길이 23 (0~22) 비교용으로 왼쪽 1칸 시프트 */
    public static int[] shiftLeftBy1ToLen23(int[] arr24) {
        int[] out = new int[23];
        System.arraycopy(arr24, 1, out, 0, 23);
        return out;
    }

    public static int n(@Nullable Byte v) { return v == null ? 0 : (v & 0xFF); }
}
