package models.datatable;

import java.util.ArrayList;
import java.util.List;

import util.datatable.DataTable;

public class StorageDriverDataTable extends DataTable {

    public StorageDriverDataTable() {
        addColumn("driverName");
        addColumn("driverVersion");
        addColumn("supportedStorageSystems");
        addColumn("type");
        addColumn("defaultNonSslPort");
        addColumn("defaultSslPort");
        addColumn("status").setRenderFunction("storageDriverStatus");
        addColumn("actions").setRenderFunction("renderButtonBar");
        sortAllExcept("actions");
    }

    public static class StorageDriverInfo {
        public String driverName;
        public String driverVersion;
        public List<String> supportedStorageSystems = new ArrayList<String>();
        public String type;
        public String defaultNonSslPort;
        public String defaultSslPort;
        public String status;

        public StorageDriverInfo() {}
    }
}
