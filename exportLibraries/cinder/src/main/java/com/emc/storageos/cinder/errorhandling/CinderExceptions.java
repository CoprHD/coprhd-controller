/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link CinderException}s
 * <p/>
 * Remember to add the English message associated to the method in
 * CinderExceptions.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to
 * create a new service code if there is no an existing one suitable for your
 * error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section
 * in the Error Handling Wiki page:
 * https://asdwiki.isus.emc.com:8443/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */

@MessageBundle
public interface CinderExceptions {
	
	@DeclareServiceCode(ServiceCode.CINDER_VOLUME_NOT_FOUND)
    public CinderException volumeNotFound(final String volumes);
	
	@DeclareServiceCode(ServiceCode.CINDER_VOLUME_CREATE_FAILED)
    public CinderException volumeCreationFailed(final String reason);
	
	@DeclareServiceCode(ServiceCode.CINDER_VOLUME_DELETE_FAILED)
	public CinderException volumeDeleteFailed(final String reason);

	@DeclareServiceCode(ServiceCode.CINDER_SNAPSHOT_NOT_FOUND)
	public CinderException snapshotNotFound(String snapshotId);

	@DeclareServiceCode(ServiceCode.CINDER_SNAPSHOT_CREATE_FAILED)
	public CinderException snapshotCreationFailed(final String reason);
	
	@DeclareServiceCode(ServiceCode.CINDER_SNAPSHOT_DELETE_FAILED)
	public CinderException snapshotDeleteFailed(final String reason);
	
	@DeclareServiceCode(ServiceCode.CINDER_VOLUME_CLONE_FAILED)
	public CinderException volumeCloneFailed(final String reason);
	
	@DeclareServiceCode(ServiceCode.CINDER_CREATE_VOLUME_FROM_SNAPSHOT_FAILED)
	public CinderException createVolumeFromSnapshotFailed(final String reason);
	
	@DeclareServiceCode(ServiceCode.CINDER_VOLUME_ATTACH_FAILED)
	public CinderException volumeAttachFailed(final String reason);
    
    @DeclareServiceCode(ServiceCode.CINDER_VOLUME_DETACH_FAILED)
    public CinderException volumeDetachFailed(final String reason);
    
    @DeclareServiceCode(ServiceCode.CINDER_VOLUME_EXPAND_FAILED)
    public CinderException volumeExpandFailed(final String reason);
}
