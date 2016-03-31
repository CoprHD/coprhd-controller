/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration of FS export map in FileShare CF to FileExportRule CF
 * 
 * @author herlea
 * 
 */
public class FileSystemExportToFileSystemExportRuleMigration extends
        BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory
            .getLogger(FileSystemExportToFileSystemExportRuleMigration.class);

    @Override
    public void process() throws MigrationCallbackException {

        log.info("FileSystemExport to FileSystem export rule migration: start");

        DbClient dbClient = getDbClient();

        try {

            List<URI> fileExpRuleURIList = dbClient.queryByType(
                    FileExportRule.class, true);

            int exisitingExportRuleCount = 0;
            for (Iterator<URI> iterator = fileExpRuleURIList.iterator(); iterator
                    .hasNext();) {
                URI uri = (URI) iterator.next();
                log.debug("Existing export rule URI: {}", uri);
                exisitingExportRuleCount++;
            }

            if (exisitingExportRuleCount > 0) {
                log.info("There are exisiting export rule(s). Skipping migration.");
                return;
            }

            // FileSystems
            List<URI> fileSystemURIList = dbClient.queryByType(FileShare.class,
                    true);
            Iterator<FileShare> fileShareListIterator = dbClient
                    .queryIterativeObjects(FileShare.class, fileSystemURIList);
            while (fileShareListIterator.hasNext()) {
                FileShare fileShare = fileShareListIterator.next();
                // Create FS Export Rule for export Map
                List<FileExportRule> fsExpRules = createFSExportRules(fileShare);
                if (null != fsExpRules && !fsExpRules.isEmpty()) {
                    log.debug("Persisting new File Export rule(s): {}",
                            fsExpRules);
                    dbClient.createObject(fsExpRules);
                }

            }

            // Snapshots
            List<URI> snapshotURIList = dbClient.queryByType(Snapshot.class,
                    true);
            Iterator<Snapshot> snapshotListIterator = dbClient
                    .queryIterativeObjects(Snapshot.class, snapshotURIList);
            while (snapshotListIterator.hasNext()) {
                Snapshot snapshot = snapshotListIterator.next();
                // Create FS Export Rule for export Map
                List<FileExportRule> snapshotExpRules = createSnapshotExportRules(snapshot);
                if (null != snapshotExpRules && !snapshotExpRules.isEmpty()) {
                    log.debug("Persisting new Snapshot Export rule(s): {}",
                            snapshotExpRules);
                    dbClient.createObject(snapshotExpRules);
                }

            }

            log.info("FileSystemExport to FileSystem export rule migration: end");

        } catch (Exception e) {
            log.error("Exception occured while migrating FileShare/Snapshot Export Map CF to FileExportRule CF");
            log.error(e.getMessage(), e);
        }
    }

    private List<FileExportRule> createSnapshotExportRules(Snapshot snapshot) {

        List<FileExportRule> fsExpRuleList = new ArrayList<FileExportRule>();

        FSExportMap fsExportMap = snapshot.getFsExports();

        if (fsExportMap != null) {

            for (String exportKey : fsExportMap.keySet()) {

                FileExport fsExport = fsExportMap.get(exportKey);
                FileExportRule expRule = new FileExportRule();
                expRule.setId(URIUtil.createId(FileExportRule.class));
                copyFileExportData(fsExport, expRule);
                // Set snapshotId in the end as setSnapshotId() calculates
                // snapshotExportIndex with exportPath and security flavor
                expRule.setSnapshotId(snapshot.getId());

                fsExpRuleList.add(expRule);

            }
        }

        return fsExpRuleList;
    }

    private List<FileExportRule> createFSExportRules(FileShare fileShare) {

        List<FileExportRule> fsExpRuleList = new ArrayList<FileExportRule>();

        FSExportMap fsExportMap = fileShare.getFsExports();

        if (fsExportMap != null) {

            for (String exportKey : fsExportMap.keySet()) {

                FileExport fsExport = fsExportMap.get(exportKey);
                FileExportRule expRule = new FileExportRule();
                expRule.setId(URIUtil.createId(FileExportRule.class));
                copyFileExportData(fsExport, expRule);
                // Set fileSystemId in the end as setFileSystemId() calculates
                // fsExportIndex with exportPath and security flavor
                expRule.setFileSystemId(fileShare.getId());

                fsExpRuleList.add(expRule);

            }
        }

        return fsExpRuleList;
    }

    private void copyFileExportData(FileExport source, FileExportRule dest) {

        dest.setExportPath(source.getMountPath());
        dest.setMountPoint(source.getMountPoint());
        dest.setSecFlavor(source.getSecurityType());
        dest.setAnon(source.getRootUserMapping());

        if (null != source.getIsilonId()) {
            dest.setDeviceExportId(source.getIsilonId());
        }

        List<String> sourceClients = source.getClients();

        if (null != sourceClients) {

            if ("rw".equals(source.getPermissions())) {
                dest.setReadWriteHosts(new StringSet(sourceClients));
            }

            if ("ro".equals(source.getPermissions())) {
                dest.setReadOnlyHosts(new StringSet(sourceClients));
            }

            if ("root".equals(source.getPermissions())) {
                dest.setRootHosts(new StringSet(sourceClients));
            }
        }

    }

}
