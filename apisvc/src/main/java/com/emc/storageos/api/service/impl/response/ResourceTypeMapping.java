/*
 * Copyright 2015 EMC Corporation
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.api.service.impl.response;

import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.db.client.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.model.ResourceTypeEnum;

import static com.emc.storageos.model.ResourceTypeEnum.*;

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
        classMapping.put(OPENSTACK_TENANT, OSTenant.class);

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
