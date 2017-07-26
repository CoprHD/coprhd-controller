/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.storagecapabilities.AutoTieringPolicyCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.CommonStorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.HostIOLimitsCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilitiesUtils;
import com.emc.storageos.storagedriver.storagecapabilities.VolumeCompressionCapabilityDefinition;

/**
 * Utility class for storage driver simulator
 */
public class StorageDriverSimulatorUtils {

    private static int count =0;

    public static CapabilityInstance getHostIOLimitsCapabilities(StorageCapabilities storageCapabilities) {
        // get hostio limits capability
        List<CapabilityInstance> hostIOLimits = null;
        CommonStorageCapabilities commonCapabilities = storageCapabilities.getCommonCapabilities();
        if (commonCapabilities != null) {
            hostIOLimits = StorageCapabilitiesUtils.getDataStorageServiceCapability(commonCapabilities, CapabilityDefinition.CapabilityUid.hostIOLimits);
        }
        return hostIOLimits == null? null : hostIOLimits.get(0);
    }

    public static CapabilityInstance getVolumeCompressionCapabilities(StorageCapabilities storageCapabilities) {
        // get volume compression capability
        List<CapabilityInstance> volumeCompression = null;
        CommonStorageCapabilities commonCapabilities = storageCapabilities.getCommonCapabilities();
        if (commonCapabilities != null) {
            volumeCompression = StorageCapabilitiesUtils.getDataStorageServiceCapability(commonCapabilities, CapabilityDefinition.CapabilityUid.volumeCompression);
        }
        return volumeCompression == null? null : volumeCompression.get(0);
    }

    public static void addHostIOLimitsCapabilities(CommonStorageCapabilities commonCapabilities) {

        int host_io_limits_iops;
        host_io_limits_iops = ++count%2 == 0 ? 100 : 200;

        HostIOLimitsCapabilityDefinition capabilityDefinition = new HostIOLimitsCapabilityDefinition();
        Map<String, List<String>> capabilityProperties = new HashMap<>();
        capabilityProperties.put(HostIOLimitsCapabilityDefinition.PROPERTY_NAME.HOST_IO_LIMIT_BANDWIDTH.name(),
                Collections.singletonList("100"));
        capabilityProperties.put(HostIOLimitsCapabilityDefinition.PROPERTY_NAME.HOST_IO_LIMIT_IOPS.name(),
                Collections.singletonList(Integer.toString(host_io_limits_iops)));

        CapabilityInstance hostIOLimitsCapability = new CapabilityInstance(capabilityDefinition.getId(),
                capabilityDefinition.getId(), capabilityProperties);

        StorageCapabilitiesUtils.addDataStorageServiceOption(commonCapabilities, Collections.singletonList(hostIOLimitsCapability));
    }

    public static void addVolumeCompressionCapability(CommonStorageCapabilities commonCapabilities) {

        VolumeCompressionCapabilityDefinition capabilityDefinition = new VolumeCompressionCapabilityDefinition();
        Map<String, List<String>> capabilityProperties = new HashMap<>();
        capabilityProperties.put(VolumeCompressionCapabilityDefinition.PROPERTY_NAME.COMPRESSION_RATIO.name(),
                Collections.singletonList("10"));
        capabilityProperties.put(VolumeCompressionCapabilityDefinition.PROPERTY_NAME.ENABLED.name(),
                Collections.singletonList("true"));

        CapabilityInstance volumeCompressionCapability = new CapabilityInstance(capabilityDefinition.getId(),
                capabilityDefinition.getId(), capabilityProperties);

        StorageCapabilitiesUtils.addDataStorageServiceOption(commonCapabilities, Collections.singletonList(volumeCompressionCapability));
    }


    public static void addVolumeAutoTieringPoliciesCapability(CommonStorageCapabilities commonCapabilities) {

        // We set the same capabilities for all volumes. Each volume will have two policy ids with thick provisioning type.
        // Based on how we create storage pool capabilities when we discover storage pools, volumes in pool 1 should match this pool
        // auto-tiering policy.
        AutoTieringPolicyCapabilityDefinition capabilityDefinition = new AutoTieringPolicyCapabilityDefinition();
        List<String> policyIds = new ArrayList<>();
        for (int j = 1; j <= 2; j++) {
            String policyId = "Auto-Tier-Policy-" + 1 + j;
            policyIds.add(policyId);
        }
        Map<String, List<String>> props = new HashMap<>();
        props.put(AutoTieringPolicyCapabilityDefinition.PROPERTY_NAME.POLICY_ID.name(), policyIds);
        String provisioningType = StoragePool.AutoTieringPolicyProvisioningType.ThicklyProvisioned.name();
        props.put(AutoTieringPolicyCapabilityDefinition.PROPERTY_NAME.PROVISIONING_TYPE.name(), Arrays.asList(provisioningType));
        CapabilityInstance capabilityInstance = new CapabilityInstance(capabilityDefinition.getId(), "auto-tiering policy", props);
        StorageCapabilitiesUtils.addDataStorageServiceOption(commonCapabilities, Collections.singletonList(capabilityInstance));
    }
}
