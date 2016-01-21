/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.file.FileObjectRestRep;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.model.adapters.StringMapAdapter;
import com.emc.storageos.model.adapters.StringSetMapAdapter;

import static com.emc.storageos.api.mapper.DbObjectMapper.*;

public class FileMapper {
    private static final Logger _log = LoggerFactory.getLogger(FileMapper.class);

    public static FileShareRestRep map(FileShare from) {
        if (from == null) {
            return null;
        }
        FileShareRestRep to = new FileShareRestRep();
        mapFileObjectFields(from, to);
        if (from.getProject() != null) {
            to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));
        }
        if (from.getTenant() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant().getURI()));
        }
        to.setCapacity(CapacityUtils.convertBytesToGBInStr(from.getCapacity()));
        to.setUsedCapacity(CapacityUtils.convertBytesToGBInStr(from.getUsedCapacity()));
        to.setVirtualPool(toRelatedResource(ResourceTypeEnum.FILE_VPOOL, from.getVirtualPool()));
        to.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, from.getVirtualArray()));
        to.setProtocols(from.getProtocol());
        to.setNativeId(from.getNativeId());
        to.setDataProtection(from.getDataProtection());
        to.setStorageSystem(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageDevice()));
        to.setPool(toRelatedResource(ResourceTypeEnum.STORAGE_POOL, from.getPool(), from.getStorageDevice()));
        to.setStoragePort(toRelatedResource(ResourceTypeEnum.STORAGE_PORT, from.getStoragePort(), from.getStorageDevice()));
        to.setVirtualNAS(toRelatedResource(ResourceTypeEnum.VIRTUAL_NAS, from.getVirtualNAS()));
        to.setThinlyProvisioned(from.getThinlyProvisioned());
        return to;
    }

    public static FileSnapshotRestRep map(Snapshot from) {
        if (from == null) {
            return null;
        }
        FileSnapshotRestRep to = new FileSnapshotRestRep();
        mapFileObjectFields(from, to);
        to.setNativeId(from.getNativeId());
        to.setTimestamp(from.getTimestamp());
        to.setParent(toRelatedResource(ResourceTypeEnum.FILE, from.getParent().getURI()));
        return to;
    }

    public static void mapFileObjectFields(FileObject from, FileObjectRestRep to) {
        mapDataObjectFields(from, to);
        to.setMountPath(from.getMountPath());
    }

    public static UnManagedFileSystemRestRep map(UnManagedFileSystem from) {
        if (from == null) {
            return null;
        }
        UnManagedFileSystemRestRep to = new UnManagedFileSystemRestRep();
        mapDataObjectFields(from, to);
        to.setNativeGuid(from.getNativeGuid());
        try {
            to.setFileSystemInformation(new StringSetMapAdapter().marshal(from.getFileSystemInformation()));
        } catch (Exception e) {
            _log.error("Exception while setting FileSystem information ", e);
        }
        to.setFileSystemCharacteristics(new StringMapAdapter().marshal(from.getFileSystemCharacterstics()));
        to.setStorageSystem(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageSystemUri()));
        to.setStoragePool(toRelatedResource(ResourceTypeEnum.STORAGE_POOL, from.getStoragePoolUri()));
        if (null != from.getSupportedVpoolUris() && !from.getSupportedVpoolUris().isEmpty()) {
            List<String> supportedVPoolList = new ArrayList<String>(from.getSupportedVpoolUris());
            to.setSupportedVPoolUris(supportedVPoolList);
        }
        
        return to;
    }

    public static QuotaDirectoryRestRep map(QuotaDirectory from) {
        if (from == null) {
            return null;
        }
        QuotaDirectoryRestRep to = new QuotaDirectoryRestRep();
        mapDataObjectFields(from, to);
        to.setName(from.getName());
        if (from.getParent() != null) {
            to.setParentFileSystem(toRelatedResource(ResourceTypeEnum.FILE, from.getParent().getURI()));
        }
        if (from.getProject() != null) {
            to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));
        }
        to.setNativeId(from.getNativeId());
        if (from.getSize() != null) {
            to.setQuotaSize(CapacityUtils.convertBytesToGBInStr(from.getSize()));
        }
        if (from.getSecurityStyle() != null) {
            to.setSecurityStyle(from.getSecurityStyle());
        }
        if (from.getOpLock() != null) {
            to.setOpLock(from.getOpLock());
        }
        return to;
    }
}
