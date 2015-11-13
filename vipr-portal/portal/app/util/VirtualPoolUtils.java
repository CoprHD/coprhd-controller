/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.createNamedRef;
import static com.emc.vipr.client.core.util.ResourceUtils.findRef;
import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import models.HighAvailability;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ObjectVirtualPoolParam;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.StoragePoolAssignmentChanges;
import com.emc.storageos.model.vpool.StoragePoolAssignments;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

import controllers.security.Security;

public class VirtualPoolUtils {
    public static boolean canUpdateACLs() {
        return Security.hasAnyRole(Security.SECURITY_ADMIN, Security.SYSTEM_ADMIN, Security.RESTRICTED_SYSTEM_ADMIN);
    }

    public static CachedResources<BlockVirtualPoolRestRep> createBlockCache() {
        return new CachedResources<BlockVirtualPoolRestRep>(getViprClient().blockVpools());
    }

    public static CachedResources<FileVirtualPoolRestRep> createFileCache() {
        return new CachedResources<FileVirtualPoolRestRep>(getViprClient().fileVpools());
    }

    public static BlockVirtualPoolRestRep getBlockVirtualPool(String id) {
        return getBlockVirtualPool(uri(id));
    }

    public static BlockVirtualPoolRestRep getBlockVirtualPool(URI id) {
        try {
            return getViprClient().blockVpools().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static FileVirtualPoolRestRep getFileVirtualPool(String id) {
        return getFileVirtualPool(uri(id));
    }

    public static FileVirtualPoolRestRep getFileVirtualPool(URI id) {
        try {
            return getViprClient().fileVpools().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }
    
    public static ObjectVirtualPoolRestRep getObjectVirtualPool(String id) {
        return getObjectVirtualPool(uri(id));
    }

    public static ObjectVirtualPoolRestRep getObjectVirtualPool(URI id) {
        try {
            return getViprClient().objectVpools().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }
    
    public static NamedRelatedResourceRep getBlockVirtualPoolRef(RelatedResourceRep ref) {
        return getBlockVirtualPoolRef(id(ref));
    }

    public static NamedRelatedResourceRep getBlockVirtualPoolRef(URI id) {
        if (Security.hasAnyRole(Security.SYSTEM_ADMIN, Security.SYSTEM_MONITOR)) {
            return createNamedRef(getViprClient().blockVpools().get(id));
        }
        else {
            return findRef(getViprClient().blockVpools().list(), id);
        }
    }

    public static NamedRelatedResourceRep getObjectVirtualPoolRef(RelatedResourceRep ref) {
        return getObjectVirtualPoolRef(id(ref));
    }

    public static NamedRelatedResourceRep getObjectVirtualPoolRef(URI id) {
        if (Security.hasAnyRole(Security.SYSTEM_ADMIN, Security.SYSTEM_MONITOR)) {
            return createNamedRef(getViprClient().objectVpools().get(id));
        }
        else {
            return findRef(getViprClient().objectVpools().list(), id);
        }
    }

    
    public static NamedRelatedResourceRep getFileVirtualPoolRef(RelatedResourceRep ref) {
        return getFileVirtualPoolRef(id(ref));
    }

    public static NamedRelatedResourceRep getFileVirtualPoolRef(URI id) {
        if (Security.hasAnyRole(Security.SYSTEM_ADMIN, Security.SYSTEM_MONITOR)) {
            return createNamedRef(getViprClient().fileVpools().get(id));
        }
        else {
            return findRef(getViprClient().fileVpools().list(), id);
        }
    }

    public static List<BlockVirtualPoolRestRep> getBlockVirtualPools() {
        return getViprClient().blockVpools().getAll();
    }

    public static List<BlockVirtualPoolRestRep> getBlockVirtualPools(ResourceFilter<BlockVirtualPoolRestRep> filter) {
        return getViprClient().blockVpools().getAll(filter);
    }

    public static List<BlockVirtualPoolRestRep> getBlockVirtualPools(Collection<URI> ids) {
        return getViprClient().blockVpools().getByIds(ids);
    }

    public static List<FileVirtualPoolRestRep> getFileVirtualPools() {
        return getViprClient().fileVpools().getAll();
    }

    public static List<FileVirtualPoolRestRep> getFileVirtualPools(ResourceFilter<FileVirtualPoolRestRep> filter) {
        return getViprClient().fileVpools().getAll(filter);
    }

    public static List<ObjectVirtualPoolRestRep> getFileVirtualPools(Collection<URI> ids) {
        return getViprClient().objectVpools().getByIds(ids);
    }

    public static List<ObjectVirtualPoolRestRep> getObjectVirtualPools() {
        return getViprClient().objectVpools().getAll();
    }

    public static List<ObjectVirtualPoolRestRep> getObjectVirtualPools(ResourceFilter<ObjectVirtualPoolRestRep> filter) {
        return getViprClient().objectVpools().getAll(filter);
    }

    public static List<FileVirtualPoolRestRep> getObjectVirtualPools(Collection<URI> ids) {
        return getViprClient().fileVpools().getByIds(ids);
    }
    public static VirtualPoolCommonRestRep getVirtualPool(String id) {
        try {
            BlockVirtualPoolRestRep virtualPool = getBlockVirtualPool(id);
            if (virtualPool != null) {
                return virtualPool;
            }
        } catch (ServiceErrorException e) {
            if (e.getServiceError().getCode() != 1008) {
                throw e;
            }
        }
        FileVirtualPoolRestRep virtualPool = getFileVirtualPool(id);
        if (virtualPool != null) {
            return virtualPool;
        }
        return null;
    }

    public static List<VirtualPoolCommonRestRep> getVirtualPools() {
        List<VirtualPoolCommonRestRep> virtualPools = Lists.newArrayList();
        virtualPools.addAll(getBlockVirtualPools());
        virtualPools.addAll(getFileVirtualPools());
        return virtualPools;
    }

    public static List<VirtualPoolCommonRestRep> getVirtualPoolsForVirtualArray(URI virtualArray) {
        List<VirtualPoolCommonRestRep> virtualPools = Lists.newArrayList();
        virtualPools.addAll(getViprClient().blockVpools().getByVirtualArray(virtualArray));
        virtualPools.addAll(getViprClient().fileVpools().getByVirtualArray(virtualArray));
        virtualPools.addAll(getViprClient().objectVpools().getByVirtualArray(virtualArray));
        return virtualPools;
    }

    public static BlockVirtualPoolRestRep create(BlockVirtualPoolParam virtualPool) {
        return getViprClient().blockVpools().create(virtualPool);
    }

    public static FileVirtualPoolRestRep create(FileVirtualPoolParam virtualPool) {
        return getViprClient().fileVpools().create(virtualPool);
    }

    public static ObjectVirtualPoolRestRep create(ObjectVirtualPoolParam virtualPool) {
        return getViprClient().objectVpools().create(virtualPool);
    }
    
    public static BlockVirtualPoolRestRep update(String id, BlockVirtualPoolUpdateParam virtualPool) {
        return getViprClient().blockVpools().update(uri(id), virtualPool);
    }

    public static FileVirtualPoolRestRep update(String id, FileVirtualPoolUpdateParam virtualPool) {
        return getViprClient().fileVpools().update(uri(id), virtualPool);
    }

    public static ObjectVirtualPoolRestRep update(String id, ObjectVirtualPoolUpdateParam virtualPool) {
        return getViprClient().objectVpools().update(uri(id), virtualPool);
    }

    public static List<NamedRelatedResourceRep> refreshMatchingPools(BlockVirtualPoolRestRep virtualPool) {
        return getViprClient().blockVpools().refreshMatchingStoragePools(id(virtualPool));
    }

    public static List<NamedRelatedResourceRep> refreshMatchingPools(FileVirtualPoolRestRep virtualPool) {
        return getViprClient().fileVpools().refreshMatchingStoragePools(id(virtualPool));
    }

    public static List<NamedRelatedResourceRep> refreshMatchingPools(ObjectVirtualPoolRestRep virtualPool) {
        return getViprClient().objectVpools().refreshMatchingStoragePools(id(virtualPool));
    }
    
    public static void deactivateBlock(URI id) {
        getViprClient().blockVpools().deactivate(id);
    }

    public static void deactivateFile(URI id) {
        getViprClient().fileVpools().deactivate(id);
    }

    public static void deactivateObject(URI id) {
        getViprClient().objectVpools().deactivate(id);
    }
    
    public static QuotaInfo getBlockQuota(String id) {
        return getViprClient().blockVpools().getQuota(uri(id));
    }

    public static QuotaInfo getFileQuota(String id) {
        return getViprClient().fileVpools().getQuota(uri(id));
    }

    public static QuotaInfo updateBlockQuota(String id, boolean enable, Long sizeInGB) {
        if (enable) {
            return enableBlockQuota(id, sizeInGB);
        }
        else {
            return disableBlockQuota(id);
        }
    }

    public static QuotaInfo updateFileQuota(String id, boolean enable, Long sizeInGB) {
        if (enable) {
            return enableFileQuota(id, sizeInGB);
        }
        else {
            return disableFileQuota(id);
        }
    }

    public static QuotaInfo enableBlockQuota(String id, Long sizeInGB) {
        return getViprClient().blockVpools().updateQuota(uri(id), new QuotaUpdateParam(true, sizeInGB));
    }

    public static QuotaInfo enableFileQuota(String id, Long sizeInGB) {
        return getViprClient().fileVpools().updateQuota(uri(id), new QuotaUpdateParam(true, sizeInGB));
    }

    public static QuotaInfo disableBlockQuota(String id) {
        return getViprClient().blockVpools().updateQuota(uri(id), new QuotaUpdateParam(false, null));
    }

    public static QuotaInfo disableFileQuota(String id) {
        return getViprClient().fileVpools().updateQuota(uri(id), new QuotaUpdateParam(false, null));
    }

    public static BlockVirtualPoolRestRep updateAssignedBlockPools(String id, Collection<String> addPools,
            Collection<String> removePools) {
        return getViprClient().blockVpools().assignStoragePools(uri(id), createPoolAssignments(addPools, removePools));
    }

    public static FileVirtualPoolRestRep updateAssignedFilePools(String id, Collection<String> addPools,
            Collection<String> removePools) {
        return getViprClient().fileVpools().assignStoragePools(uri(id), createPoolAssignments(addPools, removePools));
    }

    public static ObjectVirtualPoolRestRep updateAssignedObjectPools(String id, Collection<String> addPools,
            Collection<String> removePools) {
        return getViprClient().objectVpools().assignStoragePools(uri(id), createPoolAssignments(addPools, removePools));
    }
    
    private static VirtualPoolPoolUpdateParam createPoolAssignments(Collection<String> addPools,
            Collection<String> removePools) {
        StoragePoolAssignmentChanges changes = new StoragePoolAssignmentChanges();
        if (addPools != null && !addPools.isEmpty()) {
            StoragePoolAssignments add = new StoragePoolAssignments();
            add.getStoragePools().addAll(addPools);
            changes.setAdd(add);
        }
        if (removePools != null && !removePools.isEmpty()) {
            StoragePoolAssignments remove = new StoragePoolAssignments();
            remove.getStoragePools().addAll(removePools);
            changes.setRemove(remove);
        }
        return new VirtualPoolPoolUpdateParam(changes);
    }

    public static List<ACLEntry> getBlockACLs(String id) {
        return getViprClient().blockVpools().getACLs(uri(id));
    }

    public static List<ACLEntry> getFileACLs(String id) {
        return getViprClient().fileVpools().getACLs(uri(id));
    }

    public static List<ACLEntry> updateFileACLs(String id, ACLAssignmentChanges changes) {
        return getViprClient().fileVpools().updateACLs(uri(id), changes);
    }

    public static List<ACLEntry> updateBlockACLs(String id, ACLAssignmentChanges changes) {
        return getViprClient().blockVpools().updateACLs(uri(id), changes);
    }

    public static boolean isHighAvailability(BlockVirtualPoolRestRep vpool) {
        return (vpool.getHighAvailability() != null)
                && HighAvailability.isHighAvailability(vpool.getHighAvailability().getType());
    }
}
