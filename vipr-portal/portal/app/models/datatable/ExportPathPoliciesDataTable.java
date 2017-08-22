package models.datatable;

import java.net.URI;

import util.datatable.DataTable;

public class ExportPathPoliciesDataTable extends DataTable {

    public ExportPathPoliciesDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("description");
        addColumn("minPaths");
        addColumn("maxPaths");
        addColumn("pathsPerInit");
        addColumn("initsPerPort");

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

        public ExportPathPoliciesModel(URI id, String name, String description, 
                Integer minPaths, Integer maxPaths, Integer pathsPerInit,
                Integer initsPerPort) {
            super();
            this.id = id;
            this.name = name;
            this.description = description;
            this.minPaths = minPaths;
            this.maxPaths = maxPaths;
            this.pathsPerInit = pathsPerInit;
            this.initsPerPort = initsPerPort;
        }
    }

    public class StoragePortDisplayDataTable extends StoragePortDataTable {
        public StoragePortDisplayDataTable() {
            addColumn("storageSystem");
            setHidden("allocationDisqualified", "registrationStatus", "iqn", "alias");
            sortAllExcept("id");
            setDefaultSort("name", "asc");
        }
    }

}
