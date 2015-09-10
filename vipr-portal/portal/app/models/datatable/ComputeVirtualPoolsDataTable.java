/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.ComputeSystemTypes;
import models.PoolAssignmentTypes;
import util.ComputeVirtualPoolUtils;
import util.datatable.DataTable;

import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;

public class ComputeVirtualPoolsDataTable extends DataTable {

    public ComputeVirtualPoolsDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("description");
        addColumn("type");
        addColumn("matching");
        addColumn("numAvailElements");
        addColumn("numElements").hidden();
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class VirtualPoolInfo {
        public String id;
        public String name;
        public String description;
        public String systemType;
        public Boolean useMatchedElements;
        public String matching;
        public String numAvailElements;
        public Integer numElements;
        public String type;
        public Integer minProcessors;
        public Integer maxProcessors;
        public Integer minTotalCores;
        public Integer maxTotalCores;
        public Integer minTotalThreads;
        public Integer maxTotalThreads;
        public Integer minCpuSpeed;
        public Integer maxCpuSpeed;
        public Integer minMemory;
        public Integer maxMemory;
        public Integer minNics;
        public Integer maxNics;
        public Integer minHbas;
        public Integer maxHbas;

        public VirtualPoolInfo() {
        }

        public VirtualPoolInfo(ComputeVirtualPoolRestRep vsp) {
            this.id = vsp.getId().toString();
            this.name = vsp.getName();
            this.description = vsp.getDescription();
            this.systemType = vsp.getSystemType();
            this.type = ComputeSystemTypes.getDisplayValue(vsp.getSystemType().toLowerCase());
            this.useMatchedElements = vsp.getUseMatchedElements();
            if (this.useMatchedElements) {
                this.matching = PoolAssignmentTypes.getDisplayValue(PoolAssignmentTypes.AUTOMATIC);
                this.numElements = vsp.getMatchedComputeElements().size();
            } else {
                this.matching = PoolAssignmentTypes.getDisplayValue(PoolAssignmentTypes.MANUAL);
                ComputeElementListRestRep elementList = ComputeVirtualPoolUtils.getAssignedComputeElements(vsp.getId().toString());
                this.numElements = elementList.getList().size();
            }
            this.numAvailElements = String.valueOf(vsp.getAvailableMatchedComputeElements().size()) + "/"
                    + String.valueOf(this.numElements);
            this.minProcessors = vsp.getMinProcessors();
            this.maxProcessors = vsp.getMaxProcessors();
            this.minTotalCores = vsp.getMinTotalCores();
            this.maxTotalCores = vsp.getMaxTotalCores();
            this.minTotalThreads = vsp.getMinTotalThreads();
            this.maxTotalThreads = vsp.getMaxTotalThreads();
            this.minCpuSpeed = vsp.getMinCpuSpeed();
            this.maxCpuSpeed = vsp.getMaxCpuSpeed();
            this.minMemory = vsp.getMinMemory();
            this.maxMemory = vsp.getMaxMemory();
            this.minNics = vsp.getMinNics();
            this.maxNics = vsp.getMaxNics();
            this.minHbas = vsp.getMinHbas();
            this.maxHbas = vsp.getMaxHbas();
        }
    }
}
