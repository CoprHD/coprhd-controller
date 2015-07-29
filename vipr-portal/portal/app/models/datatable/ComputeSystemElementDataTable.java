/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static com.emc.vipr.client.core.util.ResourceUtils.name;

import java.text.DecimalFormat;

import org.apache.commons.lang.StringUtils;

import play.i18n.Messages;

import util.datatable.DataTable;

import com.emc.sa.util.SizeUtils;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.vipr.client.core.util.CachedResources;

public class ComputeSystemElementDataTable extends DataTable {

    public ComputeSystemElementDataTable() {
        addColumn("name");
        addColumn("computeSystem").hidden();
        addColumn("model");
        addColumn("numberOfProcessors");
        addColumn("numberOfCores");
        addColumn("numberOfThreads");
        addColumn("processorSpeed");
        addColumn("ram").hidden();
        addColumn("ramString");
        addColumn("uuid").hidden();
        addColumn("dn").setRenderFunction("render.operationalStatus").hidden();
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        addColumn("status");
        setDefaultSort("name", "asc");
        sortAllExcept("id");
    }

    public static class ComputeElementInfo extends DiscoveredSystemInfo {
        public String id;
        public String name;
        public String computeSystem;
        public String model;
        public Short numberOfProcessors;
        public Integer numberOfCores;
        public Integer numberOfThreads;
        public String processorSpeed;
        public Long ram;
        public String ramString;
        public String uuid;
        public String dn;
        public String registrationStatus;
        public String status;

        public boolean assigned;

        public ComputeElementInfo(ComputeElementRestRep computeElement) {
            this(computeElement, (String) null);
        }

        public ComputeElementInfo(ComputeElementRestRep computeElement, CachedResources<ComputeSystemRestRep> computeSystems) {
            this(computeElement, name(computeSystems.get(computeElement.getComputeSystem())));
        }

        public ComputeElementInfo(ComputeElementRestRep computeElement, String computeSystemName) {
            super(computeElement);
            this.id = computeElement.getId().toString();
            this.name = computeElement.getName();
            this.computeSystem = StringUtils.defaultIfEmpty(computeSystemName, "N/A");
            this.model = computeElement.getModel();
            this.numberOfProcessors = computeElement.getNumOfProcessors();
            this.numberOfThreads = computeElement.getNumOfThreads();
            this.processorSpeed = FormatSpeed(computeElement.getProcessorSpeed());
            this.ram = computeElement.getRam();
            this.ramString = SizeUtils.humanReadableMegaByteCount(computeElement.getRam());
            this.uuid = computeElement.getUuid();
            this.dn = "OK";
            this.numberOfCores = computeElement.getNumOfCores();
            this.registrationStatus = computeElement.getRegistrationStatus();
            this.status = Messages.get("computeSystemElements.Unavailable");
            if (computeElement.getAvailable()) {
                this.status = Messages.get("computeSystemElements.Available");
            }

        }

        // Suppressing Sonar violation of Method name FormatSpeed should comply with naming convention
        @SuppressWarnings("squid:S00100")
        public String FormatSpeed(String speed) {
            String returnSpeed = Messages.get("computeSystemElements.Unspecified");
            try {
                Float newSpeed = Float.parseFloat(speed);

                DecimalFormat df = new DecimalFormat("0.00");
                df.setMaximumFractionDigits(2);
                newSpeed = Float.parseFloat(df.format(newSpeed));

                returnSpeed = newSpeed.toString();
            } catch (Exception e) {
                // don't need to do anything - this is expected when UCS sends string
            }
            return returnSpeed;
        }
    }
}
