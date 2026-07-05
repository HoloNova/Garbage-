package com.slidtable.slidtab_pro.enums;

public enum SlotStatus {
    EMPTY("空闲"),
    OCCUPIED("占用"),
    RESERVED("预约"),
    TAKEN("已取出");

    private final String description;

    SlotStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
