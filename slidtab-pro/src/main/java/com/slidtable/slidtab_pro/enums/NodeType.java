package com.slidtable.slidtab_pro.enums;

public enum NodeType {
    ACTUATOR("执行机构"),
    SENSOR("传感器"),
    GATEWAY("网关"),
    CAMERA("摄像头");

    private final String description;

    NodeType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
