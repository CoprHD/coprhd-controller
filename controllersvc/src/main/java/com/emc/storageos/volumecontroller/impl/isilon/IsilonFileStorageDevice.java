/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonNFSACL;
import com.emc.storageos.isilon.restapi.IsilonNFSACL.Acl;
import com.emc.storageos.isilon.restapi.IsilonSMBShare;
import com.emc.storageos.isilon.restapi.IsilonSMBShare.Permission;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

/**
 * Isilon specific file controller implementation.
 */
public class IsilonFileStorageDevice implements FileStorageDevice {
    private static final Logger _log = LoggerFactory.getLogger(IsilonFileStorageDevice.class);

    private static final String IFS_ROOT = "/ifs";
    private static final String VIPR_DIR = "vipr";

    private static final String QUOTA = "quota";

    private static final String EXPORT_OP_NAME = "Snapshot Export";
    private static final String SHARE_OP_NAME = "Snapshot Share";

    private IsilonApiFactory _factory;
    private HashMap<String, String> configinfo;

    private DbClient _dbClient;
    
    private CustomConfigHandler customConfigHandler;

    /**
     * Set Isilon API factory
     * 
     * @param factory
     */
    public void setIsilonApiFactory(IsilonApiFactory factory) {
        _factory = factory;
    }

    /**
     * Get Isilon config info
     * 
     * @param factory
     */
    public HashMap<String, String> getConfiginfo() {
        return configinfo;
    }

    /**
     * Set Isilon config info
     * 
     * @param factory
     */
    public void setConfiginfo(HashMap<String, String> configinfo) {
        this.configinfo = configinfo;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }
    
    /**
     * Set the controller config info
     * @return
     */
	public void setCustomConfigHandler(CustomConfigHandler customConfigHandler) {
		this.customConfigHandler = customConfigHandler;
	}

    /**
     * Get isilon device represented by the StorageDevice
     * 
     * @param device StorageDevice object
     * @return IsilonApi object
     * @throws IsilonException
     */
    IsilonApi getIsilonDevice(StorageSystem device) throws IsilonException {
        IsilonApi isilonAPI;
        URI deviceURI;
        try {
            deviceURI = new URI("https", null, device.getIpAddress(), device.getPortNumber(), "/", null, null);
        } catch (URISyntaxException ex) {
            throw IsilonException.exceptions.errorCreatingServerURL(device.getIpAddress(), device.getPortNumber(), ex);
        }
        if (device.getUsername() != null && !device.getUsername().isEmpty()) {
            isilonAPI = _factory.getRESTClient(deviceURI, device.getUsername(), device.getPassword());
        } else {
            isilonAPI = _factory.getRESTClient(deviceURI);
        }

        return isilonAPI;

    }

    /**
     * create isilon snapshot path from file share path and snapshot name
     * 
     * @param fsMountPath mount path of the fileshare
     * @param name snapshot name
     * @return String
     */
    private String getSnapshotPath(String fsMountPath, String name) {
        String prefix = IFS_ROOT + "/" + VIPR_DIR;
        return String.format("%1$s/.snapshot/%2$s/%3$s%4$s",
                IFS_ROOT, name, VIPR_DIR, fsMountPath.substring(prefix.length()));
    }

    /**
     * Delete isilon export
     * 
     * @param isi IsilonApi object
     * @param exportMap exports to be deleted
     * @throws IsilonException
     */
    private void isiDeleteExports(IsilonApi isi, FileDeviceInputOutput args)
            throws IsilonException {
    	
    	FSExportMap exportMap = null;
    	
    	if (args.getFileOperation()) {
    		FileShare fileObj = args.getFs();
    		if (fileObj != null) {
    			exportMap = fileObj.getFsExports();
    		}
    	} else {
    		Snapshot snap = args.getFileSnapshot();
    		if (snap != null) {
    			exportMap = snap.getFsExports();
    		}
    	}
    	
        if (exportMap == null || exportMap.isEmpty()) {
            return;
        }
        
        String zoneName = getZoneName(args.getvNAS());
        
        Set<String> deletedExports = new HashSet<String>();
        Iterator<Map.Entry<String, FileExport>> it = exportMap.entrySet().iterator();
        try {
            while (it.hasNext()) {
                Map.Entry<String, FileExport> entry = it.next();
                String key = entry.getKey();
                FileExport fsExport = entry.getValue();
                if (zoneName != null) {
                	isi.deleteExport(fsExport.getIsilonId(), zoneName);
                } else {
                	isi.deleteExport(fsExport.getIsilonId());
                }
                
                // Safe removal from the backing map. Can not do this through iterator since this does not track changes and is not
                // reflected in the database.
                deletedExports.add(key);
            }
        } finally {
            // remove exports from the map in database.
            for (String key : deletedExports) {
                exportMap.remove(key);
            }
        }
    }

    /**
     * Deleting a file share: - deletes existing exports and smb shares for the file share
     * (only created by storage os)
     * 
     * @param isi IsilonApi object
     * @param args FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteFS(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {

        /*
         * Delete the exports for this file system
         */
        isiDeleteExports(isi, args);

        /*
         * Delete the SMB shares for this file system
         */
        isiDeleteShares(isi, args);

        /*
         * Delete quota on this path, if one exists
         */
        if (args.getFsExtensions() != null && args.getFsExtensions().containsKey(QUOTA)) {
            isi.deleteQuota(args.getFsExtensions().get(QUOTA));
            // delete from extensions
            args.getFsExtensions().remove(QUOTA);
        }

        /*
         * Delete the snapshots for this file system
         */
        isiDeleteSnapshots(isi, args);

        /*
         * Delete quota dirs, if one exists
         */
        isiDeleteQuotaDirs(isi, args);

        /**
         * Delete the directory associated with the file share.
         */
        isi.deleteDir(args.getFsMountPath(), true);
    }

    /**
     * Deleting snapshots: - deletes snapshots of a file system
     * 
     * @param isi IsilonApi object
     * @param args FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteSnapshots(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {
    	
        List<URI> snapURIList = _dbClient.queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(args.getFsId()));
        for (URI snapURI : snapURIList) {
            Snapshot snap = _dbClient.queryObject(Snapshot.class, snapURI);
            if (snap != null && (!snap.getInactive())) {
                args.addSnapshot(snap);
                isiDeleteSnapshot(isi, args);
            }
        }
    }

    /**
     * Deleting a snapshot: - deletes existing exports and smb shares for the snapshot (only
     * created by storage os)
     * 
     * @param isi IsilonApi object
     * @param args FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteSnapshot(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {

    	args.setFileOperation(false);
        /*
         * Delete the exports first
         */
        isiDeleteExports(isi, args);

        /*
         * Delete the SMB shares
         */
        isiDeleteShares(isi, args);

        /**
         * Delete the snapshot.
         */
        if (args.getSnapshotExtensions() != null && args.getSnapshotExtensions().containsKey("id")) {
            isi.deleteSnapshot(args.getSnapshotExtensions().get("id"));
        }
    }

    /**
     * Deleting Quota dirs: - deletes quota dirs of a file system
     * 
     * @param isi IsilonApi object
     * @param args FileDeviceInputOutput
     * @throws IsilonException
     */
    private void isiDeleteQuotaDirs(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {
        List<URI> quotaDirURIList = _dbClient.queryByConstraint(ContainmentConstraint.Factory.getQuotaDirectoryConstraint(args.getFsId()));
        for (URI quotaDirURI : quotaDirURIList) {
            QuotaDirectory quotaDir = _dbClient.queryObject(QuotaDirectory.class, quotaDirURI);
            if (quotaDir != null && (!quotaDir.getInactive())) {
                if (quotaDir.getExtensions() != null && quotaDir.getExtensions().containsKey(QUOTA)) {
                    String quotaId = quotaDir.getExtensions().get(QUOTA);
                    _log.info("IsilonFileStorageDevice isiDeleteQuotaDirs , Delete Quota {}", quotaId);
                    isi.deleteQuota(quotaId);
                    // delete from quota extensions
                    quotaDir.getExtensions().remove(QUOTA);

                    // delete directory for the Quota Directory
                    String quotaDirPath = args.getFsMountPath() + "/" + quotaDir.getName();
                    isi.deleteDir(quotaDirPath, true);
                }
            }
        }

    }

    /**
     * Create/modify Isilon SMB share.
     * 
     * @param isi
     * @param args
     * @param smbFileShare
     * @throws IsilonException
     */
    private void isiShare(IsilonApi isi, FileDeviceInputOutput args, SMBFileShare smbFileShare) throws IsilonException {

        IsilonSMBShare isilonSMBShare = new IsilonSMBShare(
                smbFileShare.getName(), smbFileShare.getPath(),
                smbFileShare.getDescription());

        // Check if this is a new share or update of the existing share
        SMBShareMap smbShareMap = args.getFileObjShares();
        SMBFileShare existingShare = (smbShareMap == null) ? null : smbShareMap.get(smbFileShare.getName());

        String shareId;
        
    	String zoneName = getZoneName(args.getvNAS());
    	
        if (existingShare != null) {
            shareId = existingShare.getNativeId();
            // modify share
            if (zoneName != null) {
            	isi.modifyShare(shareId, zoneName, isilonSMBShare);
            } else {
            	isi.modifyShare(shareId, isilonSMBShare);
            }
            
        } else {
            // new share
        	if(zoneName != null) {
        		_log.debug("Share will be created in zone: {}", zoneName);
        		shareId = isi.createShare(isilonSMBShare, zoneName);
        	} else {
        		shareId = isi.createShare(isilonSMBShare);
        	}
        }
        smbFileShare.setNativeId(shareId);

        // Set Mount Point
        smbFileShare.setMountPoint(smbFileShare.getStoragePortNetworkId(), smbFileShare.getStoragePortName(),
                smbFileShare.getName());
        // int file share map
        if (args.getFileObjShares() == null) {
            args.initFileObjShares();
        }
        args.getFileObjShares().put(smbFileShare.getName(), smbFileShare);
    }

    private void isiDeleteShare(IsilonApi isi, FileDeviceInputOutput args, SMBFileShare smbFileShare) throws IsilonException {
    	
    	SMBShareMap currentShares = args.getFileObjShares();
        // Do nothing if there are no shares
        if (currentShares == null || smbFileShare == null) {
            return;
        }
        
        SMBFileShare fileShare = currentShares.get(smbFileShare.getName());
        if (fileShare != null) {
        	
        	String nativeId = fileShare.getNativeId();
        	String zoneName = getZoneName(args.getvNAS());
        	
            if (zoneName != null) {
            	isi.deleteShare(nativeId, zoneName);
            } else {
            	isi.deleteShare(nativeId);
            }
            
            currentShares.remove(smbFileShare.getName());
        }
    }

    private void isiDeleteShares(IsilonApi isi, FileDeviceInputOutput args) throws IsilonException {
    	
    	SMBShareMap currentShares = null;
    	if (args.getFileOperation()) {
    		FileShare fileObj = args.getFs();
    		if (fileObj != null) {
    			currentShares = fileObj.getSMBFileShares();
    		}
    	} else {
    		Snapshot snap = args.getFileSnapshot();
    		if (snap != null) {
    			currentShares = snap.getSMBFileShares();
    		}
    	}
        if (currentShares == null || currentShares.isEmpty()) {
            return;
        }

        Set<String> deletedShares = new HashSet<String>();
        Iterator<Map.Entry<String, SMBFileShare>> it = currentShares.entrySet().iterator();
        
        String zoneName = getZoneName(args.getvNAS());

        try {
            while (it.hasNext()) {
                Map.Entry<String, SMBFileShare> entry = it.next();
                String key = entry.getKey();
                SMBFileShare smbFileShare = entry.getValue();
                if (zoneName != null) {
                	isi.deleteShare(smbFileShare.getNativeId(), zoneName);
                } else {
                	isi.deleteShare(smbFileShare.getNativeId());
                }
                
                // Safe removal from the backing map. Can not do this through iterator since this does not track changes and is not
                // reflected in the database.
                deletedShares.add(key);
            }
        } finally {
            // remove shares from the map in database.
            for (String key : deletedShares) {
                currentShares.remove(key);
            }
        }

    }

    /**
     * Create isilon exports
     * 
     * @param isi IsilonApi object
     * @param args FileDeviceInputOutput object
     * @param exports new exports to add
     * @throws IsilonException
     */
    private void isiExport(IsilonApi isi, FileDeviceInputOutput args, List<FileExport> exports) throws IsilonException {

        // process and export each NFSExport independently.
        for (FileExport fileExport : exports) {

            // create and set IsilonExport instance from NFSExport
            String permissions = fileExport.getPermissions();
            String securityType = fileExport.getSecurityType();
            List<String> securityTypes = Arrays.asList(securityType);
            String root_user = fileExport.getRootUserMapping();
            String storagePortName = fileExport.getStoragePortName();
            String storagePort = fileExport.getStoragePort();
            String protocol = fileExport.getProtocol();
            String path = fileExport.getPath();
            String mountPath = fileExport.getMountPath();
            String comments = fileExport.getComments();
            String subDirectory = fileExport.getSubDirectory();

            // Validate parameters for permissions and root user mapping.
            if (permissions.equals(FileShareExport.Permissions.root.name()) &&
                    !root_user.equals("root")) {
                String msg = "The root_user mapping is not set to root but the permission is.";
                _log.error(msg);
                throw IsilonException.exceptions.invalidParameters();
            }

            IsilonExport newIsilonExport = setIsilonExport(fileExport, permissions, securityTypes, root_user, mountPath, comments);

            _log.info("IsilonExport:" + fileExport.getClients() + ":" + fileExport.getStoragePortName()
                    + ":" + fileExport.getStoragePort() + ":" + fileExport.getRootUserMapping()
                    + ":" + fileExport.getPermissions() + ":" + fileExport.getProtocol()
                    + ":" + fileExport.getSecurityType() + ":" + fileExport.getMountPoint()
                    + ":" + fileExport.getPath() + ":" + fileExport.getSubDirectory() + ":" + fileExport.getComments());
            // Initialize exports map, if its not already initialized
            if (args.getFileObjExports() == null) {
                args.initFileObjExports();
            }
            
            String accessZoneName = getZoneName(args.getvNAS());

            // Create/update export in Isilon.
            String exportKey = fileExport.getFileExportKey();
            // If export with the given key does not exist, we create a new export in Isilon and add it to the exports map.
            // In the other case, when export with a given key already exists in Isilon, we need to overwrite endpoints in the current
            // export with endpoints in the
            // new export.
            FileExport fExport = args.getFileObjExports().get(exportKey);

            // check Isilon to verify if export does not exist.
            IsilonExport currentIsilonExport = null;
            if (fExport != null) {
            	if (accessZoneName != null) {
            		currentIsilonExport = isi.getExport(fExport.getIsilonId(), accessZoneName);
            	} else {
            		currentIsilonExport = isi.getExport(fExport.getIsilonId());
            	}
                
            }
            if (fExport == null || currentIsilonExport == null) {
                // There is no Isilon export. Create Isilon export and set it the map.
            	String id = null;
                if(accessZoneName != null) {
                	_log.debug("Export will be created in zone: {}", accessZoneName);
                	id = isi.createExport(newIsilonExport, accessZoneName);
                } else {
                	id = isi.createExport(newIsilonExport);
                }

                // set file export data and add it to the export map
                fExport = new FileExport(newIsilonExport.getClients(), storagePortName, mountPath, securityType,
                        permissions, root_user, protocol, storagePort, path, mountPath, subDirectory, comments);
                fExport.setIsilonId(id);
            } else {
                // There is export in Isilon with the given id.
                // Overwrite this export with a new set of clients.
                // We overwrite only clients element in exports. Isilon API does not use read_only_clients, read_write_clients or
                // root_clients.
                List<String> newClients = newIsilonExport.getClients();
                newIsilonExport.setClients(new ArrayList<String>(newClients));

                // modify current export in isilon.
                if (accessZoneName != null) {
                	isi.modifyExport(fExport.getIsilonId(), accessZoneName, newIsilonExport);
                } else {
                	isi.modifyExport(fExport.getIsilonId(), newIsilonExport);
                }

                // update clients
                fExport.setClients(newIsilonExport.getClients());
            }

            args.getFileObjExports().put(exportKey, fExport);
        }
    }

    private IsilonExport setIsilonExport(FileExport fileExport, String permissions, List<String> securityType, String root_user,
            String mountPath, String comments) {

        IsilonExport newIsilonExport = new IsilonExport();
        newIsilonExport.addPath(mountPath);
        if (comments == null) {
            comments = "";
        }
        newIsilonExport.setComment(comments);

        // Empty list of clients means --- all clients.
        newIsilonExport.addClients(fileExport.getClients());

        // set security type
        // Need to use "unix" instead of "sys" . Isilon requires "unix", not "sys".
        List<String> securityFlavors = new ArrayList<String>();
        for (String secType : securityType) {
            if (secType.equals(FileShareExport.SecurityTypes.sys.name())) {
                securityFlavors.add("unix");
            } else {
                securityFlavors.add(secType);
            }
        }
        newIsilonExport.setSecurityFlavors(new ArrayList<String>(securityFlavors));

        // set permission and add clients (endpoints) to the right group
        // we need to set/reset read_only and map_all to support case when list of clients in the request is empty.
        if (permissions.equals(FileShareExport.Permissions.ro.name())) {
            newIsilonExport.addReadOnlyClients(fileExport.getClients());
            newIsilonExport.setReadOnly();
            newIsilonExport.setMapRoot(root_user);
        } else if (permissions.equals(FileShareExport.Permissions.rw.name())) {
            newIsilonExport.addReadWriteClients(fileExport.getClients());
            newIsilonExport.resetReadOnly();
            newIsilonExport.setMapRoot(root_user);
        } else if (permissions.equals(FileShareExport.Permissions.root.name())) {
            // Do not set root_user. Isilon api allows only one of map_root or map_all to be set.
            newIsilonExport.addRootClients(fileExport.getClients());
            newIsilonExport.resetReadOnly();
            newIsilonExport.setMapAll("root");
        }

        return newIsilonExport;
    }

    private IsilonExport setIsilonExport(ExportRule expRule) {

        // String permissions, List<String> securityType, String root_user, String mountPath, String comments) {

        _log.info("setIsilonExport called with {}", expRule.toString());
        String mountPath = expRule.getExportPath();
        String comments = "";
        String secType = expRule.getSecFlavor();
        String root_user = expRule.getAnon();

        IsilonExport newIsilonExport = new IsilonExport();
        newIsilonExport.addPath(mountPath);
        newIsilonExport.setComment(comments);

        int roHosts = 0;
        int rwHosts = 0;
        int rootHosts = 0;

        // Empty list of clients means --- all clients.
        if (expRule.getReadOnlyHosts() != null) {
            newIsilonExport.addClients(new ArrayList(expRule.getReadOnlyHosts()));
            roHosts = expRule.getReadOnlyHosts().size();
            newIsilonExport.addReadOnlyClients(new ArrayList(expRule.getReadOnlyHosts()));
        }

        if (expRule.getReadWriteHosts() != null) {
            newIsilonExport.addClients(new ArrayList(expRule.getReadWriteHosts()));
            rwHosts = expRule.getReadWriteHosts().size();
            newIsilonExport.addReadWriteClients(new ArrayList(expRule.getReadWriteHosts()));
        }

        if (expRule.getRootHosts() != null) {
            newIsilonExport.addClients(new ArrayList(expRule.getRootHosts()));
            rootHosts = expRule.getRootHosts().size();
            newIsilonExport.addRootClients(new ArrayList(expRule.getRootHosts()));
        }

        // set security type
        // Need to use "unix" instead of "sys" . Isilon requires "unix", not "sys".

        if (secType.equals(FileShareExport.SecurityTypes.sys.name())) {
            secType = "unix";
        }

        ArrayList<String> secFlavors = new ArrayList<>();
        secFlavors.add(secType);
        newIsilonExport.setSecurityFlavors(secFlavors);

        if (roHosts > 0 && rwHosts == 0 && rootHosts == 0) {
            // RO Export
            newIsilonExport.setReadOnly();
            newIsilonExport.setMapRoot(root_user);
        } else if (roHosts == 0 && rwHosts > 0 && rootHosts == 0) {
            // RW Export
            newIsilonExport.resetReadOnly();
            newIsilonExport.setMapRoot(root_user);
        } else if (roHosts == 0 && rootHosts > 0) {
            // ROOT export
            newIsilonExport.resetReadOnly();
            newIsilonExport.setMapAll("root");
        }

        _log.info("setIsilonExport completed with creating {}", newIsilonExport.toString());
        return newIsilonExport;
    }

    /**
     * Delete exports
     * 
     * @param isi IsilonApi object to be used for communicating to the isilon system
     * @param currentExports Current exports map
     * @param exports exports to be deleted
     * @throws ControllerException
     * @throws IsilonException
     */
    private void isiUnexport(IsilonApi isi, FileDeviceInputOutput args, List<FileExport> exports)
            throws ControllerException, IsilonException {
    	
    	FSExportMap currentExports = args.getFileObjExports();
        // Do nothing if there are no exports
        if (currentExports == null || exports == null || exports.isEmpty()) {
            return;
        }

        for (FileExport fileExport : exports) {
            String key = fileExport.getFileExportKey(); // isiExportKey(req);
            String id = null;

            FileExport fExport = currentExports.get(key);
            if (fExport != null) {
                id = fExport.getIsilonId();
            }
            if (id != null) {
            	String zoneName = getZoneName(args.getvNAS());
            	if (zoneName != null) {
            		isi.deleteExport(id, zoneName);
            	} else {
            		isi.deleteExport(id);
            	}
                
                currentExports.remove(key);
            }
        }
    }

    private void isiExpandFS(IsilonApi isi, String quotaId, Long capacity) throws ControllerException, IsilonException {

        // get quota from Isilon and check that requested capacity is larger than the current capacity
        IsilonSmartQuota quota = isi.getQuota(quotaId);
        Long hard = quota.getThresholds().getHard();
        if (capacity.compareTo(hard) < 0) {
            String msg = String
                    .format("In expanding Isilon FS requested capacity is less than current capacity of file system. Path: %s, current capacity: %d",
                            quota.getPath(), quota.getThresholds().getHard());
            _log.error(msg);
            throw IsilonException.exceptions.expandFsFailedinvalidParameters(quota.getPath(),
                    quota.getThresholds().getHard());
        }
        // Modify quota for file system.
        IsilonSmartQuota expandedQuota = new IsilonSmartQuota(capacity);
        isi.modifyQuota(quotaId, expandedQuota);
    }

    @Override
    public BiosCommandResult doCreateFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doCreateFS {} with name {} - start", args.getFsId(), args.getFsName());
            IsilonApi isi = getIsilonDevice(storage);

            String projName = null;
            String tenantOrg = null;
            VirtualNAS vNAS = args.getvNAS();
            String vNASPath = null;
            
            if(vNAS != null) {
            	vNASPath = vNAS.getBaseDirPath();
            	_log.info("vNAS base directory path: {}", vNASPath);
            }

            if (args.getProject() != null) {
                projName = args.getProjectNameWithNoSpecialCharacters();
            }
            if (args.getTenantOrg() != null) {
                tenantOrg = args.getTenantNameWithNoSpecialCharacters();
            }
            
            String usePhysicalNASForProvisioning = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.USE_PHYSICAL_NAS_FOR_PROVISIONING, "isilon", null);
            _log.info("Use System access zone to provision filesystem? {}", usePhysicalNASForProvisioning);

            String mountPath = null;
            // Update the mount path as required
            if(vNASPath != null && !vNASPath.trim().isEmpty()) {
	        	if (projName != null && tenantOrg != null) {
		            mountPath = String.format("%1$s/%2$s/%3$s/%4$s/%5$s", vNASPath,
		                    args.getVPoolNameWithNoSpecialCharacters(), args.getTenantNameWithNoSpecialCharacters(),
		                    args.getProjectNameWithNoSpecialCharacters(), args.getFsName());
		        } else {
		            mountPath = String.format("%1$s/%2$s/%3$s", vNASPath,
		                    args.getVPoolNameWithNoSpecialCharacters(), args.getFsName());
		        }
	        } else if(Boolean.valueOf(usePhysicalNASForProvisioning)) {
		        if (projName != null && tenantOrg != null) {
		            mountPath = String.format("%1$s/%2$s/%3$s/%4$s/%5$s/%6$s", IFS_ROOT, VIPR_DIR,
		                    args.getVPoolNameWithNoSpecialCharacters(), args.getTenantNameWithNoSpecialCharacters(),
		                    args.getProjectNameWithNoSpecialCharacters(), args.getFsName());
		        } else {
		            mountPath = String.format("%1$s/%2$s/%3$s/%4$s", IFS_ROOT, VIPR_DIR,
		                    args.getVPoolNameWithNoSpecialCharacters(), args.getFsName());
		        }
	        } else {
	        	_log.error("No suitable access zone found for provisioning. Provisioning on System access zone is disabled");
	        	throw DeviceControllerException.exceptions.createFileSystemOnPhysicalNASDisabled();
	        }

            _log.info("Mount path to mount the Isilon File System {}", mountPath);
            args.setFsMountPath(mountPath);

            args.setFsNativeGuid(args.getFsMountPath());
            args.setFsNativeId(args.getFsMountPath());
            args.setFsPath(args.getFsMountPath());
            // create directory for the file share
            isi.createDir(args.getFsMountPath(), true);

            boolean bThresholdsIncludeOverhead = true;
            boolean bIncludeSnapshots = true;

            if (configinfo != null && configinfo.containsKey("thresholdsIncludeOverhead")) {
                bThresholdsIncludeOverhead = Boolean.parseBoolean(configinfo.get("thresholdsIncludeOverhead"));
            }
            if (configinfo != null && configinfo.containsKey("includeSnapshots")) {
                bIncludeSnapshots = Boolean.parseBoolean(configinfo.get("includeSnapshots"));
            }

            // set quota - save the quota id to extensions
            String qid = isi.createQuota(args.getFsMountPath(), bThresholdsIncludeOverhead,
                    bIncludeSnapshots, args.getFsCapacity());

            if (args.getFsExtensions() == null) {
                args.initFsExtensions();
            }
            args.getFsExtensions().put(QUOTA, qid);

            // set protection level
            // String protection = args.getFSProtectionLevel();
            // Call isilon api to set protection level
            // TODO

            _log.info("IsilonFileStorageDevice doCreateFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doCreateFS failed.", e);
            // rollback this operation to prevent partial result of file share create
            BiosCommandResult rollbackResult = doDeleteFS(storage, args);
            if (rollbackResult.isCommandSuccess()) {
                _log.info("IsilonFileStorageDevice doCreateFS {} - rollback completed.", args.getFsId());
            } else {
                _log.error("IsilonFileStorageDevice doCreateFS {} - rollback failed,  message: {} .", args.getFsId(),
                        rollbackResult.getMessage());
            }

            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doDeleteFS {} - start", args.getFsId());
            IsilonApi isi = getIsilonDevice(storage);
            isiDeleteFS(isi, args);
            _log.info("IsilonFileStorageDevice doDeleteFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteFS failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public boolean doCheckFSExists(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        _log.info("checking file system existence on array: ", args.getFsName());
        boolean isFSExists = true; // setting true by default for safer side
        try {
            IsilonApi isi = getIsilonDevice(storage);
            isFSExists = isi.existsDir(args.getFsMountPath());
        } catch (IsilonException e) {
            _log.error("Querying FS failed", e);
        }
        return isFSExists;
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doExpandFS {} - start", args.getFsId());
            IsilonApi isi = getIsilonDevice(storage);
            String quotaId = null;
            if (args.getFsExtensions() != null && args.getFsExtensions().get(QUOTA) != null) {
                quotaId = args.getFsExtensions().get(QUOTA);
            } else {
                final ServiceError serviceError = DeviceControllerErrors.isilon
                        .doExpandFSFailed(args.getFsId());
                _log.error(serviceError.getMessage());
                return BiosCommandResult.createErrorResult(serviceError);
            }

            Long newCapacity = args.getNewFSCapacity();   // new capacity
            isiExpandFS(isi, quotaId, newCapacity);
            _log.info("IsilonFileStorageDevice doExpandFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doExpandFS failed.", e);
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception e) {
            _log.error("doExpandFS failed.", e);
            // TODO convert this to a ServiceError and create/or reuse a service
            // code
            ServiceError serviceError = DeviceControllerErrors.isilon.unableToExpandFileSystem();
            return BiosCommandResult.createErrorResult(serviceError);
        }
    }

    @Override
    public BiosCommandResult doExport(StorageSystem storage, FileDeviceInputOutput args,
            List<FileExport> exportList) throws ControllerException {

        // Snapshot Export operation is not supported by ISILON.
        if (args.getFileOperation() == false) {
            return BiosCommandResult.createErrorResult(DeviceControllerErrors.isilon.unSupportedOperation(EXPORT_OP_NAME));
        }

        try {
            _log.info("IsilonFileStorageDevice doExport {} - start", args.getFileObjId());
            IsilonApi isi = getIsilonDevice(storage);
            isiExport(isi, args, exportList);
            _log.info("IsilonFileStorageDevice doExport {} - complete", args.getFileObjId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doExport failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }

    }

    @Override
    public BiosCommandResult doUnexport(StorageSystem storage, FileDeviceInputOutput args,
            List<FileExport> exportList) throws ControllerException {

        try {
            _log.info("IsilonFileStorageDevice doUnexport: {} - start", args.getFileObjId());
            IsilonApi isi = getIsilonDevice(storage);
            isiUnexport(isi, args, exportList);
            _log.info("IsilonFileStorageDevice doUnexport {} - complete", args.getFileObjId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doUnexport failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doShare(StorageSystem storage, FileDeviceInputOutput args,
            SMBFileShare smbFileShare) throws ControllerException {
        // Snapshot Share operation is not supported by ISILON.
        if (args.getFileOperation() == false) {
            return BiosCommandResult.createErrorResult(DeviceControllerErrors.isilon.unSupportedOperation(SHARE_OP_NAME));
        }

        try {
            _log.info("IsilonFileStorageDevice doShare() - start");
            IsilonApi isi = getIsilonDevice(storage);
            isiShare(isi, args, smbFileShare);
            _log.info("IsilonFileStorageDevice doShare() - complete");
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doShare failed.", e);
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception e) {
            _log.error("doShare failed.", e);
            // TODO convert this to a ServiceError and create/or reuse a service
            // code
            ServiceError serviceError = DeviceControllerErrors.isilon.unableToCreateFileShare();
            return BiosCommandResult.createErrorResult(serviceError);
        }
    }

    @Override
    public BiosCommandResult doDeleteShare(StorageSystem storage, FileDeviceInputOutput args,
            SMBFileShare smbFileShare) throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doDeleteShare: {} - start");
            IsilonApi isi = getIsilonDevice(storage);
            isiDeleteShare(isi, args, smbFileShare);
            _log.info("IsilonFileStorageDevice doDeleteShare {} - complete");
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteShare failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doDeleteShares(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doDeleteShares: {} - start");
            IsilonApi isi = getIsilonDevice(storage);
            isiDeleteShares(isi, args);
            _log.info("IsilonFileStorageDevice doDeleteShares {} - complete");
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteShares failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doModifyFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        return null; // To change body of implemented methods use File |
        // Settings | File Templates.
    }

    @Override
    public BiosCommandResult doSnapshotFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doSnapshotFS {} {} - start", args.getSnapshotId(), args.getSnapshotName());
            IsilonApi isi = getIsilonDevice(storage);
            // To Do - add timestamp for uniqueness
            String snapId = isi.createSnapshot(args.getSnapshotName(), args.getFsMountPath());
            if (args.getSnapshotExtensions() == null) {
                args.initSnapshotExtensions();
            }
            args.getSnapshotExtensions().put("id", snapId);
            args.setSnapNativeId(snapId);
            String path = getSnapshotPath(args.getFsMountPath(), args.getSnapshotName());
            args.setSnapshotMountPath(path);
            args.setSnapshotPath(path);
            _log.info("IsilonFileStorageDevice doSnapshotFS {} - complete", args.getSnapshotId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doSnapshotFS failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doRestoreFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        String opName = ResourceOperationTypeEnum.RESTORE_FILE_SNAPSHOT.getName();
        ServiceError serviceError = IsilonException.errors.jobFailed(opName);
        result.error(serviceError);
        return result;
    }

    @Override
    public BiosCommandResult doDeleteSnapshot(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("IsilonFileStorageDevice doDeleteSnapshot {} - start", args.getSnapshotId());
            IsilonApi isi = getIsilonDevice(storage);
            isiDeleteSnapshot(isi, args);
            _log.info("IsilonFileStorageDevice doDeleteSnapshot {} - complete", args.getSnapshotId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteSnapshot failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    // Get FS snapshot list from the array
    @Override
    public BiosCommandResult getFSSnapshotList(StorageSystem storage,
            FileDeviceInputOutput args, List<String> snapshots)
            throws ControllerException {

        // TODO: Implement method
        String op = "getFSSnapshotList";
        String devType = storage.getSystemType();
        BiosCommandResult result = BiosCommandResult
                .createErrorResult(DeviceControllerException.errors.unsupportedOperationOnDevType(op, devType));

        return result;

    }

    @Override
    public void doConnect(StorageSystem storage) throws ControllerException {
        try {
            _log.info("doConnect {} - start", storage.getId());
            IsilonApi isi = getIsilonDevice(storage);
            isi.getClusterInfo();
            String msg = String.format("doConnect %1$s - complete", storage.getId());
            _log.info(msg);
        } catch (IsilonException e) {
            _log.error("doConnect failed.", e);
            throw DeviceControllerException.exceptions.connectStorageFailed(e);
        }
    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        // not much to do here ... just reply success
    }

    @Override
    public BiosCommandResult getPhysicalInventory(StorageSystem storage) {
        ServiceError serviceError = DeviceControllerErrors.isilon.unableToGetPhysicalInventory(storage.getId());
        return BiosCommandResult.createErrorResult(serviceError);
    }

    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory quotaDir) throws ControllerException {

        // Get Parent FS mount path
        // Get Quota Directory Name
        // Get Quota Size
        // Call create Directory
        // Call create Quota (Aways use that quota for updating the size)

        String fsMountPath = args.getFsMountPath();
        Long qDirSize = quotaDir.getSize();
        String qDirPath = fsMountPath + "/" + quotaDir.getName();
        _log.info("IsilonFileStorageDevice doCreateQuotaDirectory {} with size {} - start", qDirPath, qDirSize);
        try {
            IsilonApi isi = getIsilonDevice(storage);
            // create directory for the file share
            isi.createDir(qDirPath, true);

            boolean bThresholdsIncludeOverhead = true;
            boolean bIncludeSnapshots = true;

            if (configinfo != null && configinfo.containsKey("thresholdsIncludeOverhead")) {
                bThresholdsIncludeOverhead = Boolean.parseBoolean(configinfo.get("thresholdsIncludeOverhead"));
            }
            if (configinfo != null && configinfo.containsKey("includeSnapshots")) {
                bIncludeSnapshots = Boolean.parseBoolean(configinfo.get("includeSnapshots"));
            }

            // set quota - save the quota id to extensions
            String qid = isi.createQuota(qDirPath, bThresholdsIncludeOverhead,
                    bIncludeSnapshots, qDirSize);

            if (args.getQuotaDirExtensions() == null) {
                args.initQuotaDirExtensions();
            }
            args.getQuotaDirExtensions().put(QUOTA, qid);

            _log.info("IsilonFileStorageDevice doCreateQuotaDirectory {} with size {} - complete", qDirPath, qDirSize);
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doCreateQuotaDirectory failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {

        // Get Parent FS Mount Path
        // Get Quota Directory Name
        // Get Quota Size
        // Call Delete Quota
        // Call Delete Directory recursively

        QuotaDirectory quotaDir = args.getQuotaDirectory();
        String fsMountPath = args.getFsMountPath();
        Long qDirSize = quotaDir.getSize();
        String qDirPath = fsMountPath + "/" + quotaDir.getName();
        _log.info("IsilonFileStorageDevice doDeleteQuotaDirectory {} with size {} - start", qDirPath, qDirSize);
        try {
            IsilonApi isi = getIsilonDevice(storage);

            String quotaId = null;
            if (quotaDir.getExtensions() != null) {
                quotaId = quotaDir.getExtensions().get(QUOTA);
            }
            if (quotaId != null) {
                _log.info("IsilonFileStorageDevice doDeleteQuotaDirectory , Delete Quota {}", quotaId);
                isi.deleteQuota(quotaId);
            }

            // delete directory for the Quota Directory
            isi.deleteDir(qDirPath, true);
            _log.info("IsilonFileStorageDevice doDeleteQuotaDirectory {} with size {} - complete", qDirPath, qDirSize);
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doDeleteQuotaDirectory failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory quotaDir) throws ControllerException {
        // Get Parent FS mount path
        // Get Quota Directory Name
        // Get Quota Size
        // Call Update Quota (Aways use that quota for updating the size)

        String fsMountPath = args.getFsMountPath();
        Long qDirSize = quotaDir.getSize();
        String qDirPath = fsMountPath + "/" + quotaDir.getName();
        _log.info("IsilonFileStorageDevice doUpdateQuotaDirectory {} with size {} - start", qDirPath, qDirSize);
        try {
            IsilonApi isi = getIsilonDevice(storage);

            String quotaId = null;
            if (quotaDir.getExtensions() != null) {
                quotaId = quotaDir.getExtensions().get(QUOTA);
            }

            if (quotaId != null) {
                // Isilon does not allow to update quota directory to zero.
                if (qDirSize > 0) {
                    _log.info("IsilonFileStorageDevice doUpdateQuotaDirectory , Update Quota {} with Capacity {}", quotaId, qDirSize);
                    IsilonSmartQuota expandedQuota = new IsilonSmartQuota(qDirSize);
                    isi.modifyQuota(quotaId, expandedQuota);
                }

            } else {
                // Create a new Quota
                boolean bThresholdsIncludeOverhead = true;
                boolean bIncludeSnapshots = true;

                if (configinfo != null && configinfo.containsKey("thresholdsIncludeOverhead")) {
                    bThresholdsIncludeOverhead = Boolean.parseBoolean(configinfo.get("thresholdsIncludeOverhead"));
                }
                if (configinfo != null && configinfo.containsKey("includeSnapshots")) {
                    bIncludeSnapshots = Boolean.parseBoolean(configinfo.get("includeSnapshots"));
                }

                // set quota - save the quota id to extensions
                String qid = isi.createQuota(qDirPath, bThresholdsIncludeOverhead,
                        bIncludeSnapshots, qDirSize);

                if (args.getQuotaDirExtensions() == null) {
                    args.initQuotaDirExtensions();
                }
                args.getQuotaDirExtensions().put(QUOTA, qid);

            }
            _log.info("IsilonFileStorageDevice doUpdateQuotaDirectory {} with size {} - complete", qDirPath, qDirSize);
            return BiosCommandResult.createSuccessfulResult();
        } catch (IsilonException e) {
            _log.error("doUpdateQuotaDirectory failed.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult deleteExportRules(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        List<ExportRule> allExports = args.getExistingDBExportRules();
        String subDir = args.getSubDirectory();
        boolean allDirs = args.isAllDir();
        FileShare fs = args.getFs();

        String exportPath;
        String subDirExportPath = "";
        subDir = args.getSubDirectory();

        if (!args.getFileOperation()) {
            exportPath = args.getSnapshotPath();
            if (subDir != null
                    && subDir.length() > 0) {
                subDirExportPath = args.getSnapshotPath() + "/"
                        + subDir;
            }

        } else {
            exportPath = args.getFs().getPath();
            if (subDir != null
                    && subDir.length() > 0) {
                subDirExportPath = args.getFs().getPath() + "/"
                        + subDir;
            }
        }

        _log.info("exportPath : {}", exportPath);
        args.setExportPath(exportPath);

        _log.info("Number of existing exports found {}", allExports.size());

        try {

            IsilonApi isi = getIsilonDevice(storage);
            String zoneName = getZoneName(args.getvNAS());

            if (allDirs) {
                // ALL EXPORTS
                _log.info("Deleting all exports specific to filesystem at device and rules from DB including sub dirs rules and exports");
                for (ExportRule rule : allExports) {
                    _log.info("Delete IsilonExport id {} for path {}",
                            rule.getDeviceExportId(), rule.getExportPath());
                    if (zoneName != null) {
                    	isi.deleteExport(rule.getDeviceExportId(), zoneName);
                    } else {
                    	isi.deleteExport(rule.getDeviceExportId());
                    }
                }

            } else if (subDir != null && !subDir.isEmpty()) {
                // Filter for a specific Sub Directory export
                _log.info("Deleting all subdir exports rules at ViPR and  sub directory export at device {}", subDir);
                for (ExportRule rule : allExports) {
                    _log.info("Delete IsilonExport id for path {} f containing subdirectory {}",
                            rule.getDeviceExportId() + ":" + rule.getExportPath(), subDir);

                    String fsExportPathWithSub = args.getFsPath() + "/" + subDir;
                    if (rule.getExportPath().equalsIgnoreCase(fsExportPathWithSub)) {
                        _log.info("Delete IsilonExport id {} for path {}",
                                rule.getDeviceExportId(), rule.getExportPath());
                        if (zoneName != null) {
                        	isi.deleteExport(rule.getDeviceExportId(), zoneName);
                        } else {
                        	isi.deleteExport(rule.getDeviceExportId());
                        }
                    }
                }

            } else {
                // Filter for No SUBDIR - main export rules with no sub dirs
                _log.info("Deleting all export rules  from DB and export at device not included sub dirs");
                for (ExportRule rule : allExports) {
                    if (rule.getExportPath().equalsIgnoreCase(exportPath)) {
                        _log.info("Delete IsilonExport id {} for path {}",
                                rule.getDeviceExportId(), rule.getExportPath());
                        if (zoneName != null) {
                        	isi.deleteExport(rule.getDeviceExportId(), zoneName);
                        } else {
                        	isi.deleteExport(rule.getDeviceExportId());
                        }
                    }
                }
            }

        } catch (IsilonException ie) {
            _log.info("Exception: {}", ie);

            throw new DeviceControllerException(
                    "Exception while performing export for {0} ",
                    new Object[] { args.getFsId() });
        }

        _log.info("IsilonFileStorageDevice exportFS {} - complete",
                args.getFsId());
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        return result;
    }

    @Override
    public BiosCommandResult updateExportRules(StorageSystem storage,
            FileDeviceInputOutput args)
            throws ControllerException {
        // Requested Export Rules
        List<ExportRule> exportAdd = args.getExportRulesToAdd();
        List<ExportRule> exportDelete = args.getExportRulesToDelete();
        List<ExportRule> exportModify = args.getExportRulesToModify();

        // To be processed export rules
        List<ExportRule> exportsToRemove = new ArrayList<>();
        List<ExportRule> exportsToModify = new ArrayList<>();
        List<ExportRule> exportsToAdd = new ArrayList<>();

        // ALL EXPORTS
        List<ExportRule> exportsToProcess = args.getExistingDBExportRules();
        Map<String, ArrayList<ExportRule>> existingExportsMapped = new HashMap();

        // Calculate Export Path
        String exportPath;
        String subDir = args.getSubDirectory();

        // It is a Snapshot Export Update and so Sub Directory will be ".snapshot"
        if (!args.getFileOperation()) {
            exportPath = args.getSnapshotPath();
            if (subDir != null
                    && subDir.length() > 0) {
                exportPath = args.getSnapshotPath() + "/"
                        + subDir;
            }

        } else {
            exportPath = args.getFs().getPath();
            if (subDir != null
                    && subDir.length() > 0) {
                exportPath = args.getFs().getPath() + "/"
                        + subDir;
            }
        }

        _log.info("exportPath : {}", exportPath);
        args.setExportPath(exportPath);

        if (exportsToProcess == null) {
            exportsToProcess = new ArrayList<>();
        }

        // Process Exports
        for (ExportRule existingRule : exportsToProcess) {
            ArrayList<ExportRule> exps = existingExportsMapped.get(existingRule.getExportPath());
            if (exps == null) {
                exps = new ArrayList<>();
            }
            exps.add(existingRule);
            _log.info("Checking existing export for {} : exps : {}", existingRule.getExportPath(), exps);
            existingExportsMapped.put(existingRule.getExportPath(), exps);
        }

        // Handle Add export Rules
        if (exportAdd != null && !exportAdd.isEmpty()) {
            // Check for existing exports for the export path including subdirectory
            ArrayList<ExportRule> exps = existingExportsMapped.get(exportPath);
            if (exps != null && !exps.isEmpty()) {
                _log.error("Adding export rules is not supported as there can be only one export rule for Isilon for a path.");
                ServiceError error = DeviceControllerErrors.isilon
                        .jobFailed("updateExportRules : Adding export rule is not supported for Isilon");
                return BiosCommandResult.createErrorResult(error);
            }
        }

        _log.info("Number of existing Rules found {}", exportsToProcess.size());

        // Handle Modified export Rules and Delete Export Rule
        if (existingExportsMapped.get(exportPath) != null && !existingExportsMapped.get(exportPath).isEmpty()) {
            for (ExportRule existingRule : existingExportsMapped.get(exportPath)) {
                for (ExportRule modifiedrule : exportModify) {
                    if (modifiedrule.getSecFlavor().equals(
                            existingRule.getSecFlavor())) {
                        modifiedrule.setDeviceExportId(existingRule.getDeviceExportId());
                        _log.info("Modifying Export Rule from {}, To {}",
                                existingRule, modifiedrule);
                        exportsToModify.add(modifiedrule);
                    }
                }
            }

            // Handle Delete export Rules
            if (exportDelete != null && !exportDelete.isEmpty()) {
                for (ExportRule existingRule : existingExportsMapped.get(exportPath)) {
                    for (ExportRule oldExport : exportDelete) {
                        if (oldExport.getSecFlavor().equals(
                                existingRule.getSecFlavor())) {
                            _log.info("Deleting Export Rule {}", existingRule);
                            exportsToRemove.add(existingRule);
                        }
                    }
                }
            }

            // No of exports found to remove from the list
            _log.info("No of exports found to remove from the existing exports list {}", exportsToRemove.size());
            exportsToProcess.removeAll(exportsToRemove);

        } else {

            if (exportsToProcess == null) {
                exportsToProcess = new ArrayList<>();
            }

            // Handle Add Export Rules
            // This is valid only if no rules to modify exists
            if (exportAdd != null && !exportAdd.isEmpty()) {
                for (ExportRule newExport : exportAdd) {
                    _log.info("Add Export Rule {}", newExport);
                    newExport.setExportPath(exportPath);
                    exportsToAdd.add(newExport);
                }
            }
            exportsToProcess.addAll(exportAdd);
        }

        // Process Mods
        IsilonApi isi = getIsilonDevice(storage);

        for (ExportRule existingRule : exportsToModify) {
            _log.info("Modify Export rule : {}", existingRule.toString());
        }

        processIsiExport(isi, args, exportsToModify);

        for (ExportRule existingRule : exportsToRemove) {
            _log.info("Remove Export rule : {}", existingRule.toString());
        }

        processRemoveIsiExport(isi, args, exportsToRemove);

        for (ExportRule existingRule : exportsToAdd) {
            _log.info("Add Export rule : {}", existingRule.toString());
        }

        processAddIsiExport(isi, args, exportsToAdd);

        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;

    }

    /**
     * Add isilon exports
     * 
     * @param isi IsilonApi object
     * @param args FileDeviceInputOutput object
     * @param exports new exports to add
     * @throws IsilonException
     */
    private void processAddIsiExport(IsilonApi isi, FileDeviceInputOutput args, List<ExportRule> exports) throws IsilonException {

        _log.info("ProcessAddExport  Start");

        List<ExportRule> modifyRules = new ArrayList<>();

        // process and export each NFSExport independently.
        for (ExportRule exportRule : exports) {

            // create and set IsilonExport instance from ExportRule

            _log.info("Add this export rule {}", exportRule.toString());

            String root_user = exportRule.getAnon();
            Set<String> rootHosts = exportRule.getRootHosts();

            if (rootHosts != null) {
                // Validate parameters for permissions and root user mapping.
                if ((!rootHosts.isEmpty()) &&
                        !root_user.equals("root")) {
                    String msg = "The root_user mapping is not set to root but the permission is.";
                    _log.error(msg);
                    throw IsilonException.exceptions.invalidParameters();
                }
            }

            String isilonExportId = exportRule.getDeviceExportId();

            if (isilonExportId != null) {
                // The Export Rule already exists on the array so modify it
                _log.info("Export {} {} exists on the device so modify it", isilonExportId, exportRule);
                modifyRules.add(exportRule);
            } else {
                // Create the Export
                List<String> allClients = new ArrayList<>();
                _log.info("Export doesnt {} exists on the device so create it", exportRule);
                IsilonExport newIsilonExport = setIsilonExport(exportRule);
                String expId = isi.createExport(newIsilonExport);
                exportRule.setDeviceExportId(expId);
            }

            if (!modifyRules.isEmpty()) {
                // Call Process Isi Export
                processIsiExport(isi, args, modifyRules);
            }
        }
        _log.info("ProcessAddExport  Completed");
    }

    /**
     * Update isilon exports
     * 
     * @param isi IsilonApi object
     * @param args FileDeviceInputOutput object
     * @param exports new exports to add
     * @throws IsilonException
     */
    private void processIsiExport(IsilonApi isi, FileDeviceInputOutput args, List<ExportRule> exports) throws IsilonException {

        _log.info("ProcessIsiExport  Start");
        // process and export each NFSExport independently.
        for (ExportRule exportRule : exports) {

            // create and set IsilonExport instance from ExportRule

            String root_user = exportRule.getAnon();
            Set<String> rootHosts = exportRule.getRootHosts();

            if (rootHosts != null) {
                // Validate parameters for permissions and root user mapping.
                if ((!rootHosts.isEmpty()) &&
                        !root_user.equals("root")) {
                    String msg = "The root_user mapping is not set to root but the permission is.";
                    _log.error(msg);
                    throw IsilonException.exceptions.invalidParameters();
                }
            }

            String isilonExportId = exportRule.getDeviceExportId();

            if (isilonExportId != null) {
            	IsilonExport isilonExport = null;
            	String zoneName = getZoneName(args.getvNAS());
            	if (zoneName != null) {
            		isilonExport = isi.getExport(isilonExportId, zoneName);
            	} else {
            		isilonExport = isi.getExport(isilonExportId); 
            	}
                

                // Update the comment
                if (exportRule.getComments() != null && !exportRule.getComments().isEmpty()) {
                    isilonExport.setComment(exportRule.getComments());
                }

                _log.info("Update Isilon Export with id {} and {}", isilonExportId, isilonExport);
                List<String> allClients = new ArrayList<>();
                if (isilonExport != null) {

                    boolean hasrwClients = false;
                    boolean hasrootClients = false;

                    if ((isilonExport.getReadWriteClients() != null && !isilonExport.getReadWriteClients().isEmpty()) ||
                            (exportRule.getReadWriteHosts() != null && !exportRule.getReadWriteHosts().isEmpty())) {
                        hasrwClients = true;
                    }
                    if ((isilonExport.getRootClients() != null && !isilonExport.getRootClients().isEmpty()) ||
                            (exportRule.getRootHosts() != null && !exportRule.getRootHosts().isEmpty())) {
                        hasrootClients = true;
                    }

                    List<String> roClients = new ArrayList<>();
                    // over write roClients
                    if (exportRule.getReadOnlyHosts() != null) {
                        roClients.addAll(exportRule.getReadOnlyHosts());
                        allClients.addAll(exportRule.getReadOnlyHosts());

                        List<String> existingRWRootClients = new ArrayList<String>();
                        existingRWRootClients.addAll(isilonExport.getReadWriteClients());
                        existingRWRootClients.addAll(isilonExport.getRootClients());

                        List<String> commonHosts = getIntersection(existingRWRootClients, roClients);

                        if (!commonHosts.isEmpty()) {
                            // RW, RO and Root permissions cannot co-exist for same client hosts
                            // Using Set to eliminate duplicates
                            Set<String> existingRWClients = new HashSet<String>(isilonExport.getReadWriteClients());
                            Set<String> existingRootClients = new HashSet<String>(isilonExport.getRootClients());
                            // Remove common hosts
                            existingRWClients.removeAll(commonHosts);
                            existingRootClients.removeAll(commonHosts);
                            isilonExport.setRootClients(new ArrayList<String>(existingRootClients));
                            isilonExport.setReadWriteClients(new ArrayList<String>(existingRWClients));
                        }
                        isilonExport.setReadOnlyClients(new ArrayList<String>(roClients));
                    }

                    List<String> rwClients = new ArrayList<>();
                    // over write rwClients has emptypayload or it contains elements
                    if (exportRule.getReadWriteHosts() != null) {
                        rwClients.addAll(exportRule.getReadWriteHosts());
                        allClients.addAll(exportRule.getReadWriteHosts());

                        List<String> existingRORootClients = new ArrayList<String>();
                        existingRORootClients.addAll(isilonExport.getReadOnlyClients());
                        existingRORootClients.addAll(isilonExport.getRootClients());

                        List<String> commonHosts = getIntersection(existingRORootClients, rwClients);

                        if (!commonHosts.isEmpty()) {

                            // RW, RO and Root permissions cannot co-exist for same client hosts
                            // Using Set to eliminate duplicates
                            Set<String> existingROClients = new HashSet<String>(isilonExport.getReadOnlyClients());
                            Set<String> existingRootClients = new HashSet<String>(isilonExport.getRootClients());
                            // Remove common hosts
                            existingROClients.removeAll(commonHosts);
                            existingRootClients.removeAll(commonHosts);
                            isilonExport.setRootClients(new ArrayList<String>(existingRootClients));
                            isilonExport.setReadOnlyClients(new ArrayList<String>(existingROClients));
                        }
                        isilonExport.setReadWriteClients(new ArrayList<String>(rwClients));
                    }

                    // over write rootClients
                    List<String> rootClients = new ArrayList<>();
                    if (rootHosts != null) {
                        rootClients.addAll(rootHosts);
                        allClients.addAll(rootHosts);

                        List<String> existingRORWClients = new ArrayList<String>();
                        existingRORWClients.addAll(isilonExport.getReadOnlyClients());
                        existingRORWClients.addAll(isilonExport.getReadWriteClients());

                        List<String> commonHosts = getIntersection(existingRORWClients, rootClients);

                        if (!commonHosts.isEmpty()) {
                            // RW, RO and Root permissions cannot co-exist for same client hosts

                            Set<String> existingROClients = new HashSet<String>(isilonExport.getReadOnlyClients());
                            Set<String> existingRWClients = new HashSet<String>(isilonExport.getReadWriteClients());
                            existingROClients.removeAll(commonHosts);
                            existingRWClients.removeAll(commonHosts);
                            isilonExport.setReadWriteClients(new ArrayList<String>(existingRWClients));
                            isilonExport.setReadOnlyClients(new ArrayList<String>(existingROClients));
                        }
                        isilonExport.setRootClients(new ArrayList<String>(rootClients));
                    }

                    if (hasrwClients || hasrootClients) {
                        isilonExport.resetReadOnly();
                    } else {
                        isilonExport.setReadOnly();
                    }

                    // Do not set root_user. Isilon api allows only one of map_root or map_all to be set.
                    if (hasrootClients) {
                        isilonExport.setMapRoot(null);
                        isilonExport.setMapAll("root");
                    } else {
                        isilonExport.setMapAll(null);
                        isilonExport.setMapRoot(root_user);
                    }

                    // There is export in Isilon with the given id.
                    // Overwrite this export with a new set of clients.
                    // We overwrite only clients element in exports. Isilon API does not use read_only_clients,
                    // read_write_clients or root_clients.

                    // List<String> newClients = isilonExport.getClients();
                    // newClients.addAll(allClients);
                    isilonExport.setClients(new ArrayList<String>(allClients));

                    IsilonExport clonedExport = cloneExport(isilonExport);

                    _log.info("Update Isilon Export with id {} and new info {}", isilonExportId, clonedExport.toString());
                    
                    if (zoneName != null) {
                    	isi.modifyExport(isilonExportId, zoneName, clonedExport);
                    } else {
                    	isi.modifyExport(isilonExportId, clonedExport);
                    }
                    
                }
            }
        }
        _log.info("ProcessIsiExport  Completed");
    }

    /**
     * Delete isilon exports
     * 
     * @param isi IsilonApi object
     * @param args FileDeviceInputOutput object
     * @param exports new exports to add
     * @throws IsilonException
     */
    private void processRemoveIsiExport(IsilonApi isi, FileDeviceInputOutput args, List<ExportRule> exports) throws IsilonException {

        _log.info("processRemoveIsiExport  Start");

        List<ExportRule> modifyRules = new ArrayList<>();

        // process and export each NFSExport independently.
        for (ExportRule exportRule : exports) {

            // create and set IsilonExport instance from ExportRule

            _log.info("Remove this export rule {}", exportRule.toString());
            String isilonExportId = exportRule.getDeviceExportId();

            if (isilonExportId != null) {
                // The Export Rule already exists on the array so modify it
                _log.info("Export {} {} exists on the device so remove it", isilonExportId, exportRule);
                String zoneName = getZoneName(args.getvNAS());
                if (zoneName != null) {
                	isi.deleteExport(isilonExportId, zoneName);
                } else {
                	isi.deleteExport(isilonExportId);
                }
                
            }
        }
        _log.info("processRemoveIsiExport  Completed");
    }

    private IsilonExport cloneExport(IsilonExport exp) {
        IsilonExport newExport = new IsilonExport();

        newExport.addPath(exp.getPaths().get(0));
        newExport.addRootClients(exp.getRootClients());
        newExport.addReadWriteClients(exp.getReadWriteClients());
        newExport.addReadOnlyClients(exp.getReadOnlyClients());

        if (exp.getReadOnly()) {
            newExport.setReadOnly();
        } else {
            newExport.resetReadOnly();
        }

        if (exp.getAllDirs()) {
            newExport.setAllDirs();
        } else {
            newExport.resetAllDirs();
        }
        newExport.addClients(exp.getClients());
        if (exp.getComment() != null) {
            newExport.setComment(exp.getComment());
        }
        newExport.setSecurityFlavors(exp.getSecurityFlavors());

        if (exp.getMap_all().getUser() != null && !exp.getMap_all().getUser().isEmpty()) {
            newExport.setMapAll(exp.getMap_all().getUser());
        }
        if (exp.getMap_root().getUser() != null && !exp.getMap_root().getUser().isEmpty()) {
            newExport.setMapRoot(exp.getMap_root().getUser());
        }

        return newExport;
    }

    private List<String> getIntersection(
            List<String> oldList, List<String> newList) {

        Set<String> a = new HashSet<String>(oldList);
        a.retainAll(newList);
        return new ArrayList<String>(a);
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {
        // Requested Export Rules
        List<ShareACL> aclsToAdd = args.getShareAclsToAdd();
        List<ShareACL> aclsToDelete = args.getShareAclsToDelete();
        List<ShareACL> aclsToModify = args.getShareAclsToModify();

        // Get existing Acls for the share
        List<ShareACL> aclsToProcess = args.getExistingShareAcls();

        _log.info("Share name : {}", args.getShareName());

        // Process Acls
        _log.info("Number of existing ACLs found {}", aclsToProcess.size());

        // Process ACLs to add
        aclsToProcess.addAll(aclsToAdd);

        // Process ACLs to modify
        for (ShareACL existingAcl : aclsToProcess) {
            String domainOfExistingAce = existingAcl.getDomain();
            if (domainOfExistingAce == null) {
                domainOfExistingAce = "";
            }
            for (ShareACL aclToModify : aclsToModify) {
                String domainOfmodifiedAce = aclToModify.getDomain();
                if (domainOfmodifiedAce == null) {
                    domainOfmodifiedAce = "";
                }

                if (aclToModify.getUser() != null && existingAcl.getUser() != null) {
                    if (domainOfExistingAce.concat(existingAcl.getUser()).equalsIgnoreCase(
                            domainOfmodifiedAce.concat(aclToModify.getUser()))) {

                        existingAcl.setPermission(aclToModify.getPermission());
                    }
                }

                if (aclToModify.getGroup() != null && existingAcl.getGroup() != null) {
                    if (domainOfExistingAce.concat(existingAcl.getGroup()).equalsIgnoreCase(
                            domainOfmodifiedAce.concat(aclToModify.getGroup()))) {
                        existingAcl.setPermission(aclToModify.getPermission());
                    }
                }
            }
        }

        // Process ACLs to delete
        for (ShareACL aclToDelete : aclsToDelete) {

            String domainOfDeleteAce = aclToDelete.getDomain();
            if (domainOfDeleteAce == null) {
                domainOfDeleteAce = "";
            }

            for (Iterator<ShareACL> iterator = aclsToProcess.iterator(); iterator.hasNext();) {
                ShareACL existingAcl = iterator.next();

                String domainOfExistingAce = existingAcl.getDomain();
                if (domainOfExistingAce == null) {
                    domainOfExistingAce = "";
                }

                if (aclToDelete.getUser() != null
                        && existingAcl.getUser() != null) {
                    if (domainOfDeleteAce.concat(aclToDelete.getUser())
                            .equalsIgnoreCase(domainOfExistingAce.concat(existingAcl.getUser()))) {
                        iterator.remove();
                    }
                }

                if (aclToDelete.getGroup() != null
                        && existingAcl.getGroup() != null) {
                    if (domainOfDeleteAce.concat(aclToDelete.getGroup())
                            .equalsIgnoreCase(domainOfExistingAce.concat(existingAcl.getGroup()))) {
                        iterator.remove();
                    }
                }
            }
        }

        // Process new ACLs
        IsilonApi isi = getIsilonDevice(storage);
        processAclsForShare(isi, args, aclsToProcess);

        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;

    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {

        IsilonApi isi = getIsilonDevice(storage);
        processAclsForShare(isi, args, null);

        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;
    }

    /**
     * Sets permissions on Isilon SMB share.
     * 
     * @param isi the isilon API handle
     * @param args in which the attribute <code>shareName</code> must be set
     * @param aclsToProcess the ACEs to set on Isilon SMB share.
     *            If this value is null, then no permissions (ACEs) will be set
     */
    private void processAclsForShare(IsilonApi isi, FileDeviceInputOutput args,
            List<ShareACL> aclsToProcess) {

        _log.info("Start processAclsForShare to set ACL for share {}: ACL: {}",
                args.getShareName(), aclsToProcess);

        IsilonSMBShare isilonSMBShare = new IsilonSMBShare(args.getShareName());
        ArrayList<Permission> permissions = new ArrayList<Permission>();
        String permissionValue = null;
        String permissionTypeValue = null;
        if (aclsToProcess != null) {
            for (ShareACL acl : aclsToProcess) {
                String domain = acl.getDomain();
                if (domain == null) {
                    domain = "";
                }
                domain = domain.toLowerCase();
                String userOrGroup = acl.getUser() == null ? acl.getGroup().toLowerCase() : acl.getUser().toLowerCase();
                if (domain.length() > 0) {
                    userOrGroup = domain + "\\" + userOrGroup;
                }
                permissionValue = acl.getPermission().toLowerCase();
                if (permissionValue.startsWith("full")) {
                    permissionValue = Permission.PERMISSION_FULL;
                }

                permissionTypeValue = Permission.PERMISSION_TYPE_ALLOW;
                Permission permission = isilonSMBShare.new Permission(
                        permissionTypeValue, permissionValue, userOrGroup);
                permissions.add(permission);
            }
        }
        /*
         * If permissions array list is empty, it means to remove
         * all ACEs on the share.
         */
        isilonSMBShare.setPermissions(permissions);
        _log.info("Calling Isilon API: modifyShare. Share {}, permissions {}",
                isilonSMBShare, permissions);
        String zoneName = getZoneName(args.getvNAS());
        if (zoneName != null) {
        	isi.modifyShare(args.getShareName(), zoneName, isilonSMBShare);
        } else {
        	isi.modifyShare(args.getShareName(), isilonSMBShare);
        }
        

        _log.info("End processAclsForShare");
    }

    /**
     * getIsilonAclFromNfsACE function will convert the nfsACE object 
     * to Isilon ACL object.
     *  
     * @param nfsACE - vipr ACE object.
     * @return
     */
    private Acl getIsilonAclFromNfsACE(NfsACE nfsACE ) {
    	
    	IsilonNFSACL isilonAcl = new IsilonNFSACL();
    	Acl acl = isilonAcl.new Acl();
    	
    	ArrayList<String> inheritFlags = new ArrayList<String>();

        inheritFlags.add("object_inherit");
        inheritFlags.add("inherit_only");
        acl.setInherit_flags(inheritFlags);
        acl.setAccessrights(getIsilonAccessList(nfsACE.getPermissionSet()));
        acl.setOp("add");
        acl.setAccesstype(nfsACE.getPermissionType());
        String user = nfsACE.getUser();
        if (nfsACE.getDomain() != null && !nfsACE.getDomain().isEmpty()) {
            user = nfsACE.getDomain() + "\\" + nfsACE.getUser();
        }

        IsilonNFSACL.Persona trustee = isilonAcl.new Persona(nfsACE.getType(), null, user);
        acl.setTrustee(trustee);
    	
    	return acl;
    }
    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {

        IsilonNFSACL isilonAcl = new IsilonNFSACL();
        ArrayList<Acl> aclCompleteList = new ArrayList<Acl>();
        List<NfsACE> aceToAdd = args.getNfsAclsToAdd();
        for (NfsACE nfsACE : aceToAdd) {
            Acl acl = getIsilonAclFromNfsACE(nfsACE );
            acl.setOp("add");
            aclCompleteList.add(acl);
        }

        List<NfsACE> aceToModify = args.getNfsAclsToModify();
        for (NfsACE nfsACE : aceToModify) {
        	Acl acl = getIsilonAclFromNfsACE(nfsACE );
            acl.setOp("replace");
            aclCompleteList.add(acl);
        }

        List<NfsACE> aceToDelete = args.getNfsAclsToDelete();
        for (NfsACE nfsACE : aceToDelete) {
        	Acl acl = getIsilonAclFromNfsACE(nfsACE );
            acl.setOp("delete");
            aclCompleteList.add(acl);
        }

        isilonAcl.setAction("update");
        isilonAcl.setAuthoritative("acl");
        isilonAcl.setAcl(aclCompleteList);
        String path = args.getFileSystemPath();
        if (args.getSubDirectory() != null && !args.getSubDirectory().isEmpty()) {
            path = path + "/" + args.getSubDirectory();

        }

        // Process new ACLs
        IsilonApi isi = getIsilonDevice(storage);
        _log.info("Calling Isilon API: modify NFS Acl for  {}, acl  {}", args.getFileSystemPath(), isilonAcl);
        isi.modifyNFSACL(path, isilonAcl);
        _log.info("End updateNfsACLs");
        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;
    }
    
    private ArrayList<String> getIsilonAccessList(Set<String> permissions) {

        ArrayList<String> accessRights = new ArrayList<String>();
        for (String per : permissions) {

            if (per.equalsIgnoreCase(FileControllerConstants.NFS_FILE_PERMISSION_READ)) {
                accessRights.add(IsilonNFSACL.AccessRights.dir_gen_read.toString());
            }
            
            if (per.equalsIgnoreCase(FileControllerConstants.NFS_FILE_PERMISSION_WRITE)) {
                accessRights.add(IsilonNFSACL.AccessRights.std_write_dac.toString());
            }
            
            if (per.equalsIgnoreCase(FileControllerConstants.NFS_FILE_PERMISSION_EXECUTE)) {
                accessRights.add(IsilonNFSACL.AccessRights.dir_gen_execute.toString());
            }
        }
        return accessRights;
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {
    	
    	IsilonNFSACL isilonAcl = new IsilonNFSACL();
        ArrayList<Acl> aclCompleteList = new ArrayList<Acl>();
 
        List<NfsACE> aceToDelete = args.getNfsAclsToDelete();
        for (NfsACE nfsACE : aceToDelete) {
        	Acl acl = getIsilonAclFromNfsACE(nfsACE );
            acl.setOp("delete");
            aclCompleteList.add(acl);
        }

        isilonAcl.setAction("update");
        isilonAcl.setAuthoritative("acl");
        isilonAcl.setAcl(aclCompleteList);
        String path = args.getFileSystemPath();
        if (args.getSubDirectory() != null && !args.getSubDirectory().isEmpty()) {
            path = path + "/" + args.getSubDirectory();

        }

        // Process new ACLs
        IsilonApi isi = getIsilonDevice(storage);
        _log.info("Calling Isilon API: to delete NFS Acl for  {}, acl  {}", args.getFileSystemPath(), isilonAcl);
        isi.modifyNFSACL(path, isilonAcl);
        _log.info("End deleteNfsACLs");
        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;
    }
    
    private String getZoneName(VirtualNAS vNAS) {
    	String zoneName = null;
    	if (vNAS != null) {
    		zoneName = vNAS.getNasName();
    	}
    	return zoneName;
    }
}
