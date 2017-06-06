/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

public enum LsServerOperStates {

    INDETERMINATE("indeterminate"),
    UNASSOCIATED("unassociated"),
    OK("ok"),
    DISCOVERY("discovery"),
    CONFIG("config"),
    UNCONFIG("unconfig"),
    POWER_OFF("power-off"),
    RESTART("restart"),
    MAINTENANCE("maintenance"),
    TEST("test"),
    COMPUTE_MISMATCH("compute-mismatch"),
    COMPUTE_FAILED("compute-failed"),
    DEGRADED("degraded"),
    DISCOVERY_FAILED("discovery-failed"),
    CONFIG_FAILURE("config-failure"),
    UNCONFIG_FAILED("unconfig-failed"),
    TEST_FAILED("test-failed"),
    MAINTENANCE_FAILED("maintenance-failed"),
    REMOVED("removed"),
    DISABLED("disabled"),
    INACCESSIBLE("inaccessible"),
    THERMAL_PROBLEM("thermal-problem"),
    POWER_PROBLEM("power-problem"),
    VOLTAGE_PROBLEM("voltage-problem"),
    INOPERABLE("inoperable"),
    DECOMMISSIONING("decommissioning"),
    BIOS_RESTORE("bios-restore"),
    CMOS_RESET("cmos-reset"),
    DIAGNOSTICS("diagnostics"),
    DIAGNOSTICS_FAILED("diagnostics-failed"),
    PENDING_REBOOT("pending-reboot"),
    PENDING_REASSOCIATION("pending-reassociation");

    private final String operState;

    private LsServerOperStates(String operState) {
        this.operState = operState;
    }

    public static final EnumSet<LsServerOperStates> terminalStates = EnumSet
            .of(INDETERMINATE, UNASSOCIATED, OK, POWER_OFF, COMPUTE_FAILED,
                    DEGRADED, DISCOVERY_FAILED, CONFIG_FAILURE,
                    UNCONFIG_FAILED, TEST_FAILED, MAINTENANCE_FAILED,
                    REMOVED, DISABLED, INACCESSIBLE, THERMAL_PROBLEM,
                    POWER_PROBLEM, VOLTAGE_PROBLEM, INOPERABLE, DIAGNOSTICS_FAILED);

    public static LsServerOperStates fromString(String fromString) {
        if (allEnumsHash.get(fromString) != null) {
            return allEnumsHash.get(fromString);
        } else {
            throw new IllegalArgumentException("No operState found for the corresponding raw string : " + fromString);
        }
    }

    public static boolean isTerminal(String fromString) {
        boolean isTerminal = false;
        if (StringUtils.isNotEmpty(fromString) && terminalStates.contains(fromString(fromString))) {
            isTerminal = true;
        }
        return isTerminal;
    }

    private static final Map<String, LsServerOperStates> allEnumsHash = Collections
            .synchronizedMap(new HashMap<String, LsServerOperStates>());

    static {

        for (LsServerOperStates operState : LsServerOperStates.values()) {
            allEnumsHash.put(operState.operState, operState);
        }

    }

}
