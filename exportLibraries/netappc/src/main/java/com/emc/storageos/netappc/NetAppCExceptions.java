/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.netappc;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link NetAppCException}s
 * <p/>
 * Remember to add the English message associated to the method in NetAppCExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface NetAppCExceptions {

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException createFSFailed(final String fsName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException deleteFSFailed(final String volName,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException deleteNFSFailed(final String volName,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException exportFSFailed(final String mountPath,
            final String exportPath, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException unexportFSFailed(final String mountPath,
            final String exportPath, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException exportFSNameFailed(final String fsName);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException unexportFSNameFailed(final String fsName);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException listNFSExportRulesFailed(final String pathName);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException setVolumeSizeFailed(final String volume, final String size);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException setVolumeQtreeModeFailed(final String volumePath, final String mode);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException listVolumeInfoFailed(final String volume);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException listAggregatesFailed(final String name);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException systemInfoFailed(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException createSnapshotFailed(final String volumeName,
            final String snapshotName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException deleteSnapshotFailed(final String volumeName,
            final String snapshotName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException restoreSnapshotFailed(final String volumeName,
            final String snapshotName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException doShareFailed(final String mountPath,
            final String shareName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException modifyShareFailed(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException deleteShareFailed(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException modifyShareNameFailed(final String shareName,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException listSharesFailed(final String shareName,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException StatisticsCollectionfailed(final URI storageSystemId, final Throwable e); // NOSONAR(Fix will be made in future
                                                                                                      // release)

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException listFileSystems(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException getFileSystemInfo(final String fileSystem,
            final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException collectStatsFailed(final String ip,
            final String type, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException discoveryFailed(final String ip, final Throwable e);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException createQtreeFailed(final String QtreeName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException deleteQtreeFailed(final String QtreeName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException listingQtreeFailed(final String QtreeName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException listCIFSShareAclFailed(final String shareName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException modifyCifsShareAclFailed(final String shareName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException addCifsShareAclFailed(final String shareName, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException deleteCIFSShareAclFailed(final String shareName, final String message);

    // snap mirror
    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException createSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException initializeSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException breakAsyncSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException resumeSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException quiesceSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException releaseSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException setScheduleSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException deleteAsyncSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException abortSnapMirrorFailed(final String volName, final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException resyncAsyncSnapMirrorFailed(final String sourceLocation, final String destLocation, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException checkSnapMirrorLicenseFailed(final String ip, final String message);

    @DeclareServiceCode(ServiceCode.NETAPPC_ERROR)
    public NetAppCException getSnapMirrorStatusFailed(final String volName, final String ip, final String message);

}
