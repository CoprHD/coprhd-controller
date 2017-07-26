/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDiscoveredSystemObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapTenantResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.UCSServiceProfile;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.StringHashMapEntry;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParametersRep;
import com.emc.storageos.model.host.HostInterfaceRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.host.PreferredPoolParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;

public class HostMapper {
    public static void mapHostInterfaceFields(HostInterface from, HostInterfaceRestRep to) {
        to.setHost(toRelatedResource(ResourceTypeEnum.HOST, from.getHost()));
        to.setProtocol(from.getProtocol());
        to.setRegistrationStatus(from.getRegistrationStatus());
    }

    public static InitiatorRestRep map(Initiator from) {
        if (from == null) {
            return null;
        }
        InitiatorRestRep to = new InitiatorRestRep();
        mapHostInterfaceFields(from, to);
        mapDataObjectFields(from, to);
        to.setHostName(from.getHostName());
        to.setInitiatorNode(from.getInitiatorNode());
        to.setInitiatorPort(from.getInitiatorPort());
        if (NullColumnValueGetter.isNotNullValue(from.getClusterName())) {
            to.setClusterName(from.getClusterName());
        }
        return to;
    }

    public static IpInterfaceRestRep map(IpInterface from) {
        if (from == null) {
            return null;
        }
        IpInterfaceRestRep to = new IpInterfaceRestRep();
        mapHostInterfaceFields(from, to);
        mapDataObjectFields(from, to);
        to.setIpAddress(from.getIpAddress());
        to.setNetmask(from.getNetmask());
        to.setPrefixLength(from.getPrefixLength());
        to.setScopeId(from.getScopeId());
        return to;
    }

    public static ExportGroupRestRep map(ExportGroup from, List<Initiator> initiators,
            Map<String, Integer> volumes, List<Host> hosts, List<Cluster> clusters,
            List<ExportPathParams> exportPathParams) {
        if (from == null) {
            return null;
        }
        ExportGroupRestRep to = new ExportGroupRestRep();
        mapDataObjectFields(from, to);
        if (initiators != null) {
            for (Initiator initiator : initiators) {
                to.getInitiators().add(map(initiator));
            }
        }
        if (volumes != null) {
            for (Map.Entry<String, Integer> entry : volumes.entrySet()) {
                ExportBlockParam volume = new ExportBlockParam();
                volume.setId(URI.create(entry.getKey()));
                Integer lun = entry.getValue();
                if (lun != null && lun != ExportGroup.LUN_UNASSIGNED) {
                    volume.setLun(lun);
                }
                to.getVolumes().add(volume);
            }
        }
        if (hosts != null) {
            for (Host host : hosts) {
                to.getHosts().add(map(host));
            }
        }
        if (clusters != null) {
            for (Cluster cluster : clusters) {
                to.getClusters().add(map(cluster));

            }
        }
        
        if (exportPathParams != null && !exportPathParams.isEmpty()) {
            for (ExportPathParams pathParam : exportPathParams) {
                ExportPathParametersRep pathParamRep = map(pathParam);
                for (Map.Entry<String, String> entry : from.getPathParameters().entrySet()) {
                    if (entry.getValue().equals(pathParam.getId().toString())) {
                        pathParamRep.getBlockObjects().add(entry.getKey());
                    }
                }
                to.getPathParams().add(pathParamRep);
            }
        }
        if (from.getProject() != null) {
            to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));
        }
        if (from.getTenant() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant().getURI()));
        }
        to.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, from.getVirtualArray()));
        if (from.getType() != null) {
            to.setType(from.getType());
        }
        to.setGeneratedName(from.getGeneratedName());
        if (from.getAltVirtualArrays() != null && !from.getAltVirtualArrays().isEmpty()) {
            // The alternate virtual array is a map from Storage System URI to Virtual Array URI
            List<StringHashMapEntry> toVirtualArrays = new ArrayList<StringHashMapEntry>();
            for (Map.Entry<String, String> entry : from.getAltVirtualArrays().entrySet()) {
                StringHashMapEntry toEntry = new StringHashMapEntry();
                toEntry.setName(entry.getKey());
                toEntry.setValue(entry.getValue());
                toVirtualArrays.add(toEntry);
            }
            to.setAltVirtualArrays(toVirtualArrays);
        }
        return to;
    }

    public static HostRestRep map(Host from) {
        if (from == null) {
            return null;
        }
        HostRestRep to = new HostRestRep();
        mapDiscoveredSystemObjectFields(from, to);
        to.setHostName(from.getHostName());
        to.setType(from.getType());
        to.setUsername(from.getUsername());
        to.setPortNumber(from.getPortNumber());
        to.setOsVersion(from.getOsVersion());
        to.setUseSsl(from.getUseSSL());
        to.setCluster(toRelatedResource(ResourceTypeEnum.CLUSTER, from.getCluster()));
        to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject()));
        to.setComputeElement(toRelatedResource(ResourceTypeEnum.COMPUTE_ELEMENT, from.getComputeElement()));
        to.setvCenterDataCenter(toRelatedResource(ResourceTypeEnum.VCENTERDATACENTER, from.getVcenterDataCenter()));
        to.setComputeVirtualPool(toRelatedResource(ResourceTypeEnum.COMPUTE_VPOOL, from.getComputeVirtualPoolId()));
        if ((from.getVolumeGroupIds() != null) && (!from.getVolumeGroupIds().isEmpty())) {
            List<RelatedResourceRep> volumeGroups = new ArrayList<RelatedResourceRep>();
            for (String volumeGroup : from.getVolumeGroupIds()) {
                volumeGroups.add(toRelatedResource(ResourceTypeEnum.VOLUME_GROUP, URI.create(volumeGroup)));
            }
            to.setVolumeGroups(volumeGroups);
        }

        StringMap pools = from.getPreferredPools();
        if (pools != null && !pools.isEmpty()) {
            for (Map.Entry<String, String> entry : pools.entrySet()) {
                RelatedResourceRep poolRep = toRelatedResource(ResourceTypeEnum.STORAGE_POOL, URI.create(entry.getKey()));
                PreferredPoolParam pool = new PreferredPoolParam(poolRep, entry.getValue());
                to.getPreferredPools().add(pool);
            }
        }

        to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant()));
        to.setDiscoverable(from.getDiscoverable());
        to.setBootVolume(toRelatedResource(ResourceTypeEnum.VOLUME, from.getBootVolumeId()));
        if (from.getDiscoverable() != null && from.getDiscoverable() == false) {
            to.setProvisioningJobStatus(from.getProvisioningStatus());
        }
        return to;
    }

    public static HostRestRep map(Host host, ComputeElement computeElement, UCSServiceProfile serviceProfile, ComputeSystem computeSystem){
        HostRestRep to = map(host);
        if (computeElement!=null){
            StringBuffer buffer = new StringBuffer();
            if (computeSystem!=null){
                buffer.append(computeSystem.getLabel()+ " : ");
            }
            buffer.append(computeElement.getLabel());
            to.setComputeElementName(buffer.toString());
        }
        if (serviceProfile != null) {
            StringBuffer buffer = new StringBuffer();
            if (computeSystem!=null){
                buffer.append(computeSystem.getLabel()+ " : ");
            }
            buffer.append(serviceProfile.getLabel());
            to.setServiceProfileName(buffer.toString());
        }
        if (computeSystem != null) {
            to.setComputeSystem(toNamedRelatedResource(computeSystem));
        }
        return to;
    }
    public static ClusterRestRep map(Cluster from) {
        if (from == null) {
            return null;
        }
        ClusterRestRep to = new ClusterRestRep();
        mapTenantResource(from, to);
        to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject()));
        to.setVcenterDataCenter(toRelatedResource(ResourceTypeEnum.VCENTERDATACENTER, from.getVcenterDataCenter()));
        to.setAutoExportEnabled(from.getAutoExportEnabled());
        if ((from.getVolumeGroupIds() != null) && (!from.getVolumeGroupIds().isEmpty())) {
            List<RelatedResourceRep> volumeGroups = new ArrayList<RelatedResourceRep>();
            for (String volumeGroup : from.getVolumeGroupIds()) {
                volumeGroups.add(toRelatedResource(ResourceTypeEnum.VOLUME_GROUP, URI.create(volumeGroup)));
            }
            to.setVolumeGroups(volumeGroups);
        }
        return to;
    }

    public static VcenterDataCenterRestRep map(VcenterDataCenter from) {
        if (from == null) {
            return null;
        }
        VcenterDataCenterRestRep to = new VcenterDataCenterRestRep();
        mapTenantResource(from, to);
        to.setVcenter(toRelatedResource(ResourceTypeEnum.VCENTER, from.getVcenter()));
        return to;
    }

    public static VcenterRestRep map(Vcenter from) {
        if (from == null) {
            return null;
        }
        VcenterRestRep to = new VcenterRestRep();
        mapDiscoveredSystemObjectFields(from, to);
        to.setUsername(from.getUsername());
        to.setPortNumber(from.getPortNumber());
        to.setIpAddress(from.getIpAddress());
        to.setUseSsl(from.getUseSSL());
        to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.findVcenterTenant()));
        to.setOsVersion(from.getOsVersion());
        to.setCascadeTenancy(from.getCascadeTenancy());
        return to;
    }
    
    public static ExportPathParametersRep map(ExportPathParams from) {
        if (from == null) {
            return null;
        }
        ExportPathParametersRep to = new ExportPathParametersRep();
        to.setMaxPaths(from.getMaxPaths());
        to.setMinPaths(from.getMinPaths());
        to.setPathsPerInitiator(from.getPathsPerInitiator());
        if (!from.getStoragePorts().isEmpty() && to.getStoragePorts() == null) {
            to.setStoragePorts(new ArrayList<URI>());
        }
        for (String portId : from.getStoragePorts()) {
            to.getStoragePorts().add(URI.create(portId));
        }
        return to;
    }
}
