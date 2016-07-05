package com.emc.storageos.validation;

import java.util.List;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;

public interface StorageSystemValidator {

	/**
	 * Validates the volumes for a single storage system. 
	 * @param storageSystem -- Storage System object
	 * @param volumes -- list of Volume objects belonging to that StorageSystem
	 * @param delete -- if true we are deleting, don't flag errors where entity is missing
	 * @param remediate -- if true, attempt remediation
	 * @param msgs -- message buffer for actions taken
	 * @param checks -- checks to be performed
	 * @return -- list of any Volumes that were remediated
	 */
	public List<Volume> volumes(StorageSystem storageSystem, 
			List<Volume> volumes, boolean delete, boolean remediate, StringBuilder msgs, ValCk... checks);

}
