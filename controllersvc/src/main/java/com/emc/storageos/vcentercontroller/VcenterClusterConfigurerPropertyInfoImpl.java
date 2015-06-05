/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import com.emc.storageos.model.property.PropertyInfo;
import com.vmware.vim25.ClusterConfigSpecEx;
import com.vmware.vim25.ClusterDasAdmissionControlPolicy;
import com.vmware.vim25.ClusterDasConfigInfo;
import com.vmware.vim25.ClusterDasVmSettings;
import com.vmware.vim25.ClusterDpmConfigInfo;
import com.vmware.vim25.ClusterDrsConfigInfo;
import com.vmware.vim25.ClusterVmToolsMonitoringSettings;
import com.vmware.vim25.ComputeResourceConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: alaplante
 * Date: 10/7/14
 * Time: 2:06 PM
 * To change this template use File | Settings | File Templates.
 */
public class VcenterClusterConfigurerPropertyInfoImpl implements VcenterClusterConfigurer {

    private final static Logger _log = LoggerFactory.getLogger(VcenterClusterConfigurerPropertyInfoImpl.class);

    public ClusterConfigSpecEx configure(Object input) throws Exception {
        PropertyInfo propertyInfo = (PropertyInfo)input;
        VcenterClusterSettingsConverter converter = VcenterClusterSettingsConverter.getInstance();

        ClusterConfigSpecEx clusterConfigSpecEx = new ClusterConfigSpecEx();
        clusterConfigSpecEx.setDrsConfig(new ClusterDrsConfigInfo());
        clusterConfigSpecEx.getDrsConfig().setEnabled(true); // Enable DRS
        clusterConfigSpecEx.getDrsConfig().setDefaultVmBehavior(converter.convertToDrsBehavior(propertyInfo.getProperty("vcenter_drs_automationLevel")));

        /**
         * VMWare DRS
         */
        if(Boolean.parseBoolean(propertyInfo.getProperty("vcenter_drs_enabled"))) {
            _log.info("Set VMWare DRS options for cluster");
            clusterConfigSpecEx.setDrsConfig(new ClusterDrsConfigInfo());
            clusterConfigSpecEx.getDrsConfig().setEnabled(true); // Enable DRS
            _log.info("Set Automation level options for cluster");
            clusterConfigSpecEx.getDrsConfig().setDefaultVmBehavior(converter.convertToDrsBehavior(propertyInfo.getProperty("vcenter_drs_automationLevel"))); // Automation level
            clusterConfigSpecEx.getDrsConfig().setVmotionRate(converter.validateThresholdRange(Integer.parseInt(propertyInfo.getProperty("vcenter_drs_migrationThreshold")))); // Migration threshold - 1 is Aggressive in vCenter
            clusterConfigSpecEx.setDpmConfig(new ClusterDpmConfigInfo()); // Power management - DRS must be enabled
            clusterConfigSpecEx.getDpmConfig().setEnabled(Boolean.parseBoolean(propertyInfo.getProperty("vcenter_drs_dpm_enabled"))); // Enable Power Management - DRS must first be enabled
            if(Boolean.parseBoolean(propertyInfo.getProperty("vcenter_drs_dpm_enabled"))) {
                _log.info("Set DPM options for cluster");
                clusterConfigSpecEx.getDpmConfig().setDefaultDpmBehavior(converter.convertToDpmBehavior(propertyInfo.getProperty("vcenter_drs_dpm_powerManagement"))); // Power Management
                clusterConfigSpecEx.getDpmConfig().setHostPowerActionRate((converter.validateThresholdRange(Integer.parseInt(propertyInfo.getProperty("vcenter_drs_dpm_dpmThreshold"))))); // DPM Threshold - 1 is Aggressive in vCenter
            }
        }
        /**
         * VMWare HA
         */
        if(Boolean.parseBoolean(propertyInfo.getProperty("vcenter_das_enabled"))) {
            _log.info("Set VMWare HA options for cluster");
            clusterConfigSpecEx.setDasConfig(new ClusterDasConfigInfo());
            clusterConfigSpecEx.getDasConfig().setEnabled(true);
            clusterConfigSpecEx.getDasConfig().setHostMonitoring(converter.convertToClusterDasConfigInfoServiceState(Boolean.parseBoolean(propertyInfo.getProperty("vcenter_das_hostMonitoring_enabled"))).toString()); // Host Monitoring Status

            _log.info("Set Admission Control Policy options for cluster");
            // Work around API quirk - A non-null ACP must be set regardless if ACP is enabled so if disabled just assign some value. Value doesnt matter and will not be used but it cannot be null!
            Boolean enableAdmissionControl = Boolean.parseBoolean(propertyInfo.getProperty("vcenter_das_admissionControl_enabled"));
            String acpName = enableAdmissionControl ? propertyInfo.getProperty("vcenter_das_admissionControlPolicy_policy") : "resourcePercentage";
            Integer acpHostFailoverLevel = enableAdmissionControl ? Integer.parseInt(propertyInfo.getProperty("vcenter_das_admissionControlPolicy_hostFailureLevel_failoverLevel")) : 1;
            Integer acpCpuFailoverPercent = enableAdmissionControl ? Integer.parseInt(propertyInfo.getProperty("vcenter_das_admissionControlPolicy_resourcePercentage_cpuPercentage")) : 50;
            Integer acpMemoryFailoverPercent = enableAdmissionControl ? Integer.parseInt(propertyInfo.getProperty("vcenter_das_admissionControlPolicy_resourcePercentage_memoryPercentage")) : 50;
            clusterConfigSpecEx.getDasConfig().setAdmissionControlEnabled(enableAdmissionControl); // Admission Control
            ClusterDasAdmissionControlPolicy clusterDasAdmissionControlPolicy = converter.convertAdmissionControlPolicyToClusterDasAdmissionControlPolicy(acpName, acpHostFailoverLevel, acpCpuFailoverPercent, acpMemoryFailoverPercent);
            clusterConfigSpecEx.getDasConfig().setAdmissionControlPolicy(clusterDasAdmissionControlPolicy);

            /**
             * Virtual Machine Options
             */
            _log.info("Set Virtual Machine options for cluster");
            clusterConfigSpecEx.getDasConfig().setDefaultVmSettings(new ClusterDasVmSettings());
            clusterConfigSpecEx.getDasConfig().getDefaultVmSettings().setRestartPriority(converter.convertToClusterDasVmSettingsRestartPriority(propertyInfo.getProperty("vcenter_das_vmSettings_restartPriority")).toString()); // VM restart priority
            clusterConfigSpecEx.getDasConfig().getDefaultVmSettings().setIsolationResponse(converter.convertToClusterDasVmSettingsIsolationResponse(propertyInfo.getProperty("vcenter_das_vmSettings_isolationResponse")).toString()); // Host Isolation Response
            clusterConfigSpecEx.getDasConfig().setVmMonitoring(converter.convertToClusterDasConfigInfoVmMonitoringState(propertyInfo.getProperty("vcenter_das_vmSettings_vmMonitoring_monitoring")).toString()); // VM Monitoring
            clusterConfigSpecEx.getDasConfig().getDefaultVmSettings().setVmToolsMonitoringSettings(new ClusterVmToolsMonitoringSettings()); // VM Monitoring - Default Cluster Settings - Monitoring sensitivity is composed of several options (simplified in UI)
            converter.convertVmMonitoringSensitivityToFinerGrainedSettings(propertyInfo.getProperty("vcenter_das_vmSettings_vmMonitoring_sensitivity"), clusterConfigSpecEx.getDasConfig().getDefaultVmSettings().getVmToolsMonitoringSettings());
        }

        /**
         * VMWare EVC
         */
        // TODO figure out EVC API
        _log.info("EVC defaulted to always disabled");
        /**
         * VM Swapfile location
         */
        _log.info("Set VM Swapfile location options for cluster");
        ComputeResourceConfigSpec computeResourceConfigSpec = (ComputeResourceConfigSpec)clusterConfigSpecEx;
        computeResourceConfigSpec.setVmSwapPlacement(converter.convertToVirtualMachineConfigInfoSwapPlacementType(propertyInfo.getProperty("vcenter_swapfile_policy")).toString()); //  vmDirectory = UI option 1,  hostLocal = UI option 2 - option 2 ALSO requires setting some additional property localSwapDatastore on VM - will we ever do this?

        return clusterConfigSpecEx;
    }
}
