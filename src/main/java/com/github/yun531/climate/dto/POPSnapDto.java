package com.github.yun531.climate.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class POPSnapDto {

    private long snapId;
    private long regionId;

    // ---- 시간대별 POP (0~23시) ----  // todo list로 수정
    private Byte popA00; private Byte popA01; private Byte popA02; private Byte popA03; private Byte popA04;
    private Byte popA05; private Byte popA06; private Byte popA07; private Byte popA08; private Byte popA09;
    private Byte popA10; private Byte popA11; private Byte popA12; private Byte popA13; private Byte popA14;
    private Byte popA15; private Byte popA16; private Byte popA17; private Byte popA18; private Byte popA19;
    private Byte popA20; private Byte popA21; private Byte popA22; private Byte popA23;

    // ---- 일자별 오전/오후 POP ----
    private Byte popA0dAm; private Byte popA0dPm;
    private Byte popA1dAm; private Byte popA1dPm;
    private Byte popA2dAm; private Byte popA2dPm;
    private Byte popA3dAm; private Byte popA3dPm;
    private Byte popA4dAm; private Byte popA4dPm;
    private Byte popA5dAm; private Byte popA5dPm;
    private Byte popA6dAm; private Byte popA6dPm;
}
