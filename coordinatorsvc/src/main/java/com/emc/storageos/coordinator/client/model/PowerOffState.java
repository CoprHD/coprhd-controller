/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

/**
 * PowerOffState is used for both target and node level information
 * After user set target state to poweroff, all nodes will go through the
 * 
 * @see State's steps in order to finish the process
 *      If it fails at any step before State.AGREED,
 *      any node, which got the lock, can revert the poweroff state back to State.NONE
 * 
 */
public class PowerOffState implements CoordinatorSerializable {
    private State _powerOffState = null;
    public static final String CONFIG_KIND = "upgradepoweroff";
    public static final String CONFIG_ID = "global";
    public static final String CONFIG_ATTR = "poweroff";
    
    public PowerOffState() {
    }

    public PowerOffState(State state) {
        _powerOffState = state;
    }

    public State getPowerOffState() {
        return _powerOffState;
    }

    public static enum State {
        NONE,
        START,
        FORCESTART,
        NOTICED,
        ACKNOWLEDGED,
        POWEROFF,
    }

    @Override
    public String encodeAsString() {
        return _powerOffState.toString();
    }

    @Override
    public PowerOffState decodeFromString(String infoStr) {
        if (infoStr == null) {
            return null;
        } else if (infoStr.equals("")) {
            return new PowerOffState();
        } else {
            return new PowerOffState(State.valueOf(infoStr));
        }
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, CONFIG_ATTR);
    }

    public String toString() {
        return "powerOffState=" + _powerOffState.toString();
    }

}
