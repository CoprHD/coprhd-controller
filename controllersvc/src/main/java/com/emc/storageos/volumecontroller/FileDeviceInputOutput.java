/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.model.file.ShareACL;

/**
 * Class defining input/output from File storage device interface
 * to expose only the fields that are needed/can be modified by storage device implementations
 */
/**
 * @author root
 * 
 */
public class FileDeviceInputOutput {
    private StoragePool pool;
    private FileObject fObj;
    private FileShare fs;
    private Snapshot snapshot;
    private Project project;
    private TenantOrg tenantOrg;
    private QuotaDirectory quotaDirectory;
    private String nativeDeviceFsId;

    private String _opType;
    private boolean isFile;
    private boolean _forceDelete;
    private long newFSSize;
    private VirtualPool vPool;
    private String opId;
    // New Additions as part of Export Redesign
    private String subDirectory;
    private boolean isAllDir;
    private String exportPath;
    private String objIdOnDevice;
    private FileExportUpdateParams FileExportUpdateParams;
    private List<ExportRule> exportRulesToAdd = new ArrayList<>();
    private List<ExportRule> exportRulesToModify = new ArrayList<>();
    private List<ExportRule> exportRulesToDelete = new ArrayList<>();
    private List<ExportRule> existingDBExportRules = new ArrayList<>();

    private String comments = "";

    // New additions for Cifs ACL work
    private String shareName;
    private String sharePathOnDevice;
    private CifsShareACLUpdateParams cifsShareACLUpdateParams;
    private List<ShareACL> shareAclsToAdd = new ArrayList<>();
    private List<ShareACL> shareAclsToModify = new ArrayList<>();
    private List<ShareACL> shareAclsToDelete = new ArrayList<>();
    private List<ShareACL> existingShareAcls = new ArrayList<>();
    
  //New additions for vNAS
    private VirtualNAS vNAS;

    // New additions for NFS ACL work
    private String fileSystemPath;
    private NfsACLUpdateParams nfsACLUpdateParams;
    private List<NfsACE> nfsAclsToAdd = new ArrayList<>();
    private List<NfsACE> nfsAclsToModify = new ArrayList<>();
    private List<NfsACE> nfsAclsToDelete = new ArrayList<>();

    public String getFileSystemPath() {
        return fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    public NfsACLUpdateParams getNfsACLUpdateParams() {
        return nfsACLUpdateParams;
    }

    public void setNfsACLUpdateParams(NfsACLUpdateParams nfsACLUpdateParams) {
        this.nfsACLUpdateParams = nfsACLUpdateParams;
    }

    public List<NfsACE> getNfsAclsToAdd() {
        return nfsAclsToAdd;
    }

    public void setNfsAclsToAdd(List<NfsACE> nfsAclsToAdd) {
        this.nfsAclsToAdd = nfsAclsToAdd;
    }

    public List<NfsACE> getNfsAclsToModify() {
        return nfsAclsToModify;
    }

    public void setNfsAclsToModify(List<NfsACE> nfsAclsToModify) {
        this.nfsAclsToModify = nfsAclsToModify;
    }

    public List<NfsACE> getNfsAclsToDelete() {
        return nfsAclsToDelete;
    }

    public void setNfsAclsToDelete(List<NfsACE> nfsAclsToDelete) {
        this.nfsAclsToDelete = nfsAclsToDelete;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public void setAllExportRules(FileExportUpdateParams param) {

        FileExportUpdateParams = param;

        if (param.getExportRulesToAdd() != null
                && param.getExportRulesToAdd().getExportRules() != null) {
            this.exportRulesToAdd = param.getExportRulesToAdd()
                    .getExportRules();
        }

        if (param.getExportRulesToModify() != null
                && param.getExportRulesToModify().getExportRules() != null) {
            this.exportRulesToModify = param.getExportRulesToModify()
                    .getExportRules();
        }

        if (param.getExportRulesToDelete() != null
                && param.getExportRulesToDelete().getExportRules() != null) {
            this.exportRulesToDelete = param.getExportRulesToDelete()
                    .getExportRules();
        }

    }

    public void setAllShareAcls(CifsShareACLUpdateParams param) {

        cifsShareACLUpdateParams = param;

        if (param.getAclsToAdd() != null
                && param.getAclsToAdd().getShareACLs() != null) {
            this.shareAclsToAdd = param.getAclsToAdd().getShareACLs();
        }

        if (param.getAclsToModify() != null
                && param.getAclsToModify().getShareACLs() != null) {
            this.shareAclsToModify = param.getAclsToModify().getShareACLs();
        }

        if (param.getAclsToDelete() != null
                && param.getAclsToDelete().getShareACLs() != null) {
            this.shareAclsToDelete = param.getAclsToDelete().getShareACLs();
        }

    }

    public void setAllNfsAcls(NfsACLUpdateParams param) {

        nfsACLUpdateParams = param;

        if (param.getAcesToAdd() != null && !param.getAcesToAdd().isEmpty()) {
            this.nfsAclsToAdd = param.getAcesToAdd();
        }
        if (param.getAcesToModify() != null && !param.getAcesToModify().isEmpty()) {
            this.nfsAclsToModify = param.getAcesToModify();
        }
        if (param.getAcesToDelete() != null && !param.getAcesToDelete().isEmpty()) {
            this.nfsAclsToDelete = param.getAcesToDelete();
        }

    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public String getSubDirectory() {
        return subDirectory;
    }

    public void setSubDirectory(String subDirectory) {
        this.subDirectory = subDirectory;
    }

    public boolean isAllDir() {
        return isAllDir;
    }

    public void setAllDir(boolean isAllDir) {
        this.isAllDir = isAllDir;
    }

    public List<ExportRule> getExistingDBExportRules() {
        return existingDBExportRules;
    }

    public void setExistingDBExportRules(List<ExportRule> existingDBExportRules) {
        this.existingDBExportRules = existingDBExportRules;
    }

    public FileExportUpdateParams getFsExportUpdateParams() {
        return FileExportUpdateParams;
    }

    public String getExportPath() {
        return exportPath;
    }

    /**
     * Sets the Export Path :
     * It can be a FS Path, SubDir Path, Snapshot Export Path.
     * 
     * @param exportPath
     */
    public void setExportPath(String exportPath) {
        this.exportPath = exportPath;
    }

    public String getObjIdOnDevice() {
        return objIdOnDevice;
    }

    /**
     * Used to save native ID of exports on device
     * 
     * @param objIdOnDevice
     */
    public void setObjIdOnDevice(String objIdOnDevice) {
        this.objIdOnDevice = objIdOnDevice;
    }

    public List<ExportRule> getExportRulesToAdd() {
        return exportRulesToAdd;
    }

    public List<ExportRule> getExportRulesToModify() {
        return exportRulesToModify;
    }

    public List<ExportRule> getExportRulesToDelete() {
        return exportRulesToDelete;
    }

    /**
     * add storage pool
     * 
     * @param pool StoragePool object
     */
    public void addStoragePool(StoragePool pool) {
        this.pool = pool;
    }

    public StoragePool getStoragePool() {
        return pool;
    }

    /**
     * add fileshare
     * 
     * @param fs FileShare object
     */
    public void addFileShare(FileShare fs) {
        this.fs = fs;
    }

    /**
     * add Snapshot
     * 
     * @param snap Snapshot object
     */
    public void addSnapshot(Snapshot snap) {
        this.snapshot = snap;
    }

    /**
     * add FileShare as a FileObject and FileShare
     * 
     * @param obj FileShare
     */
    public void addFSFileObject(FileShare obj) {
        fObj = obj;
        fs = obj;
    }

    /**
     * add Snapshot as the FileObject and Snapshot
     * 
     * @param snap
     */
    public void addSnapshotFileObject(Snapshot snap) {
        fObj = snap;
        snapshot = snap;
    }

    /**
     * Get FS thinProvision
     * 
     * @return Boolean
     */
    public Boolean getThinProvision() {
        return fs.getThinlyProvisioned();
    }

    /**
     * Get FS capacity
     * 
     * @return Long
     */
    public Long getFsCapacity() {
        return fs.getCapacity();
    }

    /**
     * Get VirtualPool
     * 
     * @return Long
     */
    public VirtualPool getVPool() {
        return vPool;
    }

    /**
     * Set VirtualPool
     * 
     * @param size
     */
    public void setVPool(VirtualPool vpoolObj) {
        vPool = vpoolObj;
    }

    /**
     * Get Port Name
     * 
     * @return String
     */
    public String getPortName() {
        return fs.getPortName();
    }

    /**
     * Set FS capacity
     * 
     * @param size
     */
    public void setFsCapacity(Long size) {
        fs.setCapacity(size);
    }

    /**
     *
     */
    public URI getFsId() {
        return fs.getId();
    }

    /**
     * Get FS Label
     * 
     * @return String
     */
    public String getFsLabel() {
        return fs.getLabel();
    }

    /**
     * Get FS extensions map
     * 
     * @return StringMap of FS extensions
     */
    public StringMap getFsExtensions() {
        return fs.getExtensions();
    }

    /**
     * Get FS exports map
     * 
     * @return FSExportMap
     */
    public FSExportMap getFsExports() {
        return fs.getFsExports();
    }

    /**
     * Get FS shares map
     * 
     * @return SMBShareMap
     */
    public SMBShareMap getFsShares() {
        return fs.getSMBFileShares();
    }

    /**
     * Init FS exports
     * 
     * @return
     */
    public void initFsExports() {
        fs.setFsExports(new FSExportMap());
    }

    /**
     * Get FS mount path
     * 
     * @return
     */
    public String getFsMountPath() {
        return fs.getMountPath();
    }

    /**
     * Get FS path
     * 
     * @return
     */
    public String getFsPath() {
        return fs.getPath();
    }

    /**
     * Get pool URI
     * 
     * @return
     */
    public URI getPoolId() {
        return pool.getId();
    }

    /**
     * Get pool nativeId
     * 
     * @return
     */
    public String getPoolNativeId() {
        return pool.getNativeId();
    }

    /**
     * Get pool name
     * 
     * @return
     */
    public String getPoolName() {
        return pool.getPoolName();
    }

    /**
     * Get Pool extensions map
     * 
     * @return StringMap of Pool extensions
     */
    public StringMap getPoolExtensions() {
        StringMap extensions = null;
        if (pool != null) {
            extensions = pool.getControllerParams();
        }
        if (extensions == null) {
            // do not return a null set
            pool.setControllerParams(new StringMap());
        } else {
            return extensions;
        }
        return pool.getControllerParams();
    }

    /**
     * Get snapshot id
     * 
     * @return
     */
    public URI getSnapshotId() {
        return snapshot.getId();
    }

    /**
     * Initialize Snapshot exports
     */
    public void initSnapshotExports() {
        if (getSnapshotExports() == null) {
            snapshot.setFsExports(new FSExportMap());
        }
    }

    /**
     * Get snapshot exports map
     * 
     * @return FSExportMap
     */
    public FSExportMap getSnapshotExports() {
        return snapshot.getFsExports();
    }

    /**
     * Get snapshot shares map
     * 
     * @return SMBShareMap
     */
    public SMBShareMap getSnapshotShares() {
        return snapshot.getSMBFileShares();
    }

    /**
     * Get Snapshot extensions map
     * 
     * @return StringMap of Snapshot extensions
     */
    public StringMap getSnapshotExtensions() {
        return snapshot.getExtensions();
    }

    /**
     * Get quota dir extensions map
     * 
     * @return StringMap of quota dir extensions
     */
    public StringMap getQuotaDirExtensions() {
        return quotaDirectory.getExtensions();
    }

    /**
     * Get FileObject id
     * 
     * @return URI
     */
    public URI getFileObjId() {
        return fObj.getId();
    }

    /**
     * Get FileObject
     * 
     * @return FileObject
     */
    public FileObject getFileObj() {
        return fObj;
    }

    /**
     * Get FileObject mountPath
     * 
     * @return String mount path
     */
    public String getFileObjMountPath() {
        return fObj.getMountPath();
    }

    /**
     * Get FileObject exports
     * 
     * @return FSExportMap - current exports map
     */
    public FSExportMap getFileObjExports() {
        return fObj.getFsExports();
    }

    /**
     * Get FileObject shares map
     * 
     * @return SMBShareMap
     */
    public SMBShareMap getFileObjShares() {
        return fObj.getSMBFileShares();
    }

    /* All sets below */

    /**
     * Initialize FileObject exports
     */
    public void initFileObjExports() {
        fObj.setFsExports(new FSExportMap());
    }

    /**
     * Initialize FileObject shares
     */
    public void initFileObjShares() {
        fObj.setSMBFileShares(new SMBShareMap());
    }

    /**
     * Initialize FS extensions
     */
    public void initFsExtensions() {
        fs.setExtensions(new StringMap());
    }

    /**
     * Initialize Snapshot extensions
     */
    public void initSnapshotExtensions() {
        snapshot.setExtensions(new StringMap());
    }

    /**
     * Initialize quota dir extensions
     */
    public void initQuotaDirExtensions() {
        quotaDirectory.setExtensions(new StringMap());
    }

    /**
     * Set FS thinProvision
     * 
     * @param thinProvision
     */
    public void setThinProvision(boolean thinProvision) {
        fs.setThinlyProvisioned(thinProvision);
    }

    /**
     * Set FS mount path
     * 
     * @param path
     */
    public void setFsMountPath(String path) {
        fs.setMountPath(path);
    }

    /**
     * Set FS path
     * 
     * @param path
     */
    public void setFsPath(String path) {
        fs.setPath(path);
    }

    /**
     * Set FS native ID
     * 
     * @param id
     */
    public void setFsNativeId(String id) {
        fs.setNativeId(id);
    }

    /**
     * Get FS native ID
     */
    public String getFsNativeId() {
        return fs.getNativeId();
    }

    /**
     * Set FS native GUID
     * 
     * @param id
     */
    public void setFsNativeGuid(String id) {
        fs.setNativeGuid(id);
    }

    /**
     * Set FS name
     * 
     * @param fsName
     */
    public void setFsName(String fsName) {
        fs.setName(fsName);
    }

    /**
     * Get Snapshot name
     * 
     * @return String
     */
    public String getSnapshotName() {
        return snapshot.getName();
    }

    /**
     * Set Snapshot name
     * 
     * @param snapshotName
     */
    public void setSnapshotName(String snapshotName) {
        snapshot.setName(snapshotName);
    }

    /**
     * Get QuotaDirectory name
     * 
     * @return String
     */

    public String getQuotaDirectoryName() {
        return quotaDirectory.getName();
    }

    /**
     * Set QuotaDirectory name
     * 
     * @param qDirName
     */
    public void setQuotaDirectoryName(String qDirName) {
        quotaDirectory.setName(qDirName);
    }

    /**
     * Get FS name
     * 
     * @return String
     */
    public String getFsName() {
        return fs.getName();
    }

    /**
     * Set SnapShot native ID
     * 
     * @param id
     */
    public void setSnapNativeId(String id) {
        snapshot.setNativeId(id);
    }

    /**
     * Get SnapShot native ID
     */
    public String getSnapNativeId() {
        return snapshot.getNativeId();
    }

    /**
     * Set snapshot mount path
     * 
     * @param path
     */
    public void setSnapshotMountPath(String path) {
        snapshot.setMountPath(path);
    }

    /**
     * Set snapshot mount path
     * 
     * @param path
     */
    public void setSnapshotPath(String path) {
        snapshot.setPath(path);
    }

    /**
     * get snapshot mount path
     */
    public String getSnapshotMountPath() {
        return snapshot.getMountPath();
    }

    /**
     * get snapshot mount path
     */
    public String getSnapshotPath() {
        return snapshot.getPath();
    }

    public void setSnapshotLabel(String snapShotName) {
        snapshot.setLabel(snapShotName);
    }

    public String getSnapshotLabel() {
        return snapshot.getLabel();
    }

    public void setSnaphotCheckPointBaseline(String checkPointBaseline) {
        snapshot.setCheckpointBaseline(checkPointBaseline);
    }

    public String getSnapshotCheckPointBaseline() {
        return snapshot.getCheckpointBaseline();
    }

    public String getFsUUID() {
        // urn:storageos:%1$s:%2$s
        String fsId = fs.getId().toString();
        fsId = fsId.substring(0, fsId.length() - 1);
        return fsId.substring(fsId.lastIndexOf(":") + 1);
    }

    public void setFileOperation(boolean isFile) {
        this.isFile = isFile;
    }

    public boolean getFileOperation() {
        return this.isFile;
    }

    public void setForceDelete(boolean forceDelete) {
        _forceDelete = forceDelete;
    }

    public boolean getForceDelete() {
        return _forceDelete;
    }

    public void setOperationType(String OpType) {
        _opType = OpType;
    }

    public String getOperationType() {
        return _opType;
    }

    public void setNewFSCapacity(long size) {
        newFSSize = size;
    }

    public long getNewFSCapacity() {
        return newFSSize;
    }

    public FileShare getFs() {
        return fs;
    }

    public Snapshot getFileSnapshot() {
        return snapshot;
    }

    public String getVPoolName() {
        return vPool.getLabel();
    }

    // replace all Special Characters ; /-+!@#$%^&())";:[]{}\ |
    public String getVPoolNameWithNoSpecialCharacters() {
        return stripSpecialCharacters(vPool.getLabel());
    }

    // replace all Special Characters ; /-+!@#$%^&())";:[]{}\ |
    public String getProjectNameWithNoSpecialCharacters() {
        return stripSpecialCharacters(project.getLabel());
    }

    // replace all Special Characters ; /-+!@#$%^&())";:[]{}\ |
    public String getTenantNameWithNoSpecialCharacters() {
        return stripSpecialCharacters(tenantOrg.getLabel());
    }

    private String stripSpecialCharacters(String label) {
        return label.replaceAll("[^\\dA-Za-z ]", "").replaceAll("\\s+", "_");
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public TenantOrg getTenantOrg() {
        return tenantOrg;
    }

    public void setTenantOrg(TenantOrg tenantOrg) {
        this.tenantOrg = tenantOrg;
    }

    public boolean isFileShareMounted() {
        FSExportMap exports = getFs().getFsExports();
        SMBShareMap shares = getFs().getSMBFileShares();
        boolean isMounted = true;
        if ((exports == null || (exports != null && exports.isEmpty())) &&
                (shares == null || (shares != null && shares.isEmpty()))) {
            isMounted = false;
        }
        return isMounted;
    }

    public String getOpId() {
        return opId;
    }

    public void setOpId(String opId) {
        this.opId = opId;
    }

    /**
     * add FileSystemQuotaDirectory object
     * 
     * @param quotaDir FileSystemQuotaDirectory object
     */
    public void addQuotaDirectory(QuotaDirectory quotaDir) {
        this.quotaDirectory = quotaDir;
    }

    /**
     * get FileSystemQuotaDirectory object
     * 
     */
    public QuotaDirectory getQuotaDirectory() {
        return this.quotaDirectory;
    }

    public void setNativeDeviceFsId(String nativeDeviceFsId) {
        this.nativeDeviceFsId = nativeDeviceFsId;
    }

    /**
     * get NativeDeviceId
     * 
     * @return
     */
    public String getNativeDeviceFsId() {
        return nativeDeviceFsId;
    }

    public FileExportUpdateParams getFileExportUpdateParams() {
        return FileExportUpdateParams;
    }

    public String getShareName() {
        return shareName;
    }

    public String getSharePathOnDevice() {
        return sharePathOnDevice;
    }

    public CifsShareACLUpdateParams getCifsShareACLUpdateParams() {
        return cifsShareACLUpdateParams;
    }

    public List<ShareACL> getShareAclsToAdd() {
        return shareAclsToAdd;
    }

    public List<ShareACL> getShareAclsToModify() {
        return shareAclsToModify;
    }

    public List<ShareACL> getShareAclsToDelete() {
        return shareAclsToDelete;
    }

    public List<ShareACL> getExistingShareAcls() {
        return existingShareAcls;
    }

    public void setExistingShareAcls(List<ShareACL> existingShareAcls) {
        this.existingShareAcls = existingShareAcls;
    }
    
    public VirtualNAS getvNAS() {
		return vNAS;
	}

	public void setvNAS(VirtualNAS vNAS) {
		this.vNAS = vNAS;
	}

}
