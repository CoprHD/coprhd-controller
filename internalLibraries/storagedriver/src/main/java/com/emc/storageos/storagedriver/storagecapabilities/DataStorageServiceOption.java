/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;


import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.storagedriver.model.ServiceOption;

public class DataStorageServiceOption extends ServiceOption {
    
    // The list of capabilities.
    List<CapabilityInstance> capabilities;
    
    
    /**
     * Default constructor
     */
    public DataStorageServiceOption() {
        capabilities = new ArrayList<>();
    }
    
    /**
     * Constructor.
     * 
     * @param capabilities List of capabilities
     */
    public DataStorageServiceOption(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
    
    /**
     * Getter for the data service option capabilities.
     * 
     * @return The data service option capabilities.
     */
    public List<CapabilityInstance> getCapabilities() {
        return capabilities;
    }

    /**
     * Setter for the data service option capabilities.
     * 
     * @param capabilities The data service option capabilities.
     */
    public void setCapabilities(List<CapabilityInstance> capabilities) {
        this.capabilities = capabilities;
    }
}
