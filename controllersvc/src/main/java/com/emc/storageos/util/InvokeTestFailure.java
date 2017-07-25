/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.wbem.WBEMException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.google.common.base.Strings;

/**
 * A static class that invokes failures in a controlled manner, giving us the ability to test
 * rollback and other workflow anomalies in a repeatable and automated way.
 */
public final class InvokeTestFailure {
    private static final Logger _log = LoggerFactory.getLogger(InvokeTestFailure.class);

    // Controller property for current failure injection
    private static final String ARTIFICIAL_FAILURE = "artificial_failure";
    // Controller property for current failure injection count.
    private static final String ARTIFICIAL_FAILURE_COUNTER_RESET = "artificial_failure_counter_reset";

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
    public static final String ARTIFICIAL_FAILURE_013 = "failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete";
    public static final String ARTIFICIAL_FAILURE_014 = "failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete";
    public static final String ARTIFICIAL_FAILURE_015 = "failure_015_SmisCommandHelper.invokeMethod_";
    public static final String ARTIFICIAL_FAILURE_016 = "failure_016_Export_doRemoveInitiator";
    public static final String ARTIFICIAL_FAILURE_017 = "failure_017_Export_doRemoveVolume";
    public static final String ARTIFICIAL_FAILURE_018 = "failure_018_Export_doRollbackExportCreate_before_delete";
    public static final String ARTIFICIAL_FAILURE_019 = "failure_019_Export_doRollbackExportCreate_after_delete";
    public static final String ARTIFICIAL_FAILURE_020 = "failure_020_Export_zoneRollback_before_delete";
    public static final String ARTIFICIAL_FAILURE_021 = "failure_021_Export_zoneRollback_after_delete";
    public static final String ARTIFICIAL_FAILURE_022 = "failure_022_VNXeStorageDevice_CreateVolume_before_async_job";
    public static final String ARTIFICIAL_FAILURE_023 = "failure_023_VNXeStorageDevice_CreateVolume_after_async_job";
    public static final String ARTIFICIAL_FAILURE_024 = "failure_024_Export_zone_removeInitiator_before_delete";
    public static final String ARTIFICIAL_FAILURE_025 = "failure_025_Export_zone_removeInitiator_after_delete";
    public static final String ARTIFICIAL_FAILURE_026 = "failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update";
    public static final String ARTIFICIAL_FAILURE_027 = "failure_027_host_cluster_ComputeSystemControllerImpl.deleteExportGroup_before_delete";
    public static final String ARTIFICIAL_FAILURE_028 = "failure_028_host_cluster_ComputeSystemControllerImpl.deleteExportGroup_after_delete";
    public static final String ARTIFICIAL_FAILURE_029 = "failure_029_host_cluster_ComputeSystemControllerImpl.verifyDatastore_after_verify";
    public static final String ARTIFICIAL_FAILURE_030 = "failure_030_host_cluster_ComputeSystemControllerImpl.unmountAndDetach_after_unmount";
    public static final String ARTIFICIAL_FAILURE_031 = "failure_031_host_cluster_ComputeSystemControllerImpl.unmountAndDetach_after_detach";
    public static final String ARTIFICIAL_FAILURE_032 = "failure_032_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences_after_updateHostAndInitiator";
    public static final String ARTIFICIAL_FAILURE_033 = "failure_033_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences_after_updateHostVcenter";
    public static final String ARTIFICIAL_FAILURE_034 = "failure_034_VNXUnityBlockStorageDeviceController.doDeleteVolume_before_remove_from_cg";
    public static final String ARTIFICIAL_FAILURE_035 = "failure_035_VNXUnityBlockStorageDeviceController.doDeleteVolume_before_delete_volume";
    public static final String ARTIFICIAL_FAILURE_036 = "failure_036_VNXUnityBlockStorageDeviceController.doDeleteVolume_after_delete_volume";
    public static final String ARTIFICIAL_FAILURE_037 = "failure_037_VNXUnityBlockStorageDeviceController.doDeleteVolume_after_delete_volume_cg_version";
    public static final String ARTIFICIAL_FAILURE_038 = "failure_038_XtremIOStorageDeviceController.doDeleteVolume_before_remove_from_cg";
    public static final String ARTIFICIAL_FAILURE_039 = "failure_039_XtremIOStorageDeviceController.doDeleteVolume_after_delete_volume";
    public static final String ARTIFICIAL_FAILURE_040 = "failure_040_XtremIOStorageDeviceController.doDeleteVolume_before_delete_volume";
    public static final String ARTIFICIAL_FAILURE_041 = "failure_041_XtremIOStorageDeviceController.doDeleteVolume_after_delete_volume";
    public static final String ARTIFICIAL_FAILURE_042 = "failure_042_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences";
    public static final String ARTIFICIAL_FAILURE_043 = "failure_043_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesFromExportMask_before_operation";
    public static final String ARTIFICIAL_FAILURE_044 = "failure_044_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesFromExportMask_after_operation";
    public static final String ARTIFICIAL_FAILURE_045 = "failure_045_VPlexDeviceController.createVirtualVolume_before_create_operation";
    public static final String ARTIFICIAL_FAILURE_046 = "failure_046_VPlexDeviceController.createVirtualVolume_after_create_operation";
    public static final String ARTIFICIAL_FAILURE_047 = "failure_047_NetworkDeviceController.zoneExportMaskCreate_before_zone";
    public static final String ARTIFICIAL_FAILURE_048 = "failure_048_NetworkDeviceController.zoneExportMaskCreate_after_zone";
    public static final String ARTIFICIAL_FAILURE_049 = "failure_049_BrocadeNetworkSMIS.getWEBMClient";
    public static final String ARTIFICIAL_FAILURE_050 = "failure_050_MaskingWorkflowEntryPoints.doExportGroupDelete_before_delete";
    public static final String ARTIFICIAL_FAILURE_051 = "failure_051_MaskingWorkflowEntryPoints.doExportGroupDelete_after_delete_before_unzone";
    public static final String ARTIFICIAL_FAILURE_052 = "failure_052_XtremIOExportOperations.runLunMapCreationAlgorithm_before_addvolume_to_lunmap";
    public static final String ARTIFICIAL_FAILURE_053 = "failure_053_XtremIOExportOperations.runLunMapCreationAlgorithm_after_addvolume_to_lunmap";
    public static final String ARTIFICIAL_FAILURE_054 = "failure_054_host_cluster_ComputeSystemControllerImpl.attachAndMount_before_attach";
    public static final String ARTIFICIAL_FAILURE_055 = "failure_055_host_cluster_ComputeSystemControllerImpl.attachAndMount_after_attach";
    public static final String ARTIFICIAL_FAILURE_056 = "failure_056_host_cluster_ComputeSystemControllerImpl.attachAndMount_after_mount";
    public static final String ARTIFICIAL_FAILURE_057 = "failure_057_MdsNetworkSystemDevice.removeZones";
    public static final String ARTIFICIAL_FAILURE_058 = "failure_058_NetworkDeviceController.zoneExportAddInitiators_before_zone";
    public static final String ARTIFICIAL_FAILURE_059 = "failure_059_NetworkDeviceController.zoneExportAddInitiators_after_zone";
    public static final String ARTIFICIAL_FAILURE_060 = "failure_060_VPlexDeviceController.storageViewAddInitiators_storageview_nonexisting";
    public static final String ARTIFICIAL_FAILURE_061 = "failure_061_UcsComputeDevice.createLsServer_createServiceProfileFromTemplate_Poll";
    public static final String ARTIFICIAL_FAILURE_062 = "failure_062_UcsComputeDevice.modifyLsServerNoBoot_setServiceProfileToNoBoot";
    public static final String ARTIFICIAL_FAILURE_063 = "failure_063_UcsComputeDevice.bindServiceProfileToBlade_bindSPToComputeElement";
    public static final String ARTIFICIAL_FAILURE_064 = "failure_064_UcsComputeDevice.bindServiceProfileToBlade_ComputeElement_DB_Failure";
    public static final String ARTIFICIAL_FAILURE_065 = "failure_065_UcsComputeDevice.addHostPortsToVArrayNetworks_varrayAssociatedNetworks_DB_Failure";
    public static final String ARTIFICIAL_FAILURE_066 = "failure_066_UcsComputeDevice.deleteLsServer_deleteServiceProfile";
    public static final String ARTIFICIAL_FAILURE_067 = "failure_067_UcsComputeDevice.unbindServiceProfile_unbindServiceProfile";
    public static final String ARTIFICIAL_FAILURE_068 = "failure_068_ComputeDeviceControllerImpl.VcenterHostCleanup_removeHostFromVcenterCluster";
    public static final String ARTIFICIAL_FAILURE_069 = "failure_069_ComputeDeviceControllerImpl.addStepsPreOsInstall_UcsComputeDevice.unbindHostFromTemplate";
    public static final String ARTIFICIAL_FAILURE_070 = "failure_070_ComputeDeviceControllerImpl.addStepsPreOsInstall_setLanBootTargetStep";
    public static final String ARTIFICIAL_FAILURE_071 = "failure_071_ComputeDeviceControllerImpl.addStepsPreOsInstall_prepareOsInstallNetworkStep";
    public static final String ARTIFICIAL_FAILURE_072 = "failure_072_ComputeDeviceControllerImpl.addStepsPostOsInstall_setSanBootTargetStep";
    public static final String ARTIFICIAL_FAILURE_073 = "failure_073_UcsComputeDevice.createLsServer_createServiceProfileFromTemplate";
    public static final String ARTIFICIAL_FAILURE_074 = "failure_074_SRDFDeviceController.createSRDFVolumePairStep_before_link_create";
    public static final String ARTIFICIAL_FAILURE_075 = "failure_075_SRDFDeviceController.createSRDFVolumePairStep_after_link_create";
    public static final String ARTIFICIAL_FAILURE_076 = "failure_076_SRDFDeviceController.rollbackSRDFLinksStep_before_link_rollback";
    public static final String ARTIFICIAL_FAILURE_077 = "failure_077_SRDFDeviceController.rollbackSRDFLinksStep_after_link_rollback";
    public static final String ARTIFICIAL_FAILURE_078 = "failure_078_SRDFDeviceController.createSrdfCgPairsStep_before_cg_pairs_create";
    public static final String ARTIFICIAL_FAILURE_079 = "failure_079_SRDFDeviceController.createSrdfCgPairsStep_after_cg_pairs_create";
    public static final String ARTIFICIAL_FAILURE_080 = "failure_080_BlockDeviceController.expandVolume_before_device_expand";
    public static final String ARTIFICIAL_FAILURE_082 = "failure_082_set_resource_tag";
    public static final String ARTIFICIAL_FAILURE_083 = "failure_083_VPlexDeviceController_late_in_add_targets_to_view";
    public static final String ARTIFICIAL_FAILURE_084 = "failure_084_VPlexDeviceController_deleteStorageView_before_delete";
    public static final String ARTIFICIAL_FAILURE_085 = "failure_085_VPlexApiDiscoveryManager_find_consistency_group";
    public static final String ARTIFICIAL_FAILURE_086 = "failure_086_BlockDeviceController.deleteReplicationGroupInCG_BeforeDelete";
    public static final String ARTIFICIAL_FAILURE_087 = "failure_087_BlockDeviceController.before_doDeleteVolumes";
    public static final String ARTIFICIAL_FAILURE_088 = "failure_088_BlockDeviceController.after_doDeleteVolumes";
    public static final String ARTIFICIAL_FAILURE_089 = "failure_089_SRDFDeviceController.before_doSuspendLink";
    public static final String ARTIFICIAL_FAILURE_090 = "failure_090_SRDFDeviceController.after_doSuspendLink";
    public static final String ARTIFICIAL_FAILURE_091 = "failure_091_SRDFDeviceController.before_doDetachLink";
    public static final String ARTIFICIAL_FAILURE_092 = "failure_092_SRDFDeviceController.after_doDetachLink";
    public static final String ARTIFICIAL_FAILURE_093 = "failure_093_SRDFDeviceController.before_doRemoveDeviceGroups";
    public static final String ARTIFICIAL_FAILURE_094 = "failure_094_SRDFDeviceController.after_doRemoveDeviceGroups";
    public static final String ARTIFICIAL_FAILURE_095 = "failure_095_SRDFDeviceController.before_doSuspendLink";
    public static final String ARTIFICIAL_FAILURE_096 = "failure_096_SRDFDeviceController.after_doSuspendLink";
    public static final String ARTIFICIAL_FAILURE_097 = "failure_097_SRDFDeviceController.before_performResume";
    public static final String ARTIFICIAL_FAILURE_098 = "failure_098_SRDFDeviceController.after_performResume";
    public static final String ARTIFICIAL_FAILURE_099 = "failure_099_SRDFDeviceController.before_performSync";
    public static final String ARTIFICIAL_FAILURE_100 = "failure_100_SRDFDeviceController.after_performSync";


    private static final int FAILURE_SUBSTRING_LENGTH = 11;

    private static final String FAILURE_OCCURRENCE_SPLIT = "&";
    private static final String ROLLBACK_FAILURE_SPLIT = ":";

    /**
     * Counter for the number of failure injection occurrences.
     */
    private static Map<String, Integer> failureCounters = new HashMap<String, Integer>();

    /**
     * Regex pattern for extracting the method name from failure 015.
     */
    private static final String invokeMethodPattern = String.format("^.*%s(\\w+|\\*)$", ARTIFICIAL_FAILURE_015);
    private static final int METHOD_NAME_GROUP = 1;

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
     * @param failureKey
     *            key from above
     */
    public static void internalOnlyInvokeTestFailure(String failureKey) {
        // Invoke an artificial failure, if set (experimental, testing only)
        String invokeArtificialFailure = _coordinator.getPropertyInfo().getProperty(ARTIFICIAL_FAILURE);

        // If the property has been specified to reset the counter, then do so now.
        resetCounter();

        // Check for basic scenarios where you don't want to run this code at all and get out quickly.
        if (invokeArtificialFailure == null || invokeArtificialFailure.isEmpty() || invokeArtificialFailure.equals("none")) {
            return;
        }

        if (invokeArtificialFailure != null && invokeArtificialFailure.contains(failureKey.substring(0, FAILURE_SUBSTRING_LENGTH))) {
            // Increment the failure occurrence counter.
            if (failureCounters.get(failureKey) == null) {
                failureCounters.put(failureKey, 0);
            }

            // Get the failure occurrence counter for the current failure key. Increment by 1 and overwrite existing count in the map.
            int failureOccurrenceCount = failureCounters.get(failureKey);
            failureOccurrenceCount++;
            failureCounters.put(failureKey, failureOccurrenceCount);

            if (canInvokeFailure(failureKey)) {
                log("Injecting failure: " + failureKey + " at failure occurrence: " + (failureOccurrenceCount));
                throw new NullPointerException("Artificially Thrown Exception: " + failureKey);
            }
        }
    }

    /**
     * Checks to see if we can invoke the injection failure. If no failure point is set, the injected
     * failure will always be invoked. If a failure point is set, the injected failure will only
     * be invoked if the failure count matches the failure point.
     *
     * @param failureKey the failure key corresponding to the injection failure being triggered
     * @return true if the injection failure can be invoked, false otherwise.
     */
    private static boolean canInvokeFailure(String failureKey) {
        String invokeArtificialFailure = _coordinator.getPropertyInfo().getProperty(ARTIFICIAL_FAILURE);

        String firstFailureKey = invokeArtificialFailure;
        String secondFailureKey = "";

        if (invokeArtificialFailure.contains(":")) {
            String[] rollbackFailurePointSplit = invokeArtificialFailure.split(ROLLBACK_FAILURE_SPLIT);
            if (rollbackFailurePointSplit.length == 2) {
                firstFailureKey = rollbackFailurePointSplit[0];
                secondFailureKey = rollbackFailurePointSplit[1];
            }
        }

        if (failureKey.contains(firstFailureKey.substring(0, FAILURE_SUBSTRING_LENGTH))) {
            invokeArtificialFailure = firstFailureKey;
        } else if (failureKey.contains(secondFailureKey.substring(0, FAILURE_SUBSTRING_LENGTH))) {
            invokeArtificialFailure = secondFailureKey;
        }

        String[] failurePointSplit = invokeArtificialFailure.split(FAILURE_OCCURRENCE_SPLIT);
        int failurePoint = -1;

        if (failurePointSplit.length == 2) {
            try {
                failurePoint = Integer.parseInt(failurePointSplit[1]);
            } catch (NumberFormatException e) {
                // failure point value is not an integer so stick with default
            }
        }

        int currentFailureCount = failureCounters.get(failureKey);

        // If no failure point has been specified (-1) or the specified failure point matches
        // the failure counter, return true
        return (failurePoint == -1 || failurePoint == currentFailureCount);
    }

    /**
     * Reset the failure injection occurrence counter.
     */
    private static void resetCounter() {
        Boolean failureCounterReset = Boolean.valueOf(_coordinator.getPropertyInfo().getProperty(ARTIFICIAL_FAILURE_COUNTER_RESET));
        if (failureCounterReset) {
            failureCounters = new HashMap<String, Integer>();
        }
    }

    /**
     * Invoke a failure associated with an SmisCommand, return a WEBMException similar to a bad connection.
     *
     * @param failureKey
     *            key from above
     * @throws WBEMException
     */
    public static void internalOnlyInvokeSmisTestFailure(String methodName, String failureKey) throws WBEMException {
        // Invoke an artificial failure, if set (experimental, testing only)
        String invokeArtificialFailure = _coordinator.getPropertyInfo().getProperty(ARTIFICIAL_FAILURE);

        // Decipher which method we are supposed to fail on:
        if (!invokeArtificialFailure.contains("invokeMethod")) {
            return;
        }

        // Extract the method name from the system property
        Pattern p = Pattern.compile(invokeMethodPattern);
        Matcher matcher = p.matcher(invokeArtificialFailure);
        if (matcher.matches()) {
            String failOnMethodName = matcher.group(METHOD_NAME_GROUP);
            if (!Strings.isNullOrEmpty(failOnMethodName)
                    && (failOnMethodName.equalsIgnoreCase(methodName) || failOnMethodName.equalsIgnoreCase("*"))) {
                log("Injecting failure: " + ARTIFICIAL_FAILURE_015 + methodName);
                throw new WBEMException("Artificially Thrown Exception: " + failureKey + methodName + ", CIM_ERROR_FAILED (Unable to connect)");
            }
        }
    }

    /**
     * Local logging, needed for debug on failure detection.
     *
     * @param msg
     *            error message
     */
    public static void log(String msg) {
        FileOutputStream fop = null;
        try {
            _log.info(msg);
            String logFileName = "/opt/storageos/logs/invoke-test-failure.log";
            File logFile = new File(logFileName);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            fop = new FileOutputStream(logFile, true);
            fop.flush();
            StringBuffer sb = new StringBuffer(msg + "\n");
            // Last chance, if file is deleted, write manually.
            fop.write(sb.toString().getBytes());
        } catch (IOException e) {
            // It's OK if we can't log this.
        } finally {
            IOUtils.closeQuietly(fop);
        }

    }

    /**
     * Overrides the sync wait time out value so invocation failures don't take 200 minutes.
     *
     * @param syncWrapperTimeOut 200 minutes, default.
     * @return an override value in seconds if the failure invocation is set.
     */
    public static int internalOnlyOverrideSyncWrapperTimeOut(int syncWrapperTimeOut) {
        String invokeArtificialFailure = _coordinator.getPropertyInfo().getProperty(ARTIFICIAL_FAILURE);
        if (invokeArtificialFailure != null && invokeArtificialFailure.contains(ARTIFICIAL_FAILURE_015)) {
            log("Temporarily setting sync wait time to 15 seconds because failure_015 is being used.");
            return 15000;
        }
        return syncWrapperTimeOut;
    }
}
