package com.slidtable.slidtab_pro.enums;

public enum ItemType {
    BOOK("图书");

    private final String description;

    ItemType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
