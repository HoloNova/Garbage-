package com.slidtable.slidtab_pro.common;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.message());
        this.code = Integer.parseInt(resultCode.code());
    }

    public int code() {
        return code;
    }
}
