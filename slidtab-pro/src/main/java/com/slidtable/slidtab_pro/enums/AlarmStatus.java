package com.slidtable.slidtab_pro.enums;

public enum AlarmStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    RESOLVED("已解决");

    private final String description;

    AlarmStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
