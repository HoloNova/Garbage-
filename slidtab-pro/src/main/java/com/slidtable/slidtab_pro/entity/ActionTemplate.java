package com.slidtable.slidtab_pro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 动作编排模板：保存一组可复用的 ActionStep JSON 序列，便于管理员手动调试设备动作链。
 */
@Entity
@Table(name = "action_templates")
public class ActionTemplate extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    @Column(name = "sequence_json", nullable = false, length = 4000)
    private String sequenceJson;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSequenceJson() {
        return sequenceJson;
    }

    public void setSequenceJson(String sequenceJson) {
        this.sequenceJson = sequenceJson;
    }
}
