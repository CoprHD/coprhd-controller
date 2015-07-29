/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.systems;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@XmlRootElement
public class StorageSystemModelsManagedCapacity {

    private Map<String, StorageSystemModelManagedCapacity> modelCapacityMap = new HashMap<String, StorageSystemModelManagedCapacity>();

    /**
     * A list of managed resource capacity response instances.
     */
    @XmlElement
    public Map<String, StorageSystemModelManagedCapacity> getModelCapacityMap() {
        if (modelCapacityMap == null) {
            modelCapacityMap = new HashMap<String, StorageSystemModelManagedCapacity>();
        }
        return modelCapacityMap;
    }

    public void setModelCapacityMap(
            Map<String, StorageSystemModelManagedCapacity> modelCapacityMap) {
        this.modelCapacityMap = modelCapacityMap;
    }

    @XmlRootElement
    public static class StorageSystemModelManagedCapacity {
        private double capacity;  // total capacities of storage systems of a specific model
        private long number;      // number of storage systems of a specific model

        /**
         * The total capacities of storage systems of a specific model
         */
        @XmlElement
        public double getCapacity() {
            return capacity;
        }

        public void setCapacity(double capacity) {
            this.capacity = capacity;
        }

        /**
         * The number of storage systems of a specific model
         */
        @XmlElement
        public long getNumber() {
            return number;
        }
        public void setNumber(long number) {
            this.number = number;
        }

    }
}
