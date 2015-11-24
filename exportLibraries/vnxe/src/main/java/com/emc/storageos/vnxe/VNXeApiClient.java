/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.models.AccessEnum;
import com.emc.storageos.vnxe.models.BasicSystemInfo;
import com.emc.storageos.vnxe.models.BlockHostAccess;
import com.emc.storageos.vnxe.models.BlockHostAccess.HostLUNAccessEnum;
import com.emc.storageos.vnxe.models.CifsShareCreateForSnapParam;
import com.emc.storageos.vnxe.models.CifsShareCreateParam;
import com.emc.storageos.vnxe.models.CifsShareDeleteParam;
import com.emc.storageos.vnxe.models.CifsShareParam;
import com.emc.storageos.vnxe.models.CreateFileSystemParam;
import com.emc.storageos.vnxe.models.DiskGroup;
import com.emc.storageos.vnxe.models.FastVP;
import com.emc.storageos.vnxe.models.FastVPParam;
import com.emc.storageos.vnxe.models.FileSystemParam;
import com.emc.storageos.vnxe.models.FileSystemSnapCreateParam;
import com.emc.storageos.vnxe.models.HostCreateParam;
import com.emc.storageos.vnxe.models.HostInitiatorCreateParam;
import com.emc.storageos.vnxe.models.HostIpPortCreateParam;
import com.emc.storageos.vnxe.models.HostLun;
import com.emc.storageos.vnxe.models.HostTypeEnum;
import com.emc.storageos.vnxe.models.LunAddParam;
import com.emc.storageos.vnxe.models.LunCreateParam;
import com.emc.storageos.vnxe.models.LunGroupCreateParam;
import com.emc.storageos.vnxe.models.LunGroupModifyParam;
import com.emc.storageos.vnxe.models.LunModifyParam;
import com.emc.storageos.vnxe.models.LunParam;
import com.emc.storageos.vnxe.models.LunSnapCreateParam;
import com.emc.storageos.vnxe.models.ModifyFileSystemParam;
import com.emc.storageos.vnxe.models.NfsShareCreateForSnapParam;
import com.emc.storageos.vnxe.models.NfsShareCreateParam;
import com.emc.storageos.vnxe.models.NfsShareDeleteParam;
import com.emc.storageos.vnxe.models.NfsShareModifyForShareParam;
import com.emc.storageos.vnxe.models.NfsShareModifyParam;
import com.emc.storageos.vnxe.models.NfsShareParam;
import com.emc.storageos.vnxe.models.NfsShareParam.NFSShareDefaultAccessEnum;
import com.emc.storageos.vnxe.models.StorageResource;
import com.emc.storageos.vnxe.models.StorageResource.TieringPolicyEnum;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCifsServer;
import com.emc.storageos.vnxe.models.VNXeCifsShare;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeEthernetPort;
import com.emc.storageos.vnxe.models.VNXeExportResult;
import com.emc.storageos.vnxe.models.VNXeFCPort;
import com.emc.storageos.vnxe.models.VNXeFSSupportedProtocolEnum;
import com.emc.storageos.vnxe.models.VNXeFileInterface;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.vnxe.models.VNXeFileSystemSnap;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
import com.emc.storageos.vnxe.models.VNXeHostInitiator.HostInitiatorTypeEnum;
import com.emc.storageos.vnxe.models.VNXeHostIpPort;
import com.emc.storageos.vnxe.models.VNXeIscsiNode;
import com.emc.storageos.vnxe.models.VNXeIscsiPortal;
import com.emc.storageos.vnxe.models.VNXeLicense;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.vnxe.models.VNXeLunGroupSnap;
import com.emc.storageos.vnxe.models.VNXeLunSnap;
import com.emc.storageos.vnxe.models.VNXeNasServer;
import com.emc.storageos.vnxe.models.VNXeNfsServer;
import com.emc.storageos.vnxe.models.VNXeNfsShare;
import com.emc.storageos.vnxe.models.VNXePool;
import com.emc.storageos.vnxe.models.VNXeStorageProcessor;
import com.emc.storageos.vnxe.models.VNXeStorageSystem;
import com.emc.storageos.vnxe.models.VNXeStorageTier;
import com.emc.storageos.vnxe.requests.BasicSystemInfoRequest;
import com.emc.storageos.vnxe.requests.BlockLunRequests;
import com.emc.storageos.vnxe.requests.CifsServerListRequest;
import com.emc.storageos.vnxe.requests.CifsShareRequests;
import com.emc.storageos.vnxe.requests.DeleteStorageResourceRequest;
import com.emc.storageos.vnxe.requests.DiskGroupRequests;
import com.emc.storageos.vnxe.requests.EthernetPortRequests;
import com.emc.storageos.vnxe.requests.FastVPRequest;
import com.emc.storageos.vnxe.requests.FcPortRequests;
import com.emc.storageos.vnxe.requests.FileInterfaceListRequest;
import com.emc.storageos.vnxe.requests.FileSystemActionRequest;
import com.emc.storageos.vnxe.requests.FileSystemListRequest;
import com.emc.storageos.vnxe.requests.FileSystemRequest;
import com.emc.storageos.vnxe.requests.FileSystemSnapRequests;
import com.emc.storageos.vnxe.requests.HostInitiatorRequest;
import com.emc.storageos.vnxe.requests.HostIpPortRequests;
import com.emc.storageos.vnxe.requests.HostListRequest;
import com.emc.storageos.vnxe.requests.HostLunRequests;
import com.emc.storageos.vnxe.requests.HostRequest;
import com.emc.storageos.vnxe.requests.IscsiNodeRequests;
import com.emc.storageos.vnxe.requests.IscsiPortalListRequest;
import com.emc.storageos.vnxe.requests.JobRequest;
import com.emc.storageos.vnxe.requests.KHClient;
import com.emc.storageos.vnxe.requests.LicenseRequest;
import com.emc.storageos.vnxe.requests.LogoutRequest;
import com.emc.storageos.vnxe.requests.LunGroupRequests;
import com.emc.storageos.vnxe.requests.LunGroupSnapRequests;
import com.emc.storageos.vnxe.requests.LunSnapRequests;
import com.emc.storageos.vnxe.requests.NasServerListRequest;
import com.emc.storageos.vnxe.requests.NfsServerListRequest;
import com.emc.storageos.vnxe.requests.NfsShareRequests;
import com.emc.storageos.vnxe.requests.PoolListRequest;
import com.emc.storageos.vnxe.requests.PoolRequest;
import com.emc.storageos.vnxe.requests.StorageProcessorListRequest;
import com.emc.storageos.vnxe.requests.StorageSystemRequest;
import com.emc.storageos.vnxe.requests.StorageTierRequest;

/**
 * This class is used to get data or execute configuration commands against VNXe arrays
 * one Client per VNXe array
 */
public class VNXeApiClient {
    private static Logger _logger = LoggerFactory.getLogger(VNXeApiClient.class);
    public static int GENERIC_STORAGE_LUN_TYPE = 1;
    public static int STANDALONE_LUN_TYPE = 2;
    public String netBios;

    // the client to invoke VNXe requests
    private KHClient _khClient;

    public VNXeApiClient(KHClient client) {
        this._khClient = client;
    }

    /**
     * get all of NasServers in the vnxe array
     */
    public List<VNXeNasServer> getNasServers() {
        _logger.info("getting NasServers");
        NasServerListRequest req = new NasServerListRequest(_khClient);
        return req.get();
    }

    /**
     * get the storage system
     */
    public VNXeStorageSystem getStorageSystem() throws VNXeException {
        _logger.info("getting storage system");
        StorageSystemRequest req = new StorageSystemRequest(_khClient);
        return req.get();

    }

    /**
     * get the basic system info
     */
    public BasicSystemInfo getBasicSystemInfo() throws VNXeException {
        _logger.info("getting basic system info");
        BasicSystemInfoRequest req = new BasicSystemInfoRequest(_khClient);
        return req.get();

    }

    /**
     * get all of Nfs servers
     */
    public List<VNXeNfsServer> getNfsServers() {
        _logger.info("getting nfs servers");
        NfsServerListRequest req = new NfsServerListRequest(_khClient);
        return req.get();
    }

    /*
     * get all of Cifs servers
     */
    public List<VNXeCifsServer> getCifsServers() {
        _logger.info("getting cifs servers");
        CifsServerListRequest req = new CifsServerListRequest(_khClient);
        return req.get();
    }

    /**
     * get all storage pools
     */
    public List<VNXePool> getPools() {
        _logger.info("getting pools");
        PoolListRequest req = new PoolListRequest(_khClient);
        return req.get();
    }

    /**
     * get all file interfaces
     */
    public List<VNXeFileInterface> getFileInterfaces() {
        _logger.info("getting fileInterfaces");
        FileInterfaceListRequest req = new FileInterfaceListRequest(_khClient);
        return req.get();
    }

    /**
     * get job information
     * 
     * @param id job id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob getJob(String id) {
        _logger.info("getting the job: " + id);
        JobRequest req = new JobRequest(_khClient, id);
        return req.get();
    }

    /**
     * get pool
     */
    public VNXePool getPool(String poolId) {
        _logger.info("getting pool: " + poolId);
        PoolRequest req = new PoolRequest(_khClient, poolId);
        return req.get();
    }

    /**
     * get file system based on storage resource Id
     */
    public VNXeFileSystem getFileSystemByStorageResourceId(String storageResourceId) {
        _logger.info("getting file system by the storage resource id: " + storageResourceId);
        FileSystemListRequest req = new FileSystemListRequest(_khClient);
        return req.getByStorageResource(storageResourceId);

    }

    /**
     * get file system based on file system name
     */
    public VNXeFileSystem getFileSystemByFSName(String fsName) {
        _logger.info("getting file system by the file system name: " + fsName);
        FileSystemListRequest req = new FileSystemListRequest(_khClient);
        return req.getByFSName(fsName);

    }

    /**
     * get file system based on file system id
     */
    public VNXeFileSystem getFileSystemByFSId(String fsId) {
        _logger.info("getting file system by the file system id: " + fsId);
        FileSystemRequest req = new FileSystemRequest(_khClient, fsId);
        return req.get();

    }

    /**
     * get host based on host id
     */
    public VNXeHost getHostById(String hostId) {
        _logger.info("getting host by host id: " + hostId);
        HostRequest req = new HostRequest(_khClient, hostId);
        return req.get();

    }

    public String getNetBios() {
        return netBios;
    }

    public void setNetBios(String netBios) {
        this.netBios = netBios;
    }

    /**
     * create file system
     * 
     * @param fsName file system name.
     * @param size size in byte.
     * @param poolId pool id.
     * @param nasServerId nasServer id.
     * @param isThin is thin enabled.
     * @param supportedProtocols
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createFileSystem(String fsName, long size, String poolId,
            String nasServerId, boolean isThin, VNXeFSSupportedProtocolEnum supportedProtocols) throws VNXeException {
        _logger.info("Creating file system:" + fsName);
        CreateFileSystemParam parm = new CreateFileSystemParam();
        parm.setName(fsName);

        FileSystemParam fsParm = new FileSystemParam();
        fsParm.setIsThinEnabled(isThin);
        VNXeBase nasServer = new VNXeBase();
        nasServer.setId(nasServerId);
        fsParm.setNasServer(nasServer);
        VNXeBase pool = new VNXeBase();
        pool.setId(poolId);
        fsParm.setPool(pool);
        fsParm.setSize(size);
        fsParm.setSupportedProtocols(supportedProtocols.getValue());

        parm.setFsParameters(fsParm);
        FileSystemActionRequest req = new FileSystemActionRequest(_khClient);
        _logger.info("submitted the create file system job for " + fsName);
        return req.createFileSystemAsync(parm);

    }

    /**
     * logout
     */
    public void logout() {
        _logger.info("logging out");
        LogoutRequest req = new LogoutRequest(_khClient);
        req.executeRequest();
    }

    /**
     * delete file system with async call
     * 
     * @param fsId file system Id
     * @param forceSnapDeletion whether to delete snapshots as well
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob deleteFileSystem(String fsId, boolean forceSnapDeletion)
            throws VNXeException {
        _logger.info("deleting file system: " + fsId);
        DeleteStorageResourceRequest req = new DeleteStorageResourceRequest(_khClient);
        return req.deleteFileSystemAsync(fsId, forceSnapDeletion);
    }

    /**
     * delete file system sync
     * 
     * @param fsId file system Id
     * @param forceSnapDeletion whether to delete snapshots as well
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandResult deleteFileSystemSync(String fsId, boolean forceSnapDeletion)
            throws VNXeException {
        _logger.info("deleting file system: " + fsId);
        DeleteStorageResourceRequest req = new DeleteStorageResourceRequest(_khClient);
        return req.deleteFileSystemSync(fsId, forceSnapDeletion);
    }

    /**
     * NFS export
     * 
     * @param fsId file system KH id
     * @param endpoints list of host ipaddresses export to
     * @param access access right
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob exportFileSystem(String fsId, List<String> roEndpoints,
            List<String> rwEndpoints, List<String> rootEndpoints,
            AccessEnum access, String path, String shareName, String shareId, String comments) throws VNXeException {
        _logger.info("Exporting file system:" + fsId);
        FileSystemRequest fsRequest = new FileSystemRequest(_khClient, fsId);
        VNXeFileSystem fs = fsRequest.get();
        if (fs == null) {
            _logger.info("Could not find file system in the vxne");
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find file system in the vnxe for: " + fsId);
        }
        String resourceId = fs.getStorageResource().getId();

        ModifyFileSystemParam modifyFSParm = new ModifyFileSystemParam();

        List<VNXeBase> roHosts = getHosts(roEndpoints);
        List<VNXeBase> rwHosts = getHosts(rwEndpoints);
        List<VNXeBase> rootHosts = getHosts(rootEndpoints);
        VNXeNfsShare nfsShareFound = null;

        if (shareName != null) {
            nfsShareFound = findNfsShare(fsId, shareName);
        } else {
            nfsShareFound = getNfsShareById(shareId);
        }

        String nfsShareId = null;
        List<VNXeBase> hosts = new ArrayList<VNXeBase>();
        if (nfsShareFound != null) {
            nfsShareId = nfsShareFound.getId();
        }

        NfsShareParam shareParm = new NfsShareParam();
        shareParm.setReadOnlyHosts(roHosts);
        shareParm.setReadWriteHosts(rwHosts);
        shareParm.setRootAccessHosts(rootHosts);
        if (comments != null) {
            shareParm.setDescription(comments);
        }

        if (access == null) {
            if (nfsShareFound != null) {
                hosts.addAll(nfsShareFound.getNoAccessHosts());
                hosts.addAll(nfsShareFound.getRootAccessHosts());
                hosts.addAll(nfsShareFound.getReadWriteHosts());
                hosts.addAll(nfsShareFound.getReadOnlyHosts());
            }
            NFSShareDefaultAccessEnum nfsShareDefaultAccess = NFSShareDefaultAccessEnum.NONE;
            if (nfsShareFound != null) {
                nfsShareDefaultAccess = nfsShareFound.getDefaultAccess();
            }
            if (nfsShareDefaultAccess.equals(NFSShareDefaultAccessEnum.ROOT)) {
                if (!hosts.isEmpty()) {
                    shareParm.setRootAccessHosts(hosts);
                } else {
                    shareParm.setRootAccessHosts(null);
                }
                shareParm.setNoAccessHosts(null);
                shareParm.setReadWriteHosts(null);
                shareParm.setReadOnlyHosts(null);
            } else if (nfsShareDefaultAccess.equals(NFSShareDefaultAccessEnum.READONLY)) {
                if (!hosts.isEmpty()) {
                    shareParm.setReadOnlyHosts(hosts);
                } else {
                    shareParm.setReadOnlyHosts(null);
                }
                shareParm.setNoAccessHosts(null);
                shareParm.setReadWriteHosts(null);
                shareParm.setRootAccessHosts(null);
            } else if (nfsShareDefaultAccess.equals(NFSShareDefaultAccessEnum.READWRITE)) {
                if (!hosts.isEmpty()) {
                    shareParm.setReadWriteHosts(hosts);
                } else {
                    shareParm.setReadWriteHosts(null);
                }
                shareParm.setNoAccessHosts(null);
                shareParm.setReadOnlyHosts(null);
                shareParm.setRootAccessHosts(null);
            } else if (nfsShareDefaultAccess.equals(NFSShareDefaultAccessEnum.NONE)) {
                if (!hosts.isEmpty()) {
                    shareParm.setNoAccessHosts(hosts);
                } else {
                    shareParm.setNoAccessHosts(null);
                }
                shareParm.setReadWriteHosts(null);
                shareParm.setReadOnlyHosts(null);
                shareParm.setRootAccessHosts(null);
            }
        }

        if (nfsShareId == null) {
            // not found, new export
            NfsShareCreateParam nfsShareCreateParm = new NfsShareCreateParam();
            nfsShareCreateParm.setName(shareName);
            nfsShareCreateParm.setPath(path);
            nfsShareCreateParm.setNfsShareParameters(shareParm);
            List<NfsShareCreateParam> nfsList = new ArrayList<NfsShareCreateParam>();
            nfsList.add(nfsShareCreateParm);
            modifyFSParm.setNfsShareCreate(nfsList);
        } else {
            // update export
            NfsShareModifyParam nfsShareModifyParam = new NfsShareModifyParam();
            VNXeBase nfsShare = new VNXeBase();
            nfsShare.setId(nfsShareId);
            nfsShareModifyParam.setNfsShare(nfsShare);
            nfsShareModifyParam.setNfsShareParameters(shareParm);
            List<NfsShareModifyParam> nfsModifyList = new ArrayList<NfsShareModifyParam>();
            nfsModifyList.add(nfsShareModifyParam);
            modifyFSParm.setNfsShareModify(nfsModifyList);
        }

        FileSystemActionRequest req = new FileSystemActionRequest(_khClient);
        return req.modifyFileSystemAsync(modifyFSParm, resourceId);

    }

    /**
     * Find nfsShare using file system Id and vipr exportKey
     * 
     * @param fsId file system Id
     * @param exportKey vipr exportKey
     * @return nfsShare Id
     */
    public VNXeNfsShare findNfsShare(String fsId, String shareName) {
        _logger.info("finding nfsShare id for file system id: {}, and nameKey: {} ",
                fsId, shareName);
        NfsShareRequests req = new NfsShareRequests(_khClient);
        VNXeNfsShare share = req.findNfsShare(fsId, shareName, getBasicSystemInfo().getSoftwareVersion());
        return share;
    }

    /**
     * Find nfsShare using share Id
     * 
     * @param shareId NFS Share Id
     * @return nfsShare
     */
    public VNXeNfsShare getNfsShareById(String shareId) {
        _logger.info("finding nfsShare id: {} ", shareId);
        NfsShareRequests req = new NfsShareRequests(_khClient);
        VNXeNfsShare share = req.getShareById(shareId);
        if (share != null) {
            _logger.info("Got the nfsShare: {}", share.getId());
        } else {
            _logger.info("Could not find nfsShare by Id: {}", shareId);
        }
        return share;
    }

    /**
     * Find nfsShare using snapshot Id and snapshot share name
     * 
     * @param snapId file system snapshot Id
     * @param shareName NFS Export/Share name
     * @return nfsShare
     */
    public VNXeNfsShare findSnapNfsShare(String snapId, String shareName) {
        _logger.info("finding nfsShare id for snap id: {}, and shareName: {} ", snapId, shareName);
        NfsShareRequests req = new NfsShareRequests(_khClient);
        VNXeNfsShare share = req.findSnapNfsShare(snapId, shareName, getBasicSystemInfo().getSoftwareVersion());
        return share;
    }

    /**
     * Delete nfsShare
     * 
     * @param nfsShareId nfsShare Id
     * @param fsId file system Id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob removeNfsShare(String nfsShareId, String fsId) {
        VNXeCommandJob job = null;
        _logger.info("unexporting file system:" + fsId);
        FileSystemRequest fsRequest = new FileSystemRequest(_khClient, fsId);
        VNXeFileSystem fs = fsRequest.get();
        if (fs == null) {
            _logger.error("Could not find file system in the vxne");
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find file system in the vnxe for: " + fsId);
        }
        if (nfsShareId == null || nfsShareId.isEmpty()) {
            _logger.error("NfsShareId is empty.");
            throw VNXeException.exceptions.vnxeCommandFailed("NfsShareId is empty. ");
        }
        String resourceId = fs.getStorageResource().getId();

        ModifyFileSystemParam modifyFSParm = new ModifyFileSystemParam();
        // set NfsShare delete parm
        NfsShareDeleteParam deleteParam = new NfsShareDeleteParam();
        VNXeBase share = new VNXeBase();
        share.setId(nfsShareId);
        deleteParam.setNfsShare(share);

        List<NfsShareDeleteParam> deleteList = new ArrayList<NfsShareDeleteParam>();
        deleteList.add(deleteParam);
        modifyFSParm.setNfsShareDelete(deleteList);
        FileSystemActionRequest req = new FileSystemActionRequest(_khClient);
        job = req.modifyFileSystemAsync(modifyFSParm, resourceId);
        return job;

    }

    /**
     * expand file system
     * 
     * @param fsId fileSystem Id
     * @param newSize new capacity
     * @return VNXeCommandJob
     */
    public VNXeCommandJob expandFileSystem(String fsId, long newSize) {
        VNXeCommandJob job = null;
        _logger.info("expanding file system:" + fsId);
        FileSystemRequest fsRequest = new FileSystemRequest(_khClient, fsId);
        VNXeFileSystem fs = fsRequest.get();
        if (fs == null) {
            _logger.info("Could not find file system in the vxne");
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find file system in the vnxe for: " + fsId);
        }
        String resourceId = fs.getStorageResource().getId();

        ModifyFileSystemParam modifyFSParm = new ModifyFileSystemParam();
        // set fileSystemParam
        FileSystemParam fsParm = new FileSystemParam();
        fsParm.setSize(newSize);
        fsParm.setIsThinEnabled(fs.getIsThinEnabled());
        fsParm.setIsFLREnabled(fs.getIsFLREnabled());
        fsParm.setSupportedProtocols(fs.getSupportedProtocols());
        fsParm.setSizeAllocated(fs.getSizeAllocated());
        modifyFSParm.setFsParameters(fsParm);
        FileSystemActionRequest req = new FileSystemActionRequest(_khClient);
        job = req.modifyFileSystemAsync(modifyFSParm, resourceId);
        return job;

    }

    /**
     * Create file system snapshot
     * 
     * @param fsId file system id
     * @param name snapshot name
     * @return VNXeCommandJob
     */
    public VNXeCommandJob createFileSystemSnap(String fsId, String name) {
        _logger.info("creating file system snap:" + fsId);
        String resourceId = getStorageResourceId(fsId);
        FileSystemSnapCreateParam parm = new FileSystemSnapCreateParam();
        VNXeBase resource = new VNXeBase();
        resource.setId(resourceId);
        parm.setStorageResource(resource);
        parm.setName(name);
        parm.setIsReadOnly(false);
        FileSystemSnapRequests req = new FileSystemSnapRequests(_khClient, getBasicSystemInfo().getSoftwareVersion());

        return req.createFileSystemSnap(parm);

    }

    /**
     * Get snapshot by its name
     * 
     * @param name snapshot name
     * @return VNXeFileSystemSnap
     */
    public VNXeFileSystemSnap getSnapshotByName(String name) {
        _logger.info("Getting the snapshot {}: ", name);
        FileSystemSnapRequests req = new FileSystemSnapRequests(_khClient, getBasicSystemInfo().getSoftwareVersion());
        return req.getByName(name);

    }

    /**
     * Delete file system snapshot
     * 
     * @param snapId snapshot VNXe Id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob deleteFileSystemSnap(String snapId) {
        _logger.info("deleting file system snap:" + snapId);
        String softwareVersion = getBasicSystemInfo().getSoftwareVersion();
        FileSystemSnapRequests req = new FileSystemSnapRequests(_khClient, softwareVersion);
        return req.deleteFileSystemSnap(snapId, softwareVersion);
    }

    /**
     * restore file system snapshot
     * 
     * @param snapId VNXe snapshot id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob restoreFileSystemSnap(String snapId) {
        _logger.info("restoring file system snap:" + snapId);
        String softwareVersion = getBasicSystemInfo().getSoftwareVersion();
        FileSystemSnapRequests req = new FileSystemSnapRequests(_khClient, softwareVersion);
        return req.restoreFileSystemSnap(snapId, null, softwareVersion);
    }

    /**
     * get file system's snapshot list
     * 
     * @param fsId file system id
     * @return list of snapshots
     */
    public List<VNXeFileSystemSnap> getFileSystemSnaps(String fsId) {
        String resourceId = getStorageResourceId(fsId);
        FileSystemSnapRequests req = new FileSystemSnapRequests(_khClient, getBasicSystemInfo().getSoftwareVersion());
        return req.getFileSystemSnaps(resourceId);
    }

    /**
     * create cifsShare
     */
    public VNXeCommandJob createCIFSShare(String fsId, String cifsName, String permission, String path)
            throws VNXeException {
        _logger.info("creating CIFS share:" + fsId);
        FileSystemRequest fsRequest = new FileSystemRequest(_khClient, fsId);
        VNXeFileSystem fs = fsRequest.get();
        if (fs == null) {
            _logger.info("Could not find file system in the vxne");
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find file system in the vnxe for: " + fsId);
        }
        String resourceId = fs.getStorageResource().getId();

        ModifyFileSystemParam modifyFSParm = new ModifyFileSystemParam();
        CifsShareParam cifsParam = new CifsShareParam();
        /*
         * CifsShareACE ace = new CifsShareACE();
         * ace.setAccessLevel(4);
         * ace.setAccessType(1);
         * ace.setSid("S-1-5-21-3623811015-3361044348-30300820-1014");
         * List<CifsShareACE> aceList = new ArrayList<CifsShareACE>();
         * aceList.add(ace);
         * cifsParam.setAddACE(aceList);
         */
        cifsParam.setIsACEEnabled(false);
        if (permission != null && !permission.isEmpty() && permission.equalsIgnoreCase(AccessEnum.READ.name())) {
            cifsParam.setIsReadOnly(true);
        } else {
            cifsParam.setIsReadOnly(false);
        }
        CifsShareCreateParam cifsCreate = new CifsShareCreateParam();

        cifsCreate.setName(cifsName);
        cifsCreate.setPath(path);
        _logger.info("Creating VNXe CIFS share by name: {} for path: {}", cifsName, path);
        List<VNXeCifsServer> cifsServers = getCifsServers(fs.getNasServer().getId());
        if (cifsServers == null || cifsServers.isEmpty()) {
            throw VNXeException.exceptions.vnxeCommandFailed("The nasServer is not configured to support CIFS");
        }
        VNXeBase cifsServer = new VNXeBase();
        cifsServer.setId(cifsServers.get(0).getId());
        cifsCreate.setCifsServer(cifsServer);
        cifsCreate.setCifsShareParameters(cifsParam);
        netBios = cifsServers.get(0).getNetbiosName();

        List<CifsShareCreateParam> cifsCreateList = new ArrayList<CifsShareCreateParam>();
        cifsCreateList.add(cifsCreate);
        modifyFSParm.setCifsShareCreate(cifsCreateList);

        FileSystemActionRequest req = new FileSystemActionRequest(_khClient);
        return req.modifyFileSystemAsync(modifyFSParm, resourceId);
    }

    /**
     * Delete cifsShare
     * 
     * @param cifsShareId cifsShare Id
     * @param fsId file system Id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob removeCifsShare(String cifsShareId, String fsId) {
        VNXeCommandJob job = null;
        _logger.info("deleting cifs share" + cifsShareId);
        FileSystemRequest fsRequest = new FileSystemRequest(_khClient, fsId);
        VNXeFileSystem fs = fsRequest.get();
        if (fs == null) {
            _logger.info("Could not find file system in the vxne");
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find file system in the vnxe for: " + fsId);
        }
        String resourceId = fs.getStorageResource().getId();
        ModifyFileSystemParam modifyFSParm = new ModifyFileSystemParam();
        // set cifsShare delete parm
        CifsShareDeleteParam deleteParam = new CifsShareDeleteParam();
        VNXeBase share = new VNXeBase();
        share.setId(cifsShareId);
        deleteParam.setCifsShare(share);

        List<CifsShareDeleteParam> deleteList = new ArrayList<CifsShareDeleteParam>();
        deleteList.add(deleteParam);
        modifyFSParm.setCifsShareDelete(deleteList);
        FileSystemActionRequest req = new FileSystemActionRequest(_khClient);
        job = req.modifyFileSystemAsync(modifyFSParm, resourceId);
        return job;

    }

    /**
     * find CIFS share by its name
     * 
     * @param shareName CIFS share name
     * @return
     */
    public VNXeCifsShare findCifsShareByName(String shareName) {
        CifsShareRequests req = new CifsShareRequests(_khClient);
        List<VNXeCifsShare> shares = req.getCifsShareByName(shareName);
        if (shares != null && !shares.isEmpty()) {
            return shares.get(0);
        } else {
            return null;
        }

    }

    /**
     * Create CIFS share for snapshot
     * 
     * @param snapId snapshot id
     * @param shareName CIFS share name
     * @param permission READ, CHANGE, FULL
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createCifsShareForSnap(String snapId, String shareName, String permission, String path, String fsId)
            throws VNXeException {
        _logger.info("Creating CIFS snapshot share name: {} for path: {}",
                shareName, path);
        // to get NETBIOS of CIFS Server file system is used as for snapshot
        FileSystemRequest fsRequest = new FileSystemRequest(_khClient, fsId);
        VNXeFileSystem fs = fsRequest.get();
        List<VNXeCifsServer> cifsServers = getCifsServers(fs.getNasServer().getId());
        netBios = cifsServers.get(0).getNetbiosName();

        CifsShareRequests req = new CifsShareRequests(_khClient);
        CifsShareCreateForSnapParam param = new CifsShareCreateForSnapParam();
        param.setPath(path);
        VNXeBase snap = new VNXeBase();
        snap.setId(snapId);
        if (!VNXeUtils.isHigherVersion(getBasicSystemInfo().getSoftwareVersion(),
                VNXeConstants.VNXE_BASE_SOFT_VER)) {
            param.setFilesystemSnap(snap);
        } else {
            param.setSnap(snap);
        }
        param.setName(shareName);
        if (permission != null && !permission.isEmpty() &&
                permission.equalsIgnoreCase(AccessEnum.READ.name())) {
            param.setIsReadOnly(true);
        } else {
            param.setIsReadOnly(false);
        }
        return req.createShareForSnapshot(param);
    }

    /**
     * Create Nfs share for snapshot
     * 
     * @param snapId snapshot id
     * @param endpoints hosts
     * @param access READ, WRITE, ROOT
     * @param path
     * @param exportKey
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createNfsShareForSnap(String snapId, List<String> roEndpoints,
            List<String> rwEndpoints, List<String> rootEndpoints,
            AccessEnum access, String path, String shareName, String comments) throws VNXeException {

        _logger.info("creating nfs share for the snap: " + snapId);
        NfsShareRequests request = new NfsShareRequests(_khClient);
        String softwareVersion = getBasicSystemInfo().getSoftwareVersion();
        FileSystemSnapRequests req = new FileSystemSnapRequests(_khClient, softwareVersion);
        VNXeFileSystemSnap snapshot = req.getFileSystemSnap(snapId, softwareVersion);
        if (snapshot == null) {
            _logger.info("Could not find snapshot in the vxne");
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find snapshot in the vnxe for: " + snapId);
        }
        NfsShareCreateForSnapParam nfsCreateParam = new NfsShareCreateForSnapParam();
        VNXeBase snap = new VNXeBase(snapId);
        if (!VNXeUtils.isHigherVersion(softwareVersion, VNXeConstants.VNXE_BASE_SOFT_VER)) {
            nfsCreateParam.setFilesystemSnap(snap);
        } else {
            nfsCreateParam.setSnap(snap);
        }
        List<VNXeBase> roHosts = getHosts(roEndpoints);
        List<VNXeBase> rwHosts = getHosts(rwEndpoints);
        List<VNXeBase> rootHosts = getHosts(rootEndpoints);

        VNXeCommandJob job = null;
        VNXeNfsShare nfsShareFound = request.findSnapNfsShare(snapId, shareName, softwareVersion);

        if (nfsShareFound == null) {   // new export
            nfsCreateParam.setReadOnlyHosts(roHosts);
            nfsCreateParam.setReadWriteHosts(rwHosts);
            nfsCreateParam.setRootAccessHosts(rootHosts);
            nfsCreateParam.setName(shareName);
            nfsCreateParam.setPath(path);
            if (comments != null) {
                nfsCreateParam.setDescription(comments);
            }
            job = request.createShareForSnapshot(nfsCreateParam);
        } else {
            String nfsShareId = nfsShareFound.getId();
            NFSShareDefaultAccessEnum nfsShareDefaultAccess = nfsShareFound.getDefaultAccess();
            NfsShareModifyForShareParam nfsModifyParam = new NfsShareModifyForShareParam();
            List<VNXeBase> hosts = new ArrayList<VNXeBase>();

            nfsModifyParam.setReadOnlyHosts(roHosts);
            nfsModifyParam.setReadWriteHosts(rwHosts);
            nfsModifyParam.setRootAccessHosts(rootHosts);
            if (comments != null) {
                nfsModifyParam.setDescription(comments);
            }
            if (access == null) {
                if (nfsShareFound != null) {
                    hosts.addAll(nfsShareFound.getNoAccessHosts());
                    hosts.addAll(nfsShareFound.getRootAccessHosts());
                    hosts.addAll(nfsShareFound.getReadWriteHosts());
                    hosts.addAll(nfsShareFound.getReadOnlyHosts());
                }
                if (nfsShareDefaultAccess.equals(NFSShareDefaultAccessEnum.ROOT)) {
                    if (!hosts.isEmpty()) {
                        nfsModifyParam.setRootAccessHosts(hosts);
                    } else {
                        nfsModifyParam.setRootAccessHosts(null);
                    }
                    nfsModifyParam.setNoAccessHosts(null);
                    nfsModifyParam.setReadWriteHosts(null);
                    nfsModifyParam.setReadOnlyHosts(null);
                } else if (nfsShareDefaultAccess.equals(NFSShareDefaultAccessEnum.READONLY)) {
                    if (!hosts.isEmpty()) {
                        nfsModifyParam.setReadOnlyHosts(hosts);
                    } else {
                        nfsModifyParam.setReadOnlyHosts(null);
                    }
                    nfsModifyParam.setNoAccessHosts(null);
                    nfsModifyParam.setReadWriteHosts(null);
                    nfsModifyParam.setRootAccessHosts(null);
                } else if (nfsShareDefaultAccess.equals(NFSShareDefaultAccessEnum.READWRITE)) {
                    if (!hosts.isEmpty()) {
                        nfsModifyParam.setReadWriteHosts(hosts);
                    } else {
                        nfsModifyParam.setReadWriteHosts(null);
                    }
                    nfsModifyParam.setNoAccessHosts(null);
                    nfsModifyParam.setReadOnlyHosts(null);
                    nfsModifyParam.setRootAccessHosts(null);
                } else if (nfsShareDefaultAccess.equals(NFSShareDefaultAccessEnum.NONE)) {
                    if (!hosts.isEmpty()) {
                        nfsModifyParam.setNoAccessHosts(hosts);
                    } else {
                        nfsModifyParam.setNoAccessHosts(null);
                    }
                    nfsModifyParam.setReadWriteHosts(null);
                    nfsModifyParam.setReadOnlyHosts(null);
                    nfsModifyParam.setRootAccessHosts(null);
                }
            }
            job = request.modifyShareForSnapshot(nfsShareId, nfsModifyParam);
        }
        return job;
    }

    /**
     * delete nfs share created for snapshot
     * 
     * @param shareId nfsShare Id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob deleteNfsShareForSnapshot(String shareId) throws VNXeException {
        NfsShareRequests req = new NfsShareRequests(_khClient);
        return req.deleteShareForSnapshot(shareId);
    }

    /**
     * delete cifs share for snapshot
     * 
     * @param shareId cifsShare Id
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob deleteCifsShareForSnapshot(String shareId) throws VNXeException {
        CifsShareRequests req = new CifsShareRequests(_khClient);
        return req.deleteShareForSnapshot(shareId);
    }

    /**
     * Get all iSCSI ports
     * 
     * @return
     */
    public List<VNXeIscsiNode> getAllIscsiPorts() {
        IscsiNodeRequests nodeReq = new IscsiNodeRequests(_khClient);
        List<VNXeIscsiNode> nodes = nodeReq.getAllNodes();
        if (nodes != null && !nodes.isEmpty()) {
            for (VNXeIscsiNode node : nodes) {
                VNXeEthernetPort eport = node.getEthernetPort();
                if (eport != null) {
                    String id = eport.getId();
                    EthernetPortRequests portRequest = new EthernetPortRequests(_khClient);
                    VNXeEthernetPort detailedPort = portRequest.get(id);
                    node.setEthernetPort(detailedPort);
                    // get iscsiPortal. comment it out for now, since API does not work.
                    IscsiPortalListRequest portalReq = new IscsiPortalListRequest(_khClient);
                    VNXeIscsiPortal portal = portalReq.getByIscsiNode(node.getId());
                    node.setIscsiPortal(portal);
                } else {
                    nodes.remove(node);
                }
            }
        }
        return nodes;
    }

    /**
     * Get all storageProcessors
     * 
     * @return
     */
    public List<VNXeStorageProcessor> getStorageProcessors() {
        StorageProcessorListRequest spReq = new StorageProcessorListRequest(_khClient);
        return spReq.get();
    }

    public List<VNXeFCPort> getAllFcPorts() {
        FcPortRequests req = new FcPortRequests(_khClient);
        List<VNXeFCPort> fcPorts = new ArrayList<VNXeFCPort>();
        try {
            fcPorts = req.get();
        } catch (VNXeException e) {
            _logger.info("Exception caught while getting all fcPorts", e);
        }
        return fcPorts;
    }

    /**
     * Create standalone lun
     * 
     * @param name
     * @param poolId
     * @param size
     * @param isThin
     * @param tieringPolicy
     * @param lunGroupId
     * @return
     */
    public VNXeCommandJob createLun(String name, String poolId, Long size, boolean isThin,
            String tieringPolicy) {
        LunParam lunParam = new LunParam();
        lunParam.setIsThinEnabled(isThin);
        lunParam.setSize(size);
        lunParam.setPool(new VNXeBase(poolId));
        FastVPParam fastVP = new FastVPParam();
        if (tieringPolicy != null && !tieringPolicy.isEmpty()) {
            TieringPolicyEnum tierValue = TieringPolicyEnum.valueOf(tieringPolicy);
            if (tierValue != null) {
                fastVP.setTieringPolicy(tierValue.getValue());
                lunParam.setFastVPParameters(fastVP);
            }
        }
        LunCreateParam createParam = new LunCreateParam();
        createParam.setName(name);
        createParam.setLunParameters(lunParam);

        BlockLunRequests req = new BlockLunRequests(_khClient);
        return req.createLun(createParam);

    }

    /**
     * Create multiple volumes in a lun group
     * 
     * @param names
     * @param poolId
     * @param size
     * @param isThin
     * @param tieringPolicy
     * @param lunGroupId
     * @return
     */
    public VNXeCommandJob createLunsInLunGroup(List<String> names, String poolId, Long size, boolean isThin,
            String tieringPolicy, String lunGroupId) {
        _logger.info("creating luns in the lun group: {}", lunGroupId);
        LunGroupModifyParam param = new LunGroupModifyParam();
        List<LunCreateParam> lunCreates = new ArrayList<LunCreateParam>();
        boolean isPolicyOn = false;
        FastVPParam fastVP = new FastVPParam();
        if (tieringPolicy != null && !tieringPolicy.isEmpty()) {
            TieringPolicyEnum tierValue = TieringPolicyEnum.valueOf(tieringPolicy);
            if (tierValue != null) {
                fastVP.setTieringPolicy(tierValue.getValue());
                isPolicyOn = true;

            }
        }

        for (String lunName : names) {
            LunParam lunParam = new LunParam();
            lunParam.setIsThinEnabled(isThin);
            lunParam.setSize(size);
            lunParam.setPool(new VNXeBase(poolId));

            LunCreateParam createParam = new LunCreateParam();
            createParam.setName(lunName);
            createParam.setLunParameters(lunParam);
            if (isPolicyOn) {
                lunParam.setFastVPParameters(fastVP);
            }
            lunCreates.add(createParam);
        }
        param.setLunCreate(lunCreates);
        LunGroupRequests req = new LunGroupRequests(_khClient);
        return req.modifyLunGroupAsync(lunGroupId, param);

    }

    public VNXeCommandJob expandLun(String lunID, long newSize, String lunGroupID) {
        VNXeCommandJob job = null;
        _logger.info("expanding lun:" + lunID);
        VNXeLun vnxeLun = getLun(lunID);
        if (vnxeLun == null) {
            _logger.info("Could not find lun in the vxne");
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find lun in the vnxe for: " + lunID);
        }

        LunModifyParam lunModifyParam = new LunModifyParam();
        // set lunParam
        LunParam lunParam = new LunParam();
        lunParam.setSize(newSize);
        lunModifyParam.setLunParameters(lunParam);

        if (vnxeLun.getType() == GENERIC_STORAGE_LUN_TYPE) {
            if (lunGroupID == null || lunGroupID.isEmpty()) {
                _logger.error("Lun Group Id not found for lun: " + lunID);
                throw VNXeException.exceptions.vnxeCommandFailed("Could not find lun group for lun: " + lunID);
            }
            LunGroupModifyParam param = new LunGroupModifyParam();
            List<LunModifyParam> lunModifyParamList = new ArrayList<LunModifyParam>();
            lunModifyParam.setLun(new VNXeBase(lunID));
            lunModifyParamList.add(lunModifyParam);
            param.setLunModify(lunModifyParamList);
            LunGroupRequests lunGroupRequest = new LunGroupRequests(_khClient);
            job = lunGroupRequest.modifyLunGroupAsync(lunGroupID, param);

        } else if (vnxeLun.getType() == STANDALONE_LUN_TYPE) {
            BlockLunRequests req = new BlockLunRequests(_khClient);
            job = req.modifyLunAsync(lunModifyParam, lunID);
        }

        return job;
    }

    public VNXeLun getLun(String lunId) {
        BlockLunRequests req = new BlockLunRequests(_khClient);
        return req.getLun(lunId);
    }

    /**
     * delete lun with async call
     * 
     * @param fsId file system Id
     * @param forceSnapDeletion whether to delete snapshots as well
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob deleteLun(String lunId, boolean forceSnapDeletion)
            throws VNXeException {
        _logger.info("deleting lun: " + lunId);
        DeleteStorageResourceRequest req = new DeleteStorageResourceRequest(_khClient);
        return req.deleteLunAsync(lunId, forceSnapDeletion);
    }

    /**
     * delete lun sync
     * 
     * @param lunId lun Id
     * @param forceSnapDeletion whether to delete snapshots as well
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandResult deleteLunSync(String lunId, boolean forceSnapDeletion)
            throws VNXeException {
        _logger.info("deleting lun: " + lunId);
        DeleteStorageResourceRequest req = new DeleteStorageResourceRequest(_khClient);
        return req.deleteLunSync(lunId, forceSnapDeletion);
    }

    /**
     * get lun based on storage resource Id
     */
    public List<VNXeLun> getLunByStorageResourceId(String storageResourceId) {
        _logger.info("getting lun by the storage resource id: " + storageResourceId);
        BlockLunRequests req = new BlockLunRequests(_khClient);
        return req.getByStorageResourceId(storageResourceId);

    }

    /**
     * Get all Storage Tiers
     * 
     * @return
     */
    public List<VNXeStorageTier> getStorageTiers() {
        StorageTierRequest req = new StorageTierRequest(_khClient);
        return req.get();
    }

    /**
     * Get tier policies
     */
    public String[] getAutoTierPolicies() {
        return StorageResource.TieringPolicyEnum.getTieringPolicyNames();
    }

    /**
     * Create lungroup
     * 
     * @param name lun group name
     * @return VNXeCommmandResult, with the lun group id.
     */
    public VNXeCommandResult createLunGroup(String name) {
        LunGroupCreateParam param = new LunGroupCreateParam();
        param.setName(name);
        LunGroupRequests req = new LunGroupRequests(_khClient);
        return req.createLunGroup(param);
    }

    /**
     * Add luns to LunGroup
     * 
     */
    public VNXeCommandResult addLunsToLunGroup(String lunGroupId, List<String> luns) {
        LunGroupModifyParam param = new LunGroupModifyParam();
        List<LunAddParam> lunAdds = new ArrayList<LunAddParam>();
        for (String lunId : luns) {
            VNXeBase lun = new VNXeBase(lunId);
            LunAddParam lunAdd = new LunAddParam();
            lunAdd.setLun(lun);
            lunAdds.add(lunAdd);
        }
        param.setLunAdd(lunAdds);
        LunGroupRequests req = new LunGroupRequests(_khClient);
        return req.modifyLunGroupSync(lunGroupId, param);

    }

    /**
     * Remove luns from the lun group
     * 
     * @param lunGroupId lun group id
     * @param luns list of lun IDs
     * @return
     */
    public VNXeCommandResult removeLunsFromLunGroup(String lunGroupId, List<String> luns) {
        LunGroupModifyParam param = new LunGroupModifyParam();
        List<LunAddParam> lunRemoves = new ArrayList<LunAddParam>();
        for (String lunId : luns) {
            VNXeBase lun = new VNXeBase(lunId);
            LunAddParam lunAdd = new LunAddParam();
            lunAdd.setLun(lun);
            lunRemoves.add(lunAdd);
        }
        param.setLunRemove(lunRemoves);
        LunGroupRequests req = new LunGroupRequests(_khClient);
        return req.modifyLunGroupSync(lunGroupId, param);
    }

    /**
     * Delete luns from lun group
     * 
     * @param lunGroupId
     * @param luns
     * @return
     */
    public VNXeCommandJob deleteLunsFromLunGroup(String lunGroupId, List<String> luns) {
        LunGroupModifyParam param = new LunGroupModifyParam();
        List<LunAddParam> lunDelete = new ArrayList<LunAddParam>();
        for (String lunId : luns) {
            VNXeBase lun = new VNXeBase(lunId);
            LunAddParam lunAdd = new LunAddParam();
            lunAdd.setLun(lun);
            lunDelete.add(lunAdd);
        }
        param.setLunDelete(lunDelete);
        LunGroupRequests req = new LunGroupRequests(_khClient);
        return req.modifyLunGroupAsync(lunGroupId, param);
    }

    /**
     * Delete lun group.
     * if isForceVolumeDeletion is true, it would delete all the volumes in the lun group
     * and the lun group.
     * if isForceVolumeDeletion is false, it would remove all the volumes from the lun group,
     * then delete the lun group.
     * 
     * @param lunGroupId
     * @param isForceSnapDeletion
     * @return
     */
    public VNXeCommandResult deleteLunGroup(String lunGroupId,
            boolean isForceSnapDeletion, boolean isForceVolumeDeletion) {
        if (isForceVolumeDeletion) {
            DeleteStorageResourceRequest deleteReq = new DeleteStorageResourceRequest(_khClient);
            return deleteReq.deleteLunGroup(lunGroupId, isForceSnapDeletion);
        } else {
            BlockLunRequests lunReq = new BlockLunRequests(_khClient);
            List<VNXeLun> luns = lunReq.getLunsInLunGroup(lunGroupId);
            if (luns != null && !luns.isEmpty()) {
                List<String> lunIds = new ArrayList<String>();
                for (VNXeLun lun : luns) {
                    lunIds.add(lun.getId());
                }
                removeLunsFromLunGroup(lunGroupId, lunIds);
            }
            DeleteStorageResourceRequest deleteReq = new DeleteStorageResourceRequest(_khClient);
            return deleteReq.deleteLunGroup(lunGroupId, isForceSnapDeletion);
        }
    }

    /**
     * Export a lun for a given host
     * 
     * @param lunId lun id
     * @param initiators host initiators info
     * @return
     * @throws VNXeException
     */
    public VNXeExportResult exportLun(String lunId, List<VNXeHostInitiator> initiators) throws VNXeException {
        _logger.info("Exporting lun: {}", lunId);

        VNXeLun lun = getLun(lunId);
        if (lun == null) {
            _logger.info("Could not find lun in the vxne: {}", lunId);
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find lun : " + lunId);
        }
        VNXeBase host = prepareHostsForExport(initiators);

        List<BlockHostAccess> hostAccesses = lun.getHostAccess();
        boolean lunHostAccessExists = false;

        if (hostAccesses == null) {
            hostAccesses = new ArrayList<BlockHostAccess>();
        } else {
            // If there are already host access associated with the lun then check if there is one
            // already defined for the given host with a different access mask.
            for (BlockHostAccess hostAccess : hostAccesses) {
                String hostId = hostAccess.getHost().getId();
                if (hostId.equals(host.getId())) {
                    if (hostAccess.getAccessMask() == HostLUNAccessEnum.SNAPSHOT.getValue()) {
                        hostAccess.setAccessMask(HostLUNAccessEnum.BOTH.getValue());
                        lunHostAccessExists = true;
                        break;
                    } else if (hostAccess.getAccessMask() == HostLUNAccessEnum.NOACCESS.getValue()) {
                        hostAccess.setAccessMask(HostLUNAccessEnum.PRODUCTION.getValue());
                        lunHostAccessExists = true;
                        break;
                    }
                }
            }
        }

        if (!lunHostAccessExists) {
            BlockHostAccess access = new BlockHostAccess();
            access.setHost(host);
            access.setAccessMask(BlockHostAccess.HostLUNAccessEnum.PRODUCTION.getValue());
            hostAccesses.add(access);
        }

        LunParam lunParam = new LunParam();
        lunParam.setHostAccess(hostAccesses);
        LunModifyParam exportParam = new LunModifyParam();
        exportParam.setLunParameters(lunParam);
        int type = lun.getType();
        if (type == VNXeLun.LUNTypeEnum.Standalone.getValue()) {
            // if standalone lun
            BlockLunRequests lunReq = new BlockLunRequests(_khClient);
            lunReq.modifyLunSync(exportParam, lun.getStorageResource().getId());
        } else {
            // lun in a lun group
            exportParam.setLun(new VNXeBase(lunId));
            List<LunModifyParam> list = new ArrayList<LunModifyParam>();
            list.add(exportParam);
            LunGroupModifyParam groupParam = new LunGroupModifyParam();
            groupParam.setLunModify(list);
            LunGroupRequests lunGroupReq = new LunGroupRequests(_khClient);
            lunGroupReq.modifyLunGroupSync(lun.getStorageResource().getId(), groupParam);
        }
        // get hlu
        HostLunRequests hostLunReq = new HostLunRequests(_khClient);
        HostLun hostLun = hostLunReq.getHostLun(lunId, host.getId(), HostLunRequests.ID_SEQUENCE_LUN);
        VNXeExportResult result = new VNXeExportResult();
        result.setHlu(hostLun.getHlu());
        result.setLunId(lunId);
        result.setHostId(host.getId());
        _logger.info("Done exporting lun: {}", lunId);
        return result;
    }

    /**
     * remove the hosts from the hostAccess list from the lun
     * 
     * @param lunId
     * @param initiators
     */
    public void unexportLun(String lunId, List<VNXeHostInitiator> initiators) {
        _logger.info("Unexporting lun: {}", lunId);

        for (VNXeHostInitiator initiator : initiators) {
            _logger.info("removing host: {} ", initiator.getName());
        }
        VNXeLun lun = getLun(lunId);
        if (lun == null) {
            _logger.info("Could not find lun in the vxne: {}", lunId);
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find lun : " + lunId);
        }

        Set<String> removingHosts = findHostsByInitiators(initiators);
        if (removingHosts.isEmpty()) {
            _logger.info("No host found.");
            return;
        }
        List<BlockHostAccess> hostAccesses = lun.getHostAccess();

        if (hostAccesses == null || hostAccesses.isEmpty()) {
            _logger.info("No block host access found for the lun: {}", lunId);
            return;
        }

        List<BlockHostAccess> changedHostAccessList = new ArrayList<BlockHostAccess>();
        for (BlockHostAccess hostAccess : hostAccesses) {
            String hostId = hostAccess.getHost().getId();
            if (removingHosts.contains(hostId)) {
                if (hostAccess.getAccessMask() == HostLUNAccessEnum.BOTH.getValue()) {
                    hostAccess.setAccessMask(HostLUNAccessEnum.SNAPSHOT.getValue());
                } else if (hostAccess.getAccessMask() == HostLUNAccessEnum.PRODUCTION.getValue()) {
                    hostAccess.setAccessMask(HostLUNAccessEnum.NOACCESS.getValue());
                }
            }
            changedHostAccessList.add(hostAccess);
        }
        if (changedHostAccessList.isEmpty()) {
            // the removing hosts are not exported
            _logger.info("The unexport hosts were not exported.");
            return;
        }

        LunParam lunParam = new LunParam();
        lunParam.setHostAccess(changedHostAccessList);
        LunModifyParam modifyParam = new LunModifyParam();
        modifyParam.setLunParameters(lunParam);
        int type = lun.getType();
        if (type == VNXeLun.LUNTypeEnum.Standalone.getValue()) {
            // if standalone lun
            BlockLunRequests lunReq = new BlockLunRequests(_khClient);
            lunReq.modifyLunSync(modifyParam, lun.getStorageResource().getId());
        } else {
            // lun in a lun group
            modifyParam.setLun(new VNXeBase(lunId));
            List<LunModifyParam> list = new ArrayList<LunModifyParam>();
            list.add(modifyParam);
            LunGroupModifyParam groupParam = new LunGroupModifyParam();
            groupParam.setLunModify(list);
            LunGroupRequests lunGroupReq = new LunGroupRequests(_khClient);
            lunGroupReq.modifyLunGroupSync(lun.getStorageResource().getId(), groupParam);
        }
        _logger.info("Done unexporting lun: {}", lunId);

    }

    /**
     * Export a snap for a given host
     * 
     * @param snapId snap id
     * @param initiators host initiators info
     * @return
     * @throws VNXeException
     */
    public VNXeExportResult exportSnap(String snapId, List<VNXeHostInitiator> initiators) throws VNXeException {
        _logger.info("Exporting lun snap: {}", snapId);

        VNXeLunSnap lunSnap = getLunSnapshot(snapId);
        if (lunSnap == null) {
            _logger.info("Could not find lun snap in the vxne: {}", snapId);
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find lun snap: " + snapId);
        }
        VNXeBase host = prepareHostsForExport(initiators);

        // If the snap is not attached, attach it
        if (!lunSnap.getIsAttached()) {
            _logger.info("Attaching the snap: {}", snapId);
            attachLunSnap(snapId);
        }

        // Get host access info of the parent lun
        VNXeLun parentLun = getLun(lunSnap.getLun().getId());
        List<BlockHostAccess> hostAccesses = parentLun.getHostAccess();
        boolean snapHostAccessExists = false;

        if (hostAccesses == null) {
            hostAccesses = new ArrayList<BlockHostAccess>();
        } else {
            // If there are already host access associated with the lun then check if there is one
            // already defined for the given host with a different access mask.
            for (BlockHostAccess hostAccess : hostAccesses) {
                String hostId = hostAccess.getHost().getId();
                if (hostId.equals(host.getId())) {
                    if (hostAccess.getAccessMask() == HostLUNAccessEnum.PRODUCTION.getValue()) {
                        hostAccess.setAccessMask(HostLUNAccessEnum.BOTH.getValue());
                        snapHostAccessExists = true;
                        break;
                    } else if (hostAccess.getAccessMask() == HostLUNAccessEnum.NOACCESS.getValue()) {
                        hostAccess.setAccessMask(HostLUNAccessEnum.SNAPSHOT.getValue());
                        snapHostAccessExists = true;
                        break;
                    }
                }
            }
        }

        if (!snapHostAccessExists) {
            BlockHostAccess access = new BlockHostAccess();
            access.setHost(host);
            access.setAccessMask(BlockHostAccess.HostLUNAccessEnum.SNAPSHOT.getValue());
            hostAccesses.add(access);
        }

        LunParam lunParam = new LunParam();
        lunParam.setHostAccess(hostAccesses);
        LunModifyParam exportParam = new LunModifyParam();
        exportParam.setLunParameters(lunParam);
        int type = parentLun.getType();
        if (type == VNXeLun.LUNTypeEnum.Standalone.getValue()) {
            // if standalone lun
            BlockLunRequests lunReq = new BlockLunRequests(_khClient);
            lunReq.modifyLunSync(exportParam, parentLun.getStorageResource().getId());
        } else {
            // lun in a lun group
            exportParam.setLun(new VNXeBase(parentLun.getId()));
            List<LunModifyParam> list = new ArrayList<LunModifyParam>();
            list.add(exportParam);
            LunGroupModifyParam groupParam = new LunGroupModifyParam();
            groupParam.setLunModify(list);
            LunGroupRequests lunGroupReq = new LunGroupRequests(_khClient);
            lunGroupReq.modifyLunGroupSync(parentLun.getStorageResource().getId(), groupParam);
        }
        // get hlu
        HostLunRequests hostLunReq = new HostLunRequests(_khClient);
        HostLun hostLun = hostLunReq.getHostLun(parentLun.getId(), host.getId(), HostLunRequests.ID_SEQUENCE_SNAP);

        VNXeExportResult result = new VNXeExportResult();
        result.setHlu(hostLun.getHlu());
        result.setHostId(host.getId());
        _logger.info("Done exporting lun snap: {}", snapId);
        return result;
    }

    public void unexportSnap(String snapId, List<VNXeHostInitiator> initiators) {
        _logger.info("Unexporting snap: {}", snapId);

        for (VNXeHostInitiator initiator : initiators) {
            _logger.info("removing host: {} ", initiator.getName());
        }
        VNXeLunSnap snap = getLunSnapshot(snapId);

        if (snap == null) {
            _logger.info("Could not find snap in the vxne: {}", snapId);
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find snap : " + snapId);
        }

        VNXeLun parentLun = getLun(snap.getLun().getId());
        ;

        Set<String> removingHosts = findHostsByInitiators(initiators);
        if (removingHosts.isEmpty()) {
            _logger.info("No host found.");
            return;
        }
        List<BlockHostAccess> hostAccesses = parentLun.getHostAccess();

        if (hostAccesses == null || hostAccesses.isEmpty()) {
            _logger.info("No block host access found for the snap: {}", snapId);
            return;
        }

        List<BlockHostAccess> changedHostAccessList = new ArrayList<BlockHostAccess>();
        /*
         * we have to detach the snap in order to unexport any host. we need to reattach the snap
         * after the unexport if the snap is still exported to any other hosts.
         */
        boolean needReattach = false;
        for (BlockHostAccess hostAccess : hostAccesses) {
            String hostId = hostAccess.getHost().getId();
            int accessMask = hostAccess.getAccessMask();
            if (removingHosts.contains(hostId)) {
                if (accessMask == HostLUNAccessEnum.BOTH.getValue()) {
                    hostAccess.setAccessMask(HostLUNAccessEnum.PRODUCTION.getValue());
                } else if (accessMask == HostLUNAccessEnum.SNAPSHOT.getValue()) {
                    hostAccess.setAccessMask(HostLUNAccessEnum.NOACCESS.getValue());
                }

            } else if (!needReattach &&
                    (accessMask == HostLUNAccessEnum.BOTH.getValue() ||
                            accessMask == HostLUNAccessEnum.SNAPSHOT.getValue())) {
                needReattach = true;
            }
            changedHostAccessList.add(hostAccess);
        }
        if (changedHostAccessList.isEmpty()) {
            // the removing hosts are not exported
            _logger.info("The unexport hosts were not exported.");
            return;
        }

        if (snap.getIsAttached()) {
            detachLunSnap(snapId);
        }

        LunParam lunParam = new LunParam();
        lunParam.setHostAccess(changedHostAccessList);
        LunModifyParam modifyParam = new LunModifyParam();
        modifyParam.setLunParameters(lunParam);
        int type = parentLun.getType();
        if (type == VNXeLun.LUNTypeEnum.Standalone.getValue()) {
            // if standalone lun
            BlockLunRequests lunReq = new BlockLunRequests(_khClient);
            lunReq.modifyLunSync(modifyParam, parentLun.getStorageResource().getId());
        } else {
            // lun in a lun group
            modifyParam.setLun(new VNXeBase(parentLun.getId()));
            List<LunModifyParam> list = new ArrayList<LunModifyParam>();
            list.add(modifyParam);
            LunGroupModifyParam groupParam = new LunGroupModifyParam();
            groupParam.setLunModify(list);
            LunGroupRequests lunGroupReq = new LunGroupRequests(_khClient);
            lunGroupReq.modifyLunGroupSync(parentLun.getStorageResource().getId(), groupParam);
        }
        if (needReattach) {
            attachLunSnap(snapId);
        }
        _logger.info("Done unexporting lun: {}", snapId);

    }

    /**
     * Get all licenses
     * 
     * @return
     */
    public Map<String, Boolean> getLicenses() {
        LicenseRequest req = new LicenseRequest(_khClient);
        List<VNXeLicense> licenses = req.get();
        Map<String, Boolean> licenseMap = new HashMap<String, Boolean>();
        for (VNXeLicense license : licenses) {
            licenseMap.put(license.getId(), license.getIsValid());
        }
        return licenseMap;
    }

    public boolean isFASTVPEnabled() {
        FastVPRequest req = new FastVPRequest(_khClient);
        List<FastVP> fastVP = req.get();
        if (fastVP != null && !fastVP.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    public VNXeLun getLunByLunGroup(String lunGroupId, String lunName) {
        BlockLunRequests req = new BlockLunRequests(_khClient);
        return req.getByLunGroup(lunGroupId, lunName);
    }

    /**
     * get storage resource Id using file system Id
     * 
     * @param fsId file system Id
     * @return storage resource Id
     */
    private String getStorageResourceId(String fsId) throws VNXeException {
        FileSystemRequest fsRequest = new FileSystemRequest(_khClient, fsId);
        VNXeFileSystem fs = fsRequest.get();
        if (fs == null) {
            _logger.info("Could not find file system in the vxne");
            throw VNXeException.exceptions.vnxeCommandFailed("Could not find file system in the vnxe for: " + fsId);
        }
        return fs.getStorageResource().getId();
    }

    private List<VNXeCifsServer> getCifsServers(String nasServerId) {
        CifsServerListRequest req = new CifsServerListRequest(_khClient);
        return req.getCifsServersForNasServer(nasServerId);

    }

    /**
     * get list host instances based on the endpoints
     * 
     * @param endpoints ipAddress, hostname or subnet
     * @return List of host instances
     * @throws VNXeException
     */
    private List<VNXeBase> getHosts(List<String> endpoints) throws VNXeException {
        List<VNXeBase> hosts = null;
        if (endpoints != null) {
            hosts = new ArrayList<VNXeBase>();
            for (String endpoint : endpoints) {
                String ipAddress = null;
                boolean isSubnet = false;
                String netMask = null;
                boolean isValid = true;
                try {
                    if (VNXeUtils.isHostType(endpoint)) {
                        ipAddress = VNXeUtils.getHostIp(endpoint);
                    } else if (VNXeUtils.isIPV4Type(endpoint) || VNXeUtils.isIPV6Type(endpoint)) {
                        ipAddress = endpoint;
                    } else {
                        // check if subnet
                        String[] ends = endpoint.split("/");
                        if (ends != null && ends.length == 2) {
                            ipAddress = ends[0];
                            endpoint = ipAddress;
                            String mask = ends[1];
                            try {
                                // CIDR format?
                                int cidr = Integer.parseInt(mask);
                                netMask = VNXeUtils.convertCIDRToNetmask(cidr);
                                isSubnet = true;
                            } catch (NumberFormatException e) {
                                if (VNXeUtils.isIPV4Type(mask) || VNXeUtils.isIPV6Type(mask)) {
                                    netMask = mask;
                                    isSubnet = true;
                                } else {
                                    isValid = false;
                                }
                            }
                        } else {
                            isValid = false;

                        }
                    }
                } catch (UnknownHostException e) {
                    _logger.error("Could not resolve the host: " + endpoint);
                    throw VNXeException.exceptions.vnxeCommandFailed("Could not resolve the host: " + endpoint);
                }
                if (!isValid) {
                    _logger.error("Unsupported endpoint type: " + endpoint);
                    throw VNXeException.exceptions.vnxeCommandFailed("Unsupported endpoint type: " + endpoint);
                }
                HostIpPortRequests ipReq = new HostIpPortRequests(_khClient);
                VNXeHostIpPort ipPort = ipReq.getIpPortByIpAddress(ipAddress);
                VNXeBase host = null;
                if (ipPort != null) {
                    // HostIPPort found
                    host = ipPort.getHost();
                    hosts.add(host);
                } else {
                    // create host and ipPort
                    HostListRequest hostReq = new HostListRequest(_khClient);
                    HostCreateParam hostCreateParm = new HostCreateParam();
                    hostCreateParm.setName(endpoint);
                    if (isSubnet) {
                        hostCreateParm.setType(HostTypeEnum.SUBNET.getValue());
                    } else {
                        hostCreateParm.setType(HostTypeEnum.HOSTMANUAL.getValue());
                    }
                    VNXeCommandResult result = hostReq.createHost(hostCreateParm);
                    String hostId = result.getId();
                    if (hostId != null) {
                        HostIpPortRequests ipReq2 = new HostIpPortRequests(_khClient);
                        HostIpPortCreateParam ipCreateParm = new HostIpPortCreateParam();
                        host = new VNXeBase(hostId);
                        ipCreateParm.setHost(host);
                        ipCreateParm.setAddress(ipAddress);
                        if (isSubnet) {
                            ipCreateParm.setSubnetMask(netMask);
                        }
                        ipReq2.createHostIpPort(ipCreateParm);
                        hosts.add(host);
                    }
                }
            }
        }
        return hosts;
    }

    /**
     * given host name and initiators, find/create hosts/initiators in the
     * 
     * @param hosts
     * @return
     * @TODO this is ISCSI only. will add FC case too.
     */
    private VNXeBase prepareHostsForExport(List<VNXeHostInitiator> hostInitiators) {

        String hostId = null;
        Set<VNXeHostInitiator> notExistingInits = new HashSet<VNXeHostInitiator>();
        for (VNXeHostInitiator init : hostInitiators) {
            VNXeHostInitiator existingInit = null;
            HostInitiatorRequest initReq = new HostInitiatorRequest(_khClient);

            existingInit = initReq.getByIQNorWWN(init.getInitiatorId());

            if (existingInit != null && existingInit.getParentHost() != null) {
                hostId = existingInit.getParentHost().getId();

            } else {
                notExistingInits.add(init);
            }
        }
        if (hostId == null) {
            // create host and hostInitiator
            HostListRequest hostReq = new HostListRequest(_khClient);
            HostCreateParam hostCreateParm = new HostCreateParam();
            hostCreateParm.setName(hostInitiators.get(0).getName());

            hostCreateParm.setType(HostTypeEnum.HOSTMANUAL.getValue());

            VNXeCommandResult result = hostReq.createHost(hostCreateParm);
            hostId = result.getId();
        }
        for (VNXeHostInitiator newInit : notExistingInits) {
            HostInitiatorCreateParam initCreateParam = new HostInitiatorCreateParam();
            VNXeBase host = new VNXeBase(hostId);
            initCreateParam.setHost(host);
            if (newInit.getType() == HostInitiatorTypeEnum.INITIATOR_TYPE_ISCSI) {
                initCreateParam.setInitiatorType(HostInitiatorTypeEnum.INITIATOR_TYPE_ISCSI.getValue());
                initCreateParam.setInitiatorWWNorIqn(newInit.getChapUserName());
                initCreateParam.setChapUser(newInit.getChapUserName());
            } else {
                initCreateParam.setInitiatorType(HostInitiatorTypeEnum.INITIATOR_TYPE_FC.getValue());
                initCreateParam.setInitiatorWWNorIqn(newInit.getInitiatorId());
            }
            HostInitiatorRequest req = new HostInitiatorRequest(_khClient);
            req.createHostInitiator(initCreateParam);

        }

        return new VNXeBase(hostId);
    }

    /**
     * Create lun snapshot
     * 
     * @param lunID lun id
     * @param name snapshot name
     * @return VNXeCommandJob
     */
    public VNXeCommandJob createLunSnap(String lunID, String name) {
        _logger.info("creating lun snap:" + lunID);
        LunSnapCreateParam parm = new LunSnapCreateParam();
        parm.setStorageResource(new VNXeBase(lunID));
        parm.setName(name);

        LunSnapRequests req = new LunSnapRequests(_khClient);

        return req.createLunSnap(parm);

    }

    /**
     * Get snapshot by its name
     * 
     * @param name snapshot name
     * @return VNXeLunSnap
     */
    public VNXeLunSnap getLunSnapshotByName(String name) {
        _logger.info("Getting the snapshot {}: ", name);
        LunSnapRequests req = new LunSnapRequests(_khClient);
        return req.getLunSnapByName(name);

    }

    /**
     * Get snapshot by its id
     * 
     * @param name snapshot name
     * @return VNXeLunSnap
     */
    public VNXeLunSnap getLunSnapshot(String id) {
        _logger.info("Getting the snapshot {}: ", id);
        LunSnapRequests req = new LunSnapRequests(_khClient);
        return req.getLunSnap(id);

    }

    /**
     * Delete lun snapshot
     * 
     * @param snapId snapshot VNXe Id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob deleteLunSnap(String snapId) {
        _logger.info("deleting lun snap:" + snapId);
        LunSnapRequests req = new LunSnapRequests(_khClient);
        return req.deleteLunSnap(snapId);
    }

    /**
     * restore lun snapshot
     * 
     * @param snapId VNXe snapshot id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob restoreLunSnap(String snapId) {
        _logger.info("restoring lun snap:", snapId);
        LunSnapRequests req = new LunSnapRequests(_khClient);
        return req.restoreLunSnap(snapId, null);
    }

    /**
     * Attach the snapshot so hosts can access it.
     * Attaching a snapshot makes the snapshot accessible to configured hosts for restoring files and data.
     */
    public VNXeCommandResult attachLunSnap(String snapId) {
        _logger.info("attaching lun snap:", snapId);
        LunSnapRequests req = new LunSnapRequests(_khClient);
        return req.attachLunSnapSync(snapId);
    }

    /**
     * Detach the snapshot so hosts can no longer access it.
     */
    public VNXeCommandResult detachLunSnap(String snapId) {
        _logger.info("detaching lun snap:", snapId);
        LunSnapRequests req = new LunSnapRequests(_khClient);
        return req.detachLunSnapSync(snapId);
    }

    /**
     * Create lun snapshot
     * 
     * @param lunID lun id
     * @param name snapshot name
     * @return VNXeCommandJob
     */
    public VNXeCommandJob createLunGroupSnap(String lunGroupID, String name) {
        _logger.info("creating lun group snap:" + lunGroupID);
        LunSnapCreateParam parm = new LunSnapCreateParam();
        VNXeBase resource = new VNXeBase(lunGroupID);
        parm.setStorageResource(resource);
        parm.setName(name);

        LunGroupSnapRequests req = new LunGroupSnapRequests(_khClient);

        return req.createLunGroupSnap(parm);

    }

    /**
     * Get group snapshot by its name
     * 
     * @param name snapshot name
     * @return VNXeLunSnap
     */
    public VNXeLunGroupSnap getLunGroupSnapshotByName(String name) {
        _logger.info("Getting the snapshot {}: ", name);
        LunGroupSnapRequests req = new LunGroupSnapRequests(_khClient);
        return req.getLunGroupSnapByName(name);

    }

    /**
     * Get group snapshot by its id
     * 
     * @param id group snapshot id
     * @return VNXeLunGroupSnap
     */
    public VNXeLunGroupSnap getLunGroupSnapshot(String id) {
        _logger.info("Getting the snapshot {}: ", id);
        LunGroupSnapRequests req = new LunGroupSnapRequests(_khClient);
        return req.getLunGroupSnap(id);

    }

    /**
     * Delete lun snapshot
     * 
     * @param snapId snapshot VNXe Id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob deleteLunGroupSnap(String snapId) {
        _logger.info("deleting lun snap:" + snapId);
        LunGroupSnapRequests req = new LunGroupSnapRequests(_khClient);
        return req.deleteLunGroupSnap(snapId);
    }

    /**
     * restore lun snapshot
     * 
     * @param snapId VNXe snapshot id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob restoreLunGroupSnap(String snapId) {
        _logger.info("restoring lun group snap:" + snapId);
        LunGroupSnapRequests req = new LunGroupSnapRequests(_khClient);
        return req.restoreLunGroupSnap(snapId, null);
    }

    /**
     * Attach the snapshot so hosts can access it.
     * Attaching a snapshot makes the snapshot accessible to configured hosts for restoring files and data.
     */
    public VNXeCommandJob attachLunGroupSnap(String snapId) {
        _logger.info("attaching lun group snap:", snapId);
        LunGroupSnapRequests req = new LunGroupSnapRequests(_khClient);
        return req.attachLunGroupSnap(snapId);
    }

    /**
     * Detach the snapshot so hosts can no longer access it.
     */
    public VNXeCommandJob detachLunGroupSnap(String snapId) {
        _logger.info("detaching lun group snap:", snapId);
        LunGroupSnapRequests req = new LunGroupSnapRequests(_khClient);
        return req.detachLunGroupSnap(snapId);
    }

    /**
     * find host ids based on passed in host initiator's iqn or wwn
     * 
     * @param hostInits
     * @return
     */
    private Set<String> findHostsByInitiators(List<VNXeHostInitiator> hostInits) {
        Set<String> hosts = new HashSet<String>();
        for (VNXeHostInitiator init : hostInits) {
            HostInitiatorRequest initReq = new HostInitiatorRequest(_khClient);
            VNXeHostInitiator existingInit = initReq.getByIQNorWWN(init.getInitiatorId());

            if (existingInit != null && existingInit.getParentHost() != null) {
                String hostId = existingInit.getParentHost().getId();
                hosts.add(hostId);

            }
        }
        return hosts;
    }

    /**
     * Get all the luns in the storage system
     * 
     * @return List of luns
     */
    public List<VNXeLun> getAllLuns() {
        BlockLunRequests req = new BlockLunRequests(_khClient);
        return req.get();
    }

    /**
     * Get all the file systems in the storage system
     * 
     * @return List of file systems
     */
    public List<VNXeFileSystem> getAllFileSystems() {
        FileSystemListRequest req = new FileSystemListRequest(_khClient);
        return req.get();
    }

    /**
     * Get all the NFS shares
     * 
     * @return List of all NFS shares
     */
    public List<VNXeNfsShare> getAllNfsShares() {
        NfsShareRequests req = new NfsShareRequests(_khClient);
        return req.get();
    }

    public DiskGroup getDiskGroup(String diskGroupId) {
        DiskGroupRequests req = new DiskGroupRequests(_khClient, diskGroupId);
        return req.get();
    }

    /**
     * Get all the CIFS shares
     * 
     * @return List of all CIFS shares
     */
    public List<VNXeCifsShare> getAllCifsShares() {
        CifsShareRequests req = new CifsShareRequests(_khClient);
        return req.get();
    }

}
