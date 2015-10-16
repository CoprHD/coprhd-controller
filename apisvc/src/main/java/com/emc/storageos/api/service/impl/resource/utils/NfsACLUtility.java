/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
import com.emc.storageos.model.file.NfsACE.NfsPermission;
import com.emc.storageos.model.file.NfsACE.NfsPermissionType;
import com.emc.storageos.model.file.NfsACE.NfsUserType;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class NfsACLUtility {

    private final static Logger _log = LoggerFactory
            .getLogger(NfsACLUtility.class);

    private DbClient dbClient;
    private FileShare fs;
    private Snapshot snapshot;
    private String subDir;
    public static final String REQUEST_PARAM_PERMISSION_TYPE = "permission_type";
    public static final String REQUEST_PARAM_PERMISSION = "permission";

    public static final String REQUEST_PARAM_USER = "user";
    public static final String REQUEST_PARAM_GROUP = "group";

    public NfsACLUtility(DbClient dbClient, FileShare fs, Snapshot snapshot, String subDir) {
        super();
        this.dbClient = dbClient;
        this.fs = fs;
        this.snapshot = snapshot;
        this.subDir = subDir;

    }

    public <T extends Enum<T>> boolean isValidEnum(String value, Class<T> enumClass) {
        for (T e : enumClass.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public void verifyNfsACLs(NfsACLUpdateParams param) {

        List<NfsACE> addList = param.getAcesToAdd();
        List<NfsACE> modifyList = param.getAcesToModify();
        List<NfsACE> deleteList = param.getAcesToDelete();
        List<NFSShareACL> dbACLList = queryDBSFileNfsACLs();
        Set<String> userSetDB = new HashSet<String>();
        for (NFSShareACL dbAcl : dbACLList) {
            userSetDB.add(dbAcl.getUser());

        }
        if (addList != null && !addList.isEmpty()) {
            verifyNfsACLsAddList(addList, userSetDB);
        }
        if (modifyList != null && !modifyList.isEmpty()) {
            verifyNfsACLsModifyOrDeleteList(modifyList, userSetDB);
        }
        if (deleteList != null && !deleteList.isEmpty()) {
            verifyNfsACLsModifyOrDeleteList(deleteList, userSetDB);
        }

    }

    /**
     * To verify the syntax of payload
     * 
     * @param nfsaces
     */
    private void validateNfsAceSyntax(List<NfsACE> nfsaces) {

        for (NfsACE ace : nfsaces) {
            // PermissionType is optional , if provided check it is proper
            if (ace.getPermissionType() != null && !ace.getPermissionType().isEmpty()) {

                if (!isValidEnum(ace.getPermissionType(), NfsPermissionType.class)) {

                    throw APIException.badRequests.invalidPermissionType(ace.getPermissionType());
                }
            }

            // user type is optional , if provided check it is proper
            if (ace.getType() != null && !ace.getType().isEmpty()) {

                if (!isValidEnum(ace.getType(), NfsUserType.class)) {

                    throw APIException.badRequests.invalidUserType(ace.getType());
                }
            }
            for (String permission : ace.getPermissionSet()) {
                if (!isValidEnum(permission, NfsPermission.class)) {

                    throw APIException.badRequests.invalidPermission(permission);
                }

            }
            // check if two times domain is provided
            if (ace.getDomain() != null && !ace.getDomain().isEmpty()) {

                int index = ace.getUser().indexOf("\\");
                if (index >= 0) {

                    throw APIException.badRequests.multipleDomainsFound("update", ace.getDomain(), ace.getUser().substring(0, index));
                }
            }

        }

    }

    private void verifyNfsACLsAddList(List<NfsACE> addList, Set<String> userSet) {

        validateNfsAceSyntax(addList);

        for (NfsACE ace : addList) {

            if (userSet.contains(ace.getUser())) {
                throw APIException.badRequests.nfsACLAlreadyExists("add",
                        ace.getUser());

            }
        }

    }

    private void verifyNfsACLsModifyOrDeleteList(List<NfsACE> changeList, Set<String> userSet) {

        validateNfsAceSyntax(changeList);
        for (NfsACE ace : changeList) {

            if (!userSet.contains(ace.getUser())) {
                throw APIException.badRequests.nfsACLNotFoundFound("modify or delete",
                        ace.getUser());

            }
        }
    }

    private List<NFSShareACL> queryDBSFileNfsACLs() {

        try {
            ContainmentConstraint containmentConstraint = null;

            if (this.fs != null) {
                _log.info(
                        "Querying DB for Nfs ACLs of fs{} of filesystemId {} ",
                        this.fs.getPath(), fs.getId());

                containmentConstraint = ContainmentConstraint.Factory
                        .getFileNfsAclsConstraint(this.fs.getId());
            } else {
                // Snapshot
                _log.info(
                        "Querying DB for Nfs ACLs of fs {} of snapshotId {} ",
                        this.snapshot.getPath(), this.snapshot.getId());
                containmentConstraint = ContainmentConstraint.Factory
                        .getSnapshotNfsAclsConstraint(this.snapshot
                                .getId());
            }

            List<NFSShareACL> nfsAclList = CustomQueryUtility
                    .queryActiveResourcesByConstraint(this.dbClient,
                            NFSShareACL.class, containmentConstraint);

            // filter out the list which is not for given sub directory
            if (this.subDir != null && !this.subDir.isEmpty()) {
                String absoluteSubdir = this.fs.getPath() + "/" + subDir;
                for (NFSShareACL nfsAcl : nfsAclList) {
                    if (!nfsAcl.getFileSystemPath().equals(absoluteSubdir)) {
                        nfsAclList.remove(nfsAcl);

                    }
                }
            }
            return nfsAclList;

        } catch (Exception e) {
            _log.error("Error while querying DB for ACL of a NFS {}", e);
        }

        return null;

    }

    private NFSShareACL queryNFSACLByIndex(String index) {

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

    public static void checkForUpdateCifsACLOperationOnStorage(
            String storageSystemType, String operation) {

        StorageSystem.Type storageSystemEnum = Enum.valueOf(
                StorageSystem.Type.class, storageSystemType);

        switch (storageSystemEnum) {
            case vnxe:
            case vnxfile:
            case datadomain:
            case netapp:
            case netappc:

                throw APIException.badRequests.operationNotSupportedForSystemType(
                        operation, storageSystemType);
        }

    }

}
