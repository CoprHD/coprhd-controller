/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NFSShareACL;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.NfsACE.NfsACLOperationType;
import com.emc.storageos.model.file.NfsACE.NfsPermission;
import com.emc.storageos.model.file.NfsACL;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.FileControllerConstants;

public class NfsACLUtility {

    private final static Logger _log = LoggerFactory
            .getLogger(NfsACLUtility.class);

    private DbClient dbClient;
    private FileShare fs;
    private Snapshot snapshot;
    private String fileSystemPath;
    private String missingRequestParameterErrorString;
    private List<String> userGroupList;
    public static final String REQUEST_PARAM_PERMISSION_TYPE = "permission_type";
    public static final String REQUEST_PARAM_PERMISSION = "permission";

    public static final String REQUEST_PARAM_USER = "user";
    public static final String REQUEST_PARAM_GROUP = "group";

    public NfsACLUtility(DbClient dbClient, FileShare fs, Snapshot snapshot,
            String fileSystemPath) {
        super();
        this.dbClient = dbClient;
        this.fs = fs;
        this.snapshot = snapshot;
        this.fileSystemPath = fileSystemPath;
        this.userGroupList = new ArrayList<String>();
    }

    public void verifyNfsACLs(NfsACLUpdateParams param) {
        // TODO

    }

    private void reportErrors(NfsACLUpdateParams param,
            NfsACLOperationType type) {

        _log.info("Report errors if found");

        switch (type) {
            case ADD: {
                reportAddErrors(param);
                break;
            }
            case MODIFY: {
                reportModifyErrors(param);
                break;
            }
            case DELETE: {
                reportDeleteErrors(param);
                break;
            }
        }

    }

    private void reportDeleteErrors(NfsACLUpdateParams param) {
        // TODO

    }

    private void reportModifyErrors(NfsACLUpdateParams param) {
        // TODO
    }

    private void reportAddErrors(NfsACLUpdateParams param) {
        // TODO

    }

    private void validateNfsACLs(NfsACE nfsAce,
            NfsACLOperationType type) {

    }

    private void verifyAddNfsACLs(List<NfsACL> nfsACLs) {
        // TODO Auto-generated method stub

    }

    private void verifyDeleteNfsACLs(List<NfsACL> nfsAclList) {
        // TODO

    }

    private void verifyModifyNfsACLs(List<NfsACL> nfsAclList) {
        // TODO
    }

    public List<NfsACL> queryExistingNfsACLs() {
        // TODO
        return null;

    }

    private List<NFSShareACL> queryDBSFileNfsACLs() {

        try {
            ContainmentConstraint containmentConstraint = null;

            if (this.fs != null) {
                _log.info(
                        "Querying DB for Nfs ACLs of fs{} of filesystemId {} ",
                        this.fileSystemPath, fs.getId());
                containmentConstraint = ContainmentConstraint.Factory
                        .getFileNfsAclsConstraint(this.fs.getId());
            } else {
                // Snapshot
                _log.info(
                        "Querying DB for Nfs ACLs of fs {} of snapshotId {} ",
                        this.fileSystemPath, this.snapshot.getId());
                containmentConstraint = ContainmentConstraint.Factory
                        .getSnapshotNfsAclsConstraint(this.snapshot
                                .getId());
            }

            List<NFSShareACL> nfsAclList = CustomQueryUtility
                    .queryActiveResourcesByConstraint(this.dbClient,
                            NFSShareACL.class, containmentConstraint);

            return nfsAclList;

        } catch (Exception e) {
            _log.error("Error while querying DB for ACL of a NFS {}", e);
        }

        return null;

    }

    private NFSShareACL getExistingACL(NfsACL requestAcl) {
        // TODO
        return null;

    }

    private NFSShareACL queryACLByIndex(String index) {

        _log.info("Querying ACL in DB by alternate Id: {}", index);

        URIQueryResultList result = new URIQueryResultList();
        NFSShareACL acl = null;

        if (this.fs != null) {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileSystemNfsACLConstraint(index), result);
        } else {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotNfsACLConstraint(index), result);
        }

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            if (result.iterator().hasNext()) {
                acl = dbClient.queryObject(NFSShareACL.class, it.next());
                if (acl != null && !acl.getInactive()) {
                    _log.info("Existing ACE found in DB: {}", acl);
                    break;
                }
            }
        }

        return acl;
    }

    private void validatePermissions(NfsACL acl) {

    }

    private void verifyUserGroup(NfsACL acl) {
    }

    private String getFormattedPermissionText(NfsPermission permission) {
        String permissionText = null;

        switch (permission) {
            case READ:
                permissionText = FileControllerConstants.NFS_FILE_PERMISSION_READ;
                break;
            case CHANGE:
                permissionText = FileControllerConstants.NFS_FILE_PERMISSION_CHANGE;
                break;
            case FULLCONTROL:
                permissionText = FileControllerConstants.NFS_FILE_PERMISSION_FULLCONTROL;
                break;
        }
        return permissionText;
    }

    public static void checkForUpdateCifsACLOperationOnStorage(
            String storageSystemType, String operation) {

        StorageSystem.Type storageSystemEnum = Enum.valueOf(
                StorageSystem.Type.class, storageSystemType);

        switch (storageSystemEnum) {
            case vnxe:
            case vnxfile:
            case datadomain:
                throw APIException.badRequests.operationNotSupportedForSystemType(
                        operation, storageSystemType);
        }

    }

}
