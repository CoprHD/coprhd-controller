/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.netapp;

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
import com.iwave.ext.netapp.NFSSecurityStyle;
import com.iwave.ext.netapp.NetAppFacade;
import com.iwave.ext.netapp.QuotaCommands.QuotaStatus;
import com.iwave.ext.netapp.VFilerInfo;
import com.iwave.ext.netapp.VolumeOptionType;
import com.iwave.ext.netapp.model.CifsAccess;
import com.iwave.ext.netapp.model.CifsAcl;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.Qtree;

@SuppressWarnings({ "findbugs:ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", "squid:S2175", "squid:S2444" })
/*
 * Following Jiras raised for tracking. The fix will be made in the future release
 * Jira COP-32 -Change static netAppFascade in future
 * Jira COP-33 - Change the code for Inappropriate Collection call
 */
public class NetAppApi {
    private static Map<String, String> ntpSecMap = null;
    private static Map<String, String> cifsPermissionMap = null;
    private static final String CIFS_DEFAULT_GROUP = "everyone";

    private static final String VIPR_CIFS_PERM_FULL = "full";
    private static final String VIPR_CIFS_PERM_CHANGE = "change";
    private static final String VIPR_CIFS_PERM_READ = "read";

    private static final String NTP_CIFS_PERM_FULL = "Full Control";
    private static final String NTP_CIFS_PERM_CHANGE = "Change";
    private static final String NTP_CIFS_PERM_READ = "Read";

    private static final String ROOT_USER = "root";
    private static final String NO_ROOT_USERS = "nobody";

    private static final int DISABLE_ROOT_ACCESS_CODE = 65535;
    private static final int DEFAULT_ANONMOUS_ROOT_ACCESS = 65534;

    private static final int SIZE_KB = 1024;

    private static final String SPACE_GUARANTEE_PARAM = "none";
    private static final String CONVERT_UCODE_ON = "on";
    private static final String CREATE_UCODE_ON = "on";
    private static final String VOL_ATTR_NAME = "Name";
    private static final String VOL_ATTR_RESULT_NAME = "name";

    private static final String DEFAULT_VFILER = "vfiler0";
    private static final String VOL_ROOT = "/vol/";

    private static final String UNIX = "unix";
    private static final String NTFS = "ntfs";
    private static final String MIXED = "mixed";

    private static final String ENABLE = "enable";
    private static final String DISABLE = "disable";
    public String NetBIOSName;

    static {
        ntpSecMap = new HashMap<String, String>();
        ntpSecMap.put("sys", "System");
        ntpSecMap.put("krb5", "Kerberos 5");
        ntpSecMap.put("krb5i", "Kerberos 5i");
        ntpSecMap.put("krb5p", "Kerberos 5p");
    }

    static {
        cifsPermissionMap = new HashMap<String, String>();
        cifsPermissionMap.put(VIPR_CIFS_PERM_FULL, NTP_CIFS_PERM_FULL);
        cifsPermissionMap.put(VIPR_CIFS_PERM_CHANGE, NTP_CIFS_PERM_CHANGE);
        cifsPermissionMap.put(VIPR_CIFS_PERM_READ, NTP_CIFS_PERM_READ);
    }
    private static final Logger _logger = LoggerFactory
            .getLogger(NetAppApi.class);

    private static NetAppFacade netAppFacade = null;
    private final String _userName;
    private final String _ipAddress;
    private final int _portNumber;
    private final String _password;
    private final Boolean _https;
    private final String _vFilerName;

    public static class Builder {
        // Required parameters
        private final String _ipAddress;
        private final Integer _portNumber;
        private final String _userName;
        private final String _password;

        // Optional parameters
        private Boolean _https = true;
        private String _vFilerName;

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

        public Builder vFiler(String vFiler) {
            _vFilerName = vFiler;
            return this;
        }

        public NetAppApi build() {
            return new NetAppApi(this);
        }
    }

    private NetAppApi(Builder builder) {
        _ipAddress = builder._ipAddress;
        _portNumber = builder._portNumber;
        _userName = builder._userName;
        _password = builder._password;
        _https = builder._https;
        _vFilerName = builder._vFilerName;
    }

    public Boolean createVolume(String volName, String aggregate, String size, Boolean isThin) {
        netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                _password, _https);
        String spaceReserve = "";
        if (isThin) {
            spaceReserve = SPACE_GUARANTEE_PARAM;
        }
        Boolean status = netAppFacade.createFlexibleVolume(volName, aggregate,
                false, null, size, null, spaceReserve, false, null);
        if (status) {
            Collection<String> attrs = new ArrayList<String>();
            attrs.add(VOL_ATTR_NAME);
            for (int i = 0; i <= 3; i++) {
                List<Map<String, String>> fileSystemCharacterics = netAppFacade
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
                    _logger.info("FS not see on the array yet, check back in few seconds");
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
        Map<VolumeOptionType, String> options = new HashMap<VolumeOptionType, String>();
        options.put(VolumeOptionType.convert_ucode, CONVERT_UCODE_ON);
        options.put(VolumeOptionType.create_ucode, CREATE_UCODE_ON);
        netAppFacade.setVolumeOptions(volName, options);

        // If vFiler enabled, need to add storage to vFiler.
        if (status && _vFilerName != null && !_vFilerName.equals(DEFAULT_VFILER)) {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, DEFAULT_VFILER);

            try {
                status = netAppFacade.addStorage(VOL_ROOT + volName, _vFilerName);

                // If adding a volume to a vfiler fails, then delete the volume.
                if (!status) {
                    _logger.error("Adding volume {} to vfiler {} failed", volName, _vFilerName);
                    deleteFS(volName);
                }
            } catch (Exception e) {
                // If adding a volume to a vfiler fails, then delete the volume.
                _logger.error("Exception when adding volume {} to vfiler {}.", volName, _vFilerName);
                deleteFS(volName);
                throw e;
            }

        }
        return status;
    }

    public Boolean createFS(String fsName, String aggregate, String size, Boolean isThin)
            throws NetAppException {
        Boolean FailedStatus = false;
        try {
            boolean createVolStatus = createVolume(fsName, aggregate, size, isThin);
            if (createVolStatus) {
                // Delete the NFS export that is created by default.
                deleteNFS("/vol/" + fsName);
            } else {
                _logger.debug("FS creation failed...");
                return FailedStatus;
            }
        } catch (Exception e) {
            throw NetAppException.exceptions.createFSFailed(fsName, e.getMessage());
        }
        return true;
    }

    public Boolean deleteFS(String volName) throws NetAppException {
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            List<String> volumes = netAppFacade.listVolumes();
            if (!volumes.contains(volName)) {
                _logger.info("Volume not found on array to delete {}", volName);
                return true;
            }
            // Delete Qtrees and its quotas, if any.
            deleteAllQTrees(volName);

            if (offlineVol(volName)) {
                netAppFacade.destroyVolume(volName, false);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw NetAppException.exceptions.deleteFSFailed(volName, _ipAddress, e.getMessage());
        }
    }

    public Boolean offlineVol(String volName) throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            netAppFacade.setVolumeOffline(volName, 1);
            return true;
        } catch (Exception e) {
            throw NetAppException.exceptions.deleteFSFailed(volName,
                    _ipAddress, e.getMessage());
        }
    }

    public Boolean deleteAllQTrees(String volName) throws NetAppException {
        String qtreeName = null;
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            List<Qtree> qtrees = netAppFacade.listQtrees(volName);
            if (qtrees != null && !qtrees.isEmpty()) {
                for (Qtree qtree : qtrees) {
                    qtreeName = qtree.getQtree();
                    // Skip the unnamed Qtree.
                    if (qtreeName != null && !qtreeName.isEmpty()) {
                        deleteQtree(qtreeName, volName, _vFilerName);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            _logger.error("Deleting the qtree {} of filesystem {} failed ", qtreeName, volName);
            throw NetAppException.exceptions.deleteQtreeFailed(qtreeName,
                    e.getMessage());
        }
    }

    public Boolean deleteNFS(String volName) throws NetAppException {
        String exportPath = volName;

        try {

            if (volName != null && !volName.isEmpty() && !volName.startsWith("/"))
            {
                exportPath = "/" + volName;
            }

            List<ExportsRuleInfo> exportRules = listNFSExportRules(volName);
            if (exportRules.isEmpty()) {
                _logger.info("Export doesn't exist on the array delete {}", exportPath);
                return true;
            }

            List<String> deletedPaths = netAppFacade.deleteNFSShare(exportPath,
                    false);
            if ((deletedPaths == null) || (1 >= deletedPaths.size())) {
                _logger.error("exportPath deletion failed");
                return false;
            }

            return true;
        } catch (Exception e) {
            throw NetAppException.exceptions.deleteNFSFailed(volName,
                    _ipAddress, e.getMessage());
        }
    }

    public Boolean deleteNFSExport(String exportPath) throws NetAppException {
        // String exportPath = "/" + volName;

        try {

            // List<ExportsRuleInfo> exportRules = listNFSExportRules(volName);
            // if (exportRules.size() < 1) {
            // _logger.info("Export doesn't exist on the array delete {}", exportPath);
            // return true;
            // }

            List<String> deletedPaths = netAppFacade.deleteNFSShare(exportPath,
                    false);
            if ((deletedPaths == null) || (1 >= deletedPaths.size())) {
                _logger.error("exportPath deletion failed");
                return false;
            }

            return true;
        } catch (Exception e) {
            throw NetAppException.exceptions.deleteNFSFailed(exportPath,
                    _ipAddress, e.getMessage());
        }
    }

    public Boolean deleteAllNFSExportRules(String exportPath) throws NetAppException {
        List<String> deletedPaths = netAppFacade.deleteNFSShare(exportPath, true);
        if ((deletedPaths == null) || (1 >= deletedPaths.size())) {
            _logger.error("exportPath deletion failed");
            return false;
        }
        return true;
    }

    public Boolean exportFS(String mountPath, String exportPath,
            List<String> rootHosts, List<String> rwHosts, List<String> roHosts,
            String root_user, String securityStyle) throws NetAppException {
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
                secruityStyleList.add(NFSSecurityStyle.valueOfLabel(ntpSecMap
                        .get(lcaseSecruityStyle)));

                // Handle all the hosts permissions here.
                boolean roAddAll = false;
                boolean rwAddAll = false;
                boolean rootAddAll = false;

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
                netAppFacade = new NetAppFacade(_ipAddress, _portNumber,
                        _userName, _password, _https, _vFilerName);
                List<String> FsList = netAppFacade.addNFSShare(null, mountPath,
                        rootMappingUid, roHosts, roAddAll, rwHosts, rwAddAll,
                        rootHosts, rootAddAll, secruityStyleList);

                if (FsList.isEmpty()) {
                    return false;
                }

            }
        } catch (Exception e) {
            throw NetAppException.exceptions.exportFSFailed(mountPath, exportPath, e.getMessage());
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
            throws NetAppException {

        try {
            _logger.debug("Un-Exporting NFS share...");

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, _vFilerName);

            netAppFacade.deleteNFSShare(mountPath, false);
        } catch (Exception e) {
            throw NetAppException.exceptions.unexportFSFailed(mountPath, exportPath, e.getMessage());
        }
        return true;
    }

    public List<ExportsRuleInfo> listNFSExportRules(String pathName)
            throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.listNFSExportRules(pathName);
        } catch (Exception e) {
            throw NetAppException.exceptions.listNFSExportRulesFailed(pathName);
        }
    }

    public Boolean setVolumeSize(String volume, String newSize)
            throws NetAppException {

        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            String cmdResult = netAppFacade.setVolumeSize(volume, newSize);
            // Return value is a empty string if the operation is not success
            if (cmdResult == null || cmdResult.equalsIgnoreCase("")) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            throw NetAppException.exceptions.setVolumeSizeFailed(volume, newSize);
        }
    }

    public List<Map<String, String>> listVolumeInfo(String volume,
            Collection<String> attrs) throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.listVolumeInfo(volume, attrs);
        } catch (Exception e) {
            throw NetAppException.exceptions.listVolumeInfoFailed(volume);
        }
    }

    public List<AggregateInfo> listAggregates(String name)
            throws NetAppException {
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.listAggregates(name);
        } catch (Exception e) {
            throw NetAppException.exceptions.listAggregatesFailed(name);
        }
    }

    public List<VFilerInfo> listVFilers(String name) {
        List<VFilerInfo> vFilers = null;
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            vFilers = netAppFacade.listVFilers();
        } catch (Exception e) {
            _logger.info("No vFilers discovered.");
        }

        return vFilers;
    }

    public Map<String, String> systemInfo() throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.systemInfo();
        } catch (Exception e) {
            throw NetAppException.exceptions.systemInfoFailed(_ipAddress, e.getMessage());
        }
    }

    public Map<String, String> systemVer() throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            Map<String, String> info = netAppFacade.systemVersion();
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
            throw new NetAppException(
                    "Exception listing the system information on NTAP array " + e.getMessage());
        }
    }

    public Boolean createSnapshot(String volumeName, String snapshotName)
            throws NetAppException {
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.createVolumeSnapshot(volumeName, snapshotName,
                    false);
        } catch (Exception e) {
            throw NetAppException.exceptions.createSnapshotFailed(volumeName,
                    snapshotName, _ipAddress, e.getMessage());
        }
    }

    public Boolean deleteSnapshot(String volumeName, String snapshotName)
            throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            List<String> snapshots = (List<String>) netAppFacade
                    .listSnapshots(volumeName);
            if ((null != snapshots) && (!snapshots.isEmpty())) {
                if (snapshots.toString().contains(snapshotName)) {
                    return netAppFacade.deleteVolumeSnapshot(volumeName, snapshotName);
                }
            }
            return true;
        } catch (Exception e) {
            throw NetAppException.exceptions.deleteSnapshotFailed(volumeName,
                    snapshotName, _ipAddress, e.getMessage());
        }
    }

    public Boolean restoreSnapshot(String volumeName, String snapshotName)
            throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.restoreVolumeFromSnapshot(volumeName, snapshotName);
        } catch (Exception e) {
            throw NetAppException.exceptions.restoreSnapshotFailed(volumeName,
                    snapshotName, _ipAddress, e.getMessage());
        }
    }

    public static NetAppFacade getNetAppFacade() {
        return netAppFacade;
    }

    public static void setNetAppFacade(NetAppFacade netAppFacade) {
        NetAppApi.netAppFacade = netAppFacade;
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

    // get CIFS server NetBIOS Name
    public String getNetBiosName() {
        return NetBIOSName;
    }

    public void setNetBios(String NetBIOSName) {
        this.NetBIOSName = NetBIOSName;
    }

    public Boolean doShare(String mntpath, String shareName, String comment, int maxusers, String permission, String forcegroup)
            throws NetAppException {
        try {
            String mountPath;
            if (mntpath.startsWith("/vol")) {
                mountPath = mntpath;
            } else {
                mountPath = "/vol" + mntpath;
            }
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, _vFilerName);
            if (!netAppFacade.addCIFSShare(mountPath, shareName, comment, maxusers, forcegroup)) {
                return false;
            }
            Map<String, String> CIFS_Config = netAppFacade.listCIFSConfig();
            this.NetBIOSName = CIFS_Config.get("NetBIOS-servername");
            return true;

        } catch (Exception e) {
            throw NetAppException.exceptions.doShareFailed(mntpath,
                    shareName, _ipAddress, e.getMessage());
        }
    }

    public void setQtreemode(String volPath, String mode) throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, _vFilerName);
            netAppFacade.setQTreeSecurityStyle(volPath, mode);

        } catch (Exception e) {
            throw NetAppException.exceptions.setVolumeQtreeModeFailed(volPath, mode);
        }
    }

    public Boolean modifyShare(String mntpath, String shareName, String comment, int maxusers, String permission, String forcegroup)
            throws NetAppException {
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, _vFilerName);
            CifsAcl acl = new CifsAcl();
            acl.setShareName(shareName);
            acl.setAccess(CifsAccess.valueOfAccess(cifsPermissionMap
                    .get(permission)));
            acl.setUserName(CIFS_DEFAULT_GROUP);
            netAppFacade.setCIFSAcl(acl);
            return true;
        } catch (Exception e) {
            throw NetAppException.exceptions.modifyShareFailed(_ipAddress, e.getMessage());
        }
    }

    public boolean deleteShare(String shareName) throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, _vFilerName);
            netAppFacade.deleteCIFSShare(shareName);
            return true;
        } catch (Exception e) {
            throw NetAppException.exceptions.deleteShareFailed(_ipAddress, e.getMessage());
        }
    }

    public void modifyShare(String shareName, Map<String, String> attrs) throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            netAppFacade.changeCIFSShare(shareName, attrs);
        } catch (Exception e) {
            throw NetAppException.exceptions.modifyShareNameFailed(shareName,
                    _ipAddress, e.getMessage());
        }
    }

    public List<Map<String, String>> listShares(String shareName) throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.listCIFSShares(shareName);
        } catch (Exception e) {
            throw NetAppException.exceptions.listSharesFailed(shareName,
                    _ipAddress, e.getMessage());
        }
    }

    public List<String> listFileSystems() throws NetAppException {
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.listVolumes();
        } catch (Exception e) {
            throw NetAppException.exceptions.listFileSystems(_ipAddress, e.getMessage());
        }
    }

    public Map<String, String> getFileSystemInfo(String fileSystem) throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            return netAppFacade.getVolumeInfoAttributes(fileSystem, true);
        } catch (Exception e) {
            throw NetAppException.exceptions.getFileSystemInfo(fileSystem, _ipAddress, e.getMessage());
        }
    }

    @SuppressWarnings("findbugs:ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public List<String> listSnapshots(String volumeName) throws NetAppException {
        List<String> snapshots = null;
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https);
            snapshots = (List<String>) netAppFacade
                    .listSnapshots(volumeName);
        } catch (Exception e) {
            String[] params = { volumeName, e.getMessage() };
            _logger.info("Failed to retrieve list of snapshots for {} due to {}", params);
        }
        return snapshots;
    }

    // New QTree methods
    public void createQtree(String qtreeName, String volumeName, Boolean opLocks, String securityStyle, Long size, String vfilerName)
            throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            netAppFacade.createQtree(qtreeName, volumeName);

            /*
             * Set the security style; if input is default we do not set it.
             * In that case, the qtree inherits the parent volume's security
             * style.
             */
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

            _logger.info("NetAppApi::createQtree -> qtreePath = {}", qtreePath);

            if (securityStyle.equalsIgnoreCase(UNIX)) {
                netAppFacade.setQTreeSecurityStyle(qtreePath, UNIX);
            } else if (securityStyle.equalsIgnoreCase(NTFS)) {
                netAppFacade.setQTreeSecurityStyle(qtreePath, NTFS);
            } else if (securityStyle.equalsIgnoreCase(MIXED)) {
                netAppFacade.setQTreeSecurityStyle(qtreePath, MIXED);
            }

            /*
             * Set qtree 'oplocks'
             */
            if (opLocks.booleanValue() == true) {
                netAppFacade.setQTreeOplocks(qtreePath, ENABLE);
            } else {
                netAppFacade.setQTreeOplocks(qtreePath, DISABLE);
            }

            /*
             * Set the size - Quota
             */
            if (size > 0) {
                netAppFacade.addDiskLimitTreeQuota(volumeName, qtreePath, size / SIZE_KB, 0);
                // Enable the quota. some times, quota wont be enabled.
                // especially, when we create quota on multi-store environment.
                try {
                    QuotaStatus quotaStatus = netAppFacade.getQuotaStatus(volumeName);
                    if (quotaStatus.OFF == quotaStatus) {
                        netAppFacade.turnQuotaOn(volumeName);
                    } else {
                        // Resizing only works for certain types of changes to the quotas file.
                        // For other changes, you need to reinitialize quotas.
                        netAppFacade.reintializeQuota(volumeName);
                    }

                    // QuotaStatus quotaStatus = netAppFacade.getQuotaStatus(volumeName);
                    _logger.info("Quota status on volume {} is {}. ", volumeName, quotaStatus.toString());
                } catch (Exception e) {
                    _logger.warn("Quota status on volume {} is not stable. ", volumeName);
                }
            }

        } catch (Exception e) {
            _logger.info("NetAppApi::createQtree -> e.getMessage() = {}", e.getMessage());
            throw NetAppException.exceptions.createQtreeFailed(qtreeName, e.getMessage());
        }
    }

    public void deleteQtree(String qtreeName, String volumeName, String vfilerName) throws NetAppException {
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            /*
             * Path of an existing qtree. The path should be in this format:
             * 
             * /vol/< volume name >/< qtree name >
             */
            String qtreePath = "/vol/" + volumeName + "/" + qtreeName;

            // TODO : Force delete option ?

            /*
             * Before deleting the qtree, delete the quota associated with the tree.
             */
            if (netAppFacade.getTreeQuota(volumeName, qtreePath) != null) {
                netAppFacade.deleteTreeQuota(volumeName, qtreePath);
            }

            /*
             * Now delete the qtree.
             */
            netAppFacade.deleteQtree(qtreePath, true);
        } catch (Exception e) {
            throw NetAppException.exceptions.deleteQtreeFailed(qtreeName, e.getMessage());
        }
    }

    public void updateQtree(String qtreeName, String volumeName, Boolean opLocks, String securityStyle, Long size, String vfilerName)
            throws NetAppException {
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            String qtreePath = "/vol/" + volumeName + "/" + qtreeName;

            // Update the security style
            if (securityStyle.equalsIgnoreCase("unix")) {
                netAppFacade.setQTreeSecurityStyle(qtreePath, "unix");
            } else if (securityStyle.equalsIgnoreCase("ntfs")) {
                netAppFacade.setQTreeSecurityStyle(qtreePath, "ntfs");
            } else if (securityStyle.equalsIgnoreCase("mixed")) {
                netAppFacade.setQTreeSecurityStyle(qtreePath, "mixed");
            }

            /*
             * Update qtree 'oplocks'
             */
            if (opLocks.booleanValue() == true) {
                netAppFacade.setQTreeOplocks(qtreePath, "enable");
            } else {
                netAppFacade.setQTreeOplocks(qtreePath, "disable");
            }

            // Modify the quota
            if (size > 0) {
                netAppFacade.setDiskLimitTreeQuota(volumeName, qtreePath, size / SIZE_KB, 0);
                try {
                    QuotaStatus quotaStatus = netAppFacade.getQuotaStatus(volumeName);
                    if (quotaStatus.OFF == quotaStatus) {
                        netAppFacade.turnQuotaOn(volumeName);
                    } else {
                        // Resizing only works for certain types of changes to the quotas file.
                        // For other changes, you need to reinitialize quotas.
                        netAppFacade.reintializeQuota(volumeName);
                    }

                    // QuotaStatus quotaStatus = netAppFacade.getQuotaStatus(volumeName);
                    _logger.info("Quota status on volume {} is {}. ", volumeName, quotaStatus.toString());
                } catch (Exception e) {
                    _logger.warn("Quota status on volume {} is not stable. ", volumeName);
                }

            }

        } catch (Exception e) {
            throw NetAppException.exceptions.createQtreeFailed(qtreeName, e.getMessage());
        }
    }

    public Boolean exportNewFS(String exportPath, List<ExportRule> exportRules) throws NetAppException {
        try {
            List<String> fsList = null;
            if (netAppFacade == null) {
                _logger.warn("Invalid Facade found {} creating now...", netAppFacade);
                netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName, _password, _https);
                _logger.warn("Facade created : {} ", netAppFacade);
            }
            _logger.info("NetApp Inputs for exportNewFS exportPath: {} , exportRules size {}", exportPath, exportRules.size());

            List<com.iwave.ext.netapp.utils.ExportRule> netAppCompatableRules = new ArrayList<>();
            for (ExportRule rule : exportRules) {
                com.iwave.ext.netapp.utils.ExportRule netAppRule = new com.iwave.ext.netapp.utils.ExportRule();
                copyPropertiesToSave(netAppRule, rule);
                netAppCompatableRules.add(netAppRule);
            }

            fsList = netAppFacade.addNewNFSShare(exportPath, netAppCompatableRules);
            if (fsList.isEmpty()) {
                return false;
            }

        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppException.exceptions.exportFSFailed(exportPath,
                    exportPath, e.getMessage());
        }
        return true;
    }

    public Boolean modifyNFSShare(String exportPath, List<ExportRule> exportRules) throws NetAppException {
        try {
            if (netAppFacade == null) {
                _logger.warn("Invalid Facade found {} creating now...", netAppFacade);
                netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName, _password, _https);
                _logger.warn("Facade created : {} ", netAppFacade);
            }
            _logger.info("NetApp Inputs for modifyNFSShare exportPath: {} , exportRules size {}", exportPath, exportRules.size());

            List<com.iwave.ext.netapp.utils.ExportRule> netAppCompatableRules = new ArrayList<>();
            for (ExportRule rule : exportRules) {
                com.iwave.ext.netapp.utils.ExportRule netAppRule = new com.iwave.ext.netapp.utils.ExportRule();
                copyPropertiesToSave(netAppRule, rule);
                netAppCompatableRules.add(netAppRule);
            }

            netAppFacade.modifyNFSShare(exportPath, netAppCompatableRules);

        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppException.exceptions.exportFSFailed(exportPath,
                    exportPath, e.getMessage());
        }
        return true;
    }

    public Boolean modifyCIFSShareAcl(String shareName, List<CifsAcl> acls) throws NetAppException {
        try {
            if (netAppFacade == null) {
                _logger.warn("Invalid Facade found {} creating now...", netAppFacade);
                netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName, _password, _https);
                _logger.warn("Facade created : {} ", netAppFacade);
            }

            for (CifsAcl acl : acls) {
                acl.setShareName(shareName);
                netAppFacade.setCIFSAcl(acl);

            }
        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppException.exceptions.modifyCifsShareAclFailed(shareName, e.getMessage());
        }
        return true;
    }

    public List<CifsAcl> listCIFSShareAcl(String ShareName) throws NetAppException {
        try {
            if (netAppFacade == null) {
                _logger.warn("Invalid Facade found {} creating now...", netAppFacade);
                netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName, _password, _https);
                _logger.warn("Facade created : {} ", netAppFacade);
            }

            List<CifsAcl> oldacls = netAppFacade.listCIFSAcls(ShareName);
            return oldacls;

        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppException.exceptions.listCIFSShareAclFailed(ShareName, e.getMessage());
        }

    }

    public Boolean deleteCIFSShareAcl(String shareName, List<CifsAcl> acls) throws NetAppException {
        try {
            if (netAppFacade == null) {
                _logger.warn("Invalid Facade found {} creating now...", netAppFacade);
                netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName, _password, _https);
                _logger.warn("Facade created : {} ", netAppFacade);
            }
            for (CifsAcl acl : acls) {
                acl.setShareName(shareName);
                netAppFacade.deleteCIFSAcl(acl);

            }
        } catch (Exception e) {
            _logger.error("Error Occured {} ", e.getMessage(), e);
            throw NetAppException.exceptions.deleteCIFSShareAclFailed(shareName, e.getMessage());
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

    public Boolean createSnapMirror(String sourcePath, String destPath, String vfilerName)
            throws NetAppException {
        Boolean FailedStatus = false;
        try {

            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            FailedStatus = netAppFacade.createSnapMirror(sourcePath, destPath);

            return FailedStatus;

        } catch (Exception e) {
            throw NetAppException.exceptions.createFSFailed("snapmirror", e.getMessage());
        }
    }

    public Boolean initializeSnapMirror(String sourcePath, String destPath, String vfilerName)
            throws NetAppException {
        Boolean FailedStatus = false;
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            FailedStatus = netAppFacade.initializeSnapMirror(sourcePath, destPath);

            return FailedStatus;

        } catch (Exception e) {
            throw NetAppException.exceptions.createFSFailed("snapmirror", e.getMessage());
        }
    }

    public boolean setScheduleSnapMirror(String type, String scheduleTime, String sourceLocation, String destLocation, String vfilerName) {
        Boolean FailedStatus = false;
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            FailedStatus = netAppFacade.setSnapMirrorSchedule(type, scheduleTime, sourceLocation, destLocation);

            return FailedStatus;

        } catch (Exception e) {
            throw NetAppException.exceptions.createFSFailed("snapmirror", e.getMessage());
        }
    }

    public Boolean destorySnapMirror(String sourcePath, String destPath, String vfilerName)
            throws NetAppException {
        Boolean FailedStatus = false;
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            FailedStatus = netAppFacade.destroyAsyncSnapMirror(sourcePath, destPath);

            return FailedStatus;

        } catch (Exception e) {
            throw NetAppException.exceptions.createFSFailed("snapmirror", e.getMessage());
        }
    }

    public Boolean resyncSnapMirror(String sourcePath, String destPath, String vfilerName)
            throws NetAppException {
        Boolean FailedStatus = false;
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            FailedStatus = netAppFacade.resyncSnapMirror(sourcePath, destPath);
            return FailedStatus;

        } catch (Exception e) {
            throw NetAppException.exceptions.createFSFailed("snapmirror", e.getMessage());
        }
    }

    /**
     * Breaks a SnapMirror relationship between a source and destination volume of a data protection mirror
     * 
     * @param sourcePath
     * @param destPath
     * @param vfilerName
     * @return
     * @throws NetAppException
     */
    public Boolean breakSnapMirror(String pathLocation, String vfilerName)
            throws NetAppException {
        Boolean FailedStatus = false;
        try {
            netAppFacade = new NetAppFacade(_ipAddress, _portNumber, _userName,
                    _password, _https, vfilerName);

            FailedStatus = netAppFacade.breakSnapMirrorSchedule(pathLocation);

            return FailedStatus;

        } catch (Exception e) {
            throw NetAppException.exceptions.createFSFailed("snapmirror", e.getMessage());
        }
    }
}
