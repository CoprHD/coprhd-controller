/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;

/**
 * A static class that invokes failures in a controlled manner, giving us the ability to test
 * rollback and other workflow anomalies in a repeatable and automated way.
 */
public final class InvokeTestFailure {
    // Controller property
    private static final String ARTIFICIAL_FAILURE = "artificial_failure";

    // Different failures
    public static final String ARTIFICIAL_FAILURE_001 = "failure_001_early_in_add_volume_to_mask";
    public static final String ARTIFICIAL_FAILURE_002 = "failure_002_late_in_add_volume_to_mask";
    public static final String ARTIFICIAL_FAILURE_003 = "failure_003_late_in_add_initiator_to_mask";
    public static final String ARTIFICIAL_FAILURE_004 = "failure_004_final_step_in_workflow_complete";
    public static final String ARTIFICIAL_FAILURE_005 = "failure_005_BlockDeviceController.createVolumes_before_device_create";
    public static final String ARTIFICIAL_FAILURE_006 = "failure_006_BlockDeviceController.createVolumes_after_device_create";
    public static final String ARTIFICIAL_FAILURE_007 = "failure_007_NetworkDeviceController.zoneExportRemoveVolumes_before_unzone";
    public static final String ARTIFICIAL_FAILURE_008 = "failure_008_NetworkDeviceController.zoneExportRemoveVolumes_after_unzone";
    public static final String ARTIFICIAL_FAILURE_009 = "failure_009_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_before_operation";
    public static final String ARTIFICIAL_FAILURE_010 = "failure_010_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_after_operation";
    public static final String ARTIFICIAL_FAILURE_011 = "failure_011_VNXVMAX_Post_Placement_outside_trycatch";
    public static final String ARTIFICIAL_FAILURE_012 = "failure_012_VNXVMAX_Post_Placement_inside_trycatch";

    private static volatile String _beanName;

    public void setName(String beanName) {
        _beanName = beanName;
    }

    private static CoordinatorClient _coordinator;

    public void setCoordinator(CoordinatorClient coordinator) {
        InvokeTestFailure._coordinator = coordinator;
    }

    /**
     * Invoke a failure if the artificial_failure variable is set.
     * This is an internal-only setting that allows testers and automated suites to inject a failure
     * into a workflow step at key locations to test rollback and Task states.
     * 
     * @param coordinator
     *            coordinator
     * @param failureKey
     *            key from above
     */
    public static void internalOnlyInvokeTestFailure(String failureKey) {
        // Invoke an artificial failure, if set (experimental, testing only)
        String invokeArtificialFailure = _coordinator.getPropertyInfo().getProperty(ARTIFICIAL_FAILURE);
        if (invokeArtificialFailure != null && invokeArtificialFailure.equalsIgnoreCase(failureKey)) {
            throw new NullPointerException("Artificially Thrown Exception");
        }
    }
}