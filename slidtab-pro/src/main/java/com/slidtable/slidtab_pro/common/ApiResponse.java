package com.slidtable.slidtab_pro.common;

public record ApiResponse<T>(int code, String message, T data, long timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, System.currentTimeMillis());
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(0, "success", null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, System.currentTimeMillis());
    }
}
