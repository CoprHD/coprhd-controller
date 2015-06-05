/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.vnx.xmlapi;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to VNX Devices
 * <p/>
 * Remember to add the English message associated to the method in
 * VNXErrors.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to
 * create a new service code if there is no an existing one suitable for your
 * error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section
 * in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface VNXErrors {

	@DeclareServiceCode(ServiceCode.VNX_ERROR)
	public ServiceError
	copyGroupSnapshotsToTargetSettingsInstanceNull(String snapLabel, String snapURI);

	@DeclareServiceCode(ServiceCode.VNX_ERROR)
	public ServiceError
	copySnapshotToTargetSettingsInstanceNull(String snapLabel, String snapURI);

	@DeclareServiceCode(ServiceCode.VNX_ERROR)
	public ServiceError copyGroupSnapshotsToTargetException(Throwable t);

	@DeclareServiceCode(ServiceCode.VNX_ERROR)
	public ServiceError copySnapshotToTargetException(Throwable t);

	@DeclareServiceCode(ServiceCode.VNXFILE_QUOTA_DIR_ERROR)
	public ServiceError unableToCreateQuotaDir();	

	@DeclareServiceCode(ServiceCode.VNXFILE_QUOTA_DIR_ERROR)
	public ServiceError unableToDeleteQuotaDir();	

	@DeclareServiceCode(ServiceCode.VNXFILE_QUOTA_DIR_ERROR)
	public ServiceError unableToUpdateQuotaDir();	
    
    @DeclareServiceCode(ServiceCode.VNXFILE_FILESYSTEM_ERROR)
    public ServiceError unableToCreateFileSystem(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_FILESYSTEM_ERROR)
    public ServiceError unableToDeleteFileSystem(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_EXPORT_ERROR)
    public ServiceError unableToExportFileSystem(String error);

    @DeclareServiceCode(ServiceCode.VNXFILE_EXPORT_ERROR)
    public ServiceError unableToUpdateExport(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_EXPORT_ERROR)
    public ServiceError unableToUnexportFileSystem(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_FILESYSTEM_ERROR)
    public ServiceError unableToExpandFileSystem(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_SHARE_ERROR)
    public ServiceError unableToCreateFileShare(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_SHARE_ERROR)
    public ServiceError unableToDeleteFileShare(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_SNAPSHOT_ERROR)
    public ServiceError unableToCreateFileSnapshot(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_SNAPSHOT_ERROR)
    public ServiceError unableToRestoreFileSystem(String error);
    
    @DeclareServiceCode(ServiceCode.VNXFILE_SNAPSHOT_ERROR)
    public ServiceError unableToDeleteFileSnapshot(String error);
    
    @DeclareServiceCode(ServiceCode.VNX_ERROR)
    public ServiceError operationNotSupported();
}
