package com.emc.storageos.validation;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.Volume;

public interface Validator {
	/**
	 * Validates a series of volumes based on the URIs supplied
	 * @param uris -- A list of URIs specifying volumes to be validated, can be from multiple StorageSystems
	 * @param delete -- If true, we are deleting volumes, do no flag error if they are missing
	 * @param remediate -- If true, attempt to remediate
	 * @param msgs -- Buffer for messages presented to log
	 * @param checks -- List of checks to be performed (Enum ValCk)
	 * @return list of volume URIs that were remediated, empty list if no remediations
	 */
	public List<URI> volumeURIs(List<URI> uris, boolean delete, boolean remediate,  StringBuilder msgs, ValCk... checks);
	

	/**
	 * Validate a series of volumes based on the volume objects supplied.
	 * @param volumes -- volume objects to be validate* @param delete -- If true, we are deleting volumes, do no flag error if they are missing
	 * @param remediate -- If true, attempt to remediate
	 * @param msgs -- Buffer for messages presented to log
	 * @param checks -- List of checks to be performed (Enum ValCk)
	 * @return list of volumes that were remediated, empty list if none remediated
	 */
	public List<Volume> volumes(List<Volume> volumes, boolean delete, boolean remediate, StringBuilder msgs, ValCk... checks);

}
