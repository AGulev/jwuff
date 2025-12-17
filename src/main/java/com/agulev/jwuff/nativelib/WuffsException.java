package com.agulev.jwuff.nativelib;

public final class WuffsException extends RuntimeException {
    private final int code;

    public WuffsException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
