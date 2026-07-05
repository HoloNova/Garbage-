package com.slidtable.slidtab_pro.enums;

public enum CommandType {
    HEARTBEAT("心跳检测"),
    SYNC_TIME("时间同步"),
    READ_SENSOR("读取传感器"),
    UPLOAD_STATUS("主动上报状态"),
    OPEN_CABINET("打开柜门/格口"),
    CLOSE_CABINET("关闭柜门/格口"),
    LOCK_CABINET("锁定柜体"),
    UNLOCK_CABINET("解锁柜体"),
    MOVE_TO_SLOT("移动到指定格口"),
    START_ARM("机械臂启动"),
    TEST_CONNECTION("连接测试"),
    GRAB_ITEM("抓取物品"),
    PLACE_ITEM("放置物品"),
    START_CONVEYOR("启动传送带"),
    STOP_CONVEYOR("停止传送带"),
    RESET_ACTUATOR("执行机构复位"),
    LIGHT_ON("开启照明"),
    LIGHT_OFF("关闭照明"),
    FAN_ON("开启风扇/通风"),
    FAN_OFF("关闭风扇/通风"),
    ALARM_ON("开启告警"),
    ALARM_OFF("关闭告警");

    private final String description;

    CommandType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
