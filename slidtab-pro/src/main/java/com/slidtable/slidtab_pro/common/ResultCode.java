package com.slidtable.slidtab_pro.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum ResultCode {

    SUCCESS("0000", "成功"),
    PARAM_ERROR("1001", "参数错误"),
    DEVICE_NOT_FOUND("1002", "设备不存在"),
    DEVICE_OFFLINE("1003", "设备离线"),
    TIMEOUT("1004", "超时"),
    PERMISSION_DENIED("1005", "权限不足"),
    STOCK_INSUFFICIENT("1006", "库存不足"),
    EXECUTE_FAILED("1007", "执行失败"),
    SENSOR_ABNORMAL("1008", "传感器异常"),
    CABINET_ABNORMAL_OPEN("1009", "柜体异常开启"),
    COMMUNICATION_FAILED("1010", "通信失败"),
    ARM_NOT_IN_PLACE("2001", "机械臂未到位"),
    SLIDE_POSITION_FAILED("2002", "滑台定位失败"),
    CONVEYOR_BLOCKED("2003", "传送带堵塞"),
    GRAB_FAILED("2004", "抓取失败"),
    PLACE_FAILED("2005", "放置失败"),
    RESET_FAILED("2006", "复位失败"),
    TEMP_THRESHOLD("3001", "温度超阈值"),
    HUMIDITY_THRESHOLD("3002", "湿度超阈值"),
    LIGHT_THRESHOLD("3006", "光照异常"),
    WEIGHT_THRESHOLD("3007", "称重超阈值"),
    SMOKE_THRESHOLD("3008", "烟雾超阈值");

    private final String code;
    private final String message;

    private static final Map<String, ResultCode> CODE_MAP = new HashMap<>();

    static {
        for (ResultCode rc : values()) {
            CODE_MAP.put(rc.code, rc);
        }
    }

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public static Optional<ResultCode> fromCode(String code) {
        return Optional.ofNullable(CODE_MAP.get(code));
    }
}
