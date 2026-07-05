package com.slidtable.slidtab_pro.enums;

public enum BorrowStatus {
    RESERVED("已预约"),
    BORROWED("已借出"),
    RETURNED("已归还"),
    OVERDUE("已超时");

    private final String description;

    BorrowStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
