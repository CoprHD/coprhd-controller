/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import com.vmware.vim25.ClusterDasAdmissionControlPolicy;
import com.vmware.vim25.ClusterDasConfigInfoServiceState;
import com.vmware.vim25.ClusterDasConfigInfoVmMonitoringState;
import com.vmware.vim25.ClusterDasVmSettingsIsolationResponse;
import com.vmware.vim25.ClusterDasVmSettingsRestartPriority;
import com.vmware.vim25.ClusterFailoverLevelAdmissionControlPolicy;
import com.vmware.vim25.ClusterFailoverResourcesAdmissionControlPolicy;
import com.vmware.vim25.ClusterVmToolsMonitoringSettings;
import com.vmware.vim25.DpmBehavior;
import com.vmware.vim25.DrsBehavior;
import com.vmware.vim25.VirtualMachineConfigInfoSwapPlacementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VcenterClusterSettingsConverter {
    private final static Logger _log = LoggerFactory.getLogger(VcenterClusterSettingsConverter.class);

    private static VcenterClusterSettingsConverter instance = new VcenterClusterSettingsConverter();

    public synchronized static VcenterClusterSettingsConverter getInstance() {
        return instance;
    }

    // Conversion methods
    public DrsBehavior convertToDrsBehavior(String name) throws Exception {
        for (DrsBehavior drsBehavior : DrsBehavior.values()) {
            if (drsBehavior.toString().equals(name))
                return drsBehavior;
        }
        _log.error("Invalid automation level " + name);
        throw new Exception("Invalid automation level " + name);
    }

    public DpmBehavior convertToDpmBehavior(String name) throws Exception {
        for (DpmBehavior dpmBehavior : DpmBehavior.values()) {
            if (dpmBehavior.toString().equals(name))
                return dpmBehavior;
        }
        _log.error("Invalid power management " + name);
        throw new Exception("Invalid power management " + name);
    }

    public ClusterDasConfigInfoServiceState convertToClusterDasConfigInfoServiceState(boolean enabled) {
        return enabled ? ClusterDasConfigInfoServiceState.enabled : ClusterDasConfigInfoServiceState.disabled;
    }

    public Integer validateThresholdRange(Integer threshold) throws Exception {
        if (threshold < 1 || threshold > 5) {
            _log.error("Invalid threshold " + threshold);
            throw new Exception("Invalid threshold " + threshold);
        }
        return threshold;
    }

    public Integer validateFailoverRange(Integer level) throws Exception {
        if (level < 1 || level > 4) {
            _log.error("Invalid failover level " + level);
            throw new Exception("Invalid failover level " + level);
        }
        return level;
    }

    public Integer validatePercentage(Integer percent) throws Exception {
        if (percent < 0 || percent > 100) {
            _log.error("Invalid percent " + percent);
            throw new Exception("Invalid percent " + percent);
        }
        return percent;
    }

    public ClusterDasAdmissionControlPolicy convertAdmissionControlPolicyToClusterDasAdmissionControlPolicy(String policy,
            Integer acpHostFailoverLevel, Integer acpCpuFailoverPercent, Integer acpMemoryFailoverPercent) throws Exception {
        if (policy.equals("hostFailureLevel")) {
            ClusterFailoverLevelAdmissionControlPolicy cflacp = new ClusterFailoverLevelAdmissionControlPolicy(); // Option 1 - Host
                                                                                                                  // failures cluster
                                                                                                                  // tolerates
            cflacp.setFailoverLevel(validateFailoverRange(acpHostFailoverLevel));
            return cflacp;
        } else if (policy.equals("resourcePercentage")) {
            ClusterFailoverResourcesAdmissionControlPolicy cdracp = new ClusterFailoverResourcesAdmissionControlPolicy(); // Option 2 -
                                                                                                                          // Percentage of
                                                                                                                          // cluster
                                                                                                                          // resources
                                                                                                                          // reserved as
                                                                                                                          // failover for
                                                                                                                          // spare capacity
            cdracp.setCpuFailoverResourcesPercent(validatePercentage(acpCpuFailoverPercent)); // This is the only option exposed in the UI
            cdracp.setMemoryFailoverResourcesPercent(validatePercentage(acpMemoryFailoverPercent));
            return cdracp;
            // } else if(policy == AdmissionControlPolicy.SPECIFIED_HOST.name) {
            // ClusterFailoverHostAdmissionControlPolicy cfhacp = new ClusterFailoverHostAdmissionControlPolicy(); // Option 3 - Specify a
            // failover host TODO
            // cfhacp.setFailoverHosts([]); // list of a single host
            // return cfhacp // Admission Control Policy
        } else {
            _log.error("Invalid admission control policy " + policy);
            throw new Exception("Invalid admission control policy " + policy);
        }
    }

    ClusterDasVmSettingsRestartPriority convertToClusterDasVmSettingsRestartPriority(String name) throws Exception {
        for (ClusterDasVmSettingsRestartPriority clusterDasVmSettingsRestartPriority : ClusterDasVmSettingsRestartPriority.values()) {
            if (clusterDasVmSettingsRestartPriority.toString().equals(name))
                return clusterDasVmSettingsRestartPriority;
        }
        _log.error("Invalid vm restart priority " + name);
        throw new Exception("Invalid vm restart priority " + name);
    }

    ClusterDasVmSettingsIsolationResponse convertToClusterDasVmSettingsIsolationResponse(String name) throws Exception {
        for (ClusterDasVmSettingsIsolationResponse clusterDasVmSettingsIsolationResponse : ClusterDasVmSettingsIsolationResponse.values()) {
            if (clusterDasVmSettingsIsolationResponse.toString().equals(name))
                return clusterDasVmSettingsIsolationResponse;
        }
        _log.error("Invalid host isolation response " + name);
        throw new Exception("Invalid host isolation response " + name);
    }

    ClusterDasConfigInfoVmMonitoringState convertToClusterDasConfigInfoVmMonitoringState(String name) throws Exception {
        for (ClusterDasConfigInfoVmMonitoringState clusterDasConfigInfoVmMonitoringState : ClusterDasConfigInfoVmMonitoringState.values()) {
            if (clusterDasConfigInfoVmMonitoringState.toString().equals(name))
                return clusterDasConfigInfoVmMonitoringState;
        }
        _log.error("Invalid vm monitoring status " + name);
        throw new Exception("Invalid vm monitoring status " + name);
    }

    VirtualMachineConfigInfoSwapPlacementType convertToVirtualMachineConfigInfoSwapPlacementType(String name) throws Exception {
        for (VirtualMachineConfigInfoSwapPlacementType virtualMachineConfigInfoSwapPlacementType : VirtualMachineConfigInfoSwapPlacementType
                .values()) {
            if (virtualMachineConfigInfoSwapPlacementType.toString().equals(name))
                return virtualMachineConfigInfoSwapPlacementType;
        }
        _log.error("Invalid swap placement " + name);
        throw new Exception("Invalid swap placement " + name);
    }

    void convertVmMonitoringSensitivityToFinerGrainedSettings(String vmMonitoringSensitivity,
            ClusterVmToolsMonitoringSettings clusterVmToolsMonitoringSettings) throws Exception {
        Integer failureInterval;
        Integer minUpTime;
        Integer maxFailures;
        Integer maxFailureWindow;
        if (vmMonitoringSensitivity.equals("low")) {
            failureInterval = 120;
            minUpTime = 480;
            maxFailures = 3;
            maxFailureWindow = 168;
        } else if (vmMonitoringSensitivity.equals("medium")) {
            failureInterval = 120;
            minUpTime = 240;
            maxFailures = 3;
            maxFailureWindow = 24;
        } else if (vmMonitoringSensitivity.equals("high")) {
            failureInterval = 30;
            minUpTime = 120;
            maxFailures = 3;
            maxFailureWindow = 1;
        } else {
            _log.error("Invalid vm monitoring sensitivity " + vmMonitoringSensitivity);
            throw new Exception("Invalid vm monitoring sensitivity " + vmMonitoringSensitivity);
        }
        clusterVmToolsMonitoringSettings.setFailureInterval(failureInterval); // Failure interval - heatbeat seconds
        clusterVmToolsMonitoringSettings.setMinUpTime(minUpTime); // Minimum uptime -
        clusterVmToolsMonitoringSettings.setMaxFailures(maxFailures); // Maximum per-VM restarts - max number of failures and restarts then
                                                                      // automated responses stop
        clusterVmToolsMonitoringSettings.setMaxFailureWindow(maxFailureWindow); // Maximum uptime - time in seconds for automated response
                                                                                // to do its thing???
    }
}
