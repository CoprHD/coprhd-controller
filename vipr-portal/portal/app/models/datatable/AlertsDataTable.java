package models.datatable;

import util.datatable.DataTable;

public class AlertsDataTable extends DataTable {
    
    public AlertsDataTable(){
        addColumn("severity");
        addColumn("name");
        addColumn("description");
        addColumn("category");
        addColumn("state");
        addColumn("actions").setRenderFunction("renderButtonBar");
        sortAllExcept("actions");
        
    }
    
    public static class Alerts {
        public String id;
        public String name;
        public String description;
        public String category;
        
        public Alerts(String id, String name, String description, String category) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
        }
    }

}
