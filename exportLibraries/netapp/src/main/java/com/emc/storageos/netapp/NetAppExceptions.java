/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.netapp;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@SuppressWarnings({ "squid:S00100" })
/**
 * This interface holds all the methods used to create {@link NetAppException}s
 * <p/>
 * Remember to add the English message associated to the method in
 * NetAppExceptions.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to
 * create a new service code if there is no an existing one suitable for your
 * error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section
 * in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface NetAppExceptions {

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException createFSFailed(final String fsName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException deleteFSFailed(final String volName,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException deleteNFSFailed(final String volName,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException exportFSFailed(final String mountPath,
            final String exportPath, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException unexportFSFailed(final String mountPath,
            final String exportPath, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException exportFSNameFailed(final String fsName);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException unexportFSNameFailed(final String fsName);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException listNFSExportRulesFailed(final String pathName);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException setVolumeSizeFailed(final String volume, final String size);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException setVolumeQtreeModeFailed(final String volumePath, final String mode);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException listVolumeInfoFailed(final String volume);
    
    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException listQtreesFailed();

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException listAggregatesFailed(final String name);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException systemInfoFailed(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException createSnapshotFailed(final String volumeName,
            final String snapshotName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException deleteSnapshotFailed(final String volumeName,
            final String snapshotName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException restoreSnapshotFailed(final String volumeName,
            final String snapshotName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException doShareFailed(final String mountPath,
            final String shareName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException modifyShareFailed(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException deleteShareFailed(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException modifyShareNameFailed(final String shareName,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException modifyCifsShareAclFailed(final String shareName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException listCIFSShareAclFailed(final String shareName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException deleteCIFSShareAclFailed(final String shareName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException listSharesFailed(final String shareName,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException statisticsCollectionfailed(final URI storageSystemId, final Throwable e);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException listFileSystems(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException getFileSystemInfo(final String fileSystem,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException collectStatsFailed(final String ip,
            final String type, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException discoveryFailed(final String ip, final Throwable e);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException createQtreeFailed(final String QtreeName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPP_ERROR)
    public NetAppException deleteQtreeFailed(final String QtreeName, final String message);
}
