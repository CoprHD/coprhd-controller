package com.emc.storageos.validation;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;

public class AbstractSystemValidator implements StorageSystemValidator {
	protected DbClient dbClient;
	protected List<Volume> remediatedVolumes = new ArrayList<Volume>();
	protected StringBuilder msgs = new StringBuilder();
	protected Logger log = null;
	
	public AbstractSystemValidator(DbClient dbClient, Logger log) {
		this.dbClient = dbClient;
		this.log = log;
	}

	@Override
	public List<Volume> volumes(StorageSystem storageSystem,
			List<Volume> volumes, boolean delete, boolean remediate,
			StringBuilder msgs, ValCk... checks) {
		return remediatedVolumes;
	}
	
	/**
	 * Log a discrepency in the data.
	 * @param id -- Identity of the domain object
	 * @param field -- Field with discrepency
	 * @param db -- Database value
	 * @param hw -- Hardware value
	 */
	void logDiff(String id, String field, String db, String hw) {
		String msg = String.format("id: %s field: %s db: %s hw: %s", id, field, db, hw);
		msgs.append(msg + "\n");
		log.info(msg);
	}

	public DbClient getDbClient() {
		return dbClient;
	}

	public void setDbClient(DbClient dbClient) {
		this.dbClient = dbClient;
	}

	public List<Volume> getRemediatedVolumes() {
		return remediatedVolumes;
	}

	public void setRemediatedVolumes(List<Volume> remediatedVolumes) {
		this.remediatedVolumes = remediatedVolumes;
	}

	public StringBuilder getMsgs() {
		return msgs;
	}

	public void setMsgs(StringBuilder msgs) {
		this.msgs = msgs;
	}

}
