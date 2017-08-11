/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.adapter.VcenterDiscoveryAdapter;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.HostNasVolume;
import com.vmware.vim25.NasDatastoreInfo;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;

/**
 * @author sanjes
 *
 */
public final class FileServiceUtils {

    private static final Logger log = LoggerFactory.getLogger(FileServiceUtils.class);
    private static final String LOCALHOST = "localhost";
    private static final String LOCALHOST_IP = "127.0.0.1";

    private FileServiceUtils() {

    }

    /**
     * Gets fileSystem with the Native GUID generated with device system and filePath
     * 
     * @param storageSys
     * @param filePath
     * @param dbClient
     * @return returns target filesystem with the specified native GUID else shall return null
     */
    public static FileShare getFileSystemUsingNativeGuid(StorageSystem storageSys, String filePath, DbClient dbClient) {
        FileShare targetFs = null;
        String fileShareNativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSys.getSystemType(), storageSys.getSerialNumber(),
                filePath);

        // Check if the target FS in the islon syncIQ policy exitst in ViPR DB
        URIQueryResultList queryResult = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFileSystemNativeGUIdConstraint(fileShareNativeGuid), queryResult);
        Iterator<URI> iter = queryResult.iterator();
        while (iter.hasNext()) {
            URI fsURI = iter.next();
            targetFs = dbClient.queryObject(FileShare.class, fsURI);
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
     * @param dbClient
     * @return it will return the storage system else will return if address is invalid or is not matched in storage
     *         system or Storage port
     */
    public static StorageSystem getTargetStorageSystem(String targetStorage, StorageSystem srcSys, DbClient dbClient) {
        // Handle the local target systems
        StorageSystem targetSys;
        InetAddress address;
        if (targetStorage.equalsIgnoreCase(LOCALHOST) || targetStorage.equalsIgnoreCase(LOCALHOST_IP)) {
            return srcSys;
        } else {
            try {
                address = InetAddress.getByName(targetStorage);
            } catch (UnknownHostException e) {
                log.error("getTargetHostSystem Failed with the exception: {}", e);
                return null;
            }
            if (address == null) {
                log.error("getTargetHostSystem Failed as the target address in invalid");
                return null;
            }
        }

        // Querying the targetSys if its storagePort IP Address
        targetSys = queryStorageSystemWithStoragePort(address.getHostAddress(), dbClient);
        if (targetSys != null) {
            return targetSys;
        }

        // Querying the targetSys if its storagePort FQDN
        targetSys = queryStorageSystemWithStoragePort(address.getHostName(), dbClient);
        if (targetSys != null) {
            return targetSys;
        }

        // Querying the targetSys if its StorageSystem IP Address
        targetSys = queryStorageSystemWithIpAddress(address.getHostAddress(), dbClient);
        if (targetSys != null) {
            return targetSys;
        }

        // Querying the targetSys if its FQDN
        targetSys = queryStorageSystemWithIpAddress(address.getHostName(), dbClient);
        return targetSys;
    }

    private static StorageSystem queryStorageSystemWithStoragePort(String aldId, DbClient dbClient) {
        StorageSystem targetSys = null;
        URIQueryResultList queryResult = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(aldId), queryResult);
        Iterator<URI> iter = queryResult.iterator();
        while (iter.hasNext()) {
            URI storagePortURI = iter.next();
            StoragePort sPort = dbClient.queryObject(StoragePort.class, storagePortURI);
            if (sPort != null) {
                targetSys = dbClient.queryObject(StorageSystem.class, sPort.getStorageDevice());
            }
        }
        return targetSys;
    }

    private static StorageSystem queryStorageSystemWithIpAddress(String ipAddress, DbClient dbClient) {
        StorageSystem targetSys = null;
        URIQueryResultList queryResult = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStorageSystemByIpAddressConstraint(ipAddress),
                queryResult);
        Iterator<URI> iter = queryResult.iterator();
        while (iter.hasNext()) {
            URI storageURI = iter.next();
            targetSys = dbClient.queryObject(StorageSystem.class, storageURI);
        }
        return targetSys;
    }

    /**
     * 
     * Validates target system for compliance with vpool, vpool capabilities, same project and target varray.
     * 
     * @param targetFs
     * @param project
     * @param sourceVpoolURI
     * @param targetVarrayURIs
     * @param dbClient
     * @return
     */
    public static boolean validateTarget(FileShare targetFs, URI sourceVpoolURI, URI project, Set<URI> targetVarrayURIs,
            DbClient dbClient) {
        // Checking if the source and target vpool is same
        if (!URIUtil.identical(sourceVpoolURI, targetFs.getVirtualPool())) {
            log.error("The target fs vpool does not match the source fs vpool");
            return false;
        }
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, targetFs.getVirtualPool());
        // checking if the vpool of the target fs is replication capable
        if (!vpool.getAllowFilePolicyAtFSLevel()) {
            log.error("The target fs vpool does not allow file replication policy at filesystem level");
            return false;
        }
        if (!targetVarrayURIs.contains(targetFs.getVirtualArray())) {
            log.error("The target fs virtual array does not match the expected target virtual array");
            return false;
        }

        if (targetFs.getProject() != null) {
            String targetprj = targetFs.getProject().getURI().toString();
            String srcprj = project.toString();
            if (!targetprj.equals(srcprj)) {
                log.error("The target fs project does not match the source fs project");
                return false;
            }

        }
        return true;
    }

    /**
     * Utility method to convert list of Fqdns to ips
     * 
     * @param dstagEndpointsList
     * @return
     */
    public static List<String> getIpsFromFqdnList(List<String> dstagEndpointsList) {
        List<String> ipList = new ArrayList<String>();
        for (String fqdn : dstagEndpointsList) {
            String ip = getIpFromFqdn(fqdn);
            if (!ip.isEmpty()) {
                ipList.add(ip);
            }
        }
        return ipList;
    }

    /**
     * Utility method to convert Fqdn to Ip. Works when Ip is passed as well
     * 
     * @param fqdn
     * @return
     */
    public static String getIpFromFqdn(String fqdn) {
        String ip = "";
        try {
            InetAddress address = InetAddress.getByName(fqdn);
            if (address != null) {
                ip = address.getHostAddress();
            }
        } catch (UnknownHostException e) {
            log.error("Error while parsing Ip  {}, {}", e.getMessage(), e);
        }
        return ip;
    }

    /**
     * Method to connect to all vCentres and get mount information - host and mount path of all active NFS datastores
     * 
     * @param _dbClient
     * @return
     */
    public static List<DatastoreMount> getDatastoreMounts(DbClient _dbClient) {
        List<DatastoreMount> datastoreMountList = new ArrayList<DatastoreMount>();
        List<URI> vCenterUri = _dbClient.queryByType(Vcenter.class, true);
        for (URI uri : vCenterUri) {
            Vcenter vCenter = _dbClient.queryObject(Vcenter.class, uri);
            VCenterAPI api = VcenterDiscoveryAdapter.createVCenterAPI(vCenter);

            for (Datacenter dataCenter : api.listAllDatacenters()) {
                for (Datastore datastore : dataCenter.getDatastores()) {

                    if (datastore.getSummary() != null) {
                        String type = datastore.getSummary().getType();
                        boolean active = datastore.getSummary().isAccessible();

                        if ("NFS".equals(type) && active) {
                            HostNasVolume hostNas = null;
                            if (datastore.getInfo() != null) {
                                hostNas = ((NasDatastoreInfo) datastore.getInfo()).getNas();
                            }
                            if (hostNas != null) {
                                String remoteHost = hostNas.getRemoteHost();
                                String remotepath = hostNas.getRemotePath();
                                if (remoteHost != null && remotepath != null) {
                                    datastoreMountList.add(new DatastoreMount(getIpFromFqdn(remoteHost), remotepath, datastore.getName()));
                                }
                            }
                        }
                    }
                }
            }
        }
        return datastoreMountList;
    }
}
