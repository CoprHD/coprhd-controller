package models.datatable;

import java.net.URI;

import com.emc.storageos.model.ports.StoragePortRestRep;

import util.datatable.DataTable;

public class ExportPathPoliciesDataTable extends DataTable {

    public ExportPathPoliciesDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("description");
        addColumn("minPaths");
        addColumn("maxPaths");
        addColumn("pathsPerInit");
        addColumn("initsPerPort");
        addColumn("numStoragePorts");

        sortAll();
        setDefaultSortField("name");
    }

    public static class ExportPathPoliciesModel {
        public URI id;
        public String name;
        public String description;
        public Integer minPaths; 
        public Integer maxPaths; 
        public Integer pathsPerInit;
        public Integer initsPerPort; 
        public int numStoragePorts;

        public ExportPathPoliciesModel(URI id, String name, String description, 
                Integer minPaths, Integer maxPaths, Integer pathsPerInit,
                Integer initsPerPort, int numStoragePorts) {
            super();
            this.id = id;
            this.name = name;
            this.description = description;
            this.minPaths = minPaths;
            this.maxPaths = maxPaths;
            this.pathsPerInit = pathsPerInit;
            this.initsPerPort = initsPerPort;
            this.numStoragePorts = numStoragePorts;
        }
    }

    public class StoragePortsDataTable extends StoragePortDataTable {
        public StoragePortsDataTable() {
            addColumn("storageSystem").hidden();
            addColumn("networkIdentifier").hidden();// .setRenderFunction("render.networkIdentifier");
            addColumn("iqn").hidden();

            addColumn("network").hidden();
            addColumn("allocationDisqualified").hidden();// .setRenderFunction("render.allocationDisqualified");
            sortAllExcept("id");
            setDefaultSort("name", "asc");
        }

    }

    public class PortSelectionDataTable extends DataTable {

        public PortSelectionDataTable() {
            addColumn("name");
            addColumn("identifier");
            addColumn("portGroup");
            addColumn("alias");
            addColumn("ipAddress");
            addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        }

        public class PortSelectionModel {
            public URI id;
            public String name;
            public String registrationStatus;
            public String portGroup;
            public String storageSystem;
            public String ipAddress;
            public String identifier;
            public String alias;

            public PortSelectionModel(StoragePortRestRep storagePortRestRep) {
                this.id = storagePortRestRep.getId();
                this.name = storagePortRestRep.getPortName();
                this.registrationStatus = storagePortRestRep.getDiscoveryStatus();
                this.portGroup = storagePortRestRep.getAdapterName();
                this.ipAddress = storagePortRestRep.getIpAddress();
                this.identifier = storagePortRestRep.getPortNetworkId();
                this.alias = storagePortRestRep.getPortAlias();

            }
        }
    }

}
