/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.netappc;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to NetApp Cluster Devices
 * <p/>
 * Remember to add the English message associated to the method in NetAppErrors.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to create a new service code if there is no an existing one
 * suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */

@MessageBundle
public interface NetAppCErrors {

    @DeclareServiceCode(ServiceCode.NETAPPC_FS_CREATE_ERROR)
    public ServiceError unableToCreateFileSystem();

    @DeclareServiceCode(ServiceCode.NETAPPC_FS_DELETE_ERROR)
    public ServiceError unableToDeleteFileSystem();

    @DeclareServiceCode(ServiceCode.NETAPPC_SHARE_CREATE_ERROR)
    public ServiceError unableToCreateFileShare();

    @DeclareServiceCode(ServiceCode.NETAPPC_SHARE_DELETE_ERROR)
    public ServiceError unableToDeleteFileShare();

    @DeclareServiceCode(ServiceCode.NETAPPC_FS_EXPAND_ERROR)
    public ServiceError unableToExpandFileSystem();

    @DeclareServiceCode(ServiceCode.NETAPPC_SNAPSHOT_CREATE_ERROR)
    public ServiceError unableToCreateSnapshot();

    @DeclareServiceCode(ServiceCode.NETAPPC_SNAPSHOT_DELETE_ERROR)
    public ServiceError unableToDeleteSnapshot();

    @DeclareServiceCode(ServiceCode.NETAPPC_FS_RESTORE_ERROR)
    public ServiceError unableToRestoreFileSystem();

    @DeclareServiceCode(ServiceCode.NETAPPC_FS_EXPORT_ERROR)
    public ServiceError unableToExportFileSystem();

    @DeclareServiceCode(ServiceCode.NETAPPC_FS_UNEXPORT_ERROR)
    public ServiceError unableToUnexportFileSystem();

    @DeclareServiceCode(ServiceCode.NETAPPC_SNAPSHOT_EXPORT_ERROR)
    public ServiceError unableToExportSnapshot();

    @DeclareServiceCode(ServiceCode.NETAPPC_SNAPSHOT_UNEXPORT_ERROR)
    public ServiceError unableToUnexportSnapshot();

    @DeclareServiceCode(ServiceCode.NETAPPC_QTREE_CREATE_ERROR)
    public ServiceError unableToCreateQtree();

    @DeclareServiceCode(ServiceCode.NETAPPC_QTREE_DELETE_ERROR)
    public ServiceError unableToDeleteQtree();

    @DeclareServiceCode(ServiceCode.NETAPPC_QTREE_UPDATE_ERROR)
    public ServiceError unableToUpdateQtree();

    @DeclareServiceCode(ServiceCode.NETAPPC_CIFS_SHARE_ACL_UPDATE_ERROR)
    public ServiceError unableToUpdateCIFSShareAcl();

    @DeclareServiceCode(ServiceCode.NETAPPC_CIFS_SHARE_ACL_DELETE_ERROR)
    public ServiceError unableToDeleteCIFSShareAcl();

    @DeclareServiceCode(ServiceCode.NETAPPC_INVALID_OPERATION)
    public ServiceError operationNotSupported();
}
