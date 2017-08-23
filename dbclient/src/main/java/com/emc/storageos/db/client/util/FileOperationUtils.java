package com.emc.storageos.db.client.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileMountInfo;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.MountInfo;

public class FileOperationUtils {

    private static final Logger _log = LoggerFactory.getLogger(FileOperationUtils.class);

    /**
     * Utility method to find Export rule from DB using Fileshare object
     * 
     * @param fFileshare object
     * @param subDirectory
     * @param securityType
     * @param dbClient
     * @return Export rule
     */
    public static ExportRule findExport(FileShare fs, String subDirectory, String securityType, DbClient dbClient) {
        updateStoragePortDetails(fs.getId(), dbClient);
        dbClient.queryByType(FileShare.class, true);
        List<ExportRule> exportList = getExportRules(fs.getId(), false, subDirectory, dbClient);
        for (ExportRule export : exportList) {
            List<String> securityTypes = Arrays.asList(export.getSecFlavor().split("\\s*,\\s*"));
            if (securityTypes.contains(securityType)) {
                return export;
            }
        }
        throw new IllegalArgumentException("No exports found for the provided security type and subdirectory.");
    }

    /**
     * Utility method to find Export rule from DB using Id of the Filesystem
     * 
     * @param Filesystem id
     * @param subDirectory
     * @param securityType
     * @param dbClient
     * @return Export rule
     */
    public static ExportRule findExport(URI id, String subDirectory, String securityType, DbClient dbClient) {
        FileShare fs = dbClient.queryObject(FileShare.class, id);
        return findExport(fs, subDirectory, securityType, dbClient);
    }

    public static List<FileExportRule> queryDBFSExports(FileShare fs, DbClient dbClient) {
        _log.info("Querying all ExportRules Using FsId {}", fs.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
            List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileExportRule.class,
                    containmentConstraint);
            return fileExportRules;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }
        return null;
    }

    /**
     * Method to extract export rules using URI of the filestystem
     * 
     * @param id
     * @param allDirs
     * @param subDir
     * @param dbClient
     * @return
     */
    public static List<ExportRule> getExportRules(URI id, boolean allDirs, String subDir, DbClient dbClient) {
        updateStoragePortDetails(id, dbClient);
        FileShare fs = dbClient.queryObject(FileShare.class, id);
        return getExportRules(fs, allDirs, subDir, dbClient);
    }

    /**
     * Method to extract export rules using Fileshare object
     * 
     * @param fs
     * @param allDirs
     * @param subDir
     * @param dbClient
     * @return
     */
    public static List<ExportRule> getExportRules(FileShare fs, boolean allDirs, String subDir, DbClient dbClient) {

        List<ExportRule> exportRule = new ArrayList<>();

        // Query All Export Rules Specific to a File System.
        List<FileExportRule> exports = queryDBFSExports(fs, dbClient);
        _log.info("Number of existing exports found : {} ", exports.size());
        if (allDirs) {
            // ALL EXPORTS
            for (FileExportRule rule : exports) {
                ExportRule expRule = new ExportRule();
                // Copy Props
                copyPropertiesToSave(rule, expRule, fs);
                exportRule.add(expRule);
            }
        } else if (subDir != null && subDir.length() > 0) {
            // Filter for a specific Sub Directory export
            for (FileExportRule rule : exports) {
                if (rule.getExportPath().endsWith("/" + subDir)) {
                    ExportRule expRule = new ExportRule();
                    // Copy Props
                    copyPropertiesToSave(rule, expRule, fs);
                    exportRule.add(expRule);
                }
            }
        } else {
            // Filter for No SUBDIR - main export rules with no sub dirs
            for (FileExportRule rule : exports) {
                if (rule.getExportPath().equalsIgnoreCase(fs.getPath())) {
                    ExportRule expRule = new ExportRule();
                    // Copy Props
                    copyPropertiesToSave(rule, expRule, fs);
                    exportRule.add(expRule);
                }
            }
        }
        _log.info("Number of export rules returning {}", exportRule.size());
        return exportRule;
    }

    /**
     * Update port name details in FileShare object
     * 
     * @param id - filesystem URI
     */
    public static boolean updateStoragePortDetails(URI id, DbClient dbClient) {
        FileShare fs = dbClient.queryObject(FileShare.class, id);
        return updateStoragePortDetails(fs, dbClient);
    }

    /**
     * Update port name details in FileShare object
     * 
     * @param id - filesystem URI
     */
    public static boolean updateStoragePortDetails(FileShare fs, DbClient dbClient) {
        StorageSystem system = dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        boolean isPortNameChanged = false;
        // check for islon device type
        if (system.deviceIsType(DiscoveredDataObject.Type.isilon)) {

            String mountPath = "";
            String mountPoint = "";
            FileExport fileExport = null;
            StoragePort port = dbClient.queryObject(StoragePort.class, fs.getStoragePort());
            // if port nativeID=null then it is old port we need to update new storageport id and name
            if (port.getNativeId() == null) {
                String porturi = "";

                if (fs.getVirtualNAS() != null) { // from virtual nas get latest storageport then replace old storageport uri
                    VirtualNAS vNAS = dbClient.queryObject(VirtualNAS.class, fs.getVirtualNAS());
                    porturi = vNAS.getStoragePorts().iterator().next();
                } else {
                    // get physical nas and then get update storageport
                    List<PhysicalNAS> phyNASlist = getPhyNasbySystemId(system.getId(), dbClient);
                    if (!phyNASlist.isEmpty()) {
                        PhysicalNAS physicalNAS = phyNASlist.iterator().next();
                        porturi = physicalNAS.getStoragePorts().iterator().next();
                    }
                }
                port = dbClient.queryObject(StoragePort.class, URI.create(porturi));
                // update portname and uri id
                fs.setPortName(port.getPortName());
                fs.setStoragePort(port.getId());

                String portName = port.getPortName();
                FSExportMap fsExports = fs.getFsExports();
                Iterator it = fsExports.keySet().iterator();
                while (it.hasNext()) {
                    fileExport = fs.getFsExports().get(it.next());
                    // update storage port
                    if (fileExport != null) {
                        if ((fileExport.getMountPath() != null) && (fileExport.getMountPath().length() > 0)) {
                            mountPath = fileExport.getMountPath();
                        } else {
                            mountPath = fileExport.getPath();
                        }
                        mountPoint = getMountPoint(portName, mountPath);
                        // set mountPoint, portName, portId
                        fileExport.setMountPoint(mountPoint);
                        fileExport.setStoragePort(port.getId().toString());
                        fileExport.setStoragePortName(port.getPortName());
                    }
                }

                // update db object
                dbClient.updateObject(fs);

            } else {
                // if storage port name is changed then update with new storageport name
                // finally set the mount point
                if (fs.getPortName().equals(port.getPortName())) {
                    String portName = port.getPortName();
                    FSExportMap fsExports = fs.getFsExports();
                    Iterator it = fsExports.keySet().iterator();
                    while (it.hasNext()) {
                        fileExport = fs.getFsExports().get(it.next());
                        // update storage port
                        if (fileExport != null) {
                            if ((fileExport.getMountPath() != null) && (fileExport.getMountPath().length() > 0)) {
                                mountPath = fileExport.getMountPath();
                            } else {
                                mountPath = fileExport.getPath();
                            }
                            mountPoint = getMountPoint(portName, mountPath);
                            // set mountPoint and portName
                            fileExport.setMountPoint(mountPoint);
                            fileExport.setStoragePortName(port.getPortName());
                        }
                    }
                    fs.setPortName(portName);
                    // update db object
                    dbClient.updateObject(fs);
                }
            }
        }

        return isPortNameChanged;
    }

    /**
     * List of Virtual NAS for given storage system
     * 
     * @param uriStorage - storage system id
     * @param dbClient - vipr db context
     * @return List<PhysicalNAS>
     */
    private static List<PhysicalNAS> getPhyNasbySystemId(URI uriStorage, DbClient dbClient) {
        List<PhysicalNAS> phyNASURIs = new ArrayList<PhysicalNAS>();
        URIQueryResultList physicalNASURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDevicePhysicalNASConstraint(uriStorage),
                physicalNASURIs);
        Iterator<URI> phyNASIter = physicalNASURIs.iterator();
        PhysicalNAS vNAS = null;
        while (phyNASIter.hasNext()) {
            vNAS = dbClient.queryObject(PhysicalNAS.class, phyNASIter.next());
            phyNASURIs.add(vNAS);
        }
        return phyNASURIs;
    }

    public static String getMountPoint(String fileStoragePort, String path) {
        if (InetAddressUtils.isIPv6Address(fileStoragePort)) {
            fileStoragePort = "[" + fileStoragePort + "]";
        }

        return fileStoragePort + ":" + path;
    }

    private static void copyPropertiesToSave(FileExportRule orig, ExportRule dest, FileShare fs) {

        dest.setFsID(fs.getId());
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        dest.setReadOnlyHosts(orig.getReadOnlyHosts());
        dest.setReadWriteHosts(orig.getReadWriteHosts());
        dest.setRootHosts(orig.getRootHosts());
        dest.setMountPoint(orig.getMountPoint());
        // Test
        _log.info("Expor Rule : {} - {}", orig, dest);
    }

    public static List<MountInfo> queryDBFSMounts(URI fsId, DbClient dbClient) {
        _log.info("Querying File System mounts using FsId {}", fsId);
        List<MountInfo> fsMounts = new ArrayList<MountInfo>();
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileMountsConstraint(fsId);
            List<FileMountInfo> fsDBMounts = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileMountInfo.class,
                    containmentConstraint);
            if (fsDBMounts != null && !fsDBMounts.isEmpty()) {
                for (FileMountInfo dbMount : fsDBMounts) {
                    MountInfo mountInfo = new MountInfo();
                    getMountInfo(dbMount, mountInfo);
                    fsMounts.add(mountInfo);
                }
            }
            return fsMounts;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return fsMounts;
    }

    private static void getMountInfo(FileMountInfo orig, MountInfo dest) {

        dest.setFsId(orig.getFsId());
        dest.setHostId(orig.getHostId());
        dest.setMountPath(orig.getMountPath());
        dest.setSecurityType(orig.getSecurityType());
        dest.setSubDirectory(orig.getSubDirectory());
    }

    /**
     * Method to get the list file system mounts which are mount on a host
     *
     * @param host
     *            host system URI
     * @return List<MountInfo> List of mount infos
     */
    public static List<MountInfo> queryDBHostMounts(URI host, DbClient dbClient) {
        _log.info("Querying NFS mounts for host {}", host);
        List<MountInfo> hostMounts = new ArrayList<MountInfo>();
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getHostFileMountsConstraint(host);
            List<FileMountInfo> fileMounts = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileMountInfo.class,
                    containmentConstraint);
            if (fileMounts != null && !fileMounts.isEmpty()) {
                for (FileMountInfo dbMount : fileMounts) {
                    MountInfo mountInfo = new MountInfo();
                    getMountInfo(dbMount, mountInfo);
                    hostMounts.add(mountInfo);
                }
            }
            return hostMounts;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return hostMounts;
    }
}
