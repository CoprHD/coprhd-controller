/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;


import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;


/**
 * Base data object for file shares and snapshots
 */
public abstract class UnManagedFileObject extends UnManagedDiscoveredObject {

    // mount path used by hosts for this file share
    protected String _mountPath;

    //path of the file object
    protected String _path;

    //FSExports
    protected UnManagedFSExportMap fsUnManagedExportMap;


	//SMB Shares. SMB share name should be unique across SMB server
    protected UnManagedSMBShareMap unManagedSmbShareMap;

    

	// these will include things like
    // thinProvisioned->Y/N, ALU->1,2,3, and raidLevel->RAID-1,RAID-6+2
    // may include volumeGroup->name for mapping multiple volumes
    protected StringMap _extensions;

    /**
     * Get mount path
     * @return
     */
    @Name("mountPath")
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
    public UnManagedFSExportMap getFsUnManagedExportMap() {
		return fsUnManagedExportMap;
	}

	public void setFsUnManagedExportMap(UnManagedFSExportMap fsUnManagedExportMap) {
		this.fsUnManagedExportMap = fsUnManagedExportMap;
	}

    @Name("smbShare")
    public UnManagedSMBShareMap getUnManagedSmbShareMap() {
		return unManagedSmbShareMap;
	}

	public void setUnManagedSmbShareMap(UnManagedSMBShareMap unManagedSmbShareMap) {
		this.unManagedSmbShareMap = unManagedSmbShareMap;
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
        if (fsUnManagedExportMap != null && !fsUnManagedExportMap.isEmpty()) {
            return UnManagedFSExport.class.getSimpleName();
        }
        if (unManagedSmbShareMap != null && !unManagedSmbShareMap.isEmpty()) {
            return UnManagedSMBFileShare.class.getSimpleName();
        }
        return null;
    }
}
