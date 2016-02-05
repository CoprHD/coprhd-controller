/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.response;

import static com.emc.storageos.model.ResourceTypeEnum.AUTHN_PROVIDER;
import static com.emc.storageos.model.ResourceTypeEnum.AUTO_TIERING_POLICY;
import static com.emc.storageos.model.ResourceTypeEnum.BLOCK_CONSISTENCY_GROUP;
import static com.emc.storageos.model.ResourceTypeEnum.BLOCK_MIRROR;
import static com.emc.storageos.model.ResourceTypeEnum.BLOCK_SNAPSHOT;
import static com.emc.storageos.model.ResourceTypeEnum.BLOCK_SNAPSHOT_SESSION;
import static com.emc.storageos.model.ResourceTypeEnum.BLOCK_VPOOL;
import static com.emc.storageos.model.ResourceTypeEnum.BUCKET;
import static com.emc.storageos.model.ResourceTypeEnum.CLUSTER;
import static com.emc.storageos.model.ResourceTypeEnum.COMPUTE_ELEMENT;
import static com.emc.storageos.model.ResourceTypeEnum.COMPUTE_IMAGE;
import static com.emc.storageos.model.ResourceTypeEnum.COMPUTE_IMAGESERVER;
import static com.emc.storageos.model.ResourceTypeEnum.COMPUTE_SYSTEM;
import static com.emc.storageos.model.ResourceTypeEnum.COMPUTE_VPOOL;
import static com.emc.storageos.model.ResourceTypeEnum.CUSTOM_CONFIG;
import static com.emc.storageos.model.ResourceTypeEnum.DATA_STORE;
import static com.emc.storageos.model.ResourceTypeEnum.EXPORT_GROUP;
import static com.emc.storageos.model.ResourceTypeEnum.FC_PORT_CONNECTION;
import static com.emc.storageos.model.ResourceTypeEnum.FILE;
import static com.emc.storageos.model.ResourceTypeEnum.FILE_SNAPSHOT;
import static com.emc.storageos.model.ResourceTypeEnum.FILE_VPOOL;
import static com.emc.storageos.model.ResourceTypeEnum.HOST;
import static com.emc.storageos.model.ResourceTypeEnum.INITIATOR;
import static com.emc.storageos.model.ResourceTypeEnum.IPINTERFACE;
import static com.emc.storageos.model.ResourceTypeEnum.MIGRATION;
import static com.emc.storageos.model.ResourceTypeEnum.NETWORK;
import static com.emc.storageos.model.ResourceTypeEnum.NETWORK_SYSTEM;
import static com.emc.storageos.model.ResourceTypeEnum.OBJECT_VPOOL;
import static com.emc.storageos.model.ResourceTypeEnum.PROJECT;
import static com.emc.storageos.model.ResourceTypeEnum.PROTECTION_SET;
import static com.emc.storageos.model.ResourceTypeEnum.PROTECTION_SYSTEM;
import static com.emc.storageos.model.ResourceTypeEnum.QUOTA_DIR;
import static com.emc.storageos.model.ResourceTypeEnum.RDF_GROUP;
import static com.emc.storageos.model.ResourceTypeEnum.SMIS_PROVIDER;
import static com.emc.storageos.model.ResourceTypeEnum.STORAGE_POOL;
import static com.emc.storageos.model.ResourceTypeEnum.STORAGE_PORT;
import static com.emc.storageos.model.ResourceTypeEnum.STORAGE_PROVIDER;
import static com.emc.storageos.model.ResourceTypeEnum.STORAGE_SYSTEM;
import static com.emc.storageos.model.ResourceTypeEnum.STORAGE_TIER;
import static com.emc.storageos.model.ResourceTypeEnum.SYS_EVENT;
import static com.emc.storageos.model.ResourceTypeEnum.TASK;
import static com.emc.storageos.model.ResourceTypeEnum.TENANT;
import static com.emc.storageos.model.ResourceTypeEnum.UNMANAGED_FILESYSTEMS;
import static com.emc.storageos.model.ResourceTypeEnum.UNMANAGED_VOLUMES;
import static com.emc.storageos.model.ResourceTypeEnum.USER_GROUP;
import static com.emc.storageos.model.ResourceTypeEnum.VARRAY;
import static com.emc.storageos.model.ResourceTypeEnum.VCENTER;
import static com.emc.storageos.model.ResourceTypeEnum.VCENTERDATACENTER;
import static com.emc.storageos.model.ResourceTypeEnum.VDC;
import static com.emc.storageos.model.ResourceTypeEnum.VIRTUAL_NAS;
import static com.emc.storageos.model.ResourceTypeEnum.VOLUME;
import static com.emc.storageos.model.ResourceTypeEnum.VOLUME_GROUP;
import static com.emc.storageos.model.ResourceTypeEnum.VPLEX_MIRROR;
import static com.emc.storageos.model.ResourceTypeEnum.VPOOL;
import static com.emc.storageos.model.ResourceTypeEnum.WORKFLOW;
import static com.emc.storageos.model.ResourceTypeEnum.WORKFLOW_STEP;
import static com.emc.storageos.model.ResourceTypeEnum.SCHEDULE_POLICY;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.CustomConfig;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostingDeviceInfo;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.SMISProvider;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.SysEvent;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.model.ResourceTypeEnum;

public class ResourceTypeMapping {
    private static final Logger _log = LoggerFactory.getLogger(ResourceTypeMapping.class);
    // Mapping of Resource Type -> DB Class
    private static final Map<ResourceTypeEnum, Class<? extends DataObject>> classMapping = new HashMap<>();
    // Reverse mapping of DB Class -> Resource Type
    private static final Map<Class<? extends DataObject>, ResourceTypeEnum> resourceMapping = new HashMap<>();

    static {
        classMapping.put(FILE, FileShare.class);
        classMapping.put(VOLUME, Volume.class);
        classMapping.put(BUCKET, Bucket.class);
        classMapping.put(PROJECT, Project.class);
        classMapping.put(TENANT, TenantOrg.class);
        // TODO: Conflict between VPOOL types
        classMapping.put(VPOOL, VirtualPool.class);
        classMapping.put(BLOCK_VPOOL, VirtualPool.class);
        classMapping.put(FILE_VPOOL, VirtualPool.class);
        classMapping.put(OBJECT_VPOOL, VirtualPool.class);
        classMapping.put(VARRAY, VirtualArray.class);
        classMapping.put(STORAGE_SYSTEM, StorageSystem.class);
        classMapping.put(STORAGE_POOL, StoragePool.class);
        classMapping.put(STORAGE_TIER, StorageTier.class);
        classMapping.put(STORAGE_PORT, StoragePort.class);
        classMapping.put(RDF_GROUP, RemoteDirectorGroup.class);
        classMapping.put(PROTECTION_SYSTEM, ProtectionSystem.class);
        classMapping.put(PROTECTION_SET, ProtectionSet.class);
        classMapping.put(FILE_SNAPSHOT, Snapshot.class);
        classMapping.put(BLOCK_SNAPSHOT, BlockSnapshot.class);
        classMapping.put(BLOCK_MIRROR, BlockMirror.class);
        classMapping.put(VPLEX_MIRROR, VplexMirror.class);
        classMapping.put(NETWORK, Network.class);
        classMapping.put(EXPORT_GROUP, ExportGroup.class);
        classMapping.put(SMIS_PROVIDER, SMISProvider.class);
        classMapping.put(STORAGE_PROVIDER, StorageProvider.class);
        classMapping.put(NETWORK_SYSTEM, NetworkSystem.class);
        classMapping.put(FC_PORT_CONNECTION, FCEndpoint.class);
        classMapping.put(AUTHN_PROVIDER, AuthnProvider.class);
        classMapping.put(WORKFLOW, Workflow.class);
        classMapping.put(WORKFLOW_STEP, WorkflowStep.class);
        classMapping.put(HOST, Host.class);
        classMapping.put(COMPUTE_SYSTEM, ComputeSystem.class);
        classMapping.put(COMPUTE_ELEMENT, ComputeElement.class);
        classMapping.put(COMPUTE_IMAGE, ComputeImage.class);
        classMapping.put(COMPUTE_VPOOL, ComputeVirtualPool.class);
        classMapping.put(VCENTER, Vcenter.class);
        classMapping.put(CLUSTER, Cluster.class);
        classMapping.put(INITIATOR, Initiator.class);
        classMapping.put(IPINTERFACE, IpInterface.class);
        classMapping.put(VCENTERDATACENTER, VcenterDataCenter.class);
        classMapping.put(AUTO_TIERING_POLICY, AutoTieringPolicy.class);
        classMapping.put(MIGRATION, Migration.class);
        classMapping.put(UNMANAGED_VOLUMES, UnManagedVolume.class);
        classMapping.put(UNMANAGED_FILESYSTEMS, UnManagedFileSystem.class);
        classMapping.put(DATA_STORE, HostingDeviceInfo.class);
        classMapping.put(BLOCK_CONSISTENCY_GROUP, BlockConsistencyGroup.class);
        classMapping.put(VDC, VirtualDataCenter.class);
        classMapping.put(TASK, Task.class);
        classMapping.put(QUOTA_DIR, QuotaDirectory.class);
        classMapping.put(CUSTOM_CONFIG, CustomConfig.class);
        classMapping.put(SYS_EVENT, SysEvent.class);
        classMapping.put(USER_GROUP, UserGroup.class);
        classMapping.put(VIRTUAL_NAS, VirtualNAS.class);
        classMapping.put(COMPUTE_IMAGESERVER, ComputeImageServer.class);
        classMapping.put(VOLUME_GROUP, VolumeGroup.class);
        classMapping.put(BLOCK_SNAPSHOT_SESSION, BlockSnapshotSession.class);
        classMapping.put(SCHEDULE_POLICY, SchedulePolicy.class);

        for (Map.Entry<ResourceTypeEnum, Class<? extends DataObject>> entry : classMapping.entrySet()) {
            resourceMapping.put(entry.getValue(), entry.getKey());
        }
    }

    public static Class<? extends DataObject> getDataObjectClass(ResourceTypeEnum type) {
        return classMapping.get(type);
    }

    public static ResourceTypeEnum getResourceType(DataObject object) {
        return getResourceType(object.getClass());
    }

    public static ResourceTypeEnum getResourceType(Class objectClazz) {
        if (!resourceMapping.containsKey(objectClazz)) {
            _log.error("No resourceMapping for type " + objectClazz.getName());
        }
        return resourceMapping.get(objectClazz);
    }
}
