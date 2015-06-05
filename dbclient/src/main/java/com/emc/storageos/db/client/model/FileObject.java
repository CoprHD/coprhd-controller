/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model;


/**
 * Base data object for file shares and snapshots
 */
public abstract class FileObject extends DataObject {

    // mount path used by hosts for this file share
    protected String _mountPath;

    //path of the file object
    protected String _path;

    //FSExports
    protected FSExportMap _fsExportMap;

    //SMB Shares. SMB share name should be unique across SMB server
    protected SMBShareMap _smbShareMap;

    // these will include things like
    // thinProvisioned->Y/N, ALU->1,2,3, and raidLevel->RAID-1,RAID-6+2
    // may include volumeGroup->name for mapping multiple volumes
    protected StringMap _extensions;

    /**
     * Get mount path
     * @return
     */
    @Name("mountPath")
    @AlternateId("AltIdIndex")
    public String getMountPath() {
        return _mountPath;
    }

    /**
     * Set mount path
     * @param mountPath
     */
    public void setMountPath(String mountPath) {
        _mountPath = mountPath;
        setChanged("mountPath");
    }

    /**
     * Get path
     * @return
     */
    @Name("path")
    @AlternateId("AltIdIndex")
    public String getPath() {
        return _path;
    }

    /**
     * Set path
     * @param path
     */
    public void setPath(String path) {
        _path = path;
        setChanged("path");
    }

    @Name("export")
    public FSExportMap getFsExports() {
        return _fsExportMap;
    }

    public void setFsExports(FSExportMap fsExportMap) {
        _fsExportMap = fsExportMap;
    }

    @Name("smbShare")
    public SMBShareMap getSMBFileShares() {
        return _smbShareMap;
    }

    public void setSMBFileShares(SMBShareMap smbShareMap) {
        _smbShareMap = smbShareMap;
    }

    /**
     * Get extensions map
     * @return
     */
    @Name ("extensions")
    public StringMap getExtensions() {
        return _extensions;
    }

    /**
     * Set extensions map - overwrites existing one
     * @param map        StringMap of extensions to set
     */
    public void setExtensions(StringMap map) {
        _extensions = map;
    }


    @Override
    public String canBeDeleted() {
        if (_fsExportMap != null && !_fsExportMap.isEmpty()) {
            return FileExport.class.getSimpleName();
        }
        if (_smbShareMap != null && !_smbShareMap.isEmpty()) {
            return SMBFileShare.class.getSimpleName();
        }
        return null;
    }
}
