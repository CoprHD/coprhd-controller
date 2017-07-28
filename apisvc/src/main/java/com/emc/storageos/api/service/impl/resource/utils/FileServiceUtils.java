/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * @author sanjes
 *
 */
public class FileServiceUtils {
    private static final Logger _log = LoggerFactory.getLogger(FileServiceUtils.class);

    /**
     * Gets fileSystem with the Native GUID generated with device system and filePath
     * 
     * @param storageSys
     * @param filePath
     * @param _dbClient
     * @return
     */
    public static FileShare getFileSystemUsingNativeGuid(StorageSystem storageSys, String filePath, DbClient _dbClient) {
        FileShare targetFs = null;
        String fileShareNativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSys.getSystemType(), storageSys.getSerialNumber(),
                filePath);

        // Check if the target FS in the islon syncIQ policy exitst in ViPR DB
        URIQueryResultList queryResult = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFileSystemNativeGUIdConstraint(fileShareNativeGuid), queryResult);
        Iterator<URI> iter = queryResult.iterator();
        while (iter.hasNext()) {
            URI fsURI = iter.next();
            targetFs = _dbClient.queryObject(FileShare.class, fsURI);
        }
        return targetFs;
    }

    /**
     * Gets storage system with the help ip address obtained as target host from replication policy at the backend.
     * This resolves if the obtained target host id is a localhost/127.0.0.1/ storage port IP address (smart connect
     * zone)/ storage port FQDN/ storage system IP address/ storage system FQDN.
     * 
     * @param targetStorage
     * @param srcSys
     * @param _dbClient
     * @return
     */
    public static StorageSystem getTargetStorageSystem(String targetStorage, StorageSystem srcSys, DbClient _dbClient) {
        // Handle the local target systems
        StorageSystem targetSys = null;
        InetAddress address = null;
        if (targetStorage.equalsIgnoreCase("localhost") || targetStorage.equalsIgnoreCase("127.0.0.1")) {
            return srcSys;
        } else {
            try {
                address = InetAddress.getByName(targetStorage);
            } catch (UnknownHostException e) {
                _log.error("getTargetHostSystem Failed with the exception", e);
                return null;
            }
            if (address == null) {
                _log.error("getTargetHostSystem Failed as the target address in invalid");
                return null;
            }
        }

        // Querying the targetSys if its storagePort IP Address
        targetSys = queryStorageSystemWithStoragePort(address.getHostAddress(), _dbClient);
        if (targetSys != null) {
            return targetSys;
        }

        // Querying the targetSys if its storagePort FQDN
        targetSys = queryStorageSystemWithStoragePort(address.getHostName(), _dbClient);
        if (targetSys != null) {
            return targetSys;
        }

        // Querying the targetSys if its StorageSystem IP Address
        targetSys = queryStorageSystem(address.getHostAddress(), _dbClient);
        if (targetSys != null) {
            return targetSys;
        }

        // Querying the targetSys if its FQDN
        targetSys = queryStorageSystem(address.getHostName(), _dbClient);
        return targetSys;
    }

    private static StorageSystem queryStorageSystemWithStoragePort(String aldId, DbClient _dbClient) {
        StorageSystem targetSys = null;
        URIQueryResultList queryResult = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(aldId), queryResult);
        Iterator<URI> iter = queryResult.iterator();
        while (iter.hasNext()) {
            URI storagePortURI = iter.next();
            StoragePort sPort = _dbClient.queryObject(StoragePort.class, storagePortURI);
            if (sPort != null) {
                targetSys = _dbClient.queryObject(StorageSystem.class, sPort.getStorageDevice());
            }
        }
        return targetSys;
    }

    private static StorageSystem queryStorageSystem(String id, DbClient _dbClient) {
        StorageSystem targetSys = null;
        URIQueryResultList queryResult = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStorageSystemByIpAddressConstraint(id),
                queryResult);
        Iterator<URI> iter = queryResult.iterator();
        while (iter.hasNext()) {
            URI storageURI = iter.next();
            targetSys = _dbClient.queryObject(StorageSystem.class, storageURI);
        }
        return targetSys;
    }

    /**
     * 
     * Validates target system for compliance with vpool capabilities, same project and target varray.
     * 
     * @param targetFs
     * @param project
     * @param targertVarrayURIs
     * @param _dbClient
     * @return
     */
    public static boolean validateTarget(FileShare targetFs, URI project, Set<URI> targertVarrayURIs, DbClient _dbClient) {
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, targetFs.getVirtualPool());
        // checking if the vpool of the target fs is replication capable
        if (!vpool.getAllowFilePolicyAtFSLevel()) {
            _log.error("The target fs vpool does not allow file replication policy at filesystem level");
            return false;
        }
        if (!targertVarrayURIs.contains(targetFs.getVirtualArray())) {
            _log.error("The target fs virtual array does not match the expected target virtual array");
            return false;
        }

        if (targetFs.getProject() != null) {
            String targetprj = targetFs.getProject().getURI().toString();
            String srcprj = project.toString();
            if (!targetprj.equals(srcprj)) {
                _log.error("The target fs project does not match the source fs project");
                return false;
            }

        }
        return true;
    }

}
