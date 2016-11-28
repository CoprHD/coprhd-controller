package models.datatable;

import java.net.URI;

import util.datatable.DataTable;

public class ExportPathParamsDataTable extends DataTable  {
    
    public ExportPathParamsDataTable(){
        addColumn("name").setRenderFunction("renderLink");
        addColumn("description");
        
        sortAll();
        setDefaultSortField("name");
    }
    
    public static class ExportPathParamsModel{
        URI id;
        String name;
        String description;
        public ExportPathParamsModel(URI id, String name, String description) {
            super();
            this.id = id;
            this.name = name;
            this.description = description;
        }
       
        
        
    }

}
