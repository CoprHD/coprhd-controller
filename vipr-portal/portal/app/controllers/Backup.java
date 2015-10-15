package controllers;

import java.util.List;

import models.datatable.BackupDataTable;
import play.data.binding.As;
import play.mvc.Controller;
import play.mvc.With;
import util.BourneUtil;
import util.datatable.DataTablesSupport;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;

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
	        render();
	    }
	    
	    public static void cancel() {
	        create();
	    }
	    
	    @FlashException(keep = true, referrer = { "list"})
	    public static void save(BackupForm backupForm) {
	    	backupForm.save();
	        list();
	    }
	    
	    public static void edit(String id) {
	        list();
	    }
	    
	    public static void delete(@As(",") String[] ids) {
	       // delete(uris(ids));
	    }
	    
	    public static class BackupForm {
	        public String name;
	        public boolean force;

	        public void save() {
	            try {
	                BourneUtil.getSysClient().backup().createBackup(name, force);
	            } catch (Exception e) {
	                Common.flashException(e);
	            }
	        }
	    }

}
