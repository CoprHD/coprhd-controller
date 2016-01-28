/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class CifsShareACLMigration extends BaseCustomMigrationCallback {

    private static final Logger logger = LoggerFactory
            .getLogger(CifsShareACLMigration.class);

    private static final String USER_EVERYONE = "Everyone";
    private static final String PERMISSION_TYPE_ALLOW = "allow";
    private static final String PERMISSION_READ = "Read";
    private static final String PERMISSION_CHANGE = "Change";
    private static final String PERMISSION_FULLCONTROL = "FullControl";

    @Override
    public void process() throws MigrationCallbackException {
        logger.info("Migration started");

        DbClient dbClient = getDbClient();

        try {
            List<URI> fileSystemURIList = dbClient.queryByType(FileShare.class,
                    true);
            Iterator<FileShare> fileSystemList = dbClient
                    .queryIterativeObjects(FileShare.class, fileSystemURIList,
                            true);

            while (fileSystemList.hasNext()) {
                FileShare fs = fileSystemList.next();

                SMBShareMap smbShareMap = fs.getSMBFileShares();
                Collection<SMBFileShare> smbShares = new ArrayList<SMBFileShare>();
                if (smbShareMap != null) {
                    smbShares = smbShareMap.values();

                    for (SMBFileShare smbShare : smbShares) {

                        if (smbShare.getPermissionType().equalsIgnoreCase(
                                PERMISSION_TYPE_ALLOW)) {

                            CifsShareACL acl = new CifsShareACL();
                            acl.setId(URIUtil.createId(CifsShareACL.class));
                            acl.setShareName(smbShare.getName());
                            acl.setPermission(smbShare.getPermission());
                            acl.setUser(USER_EVERYONE);
                            acl.setFileSystemId(fs.getId());
                            logger.debug("Persisting new ACE into DB: {}", acl);
                            dbClient.createObject(acl);
                        }
                    }
                }

            }

            // File snapshots
            List<URI> fileSnapshotURIList = dbClient.queryByType(
                    Snapshot.class, true);
            Iterator<Snapshot> fileSnapshotList = dbClient
                    .queryIterativeObjects(Snapshot.class, fileSnapshotURIList,
                            true);

            while (fileSnapshotList.hasNext()) {

                Snapshot snapshot = fileSnapshotList.next();

                SMBShareMap smbShareMap = snapshot.getSMBFileShares();
                Collection<SMBFileShare> smbShares = new ArrayList<SMBFileShare>();

                if (smbShareMap != null) {
                    smbShares = smbShareMap.values();

                    for (SMBFileShare smbShare : smbShares) {

                        if (smbShare.getPermissionType().equalsIgnoreCase(
                                PERMISSION_TYPE_ALLOW)) {

                            CifsShareACL acl = new CifsShareACL();
                            acl.setId(URIUtil.createId(CifsShareACL.class));
                            acl.setShareName(smbShare.getName());
                            acl.setPermission(getFormattedPermissionText(smbShare
                                    .getPermission()));
                            acl.setUser(USER_EVERYONE);
                            acl.setSnapshotId(snapshot.getId());
                            logger.debug("Persisting new ACE into DB: {}", acl);
                            dbClient.createObject(acl);
                        }
                    }
                }

            }
            logger.info("Migration completed successfully");
        } catch (Exception e) {
            logger.error("Exception occured while migrating cifs share access control settings");
            logger.error(e.getMessage(), e);
        }

    }

    private String getFormattedPermissionText(String permission) {

        String permissionText = null;

        switch (permission) {
            case "read":
                permissionText = PERMISSION_READ;
                break;
            case "change":
                permissionText = PERMISSION_CHANGE;
                break;
            case "full":
                permissionText = PERMISSION_FULLCONTROL;
                break;
        }

        logger.debug("Formatted permission text: {}", permissionText);
        return permissionText;

    }
}
