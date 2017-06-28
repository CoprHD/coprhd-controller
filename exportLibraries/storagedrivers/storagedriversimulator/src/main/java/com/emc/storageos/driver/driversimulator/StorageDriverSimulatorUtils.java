package com.emc.storageos.driver.driversimulator;


import java.util.List;

import com.emc.storageos.storagedriver.storagecapabilities.CapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.CommonStorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.DataStorageServiceOption;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class StorageDriverSimulatorUtils {

    public static CapabilityInstance getHostIOLimitsCapabilities(StorageCapabilities storageCapabilities) {
        // get hostio limits capability
        CapabilityInstance hostIOLimits = null;
        CommonStorageCapabilities commonCapabilities= storageCapabilities.getCommonCapabilitis();
        if (commonCapabilities != null) {
            List<DataStorageServiceOption> dataService = commonCapabilities.getDataStorage();
            if (dataService != null) {
                for (DataStorageServiceOption dataServiceOption : dataService) {
                    List<CapabilityInstance> capabilityList = dataServiceOption.getCapabilities();
                    if (capabilityList != null) {
                        for (CapabilityInstance ci : capabilityList) {
                            if (ci.getCapabilityDefinitionUid() != null &&
                                    ci.getCapabilityDefinitionUid().equals(CapabilityDefinition.CapabilityUid.hostIOLimits.toString()) &&
                                    ci.getProperties() != null) {
                                hostIOLimits = ci;
                            }
                        }
                    }

                }
            }
        }
        return hostIOLimits;
    }
}
