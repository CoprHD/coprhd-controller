/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NFSShareACL;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.NfsACE.NfsPermission;
import com.emc.storageos.model.file.NfsACE.NfsPermissionType;
import com.emc.storageos.model.file.NfsACE.NfsUserType;
import com.emc.storageos.model.file.NfsACL;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.model.file.NfsACLs;
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
        this.dbClient = dbClient;
        this.fs = fs;
        this.snapshot = snapshot;
        this.subDir = subDir;

    }

    /**
     * Check the provided String value is a valid type of enum or not
     * 
     * @param value String need to be checked
     * @param enumClass the enum class for which it need to be checked.
     * @return
     */
    public <T extends Enum<T>> boolean isValidEnum(String value, Class<T> enumClass) {
        for (T e : enumClass.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This function verify the NfsACLUpdateParams data provided my user to set ACL is a valid for not
     * it throw exception for invalid NfsACLUpdateParams data
     * 
     * @param param input data converted from xml payload.
     */
    public void verifyNfsACLs(NfsACLUpdateParams param) {

        List<NfsACE> addList = param.getAcesToAdd();
        List<NfsACE> modifyList = param.getAcesToModify();
        List<NfsACE> deleteList = param.getAcesToDelete();
        List<NFSShareACL> dbACLList = queryDBSFileNfsACLs(false);
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
     * @param nfsaces list of the ACE need to be validated
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
            int index = ace.getUser().indexOf("\\");
            if (index >= 0) {
                if (ace.getDomain() != null && !ace.getDomain().isEmpty()) {

                    throw APIException.badRequests.multipleDomainsFound("update", ace.getDomain(), ace.getUser().substring(0, index));
                } else {
                    // split takes regex so need 4 backslash
                    String domainAnduser[] = ace.getUser().split("\\\\");
                    if (domainAnduser.length > 2) {
                        throw APIException.badRequests.multipleDomainsFound("update", domainAnduser[0], domainAnduser[1]);
                    }
                    // split the user and domain form user field and store it separately
                    // this is required to store value in DB in one format
                    if (domainAnduser.length == 2) {
                        ace.setDomain(domainAnduser[0]);
                        ace.setUser(domainAnduser[1]);

                    }
                }

            }

        }

    }

    /**
     * Verify the new ACE which need to be added exist in DB or not
     * 
     * @param addList list which need to be added
     * @param userSet list already in the DB
     */
    private void verifyNfsACLsAddList(List<NfsACE> addList, Set<String> userSet) {

        validateNfsAceSyntax(addList);

        for (NfsACE ace : addList) {

            if (userSet.contains(ace.getUser())) {
                throw APIException.badRequests.nfsACLAlreadyExists("add",
                        ace.getUser());

            }
        }

    }

    /**
     * Verify the modify ACE which need to be updated is in DB or not
     * 
     * @param changeList list which need to be updated
     * @param userSet list already in the DB
     */
    private void verifyNfsACLsModifyOrDeleteList(List<NfsACE> changeList, Set<String> userSet) {

        validateNfsAceSyntax(changeList);
        for (NfsACE ace : changeList) {

            if (!userSet.contains(ace.getUser())) {
                throw APIException.badRequests.nfsACLNotFound("modify or delete",
                        ace.getUser());

            }
        }
    }

    /**
     * Get the list of DB Object for current file System
     * 
     * @return
     */
    private List<NFSShareACL> queryDBSFileNfsACLs(boolean allDir) {

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
            if (allDir) {
                return nfsAclList;

            }
            List<NFSShareACL> rootAclList = new ArrayList<NFSShareACL>();
            List<NFSShareACL> subDirAclList = new ArrayList<NFSShareACL>();
            String absoluteSubdir = "";
            if (this.subDir != null && !this.subDir.isEmpty()) {
                absoluteSubdir = this.fs.getPath() + "/" + subDir;
            }
            for (NFSShareACL nfsAcl : nfsAclList) {
                if (nfsAcl.getFileSystemPath().equals(fs.getPath())) {
                    rootAclList.add(nfsAcl);

                }
                if (!absoluteSubdir.isEmpty()) {
                    if (nfsAcl.getFileSystemPath().equals(absoluteSubdir)) {
                        subDirAclList.add(nfsAcl);
                    }

                }
            }
            if (!absoluteSubdir.isEmpty()) {

                return subDirAclList;
            }

            return rootAclList;

        } catch (Exception e) {
            _log.error("Error while querying DB for ACL of a NFS {}", e);
        }

        return null;

    }

    /**
     * This function return all the ACL in the DB in the format of xml Object
     * 
     * @param allDirs if true it function return complete list of ACL including its SubDir
     * @return
     */
    public NfsACLs getNfsAclFromDB(boolean allDirs) {
        NfsACLs acls = new NfsACLs();
        List<NfsACL> nfsAclList = new ArrayList<NfsACL>();
        Map<String, List<NfsACE>> nfsAclMap = new HashMap<String, List<NfsACE>>();
        // Query All ACl Specific to a File System.
        List<NFSShareACL> nfsAcls = queryDBSFileNfsACLs(allDirs);
        _log.info("Subdir value {} and allDirs={}", this.subDir, allDirs);
        _log.info("Number of existing ACL found : {} ", nfsAcls.size());

        // ALl ACL
        for (NFSShareACL nfsAcl : nfsAcls) {
            String fspath = nfsAcl.getFileSystemPath();
            List<NfsACE> nfsAceList = nfsAclMap.get(fspath);
            if (nfsAceList == null) {
                nfsAceList = new ArrayList<NfsACE>();

            }
            NfsACE ace = new NfsACE();
            getNFSAce(nfsAcl, ace);
            nfsAceList.add(ace);
            nfsAclMap.put(fspath, nfsAceList);

        }

        for (String fspath : nfsAclMap.keySet()) {
            NfsACL nfsAcl = new NfsACL(fspath, nfsAclMap.get(fspath));

            if (fspath.length() > fs.getPath().length()) {
                nfsAcl.setSubDir(fspath.substring(fs.getPath().length() + 1));
            }

            nfsAclList.add(nfsAcl);

        }
        acls.setNfsACLs(nfsAclList);
        return acls;
    }

    /**
     * This function is to convert DB object into xml NfsACE object
     * 
     * @param orig provided DB object
     * @param dest updated NfsACE object
     */
    private void getNFSAce(NFSShareACL orig, NfsACE dest) {

        dest.setDomain(orig.getDomain());
        dest.setPermissions(orig.getPermissions());
        if (orig.getPermissionType() != null && !orig.getPermissionType().isEmpty()) {

            dest.setPermissionType(orig.getPermissionType());
        } else {
            dest.setPermissionType("allow");

        }
        if (orig.getType() != null && !orig.getType().isEmpty()) {

            dest.setType(orig.getType());
        } else {
            dest.setType("user");

        }

        dest.setUser(orig.getUser());

    }

}
