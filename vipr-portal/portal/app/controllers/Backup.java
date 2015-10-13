package controllers;

import java.util.List;

import models.datatable.BackupDataTable;
import play.data.binding.As;
import play.mvc.Controller;
import play.mvc.With;
import util.datatable.DataTablesSupport;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class Backup extends Controller{
	
	
	    public static void list() {
	        BackupDataTable dataTable = new BackupDataTable();
	        render(dataTable);
	    }
	    
	    public static void listJson() {
	         List<BackupDataTable.Backup> backups = BackupDataTable.fetch();
	         renderJSON(DataTablesSupport.createJSON(backups, params));
	    }
	    
	    public static void create() {
	        list();
	    }
	    
	    public static void edit(String id) {
	        list();
	    }
	    
	    public static void delete(@As(",") String[] ids) {
	       // delete(uris(ids));
	    }

}
