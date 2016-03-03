package models.datatable;

import util.datatable.DataTable;

public class AlertsDataTable extends DataTable {
    
    public AlertsDataTable(){
        addColumn("severity").setRenderFunction("render.alertSeverity");
        addColumn("name");
        addColumn("resource");
        addColumn("description");
        addColumn("alertValue");
        addColumn("state");
        addColumn("actions").setRenderFunction("renderButtonBar");
        sortAllExcept("actions");
        
    }
    
    public static class Alerts {
        public String id;
        public String resource;
        public String name;
        public String description;
        public String alertValue;
        public String severity;
        public String state;
        
        public Alerts(String id, String name, String description, String alertValue, String severity, String state) {
            this.id = id;
            this.resource = name;
            this.name = "volumeCapacityExceed";
            this.description = description;
            this.alertValue = alertValue;
            this.severity = severity;
            this.state = state;
        }
    }

}
