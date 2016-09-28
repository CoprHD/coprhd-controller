/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper for values that need to be communicated to the controller for all sorts of
 * operations. Usually gets translated into Volume Descriptors and eases the need for
 * many arguments in apisvc methods.
 * 
 * NOTE: Several fields in VirtualPoolCapabilitiesWrapper should be moved into this class!
 * (Especially fields in there that have nothing do to with virtual pool capabilities)
 */
public class ControllerOperationValuesWrapper implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String MIGRATION_SUSPEND_BEFORE_COMMIT = "migration_suspend_before_commit";
    public static final String MIGRATION_SUSPEND_BEFORE_DELETE_SOURCE = "migration_suspend_before_delete_source";

    private final Map<String, Object> _controllerOperationValues = new HashMap<String, Object>();

    /**
     * Default constructor
     */
    public ControllerOperationValuesWrapper() {
    }

    /**
     * Copy the passed values to a new instance.
     * 
     * @param values
     *            A reference to a ControllerOperationValueVirtualPoolCapabilityValuesWrapper
     */
    public ControllerOperationValuesWrapper(ControllerOperationValuesWrapper values) {
        // Copy the value set in the passed reference capabilities.
        if (values.contains(MIGRATION_SUSPEND_BEFORE_COMMIT)) {
            _controllerOperationValues.put(MIGRATION_SUSPEND_BEFORE_COMMIT, values.getMigrationSuspendBeforeCommit());
        }

        if (values.contains(MIGRATION_SUSPEND_BEFORE_DELETE_SOURCE)) {
            _controllerOperationValues.put(MIGRATION_SUSPEND_BEFORE_DELETE_SOURCE, values.getMigrationSuspendBeforeDeleteSource());
        }
    }

    public void put(String key, Object value) {
        _controllerOperationValues.put(key, value);
    }

    public boolean contains(String key) {
        return _controllerOperationValues.containsKey(key);
    }

    public Boolean getMigrationSuspendBeforeCommit() {
        Object value = _controllerOperationValues.get(MIGRATION_SUSPEND_BEFORE_COMMIT);
        return value != null ? (Boolean) value : null;
    }

    public Boolean getMigrationSuspendBeforeDeleteSource() {
        Object value = _controllerOperationValues.get(MIGRATION_SUSPEND_BEFORE_DELETE_SOURCE);
        return value != null ? (Boolean) value : null;
    }

}
