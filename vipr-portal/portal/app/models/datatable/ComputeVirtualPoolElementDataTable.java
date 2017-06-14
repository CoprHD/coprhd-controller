/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.text.DecimalFormat;

import org.apache.commons.lang.StringUtils;

import util.datatable.DataTable;

import com.emc.sa.util.SizeUtils;
import com.emc.storageos.model.compute.ComputeElementRestRep;

public class ComputeVirtualPoolElementDataTable extends DataTable {

    public ComputeVirtualPoolElementDataTable() {
        addColumn("computeSystem").hidden();
        addColumn("name");
        addColumn("computeVirtualPool").hidden();
        addColumn("model");
        addColumn("numberOfProcessors");
        addColumn("numberOfCores");
        addColumn("numberOfThreads");
        addColumn("processorSpeed");
        addColumn("ram").hidden();
        addColumn("ramString");
        addColumn("hostName");
        addColumn("available").setRenderFunction("renderBoolean");
        addColumn("uuid").hidden();
        setDefaultSort("name", "asc");
        sortAllExcept("id");
    }

    public static class ComputeVirtualElementInfo extends DiscoveredSystemInfo {
        public String id;
        public String name;
        public String model;
        public Boolean available;
        public String computeVirtualPool;
        public String computeSystem;
        public Short numberOfProcessors;
        public Integer numberOfCores;
        public Integer numberOfThreads;
        public String processorSpeed;
        public Long ram;
        public String ramString;
        public String uuid;
        public String hostName;

        public boolean assigned;

        public ComputeVirtualElementInfo(ComputeElementRestRep computeElement) {
            this(computeElement, (String) null, (String) null);
        }

        public ComputeVirtualElementInfo(ComputeElementRestRep computeElement, String computeVirtualPoolName, String computeSystemName) {
            super(computeElement);
            this.id = computeElement.getId().toString();
            this.name = computeElement.getName();
            this.model = computeElement.getModel();
            this.available = computeElement.getAvailable();
            this.computeVirtualPool = StringUtils.defaultIfEmpty(computeVirtualPoolName, "N/A");
            this.computeSystem = StringUtils.defaultIfEmpty(computeSystemName, "N/A");
            this.numberOfProcessors = computeElement.getNumOfProcessors();
            this.numberOfThreads = computeElement.getNumOfThreads();
            this.processorSpeed = FormatSpeed(computeElement.getProcessorSpeed());
            this.ram = computeElement.getRam();
            this.ramString = SizeUtils.humanReadableMegaByteCount(computeElement.getRam());
            this.uuid = computeElement.getUuid();
            this.numberOfCores = computeElement.getNumOfCores();
            this.hostName = computeElement.getHostName();
        }

        // Suppressing Sonar violation of Method name FormatSpeed should comply with naming convention
        @SuppressWarnings("squid:S00100")
        public String FormatSpeed(String speed) {
            Float newSpeed = Float.parseFloat(speed);

            DecimalFormat df = new DecimalFormat("0.00");
            df.setMaximumFractionDigits(2);
            newSpeed = Float.parseFloat(df.format(newSpeed));

            return newSpeed.toString();
        }
    }
}
