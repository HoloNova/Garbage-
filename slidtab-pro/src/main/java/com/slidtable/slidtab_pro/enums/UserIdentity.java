package com.slidtable.slidtab_pro.enums;

public enum UserIdentity {
    USER("普通用户"),
    ADMIN("管理员");

    private final String description;

    UserIdentity(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
