/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * 
 * This class is useful in zeroing the records which are inactive and for the
 * resources which are deleted outside the Bourne system.
 * 
 */
public abstract class ZeroRecordGenerator {
    private Logger _logger = LoggerFactory.getLogger(ZeroRecordGenerator.class);

    /**
     * Say, Cache has 100 Volumes stored in it. The current Metering Collection
     * results in 90 Volumes being retrieved from Providers. i.e. 10 Volumes
     * might get deleted, and needs to be Zeroed. Logic below identifies the 10
     * Missing Volumes and pushes to a Map,later Zero Stat Records would get
     * generated for those 10 Volumes before pushing to Cassandra.
     * * @param keyMap
     * 
     * @param Volumes
     */
    public void identifyRecordstobeZeroed(
            Map<String, Object> keyMap, List<Stat> metricsObjList, final Class clazz) {
        try {
            @SuppressWarnings("unchecked")
            Set<String> resourceIds = (Set<String>) keyMap.get(Constants._nativeGUIDs);
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);

            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            URI storageSystemURI = profile.getSystemId();
            List<URI> volumeURIsInDB = extractVolumesOrFileSharesFromDB(storageSystemURI, dbClient, clazz);

            Set<String> zeroedRecords = Sets.newHashSet();
            Set<String> volumeURIsInDBSet = new HashSet<String>(Lists.transform(
                    volumeURIsInDB, Functions.toStringFunction()));
            // used Sets in Guava libraries, which has the ability to get us
            // the difference without altering the Cache.
            Sets.difference(volumeURIsInDBSet, resourceIds).copyInto(zeroedRecords);

            if (!zeroedRecords.isEmpty()) {
                _logger.info("Records Zeroed : {}", zeroedRecords.size());
                // used in caching Volume Records
                for (String record : zeroedRecords) {
                    Stat zeroStatRecord = injectattr(keyMap, record, clazz);
                    if (null != zeroStatRecord) {
                        generateZeroRecord(zeroStatRecord, keyMap);
                        metricsObjList.add(zeroStatRecord);
                    } else {
                        _logger.debug(
                                "Records need to get Zeroed doesn't have VolumeUUID : {}",
                                record);
                    }
                }
            }
        } catch (Exception ex) {
            // No need to throw Exception just because Zeroing Records failed,
            // continue with persisting other records
            _logger.error("Error in Zeroing records :", ex);
        }
    }

    /**
     * extract List of Volume or FileSahre URIs associated to StorageSystem
     * 
     * @param storageSystemURI
     * @param dbClient
     * @return
     * @throws IOException
     */
    @SuppressWarnings("deprecation")
    public List<URI> extractVolumesOrFileSharesFromDB(
            URI storageSystemURI, DbClient dbClient, Class clazz) throws IOException {
        if (clazz.equals(Volume.class)) {
            return dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceVolumeConstraint(storageSystemURI));
        } else if (clazz.equals(FileShare.class)) {
            return dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceFileshareConstraint(storageSystemURI));
        }
        return Collections.emptyList();
    }

    /**
     * 
     * Inject UUID, vpool, & project into Stats before pushing to Cassandra.
     * To-Do: Inspect on using any external Cache Mechanism like EhCache
     * Memcache, if needed.
     * 
     * Made this method as abstract sothat each plugin will implement their logic
     * set the resourceId.
     * 
     * @param Map
     *            <String, Object>
     * @param Stat
     * @param NativeGUID
     * 
     */
    public <T extends DataObject> Stat injectattr(
            Map<String, Object> keyMap, String nativeGuid, Class<T> clazz) {
        Stat statObj = null;
        URI volURI = null;
        URI projectURI = null;
        URI tenantURI = null;
        URI vPoolURI = null;
        T volObj = null;
        boolean snapProcessed = false;
        try {
            DbClient client = (DbClient) keyMap.get(Constants.dbClient);
            /**
             * Number of records to be Zeroed, generally will not be a huge
             * number compared to the Volumes getting processed. Hence, the
             * number of calls would be minimum. (Or) the other approach would
             * be to Cache NativeGuids.
             * 
             * the Argument nativeGuid would be the Resource ID URI in case of
             * Zero Records.
             * 
             */
            if (null != clazz) {
                volURI = new URI(nativeGuid);
                volObj = client.queryObject(clazz, volURI);
                if (volObj instanceof Volume) {
                    Volume volume = (Volume) volObj;
                    nativeGuid = volume.getNativeGuid();
                    projectURI = volume.getProject().getURI();
                    tenantURI = volume.getTenant().getURI();
                    vPoolURI = volume.getVirtualPool();
                } else {
                    FileShare fileShare = (FileShare) volObj;
                    nativeGuid = fileShare.getNativeGuid();
                    projectURI = fileShare.getProject().getURI();
                    tenantURI = fileShare.getTenant().getURI();
                    vPoolURI = fileShare.getVirtualPool();
                }
            } else {
                List<URI> volumeURIs = injectResourceURI(client, nativeGuid);

                if (null == volumeURIs || volumeURIs.isEmpty()) {

                    _logger.debug("Querying Cassandra using nativeGUID:" + nativeGuid
                            + "yields : 0 ResourceID");
                    return statObj;
                }
                volURI = volumeURIs.get(0);
            }
            long allocatedCapacity = 0L;
            // if snap,process the parent volume
            if (!URIUtil.isType(volURI, Volume.class) && !URIUtil.isType(volURI, FileShare.class)) {
                _logger.debug("Skipping Statistics for Snapshots :" + volURI);
                BlockObject bo = BlockObject.fetch(client, volURI);
                if (bo instanceof BlockSnapshot) {
                    Volume parent = client.queryObject(Volume.class, ((BlockSnapshot) (bo)).getParent().getURI());
                    _logger.info("Processing snapshot's parent Volume {}", parent.getNativeGuid());
                    volURI = parent.getId();
                    nativeGuid = parent.getNativeGuid();
                    allocatedCapacity = ((BlockSnapshot) bo).getAllocatedCapacity();
                    snapProcessed = true;
                }

            }
            // No need to verify whether Volume is inactive or not, as for
            // zeroing records
            // even inactive Volumes need to get zeroed.
            // for verification purpose
            _logger.debug("Querying Cassandra using nativeGUID:" + nativeGuid
                    + "yields Resource ID :" + volURI);
            if (keyMap.containsKey(nativeGuid)) {
                statObj = (Stat) keyMap.get(nativeGuid);
            } else {
                // create a Metrics Object
                statObj = getStatObject(volURI, client);
                if (null == statObj) {
                    return statObj;
                }
                keyMap.put(nativeGuid, statObj);
                statObj.setResourceId(volURI);
            }

            statObj.setNativeGuid(nativeGuid);

            // set Project, Tenant and vPool info for Zero records,
            // as we already have Volume/File object queried from DB above to
            // get nativeGuid from URI.
            // calling Block/FileInsertion.injectColumnsDetails() results in
            // additional DB call to get Volume/File object.
            if (clazz != null) {
                statObj.setProject(projectURI);
                statObj.setTenant(tenantURI);
                statObj.setVirtualPool(vPoolURI);
            }

            if (snapProcessed) {
                _logger.info("Adding SnapShot details");
                // I can't add this as default value due to existing layers
                // which consume metering data.
                if (null == statObj.getSnapshotCount()) {
                    statObj.setSnapshotCount(0);
                    statObj.setSnapshotCapacity(0);

                }
                statObj.setSnapshotCount(statObj.getSnapshotCount() + 1);
                statObj.setSnapshotCapacity(statObj.getSnapshotCapacity() + allocatedCapacity);
            }

            // Add Volume URIs to local Collection, which will be compared
            // against Volumes in DB to determine Zero Records.
            @SuppressWarnings("unchecked")
            Set<String> volumeURIList = (Set<String>) keyMap.get(Constants._nativeGUIDs);
            volumeURIList.add(volURI.toString());
        } catch (Exception e) {
            // Even if one volume fails, no need to throw exception instead
            // continue processing other volumes
            if (null != nativeGuid) {
                _logger.error(
                        "Cassandra Database Error while querying VolumeUUId, VirtualPool & Project URIs : {}-->",
                        nativeGuid, e);
            }

        }
        // if processing snap, parent volume would have already added to the list, hence return null to skip
        return snapProcessed ? null : statObj;
    }

    /**
     * Return the Stat object based on the certain conditions.
     * Ex. If there are no project details in Volume/FS, don't create Stat object.
     * 
     * @param resourceURI
     * @return
     */
    protected abstract Stat getStatObject(URI resourceURI, DbClient dbClient);

    /**
     * inject ResourceId of the given nativeGuid.
     * 
     * @param client
     * @param nativeGuid
     * @return
     */
    protected abstract List<URI> injectResourceURI(DbClient client, String nativeGuid);

    /**
     * Generate Zero Record for Volumes
     * 
     * @param nativeGuid
     * @param keyMap
     */
    public abstract void generateZeroRecord(
            Stat zeroStatRecord, Map<String, Object> keyMap);
}
