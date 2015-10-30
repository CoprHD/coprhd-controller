/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.List;

import util.BackupUtils;
import util.datatable.DataTable;

import com.emc.vipr.model.sys.backup.BackupUploadStatus;
import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.emc.vipr.model.sys.backup.BackupUploadStatus.Status;
import com.google.common.collect.Lists;

public class BackupDataTable extends DataTable {

	public BackupDataTable() {
		addColumn("name");
		addColumn("creationtime").setCssClass("time").setRenderFunction(
				"render.localDate");
		addColumn("size");
		addColumn("uploadstatus").setSearchable(false).setRenderFunction(
				"render.uploadProgress");
		addColumn("upload").setSearchable(false).setRenderFunction(
				"render.uploadBtn");
		sortAllExcept("upload", "uploadstatus");
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
		public String id;
		public String upload;
		public String status;
		public int progress;

		public Backup(BackupSet backup) {
			id = backup.getName();
			name = backup.getName();
			creationtime = backup.getCreateTime();
			size = backup.getSize();
			status = backup.getUploadStatus().getStatus().name();
			progress = backup.getUploadStatus().getProgress();
			if(status.equals(Status.FAILED.toString())){
				progress = 100;
			}
			if (status.equals(Status.NOT_STARTED.toString())
					|| status.equals(Status.FAILED.toString())) {
				upload = backup.getName() + "_enable";
			} else {
				upload = backup.getName() + "_disable";
			}
		}

	}

}
