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
		addColumn("upload").setRenderFunction("render.uploadBtn");
		sortAllExcept("upload");
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
		public static String id;
		public String upload;

		public Backup(BackupSet backup) {
			id = backup.getName();
			name = backup.getName();
			creationtime = backup.getCreateTime();
			size = backup.getSize();
			BackupUploadStatus uploadStat = BackupUtils.getUploadStatus(backup
					.getName());
			if (uploadStat.getStatus().name()
					.equals(Status.NOT_STARTED.toString())
					|| uploadStat.getStatus().name()
							.equals(Status.FAILED.toString())) {
				upload = backup.getName() + "_" + uploadStat.getStatus().name()
						+ "_enable";
			} else {
				upload = backup.getName() + "_disable";
			}
		}

	}

}
