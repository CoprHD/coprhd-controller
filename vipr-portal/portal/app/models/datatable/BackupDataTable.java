package models.datatable;

import java.util.List;

import util.BackupUtils;
import util.datatable.DataTable;

import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.google.common.collect.Lists;

public class BackupDataTable extends DataTable {
    
    public BackupDataTable() {
        addColumn("name");
        addColumn("creationtime").setCssClass("time").setRenderFunction("render.localDate");;
        addColumn("size");
        sortAll();
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<Backup> fetch() {
        List<Backup> results = Lists.newArrayList();
        for (BackupSet backup : BackupUtils.getBackups()) {
            results.add(new Backup(backup));
        }
        return results;
    }

    public static class Backup {
        public String name;
        public long creationtime;
        public long size;

        public Backup(BackupSet backup) {
        	name = backup.getName();
        	creationtime = backup.getCreateTime();
        	size = backup.getSize();
            
        }
    }


}

