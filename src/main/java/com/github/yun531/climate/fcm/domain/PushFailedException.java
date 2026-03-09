package com.github.yun531.climate.fcm.domain;

/**
 * 푸시 전송 실패 시 던지는 도메인 예외.
 * Firebase 예외를 래핑하여 소비자가 SDK에 의존하지 않도록 함.
 */
public class PushFailedException extends RuntimeException {

    public PushFailedException(String message) {
        super(message);
    }

    public PushFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
