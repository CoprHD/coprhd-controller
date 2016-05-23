/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.netappc;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.file.ExportRule;
import com.iwave.ext.netapp.AggregateInfo;
import com.iwave.ext.netapp.QuotaCommands.QuotaStatus;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.Qtree;
import com.iwave.ext.netappc.NFSSecurityStyle;
import com.iwave.ext.netappc.NetAppClusterFacade;
import com.iwave.ext.netappc.StorageVirtualMachineInfo;
import com.iwave.ext.netappc.model.CifsAccess;
import com.iwave.ext.netappc.model.CifsAcl;
import com.iwave.ext.netappc.model.SnapMirrorVolumeStatus;
import com.iwave.ext.netappc.model.SnapmirrorCreateParam;
import com.iwave.ext.netappc.model.SnapmirrorCronScheduleInfo;
import com.iwave.ext.netappc.model.SnapmirrorInfo;
import com.iwave.ext.netappc.model.SnapmirrorInfoResp;
import com.iwave.ext.netappc.model.SnapmirrorResp;

/*
 * Following Jiras raised for tracking. The fix will be made in the future release.
 * Jira COP-32 -Change static netAppClusterFacade in future
 * Jira COP-33 - Change the code for Inappropriate Collection call
 */
@SuppressWarnings({ "findbugs:ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", "squid:S2444", "squid:S2175" })
public class NetAppClusterApi {
    private static Map<String, String> ntpSecMap = null;
    private static Map<String, String> cifsPermissionMap = null;
    private static final String CIFS_DEFAULT_GROUP = "everyone";

    private static final String VIPR_CIFS_PERM_FULL = "full";
    private static final String VIPR_CIFS_PERM_CHANGE = "change";
    private static final String VIPR_CIFS_PERM_READ = "read";

    private static final String NTP_CIFS_PERM_FULL = "Full_Control";
    private static final String NTP_CIFS_PERM_CHANGE = "Change";
    private static final String NTP_CIFS_PERM_READ = "Read";

    private static final String ROOT_USER = "root";
    private static final String NO_ROOT_USERS = "nobody";

    private static final int DISABLE_ROOT_ACCESS_CODE = 65535;
    private static final int DEFAULT_ANONMOUS_ROOT_ACCESS = 65534;

    private static final int SIZE_KB = 1024;

    private static final String VOL_ATTR_NAME = "Name";
    private static final String VOL_ATTR_RESULT_NAME = "name";
    private static final String VOL_PERMISSION = "0777";
    private static final String VOL_ROOT = "/vol/";
    public String NetBIOSName;

    static {
        ntpSecMap = new HashMap<String, String>();
        ntpSecMap.put("sys", "System");
        ntpSecMap.put("krb5", "Kerberos 5");
    }

    static {
        cifsPermissionMap = new HashMap<String, String>();
        cifsPermissionMap.put(VIPR_CIFS_PERM_FULL, NTP_CIFS_PERM_FULL);
        cifsPermissionMap.put(VIPR_CIFS_PERM_CHANGE, NTP_CIFS_PERM_CHANGE);
        cifsPermissionMap.put(VIPR_CIFS_PERM_READ, NTP_CIFS_PERM_READ);
    }

    private static final Logger _logger = LoggerFactory
            .getLogger(NetAppClusterApi.class);

    private static NetAppClusterFacade netAppClusterFacade = null;
    private final String _userName;
    private final String _ipAddress;
    private final int _portNumber;
    private final String _password;
    private final Boolean _https;
    private final String _svmName;

    public static class Builder {
        // Required parameters
        private final String _ipAddress;
        private final Integer _portNumber;
        private final String _userName;
        private final String _password;

        // Optional parameters
        private Boolean _https = true;
        private String _svmName;

        public Builder(String ipAddress, int portNumber, String userName,
                String password) {
            this._ipAddress = ipAddress;
            this._portNumber = portNumber;
            this._userName = userName;
            this._password = password;
        }

        public Builder https(Boolean https) {
            _https = https;
            return this;
        }

        public Builder svm(String svm) {
            _svmName = svm;
            return this;
        }

        public NetAppClusterApi build() {
            return new NetAppClusterApi(this);
        }
    }

    private NetAppClusterApi(Builder builder) {
        _ipAddress = builder._ipAddress;
        _portNumber = builder._portNumber;
        _userName = builder._userName;
        _password = builder._password;
        _https = builder._https;
        _svmName = builder._svmName;
    }

    public Boolean createVolume(String volName, String aggregate, String path, String size, Boolean isThin) {
        netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                _password, _https, true, _svmName);
        String spaceReserve = "";
        if (isThin) {
            spaceReserve = "none";
        }
        Boolean status = netAppClusterFacade.createFlexibleVolume(volName, aggregate,
                path, size, spaceReserve, VOL_PERMISSION);
        if (status) {
            Collection<String> attrs = new ArrayList<String>();
            attrs.add(VOL_ATTR_NAME);
            for (int i = 0; i <= 3; i++) {
                List<Map<String, String>> fileSystemCharacterics = netAppClusterFacade
                        .listVolumeInfo(volName, attrs);
                Map<String, String> fileSystemChar = fileSystemCharacterics
                        .get(0);
                String fsName = fileSystemChar.get(VOL_ATTR_RESULT_NAME);
                if (volName.equals(fsName)) {
                    _logger.info(
                            "FS {} has been created successfully on the array",
                            fsName);
                    status = true;
                    break;
                } else {
                    _logger.info("FS not seen on the array yet, check back in few seconds");
                    status = false;
                    try {
                        Thread.sleep(3);
                    } catch (InterruptedException e) {
                        _logger.info("Failed to sleep after FS creation");
                    }
                    continue;
                }
            }
        } else {
            _logger.info("FS creation failed");
            status = false;
        }
        return status;
    }

    public Boolean createVolume(String volName, String aggregate, String path, String size, Boolean isThin, String state, String type) {
        netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                _password, _https, true, _svmName);
        String spaceReserve = "";
        if (isThin) {
            spaceReserve = "none";
        }

        String stateVol = "online";
        if (state != null && !state.isEmpty()) {
            stateVol = state;
        }

        String permissionVol = null;
        String typeVol = "rw";
        if (type != null && !type.isEmpty()) {
            typeVol = type;
            // Only volumes of type "RW" can be assigned UNIX permissions
            if ("rw".equals(type)) {
                permissionVol = VOL_PERMISSION;
            }
        }
        Boolean status = netAppClusterFacade.createFlexibleVolume(volName, aggregate,
                path, size, spaceReserve, permissionVol, stateVol, typeVol);
        if (status) {
            Collection<String> attrs = new ArrayList<String>();
            attrs.add(VOL_ATTR_NAME);
            for (int i = 0; i <= 3; i++) {
                List<Map<String, String>> fileSystemCharacterics = netAppClusterFacade
                        .listVolumeInfo(volName, attrs);
                Map<String, String> fileSystemChar = fileSystemCharacterics
                        .get(0);
                String fsName = fileSystemChar.get(VOL_ATTR_RESULT_NAME);
                if (volName.equals(fsName)) {
                    _logger.info(
                            "FS {} has been created successfully on the array",
                            fsName);
                    status = true;
                    break;
                } else {
                    _logger.info("FS not seen on the array yet, check back in few seconds");
                    status = false;
                    try {
                        Thread.sleep(3);
                    } catch (InterruptedException e) {
                        _logger.info("Failed to sleep after FS creation");
                    }
                    continue;
                }
            }
        } else {
            _logger.info("FS creation failed");
            status = false;
        }
        return status;
    }

    public Boolean createFS(String fsName, String aggregate, String size, Boolean isThin)
            throws NetAppCException {
        Boolean FailedStatus = false;
        try {
            String path = "/" + fsName;
            boolean createVolStatus = createVolume(fsName, aggregate, path, size, isThin);
            if (createVolStatus) {
                // Delete the NFS export that is created by default.
                deleteNFS(path);
            } else {
                _logger.debug("FS creation failed...");
                return FailedStatus;
            }
        } catch (Exception e) {
            throw NetAppCException.exceptions.createFSFailed(fsName, e.getMessage());
        }
        return true;
    }

    public Boolean createFS(String fsName, String aggregate, String size, Boolean isThin, String state, String type)
            throws NetAppCException {
        Boolean FailedStatus = false;
        try {
            String path = "/" + fsName;
            // Only volumes of type "RW" can be mounted during create.
            if ("dp".equals(type)) {
                path = null;
            }

            boolean createVolStatus = createVolume(fsName, aggregate, path, size, isThin, state, type);
            if (createVolStatus && path != null) {
                // Delete the NFS export that is created by default.
                deleteNFS(fsName);
            } else {
                _logger.debug("FS creation failed...");
                return FailedStatus;
            }
        } catch (Exception e) {
            throw NetAppCException.exceptions.createFSFailed(fsName, e.getMessage());
        }
        return true;
    }

    public Boolean deleteAllQTrees(String volName) throws NetAppCException {
        String qtreeName = null;
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            List<Qtree> qtrees = netAppClusterFacade.listQtrees(volName);
            if (qtrees != null && !qtrees.isEmpty()) {
                for (Qtree qtree : qtrees) {
                    qtreeName = qtree.getQtree();
                    // Skip the unnamed Qtree.
                    if (qtreeName != null && !qtreeName.isEmpty()) {
                        deleteQtree(qtreeName, volName, _svmName);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            _logger.error("Deleting the qtree {} of filesystem {} failed ", qtreeName, volName);
            throw NetAppCException.exceptions.deleteQtreeFailed(qtreeName,
                    e.getMessage());
        }
    }

    public boolean isQtree(String volName, String qtreeName) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            return netAppClusterFacade.isQtree(volName, qtreeName);
        } catch (Exception e) {
            _logger.error("Checking if {} is qtree on filesystem {} failed ", qtreeName, volName);
            throw NetAppCException.exceptions.listingQtreeFailed(qtreeName,
                    e.getMessage());
        }
    }

    public Boolean deleteFS(String volName) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            List<String> volumes = netAppClusterFacade.listVolumes();
            if (!volumes.contains(volName)) {
                _logger.info("Volume not found on array to delete {}", volName);
                return true;
            }
            // Delete Qtrees and its quotas, if any.
            deleteAllQTrees(volName);

            netAppClusterFacade.unmountVolume(volName, false);
            if (offlineVol(volName)) {
                netAppClusterFacade.destroyVolume(volName, false);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteFSFailed(volName,
                    _ipAddress, e.getMessage());
        }
    }

    public Boolean offlineVol(String volName) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            netAppClusterFacade.setVolumeOffline(volName, 1);
            return true;
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteFSFailed(volName,
                    _ipAddress, e.getMessage());
        }
    }

    public Boolean deleteNFS(String volName) throws NetAppCException {
        String exportPath = volName;
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber,
                    _userName, _password, _https, true, _svmName);
            if (volName != null && !volName.isEmpty() && !volName.startsWith("/")) {
                exportPath = "/" + volName;
            }

            List<ExportsRuleInfo> exportRules = listNFSExportRules(volName);
            if (exportRules.isEmpty()) {
                _logger.info("Export doesn't exist on the array to delete {}", exportPath);
                return true;
            }

            List<String> deletedPaths = netAppClusterFacade.deleteNFSShare(exportPath,
                    false);
            if ((deletedPaths == null) || (1 >= deletedPaths.size())) {
                _logger.error("exportPath deletion failed");
                return false;
            }

            return true;
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteNFSFailed(volName,
                    _ipAddress, e.getMessage());
        }
    }

    public Boolean deleteNFSExport(String exportPath) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber,
                    _userName, _password, _https, true, _svmName);
            List<String> deletedPaths = netAppClusterFacade.deleteNFSShare(exportPath,
                    false);
            if ((deletedPaths == null) || (1 >= deletedPaths.size())) {
                _logger.error("exportPath deletion failed");
                return false;
            }

            return true;
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteNFSFailed(exportPath,
                    _ipAddress, e.getMessage());
        }
    }

    public Boolean deleteNFSShare(String fsName, String qtreeName, ExportRule oldRule,
            String mountPath) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber,
                    _userName, _password, _https, true, _svmName);

            List<com.iwave.ext.netapp.utils.ExportRule> netAppCompatableRules = new ArrayList<>();
            com.iwave.ext.netapp.utils.ExportRule netAppOldRule = new com.iwave.ext.netapp.utils.ExportRule();
            copyPropertiesToSave(netAppOldRule, oldRule);
            netAppCompatableRules.add(netAppOldRule);

            netAppClusterFacade.deleteNFSShare(fsName, qtreeName,
                    netAppOldRule, mountPath);
            return true;
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteNFSFailed(mountPath,
                    _ipAddress, e.getMessage());
        }
    }

    public Boolean exportFS(String fsName, String qtreeName, String mountPath, String exportPath,
            List<String> rootHosts, List<String> rwHosts, List<String> roHosts,
            String root_user, String securityStyle) throws NetAppCException {
        try {
            if ((null == roHosts) && (null == rwHosts) && (null == rootHosts)) {
                _logger.debug("End points list is null...");
                return false;
            } else {

                // Add all root hosts to rw hosts as well (currently NTP GUI
                // takes care of this).
                addRootToHosts(rootHosts, rwHosts);

                // TODO: Handle multiple security Types here
                List<NFSSecurityStyle> secruityStyleList = new ArrayList<NFSSecurityStyle>();
                String lcaseSecruityStyle = securityStyle.toLowerCase();
                secruityStyleList.add(NFSSecurityStyle.valueOfName(lcaseSecruityStyle));

                // TODO: Handle all root and anonymous user mappings here.
                int rootMappingUid = 0;
                if (root_user.equals(ROOT_USER)) {
                    rootMappingUid = 0;
                } else if (root_user.equals(NO_ROOT_USERS)) {
                    rootMappingUid = DISABLE_ROOT_ACCESS_CODE;
                } else {
                    // If UID is specified other than root or nobody default it
                    // to this value.
                    rootMappingUid = DEFAULT_ANONMOUS_ROOT_ACCESS;
                }

                // Finally fire up export.
                netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber,
                        _userName, _password, _https, true, _svmName);
                netAppClusterFacade.addNFSShare(fsName, qtreeName, null, mountPath,
                        rootMappingUid, roHosts, rwHosts, rootHosts, secruityStyleList);
            }
        } catch (IllegalArgumentException e) {
            String msg = "Failed to create NFS share on path: " + (mountPath != null ? mountPath : exportPath);
            _logger.error(msg, e);
            throw NetAppCException.exceptions.exportFSFailed(mountPath, exportPath, e.getMessage());
        } catch (Exception e) {
            throw NetAppCException.exceptions.exportFSFailed(mountPath, exportPath, e.getMessage());
        }
        return true;
    }

    public Boolean addNFSShare(String fsName, String qtreeName, String exportPath,
            ExportRule newRule) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName, _password, _https, true, _svmName);
            _logger.info("NetApp Inputs for modifyNFSShare exportPath: {} ", exportPath);

            List<com.iwave.ext.netapp.utils.ExportRule> netAppCompatableRules = new ArrayList<>();
            com.iwave.ext.netapp.utils.ExportRule netAppNewRule = new com.iwave.ext.netapp.utils.ExportRule();
            copyPropertiesToSave(netAppNewRule, newRule);
            netAppCompatableRules.add(netAppNewRule);

            netAppClusterFacade.addNFSShare(fsName, qtreeName, exportPath, netAppNewRule);

        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppCException.exceptions.exportFSFailed(exportPath,
                    exportPath, e.getMessage());
        }
        return true;
    }

    public void addRootToHosts(List<String> rootHosts, List<String> rwHosts) {
        if (null != rootHosts) {
            for (String rootHost : rootHosts) {
                if ((null != rwHosts) && (!(rwHosts.contains(rootHosts)))) {
                    rwHosts.add(rootHost);
                }
            }
        }
    }

    public Boolean unexportFS(String mountPath, String exportPath)
            throws NetAppCException {
        return true;
    }

    public List<ExportsRuleInfo> listNFSExportRules(String pathName)
            throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            return netAppClusterFacade.listNFSExportRules(pathName);
        } catch (Exception e) {
            throw NetAppCException.exceptions.listNFSExportRulesFailed(pathName);
        }
    }

    public Boolean setVolumeSize(String volume, String newSize)
            throws NetAppCException {

        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            String cmdResult = netAppClusterFacade.setVolumeSize(volume, newSize);
            // Return value is a empty string if the operation is not success
            if (cmdResult == null || cmdResult.equalsIgnoreCase("")) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            throw NetAppCException.exceptions.setVolumeSizeFailed(volume, newSize);
        }
    }

    public List<Map<String, String>> listVolumeInfo(String volume,
            Collection<String> attrs) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.listVolumeInfo(volume, attrs);
        } catch (Exception e) {
            throw NetAppCException.exceptions.listVolumeInfoFailed(volume);
        }
    }

    public List<AggregateInfo> listClusterAggregates(String name)
            throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.listAggregates(name);
        } catch (Exception e) {
            throw NetAppCException.exceptions.listAggregatesFailed(name);
        }
    }

    public List<StorageVirtualMachineInfo> listSVM() {
        List<StorageVirtualMachineInfo> svms = null;
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            svms = netAppClusterFacade.listSVM();
        } catch (Exception e) {
            _logger.info("No vSevrers discovered.");
        }

        return svms;
    }

    public Map<String, String> systemInfo() throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.systemInfo();
        } catch (Exception e) {
            throw NetAppCException.exceptions.systemInfoFailed(_ipAddress, e.getMessage());
        }
    }

    public Map<String, String> clusterSystemInfo() throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.systemInfo();
        } catch (Exception e) {
            throw NetAppCException.exceptions.systemInfoFailed(_ipAddress, e.getMessage());
        }
    }

    public Map<String, String> systemVer() throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            Map<String, String> info = netAppClusterFacade.systemVersion();
            Map<String, String> versionInfo = new HashMap<String, String>();
            String version = info.get("version");
            // Sample NetApp Release 8.1.2 7-Mode: Tue Oct 30 19:56:51 PDT 2012
            // Sample 8.1.1xsdf
            String[] versInfo = version.split(" ");
            String[] parseVersion = versInfo[2].split("\\.");
            String convertedVers = "";
            for (int i = 0; i < parseVersion.length; i++) {
                _logger.info(parseVersion[i]);
                Number num = NumberFormat.getInstance().parse(parseVersion[i]).intValue();
                convertedVers = convertedVers + num.toString() + ".";
            }
            convertedVers = convertedVers.substring(0, convertedVers.length() - 1);
            _logger.info("Converted Version info {}", convertedVers);
            versionInfo.put("version", convertedVers);
            versionInfo.put("mode", versInfo[3]);
            versionInfo.put("is-clustered", info.get("is-clustered"));
            return versionInfo;
        } catch (Exception e) {
            throw new NetAppCException(
                    "Exception listing the system information on NTAP array " + e.getMessage());
        }
    }

    public Boolean createSnapshot(String volumeName, String snapshotName)
            throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            return netAppClusterFacade.createVolumeSnapshot(volumeName, snapshotName);
        } catch (Exception e) {
            throw NetAppCException.exceptions.createSnapshotFailed(volumeName,
                    snapshotName, _ipAddress, e.getMessage());
        }
    }

    public Boolean deleteSnapshot(String volumeName, String snapshotName)
            throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            List<String> snapshots = (List<String>) netAppClusterFacade
                    .listSnapshots(volumeName);
            if ((null != snapshots) && (!snapshots.isEmpty())) {
                if (snapshots.toString().contains(snapshotName)) {
                    return netAppClusterFacade.deleteVolumeSnapshot(volumeName, snapshotName);
                }
            }
            return true;
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteSnapshotFailed(volumeName,
                    snapshotName, _ipAddress, e.getMessage());
        }
    }

    public Boolean restoreSnapshot(String volumeName, String snapshotName)
            throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            return netAppClusterFacade.restoreVolumeFromSnapshot(volumeName, snapshotName);
        } catch (Exception e) {
            throw NetAppCException.exceptions.restoreSnapshotFailed(volumeName,
                    snapshotName, _ipAddress, e.getMessage());
        }
    }

    public static NetAppClusterFacade getNetAppFacade() {
        return netAppClusterFacade;
    }

    public static void setNetAppFacade(NetAppClusterFacade netAppClusterFacade) {
        NetAppClusterApi.netAppClusterFacade = netAppClusterFacade;
    }

    public static Logger getLogger() {
        return _logger;
    }

    public String getIpAddress() {
        return _ipAddress;
    }

    public int getPortNumber() {
        return _portNumber;
    }

    public String getPassword() {
        return _password;
    }

    public Boolean getHttps() {
        return _https;
    }

    public String getSvmName() {
        return _svmName;
    }

    public String getNetBiosName() {
        return NetBIOSName;
    }

    public void setNetBios(String NetBIOSName) {
        this.NetBIOSName = NetBIOSName;
    }

    public Boolean doShare(String mntpath, String shareName, String comment, int maxusers, String permission, String forcegroup)
            throws NetAppCException {
        try {
            String mountPath;
            if (mntpath.startsWith("/")) {
                mountPath = mntpath;
            } else {
                mountPath = "/" + mntpath;
            }
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            if (!netAppClusterFacade.addCIFSShare(mountPath, shareName, comment, maxusers, forcegroup)) {
                return false;
            }
            List<Map<String, String>> ShareInfo = netAppClusterFacade.listCIFSShares(shareName);
            this.NetBIOSName = ShareInfo.get(0).get("cifs-server");
            return true;

        } catch (Exception e) {
            throw NetAppCException.exceptions.doShareFailed(mntpath,
                    shareName, _ipAddress, e.getMessage());
        }
    }

    public void setQtreemode(String volPath, String mode) throws NetAppCException {
        try {

            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            netAppClusterFacade.setQTreeSecurityStyle(volPath, mode);

        } catch (Exception e) {
            throw NetAppCException.exceptions.setVolumeQtreeModeFailed(volPath, mode);
        }
    }

    public Boolean modifyShare(String mntpath, String shareName, String comment, int maxusers, String permission, String forcegroup)
            throws NetAppCException {
        try {

            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            CifsAcl acl = new CifsAcl();
            acl.setShareName(shareName);
            acl.setAccess(CifsAccess.valueOfAccess(cifsPermissionMap
                    .get(permission)));
            acl.setUserName(CIFS_DEFAULT_GROUP);
            netAppClusterFacade.setCIFSAcl(acl);
            return true;
        } catch (Exception e) {
            throw NetAppCException.exceptions.modifyShareFailed(_ipAddress, e.getMessage());
        }
    }

    public boolean deleteShare(String shareName) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            netAppClusterFacade.deleteCIFSShare(shareName);
            return true;
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteShareFailed(_ipAddress, e.getMessage());
        }
    }

    public void modifyShare(String shareName, Map<String, String> attrs) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            netAppClusterFacade.changeCIFSShare(shareName, attrs);
        } catch (Exception e) {
            throw NetAppCException.exceptions.modifyShareNameFailed(shareName,
                    _ipAddress, e.getMessage());
        }
    }

    public List<String> listFileSystems() throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.listVolumes();
        } catch (Exception e) {
            throw NetAppCException.exceptions.listFileSystems(_ipAddress, e.getMessage());
        }
    }

    public List<Map<String, String>> listShares(String shareName) throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            return netAppClusterFacade.listCIFSShares(shareName);
        } catch (Exception e) {
            throw NetAppCException.exceptions.listSharesFailed(shareName,
                    _ipAddress, e.getMessage());
        }
    }

    public List<String> listSnapshots(String volumeName) throws NetAppCException {
        List<String> snapshots = null;
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);
            snapshots = (List<String>) netAppClusterFacade
                    .listSnapshots(volumeName);
        } catch (Exception e) {
            String[] params = { volumeName, e.getMessage() };
            _logger.info("Failed to retrieve list of snapshots for {} due to {}", params);
        }
        return snapshots;
    }

    private String constructQtreePath(String volumeName, String qtreeName) throws NetAppCException {

        String qtreePath = null;
        if (volumeName.contains(VOL_ROOT)) {
            if (volumeName.endsWith("/")) {
                // i.e. volume name is something like /vol/lookAtMe/
                qtreePath = volumeName + qtreeName;
            } else {
                // i.e. volume name is something like /vol/lookAtMe
                qtreePath = volumeName + "/" + qtreeName;
            }
        } else {
            // i.e. volume name is something like "lookAtMe"
            qtreePath = "/vol/" + volumeName + "/" + qtreeName;
        }

        _logger.info("NetAppClusterApi::createQtree -> qtreePath = {}", qtreePath);
        return qtreePath;

    }

    // New QTree methods
    public void createQtree(String qtreeName, String volumeName, Boolean opLocks, String securityStyle, Long size, String vfilerName)
            throws NetAppCException {
        try {

            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);

            netAppClusterFacade.createQtree(qtreeName, volumeName, opLocks, securityStyle);

            String qtreePath = constructQtreePath(volumeName, qtreeName);
            // Set the size - Quota
            if (size > 0) {
                netAppClusterFacade.addDiskLimitTreeQuota(volumeName, qtreePath, size / SIZE_KB, 0);
                // Enable the quota. some times, quota wont be enabled.
                // especially, when we create quota on multi-store environment.
                try {
                    QuotaStatus quotaStatus = netAppClusterFacade.getQuotaStatus(volumeName);
                    if (quotaStatus.OFF == quotaStatus) {
                        netAppClusterFacade.turnQuotaOn(volumeName);
                    } else {
                        // Resizing only works for certain types of changes to the quotas file.
                        // For other changes, you need to reinitialize quotas.
                        netAppClusterFacade.reintializeQuota(volumeName);
                    }
                    _logger.info("Quota status on volume {} is {}. ", volumeName, quotaStatus.toString());
                } catch (Exception e) {
                    _logger.warn("Quota status on volume {} is not stable. ", volumeName);
                }
            }

        } catch (Exception e) {
            _logger.info("NetAppClusterApi::createQtree -> e.getMessage() = {}", e.getMessage());
            throw NetAppCException.exceptions.createQtreeFailed(qtreeName, e.getMessage());
        }
    }

    public void deleteQtree(String qtreeName, String volumeName, String vfilerName) throws NetAppCException {
        try {

            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);

            String qtreePath = constructQtreePath(volumeName, qtreeName);
            // TODO : Force delete option ?

            // Before deleting the qtree, delete the quota associated with the tree.
            if (netAppClusterFacade.getTreeQuota(volumeName, qtreePath) != null) {
                netAppClusterFacade.deleteTreeQuota(volumeName, qtreePath);
            }

            // Now delete the qtree.

            netAppClusterFacade.deleteQtree(qtreePath, true);
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteQtreeFailed(qtreeName, e.getMessage());
        }
    }

    public void updateQtree(String qtreeName, String volumeName, Boolean opLocks, String securityStyle, Long size, String vfilerName)
            throws NetAppCException {
        try {

            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, true, _svmName);

            netAppClusterFacade.updateQtree(qtreeName, volumeName, opLocks, securityStyle);

            String qtreePath = constructQtreePath(volumeName, qtreeName);

            // Modify the quota
            if (size > 0) {
                netAppClusterFacade.setDiskLimitTreeQuota(volumeName, qtreePath, size / SIZE_KB, 0);
                try {
                    QuotaStatus quotaStatus = netAppClusterFacade.getQuotaStatus(volumeName);
                    if (quotaStatus.OFF == quotaStatus) {
                        netAppClusterFacade.turnQuotaOn(volumeName);
                    } else {
                        // Resizing only works for certain types of changes to the quotas file.
                        // For other changes, you need to reinitialize quotas.
                        netAppClusterFacade.reintializeQuota(volumeName);
                    }

                    // QuotaStatus quotaStatus = netAppClusterFacade.getQuotaStatus(volumeName);
                    _logger.info("Quota status on volume {} is {}. ", volumeName, quotaStatus.toString());
                } catch (Exception e) {
                    _logger.warn("Quota status on volume {} is not stable. ", volumeName);
                }

            }

        } catch (Exception e) {
            throw NetAppCException.exceptions.createQtreeFailed(qtreeName, e.getMessage());
        }
    }

    public Boolean modifyNFSShare(String fsName, String qtreeName, String exportPath,
            ExportRule oldRule, ExportRule newRule)
            throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName, _password, _https, true, _svmName);
            _logger.info("NetApp Inputs for modifyNFSShare exportPath: {} ", exportPath);

            List<com.iwave.ext.netapp.utils.ExportRule> netAppCompatableRules = new ArrayList<>();
            com.iwave.ext.netapp.utils.ExportRule netAppOldRule = new com.iwave.ext.netapp.utils.ExportRule();
            copyPropertiesToSave(netAppOldRule, oldRule);
            netAppCompatableRules.add(netAppOldRule);
            com.iwave.ext.netapp.utils.ExportRule netAppNewRule = new com.iwave.ext.netapp.utils.ExportRule();
            copyPropertiesToSave(netAppNewRule, newRule);
            netAppCompatableRules.add(netAppNewRule);

            netAppClusterFacade.modifyNFSShare(fsName, qtreeName, exportPath, netAppOldRule, netAppNewRule);

        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppCException.exceptions.exportFSFailed(exportPath,
                    exportPath, e.getMessage());
        }
        return true;
    }

    private void copyPropertiesToSave(com.iwave.ext.netapp.utils.ExportRule dest, ExportRule orig) {

        dest.setFsID(orig.getFsID());
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        if (orig.getReadOnlyHosts() != null && !orig.getReadOnlyHosts().isEmpty()) {
            dest.setReadOnlyHosts(orig.getReadOnlyHosts());
        }
        if (orig.getReadWriteHosts() != null && !orig.getReadWriteHosts().isEmpty()) {
            dest.setReadWriteHosts(orig.getReadWriteHosts());
        }
        if (orig.getRootHosts() != null && !orig.getRootHosts().isEmpty()) {
            dest.setRootHosts((orig.getRootHosts()));
        }

    }

    public List<CifsAcl> listCIFSShareAcl(
            String shareName) {
        try {

            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName, _password, _https, true, _svmName);
            _logger.info("Facade created : {} ", netAppClusterFacade);

            List<CifsAcl> oldAcls = netAppClusterFacade.listCIFSAcls(shareName);
            return oldAcls;

        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppCException.exceptions.listCIFSShareAclFailed(shareName, e.getMessage());
        }
    }

    public boolean modifyCIFSShareAcl(String shareName,
            List<CifsAcl> acls) {
        try {

            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName, _password, _https, true, _svmName);
            _logger.info("Facade created : {} ", netAppClusterFacade);

            for (CifsAcl acl : acls) {
                acl.setShareName(shareName);
                netAppClusterFacade.setCIFSAcl(acl);

            }
        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppCException.exceptions.modifyCifsShareAclFailed(shareName, e.getMessage());
        }
        return true;

    }

    public boolean addCIFSShareAcl(String shareName,
            List<CifsAcl> acls) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName, _password, _https, true, _svmName);
            _logger.info("Facade created : {} ", netAppClusterFacade);

            for (CifsAcl acl : acls) {
                acl.setShareName(shareName);
                netAppClusterFacade.addCIFSAcl(acl);

            }
        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppCException.exceptions.addCifsShareAclFailed(shareName, e.getMessage());
        }
        return true;

    }

    public boolean deleteCIFSShareAcl(String shareName,
            List<CifsAcl> acls) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName, _password, _https, true, _svmName);
            _logger.info("Facade created : {} ", netAppClusterFacade);

            for (CifsAcl acl : acls) {
                acl.setShareName(shareName);
                netAppClusterFacade.deleteCIFSAcl(acl);

            }
        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppCException.exceptions.deleteCIFSShareAclFailed(shareName, e.getMessage());
        }
        return true;

    }

    // snap mirror operations

    public SnapmirrorInfoResp createSnapMirror(SnapmirrorCreateParam snapMirrorCreateParam)
            throws NetAppCException {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.createSnapmirror(snapMirrorCreateParam);
        } catch (Exception e) {
            throw NetAppCException.exceptions.createSnapMirrorFailed(snapMirrorCreateParam.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public boolean initialiseSnapMirror(String destLocation) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.initialiseSnapMirror(destLocation);
        } catch (Exception e) {
            throw NetAppCException.exceptions.initializeSnapMirrorFailed(destLocation, _ipAddress, e.getMessage());
        }
    }

    public boolean breakSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.breakSnapMirror(snapMirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.breakSnapMirrorFailed(snapMirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public SnapmirrorResp deleteSnapMirrorAsync(SnapmirrorInfo snapMirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.deleteSnapMirrorAsync(snapMirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteSnapMirrorFailed(snapMirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public SnapmirrorResp resyncSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.resyncSnapMirror(snapMirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.resyncSnapMirrorFailed(snapMirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public boolean quienceSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.quienceSnapMirror(snapMirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.quiesceSnapMirrorFailed(snapMirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public boolean resumeSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.resumeSnapMirror(snapMirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.resumeSnapMirrorFailed(snapMirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public boolean releaseSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.releaseSnapMirror(snapMirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.releaseSnapMirrorFailed(snapMirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public boolean destorySnapMirror(SnapmirrorInfo snapMirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.destroySnapMirror(snapMirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteSnapMirrorFailed(snapMirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public boolean abortSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.abortSnapMirror(snapMirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.abortSnapMirrorFailed(snapMirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    public Boolean checkSnapMirrorLicense() {
        Boolean licenseExists = false;
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            licenseExists = netAppClusterFacade.checkSnapMirrorLicense();
        } catch (Exception e) {
            throw NetAppCException.exceptions.checkSnapMirrorLicenseFailed(_ipAddress, e.getMessage());
        }

        return licenseExists;
    }

    public SnapmirrorInfoResp getSnapMirrorInfo(String destPath) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.getSnapMirrorInfo(destPath);
        } catch (Exception e) {
            throw NetAppCException.exceptions.getSnapMirrorStatusFailed(destPath, _ipAddress, e.getMessage());
        }
    }

    public SnapMirrorVolumeStatus getSnapMirrorVolumeStatus(String volume) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.getSnapMirrorVolumeStatus(volume);
        } catch (Exception e) {
            throw NetAppCException.exceptions.getSnapMirrorVolumeStatus(volume, _ipAddress, e.getMessage());
        }
    }

    public SnapmirrorInfoResp getSnapMirrorDestInfo(SnapmirrorInfo mirrorInfo) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.getSnapMirrorDestInfo(mirrorInfo);
        } catch (Exception e) {
            throw NetAppCException.exceptions.getSnapMirrorStatusFailed(mirrorInfo.getSourceVolume(), _ipAddress, e.getMessage());
        }
    }

    // NetappC mode cron schedule operations

    public SnapmirrorCronScheduleInfo getCronSchedule(String name) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.getCronSchedule(name);
        } catch (Exception e) {
            throw NetAppCException.exceptions.getCronScheduleFailed(name, _ipAddress, e.getMessage());
        }
    }

    public SnapmirrorCronScheduleInfo createCronSchedule(String fsRpoValue, String fsRpoType, String name) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.createCronSchedule(fsRpoValue, fsRpoType, name);
        } catch (Exception e) {
            throw NetAppCException.exceptions.createCronScheduleFailed(name, _ipAddress, e.getMessage());
        }
    }

    public SnapmirrorCronScheduleInfo modifyCronSchedule(String fsRpoValue, String fsRpoType, String name) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.modifyCronSchedule(fsRpoValue, fsRpoType, name);
        } catch (Exception e) {
            throw NetAppCException.exceptions.modifyCronScheduleFailed(name, _ipAddress, e.getMessage());
        }
    }

    public boolean deleteCronSchedule(String name) {
        try {
            netAppClusterFacade = new NetAppClusterFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppClusterFacade.deleteCronSchedule(name);
        } catch (Exception e) {
            throw NetAppCException.exceptions.deleteCronScheduleFailed(name, _ipAddress, e.getMessage());
        }
    }

}
