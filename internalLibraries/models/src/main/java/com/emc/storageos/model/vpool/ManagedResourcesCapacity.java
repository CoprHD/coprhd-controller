/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class ManagedResourcesCapacity {

    private List<ManagedResourceCapacity> resourceCapacityList = new ArrayList<ManagedResourceCapacity>();

    /**
     * A list of managed resource capacity response instances.
     * 
     */
    @XmlElement
    public List<ManagedResourceCapacity> getResourceCapacityList() {
        if (resourceCapacityList == null) {
            resourceCapacityList = new ArrayList<ManagedResourceCapacity>();
        }
        return resourceCapacityList;
    }

    public void setResourceCapacityList(
            List<ManagedResourceCapacity> resourceCapacityList) {
        this.resourceCapacityList = resourceCapacityList;
    }

    @XmlRootElement
    public static class ManagedResourceCapacity {

        private CapacityResourceType type;
        private long numResources;
        private double resourceCapacity;

        /**
         * The type of the resources. 
         * Valid values:
         *  VOLUME
         *  FILESHARE
         *  POOL
         *  BUCKET
         * 
         */
        @XmlElement
        public CapacityResourceType getType() {
            return type;
        }

        public void setType(CapacityResourceType type) {
            this.type = type;
        }

        /**
         * The number of resources.
         * 
         */
        @XmlElement(name = "nResources")
        @JsonProperty("nResources")
        public long getNumResources() {
            return numResources;
        }

        public void setNumResources(long numResources) {
            this.numResources = numResources;
        }

        /**
         * The capacity of the resources.
         * 
         */
        @XmlElement
        public double getResourceCapacity() {
            return resourceCapacity;
        }

        public void setResourceCapacity(double resourceCapacity) {
            this.resourceCapacity = resourceCapacity;
        }

    }

    public enum CapacityResourceType {
        VOLUME,
        FILESHARE,
        POOL, 
        BUCKET
    }

}
