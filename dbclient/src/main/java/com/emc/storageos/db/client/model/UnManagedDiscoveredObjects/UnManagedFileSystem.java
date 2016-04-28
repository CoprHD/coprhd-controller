/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import java.net.URI;
import java.util.Map;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;

@Cf("UnManagedFileSystem")
public class UnManagedFileSystem extends UnManagedFileObject {

    private StringSetMap _fileSystemInformation;

    private StringMap _fileSystemCharacterstics;

    private URI _storageSystemUri;

    private URI storagePoolUri;

    private Boolean _hasExports = false;

    private Boolean _hasShares = false;
    
    private Boolean _hasNFSAcl = false;

    @Name("hasExports")
    public Boolean getHasExports() {
        return _hasExports;
    }

    public void setHasExports(Boolean hasExports) {
        _hasExports = hasExports;
        setChanged("hasExports");
    }

    @Name("hasShares")
    public Boolean getHasShares() {
        return _hasShares;
    }

    public void setHasShares(Boolean hasShares) {
        _hasShares = hasShares;
        setChanged("hasShares");
    }
    
    @Name("hasNFSAcl")
    public Boolean getHasNFSAcl() {
        return _hasNFSAcl;
    }

    public void setHasNFSAcl(Boolean hasNFSAcl) {
        _hasNFSAcl = hasNFSAcl;
        setChanged("hasNFSAcl");
    }

    public enum SupportedFileSystemCharacterstics {

        IS_SNAP_SHOT("Snapshot"),
        IS_THINLY_PROVISIONED("isThinlyProvisioned"),
        IS_INGESTABLE("IsIngestable"),
        IS_FILESYSTEM_EXPORTED("isFileSystemExported");

        private String _charactersticsKey;

        SupportedFileSystemCharacterstics(String charactersticsKey) {
            _charactersticsKey = charactersticsKey;
        }

        public String getCharacterstic() {
            return _charactersticsKey;
        }

        public static String getFileSystemCharacterstic(String charactersticsKey) {
            for (SupportedFileSystemCharacterstics characterstic : values()) {
                if (characterstic.getCharacterstic().equalsIgnoreCase(charactersticsKey)) {
                    return characterstic.toString();
                }
            }
            return null;
        }
    }

    public enum SupportedFileSystemInformation {
        ALLOCATED_CAPACITY("AllocatedCapacity"),
        PROVISIONED_CAPACITY("ProvisionedCapacity"),
        STORAGE_POOL("PoolUri"),
        STORAGE_PORT("PortUri"),
        NATIVE_GUID("NativeGuid"),
        SYSTEM_TYPE("SystemType"),
        IS_THINLY_PROVISIONED("ThinlyProvisioned"),
        NATIVE_ID("FileSystemDeviceID"),
        SUPPORTED_COS_LIST("CosUriList"),
        SUPPORTED_VPOOL_LIST("vpoolUriList"),
        DEVICE_LABEL("ElementName"),
        NAME("FSName"),
        PATH("FSPath"),
        NAS("NasUri"),
        MOUNT_PATH("FSMountPath");
        private String _infoKey;

        SupportedFileSystemInformation(String infoKey) {
            _infoKey = infoKey;
        }

        public String getInfoKey() {
            return _infoKey;
        }

        public static String getFileSystemInformation(String infoKey) {
            for (SupportedFileSystemInformation info : values()) {
                if (info.getInfoKey().equalsIgnoreCase(infoKey)) {
                    return info.toString();
                }
            }
            return null;
        }
    }

    public void putFileSystemInfo(String key, StringSet values) {
        if (null == _fileSystemInformation) {
            setFileSystemInformation(new StringSetMap());
        }
        _fileSystemInformation.put(key, values);
    }

    public void addFileSystemInformation(Map<String, StringSet> fileSystemInfo) {
        if (null == _fileSystemInformation) {
            setFileSystemInformation(new StringSetMap());
        } else {
            _fileSystemInformation.clear();
        }

        if (fileSystemInfo.size() > 0) {
            _fileSystemInformation.putAll(fileSystemInfo);
        }
    }

    public void setFileSystemInformation(StringSetMap fileSystemInfo) {
        _fileSystemInformation = fileSystemInfo;
    }

    @Name("fileSystemInformation")
    public StringSetMap getFileSystemInformation() {
        return _fileSystemInformation;
    }

    public void putFileSystemCharacterstics(String key, String value) {
        if (null == _fileSystemCharacterstics) {
            setFileSystemCharacterstics(new StringMap());
        }
        _fileSystemCharacterstics.put(key, value);
    }

    public void addFileSystemCharacterstcis(Map<String, String> fileSystemCharacterstics) {
        if (null == _fileSystemCharacterstics) {
            setFileSystemCharacterstics(new StringMap());
        } else {
            _fileSystemCharacterstics.clear();
        }

        if (fileSystemCharacterstics.size() > 0) {
            _fileSystemCharacterstics.putAll(fileSystemCharacterstics);
        }
    }

    public void setFileSystemCharacterstics(StringMap fileSystemCharacterstics) {
        _fileSystemCharacterstics = fileSystemCharacterstics;
    }

    @Name("fileSystemCharacterstics")
    public StringMap getFileSystemCharacterstics() {
        return _fileSystemCharacterstics;
    }

    public void setStorageSystemUri(URI storageSystemUri) {
        _storageSystemUri = storageSystemUri;
        setChanged("storageDevice");
    }

    @RelationIndex(cf = "UnManagedFileSystemRelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageSystemUri() {
        return _storageSystemUri;
    }

    @RelationIndex(cf = "UnManagedFileSystemRelationIndex", type = StoragePool.class)
    @Name("storagePool")
    public URI getStoragePoolUri() {
        return storagePoolUri;
    }

    public void setStoragePoolUri(URI storagePoolUri) {
        this.storagePoolUri = storagePoolUri;
        setChanged("storagePool");
    }

}
