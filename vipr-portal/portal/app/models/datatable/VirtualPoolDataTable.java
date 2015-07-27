/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.Collection;

import models.PoolTypes;
import models.ProvisioningTypes;

import org.apache.commons.lang.StringUtils;

import util.datatable.DataTable;

import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;

public class VirtualPoolDataTable extends DataTable {

    public VirtualPoolDataTable() {
        addColumn("storageType").hidden();
        addColumn("name").setRenderFunction("renderLink");
        addColumn("description");
        addColumn("poolType").hidden();
        addColumn("provisioningType").hidden();
        addColumn("provisionedAs");
        addColumn("storagePoolAssignment");
        addColumn("protocols");
        addColumn("numPools");
        addColumn("numResources");
        sortAllExcept("storageType");
        setDefaultSort("name", "asc");
    }

    public static class VirtualPoolInfo {
        public String id;
        public String name;
        public String description;
        public Integer numPools;
        public Integer numResources;
        public String poolType;
        public String poolTypeDisplay;
        public String provisioningType;
        public String provisioningTypeDisplay;
        public String protocols;
        public Boolean useMatchedPools;
        public String storagePoolAssignment;
        public Boolean warning;
        public String provisionedAs;

        public VirtualPoolInfo() {
        }

        public VirtualPoolInfo(VirtualPoolCommonRestRep vsp) {
            this.id = vsp.getId().toString();
            this.name = vsp.getName();
            this.poolType = vsp.getType();
            this.provisioningType = vsp.getProvisioningType();
            
            this.poolTypeDisplay = PoolTypes.getDisplayValue(vsp.getType());
            this.provisioningTypeDisplay = ProvisioningTypes.getDisplayValue(vsp.getProvisioningType());
            this.provisionedAs = String.format("%s (%s)", poolTypeDisplay, provisioningTypeDisplay);
            this.description = vsp.getDescription();
            if (Boolean.TRUE.equals(vsp.getUseMatchedPools())) {
                this.storagePoolAssignment = "automatic";
                this.numPools = size(vsp.getMatchedStoragePools());
            }
            else {
                this.storagePoolAssignment = "manual";
                this.numPools = size(vsp.getAssignedStoragePools());
            }

            this.numResources = defaultInt(vsp.getNumResources());
            this.protocols = StringUtils.join(vsp.getProtocols(), ", ");
        }

        private static int size(Collection<?> collection) {
            return collection != null ? collection.size() : 0;
        }

        private int defaultInt(Integer value) {
            return value != null ? value : 0;
        }
    }
}
