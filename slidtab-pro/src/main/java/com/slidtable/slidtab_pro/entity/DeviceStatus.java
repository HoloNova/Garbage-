package com.slidtable.slidtab_pro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_status")
public class DeviceStatus extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String deviceId;

    @Column(name = "node_type")
    private String nodeType;

    private boolean online;

    @Column(name = "motor_state")
    private String motorState;

    @Column(name = "cabinet_door")
    private String cabinetDoor;

    @Column(name = "conveyor_state")
    private String conveyorState;

    @Column(name = "alarm_state")
    private String alarmState;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getMotorState() {
        return motorState;
    }

    public void setMotorState(String motorState) {
        this.motorState = motorState;
    }

    public String getCabinetDoor() {
        return cabinetDoor;
    }

    public void setCabinetDoor(String cabinetDoor) {
        this.cabinetDoor = cabinetDoor;
    }

    public String getConveyorState() {
        return conveyorState;
    }

    public void setConveyorState(String conveyorState) {
        this.conveyorState = conveyorState;
    }

    public String getAlarmState() {
        return alarmState;
    }

    public void setAlarmState(String alarmState) {
        this.alarmState = alarmState;
    }

    public LocalDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(LocalDateTime lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
}
