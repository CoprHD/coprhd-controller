package com.emc.storageos.db.client.model;

/**
 * An enum class representing the SMI-S values of CIM_StorageSynchronized.SyncState.
 *
 * @author Ian Bibby
 */
public enum SynchronizationState {
    UNKNOWN(0), PREPARED(4), RESYNCHRONIZING(5), SYNCHRONIZED(6), FRACTURED(13), COPYINPROGRESS(15);

    private int state;

    SynchronizationState(int state) {
        this.state = state;
    }

    public static SynchronizationState fromState(String state) {
        for (SynchronizationState synchronizationState : values()) {
            if (Integer.toString(synchronizationState.state).equals(state)) {
                return synchronizationState;
            }
        }
        return UNKNOWN;
    }

    public String toString() {
        return Integer.toString(state);
    }
}
