package models.datatable;

import util.datatable.DataTable;

public class AlertsDataTable extends DataTable {
    
    public AlertsDataTable(){
        addColumn("severity");
        addColumn("name");
        addColumn("description");
        addColumn("deviceType");
        addColumn("state");
        addColumn("actions").setRenderFunction("renderButtonBar");
        sortAllExcept("actions");
        
    }
    
    public static class Alerts {
        public String id;
        public String name;
        public String description;
        public String deviceType;
        public String severity;
        public String state;
        
        public Alerts(String id, String name, String description, String deviceType, String severity, String state) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.deviceType = deviceType;
            this.severity = severity;
            this.state = state;
        }
    }

}
