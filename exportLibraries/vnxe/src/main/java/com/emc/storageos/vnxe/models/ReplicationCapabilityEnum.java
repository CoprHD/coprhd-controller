package com.emc.storageos.vnxe.models;

public enum ReplicationCapabilityEnum {
    Sync(0), Async(1), Both(2);

    private final int value;

    private ReplicationCapabilityEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
