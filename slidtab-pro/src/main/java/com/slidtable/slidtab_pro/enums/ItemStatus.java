package com.slidtable.slidtab_pro.enums;

public enum ItemStatus {
    AVAILABLE("可借"),
    BORROWED("已借出"),
    RESERVED("已预约"),
    MAINTENANCE("维护中");

    private final String description;

    ItemStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
