package com.github.yun531.climate.fcm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushFailedExceptionTest {

    @Test
    @DisplayName("메시지만으로 생성")
    void messageOnly() {
        PushFailedException ex = new PushFailedException("push failed");

        assertThat(ex).hasMessage("push failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("메시지 + 원인(cause) 체인 보존")
    void messageAndCause() {
        RuntimeException cause = new RuntimeException("firebase error");
        PushFailedException ex = new PushFailedException("push failed", cause);

        assertThat(ex).hasMessage("push failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("RuntimeException 하위 타입 — unchecked exception")
    void isUnchecked() {
        PushFailedException ex = new PushFailedException("fail");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}