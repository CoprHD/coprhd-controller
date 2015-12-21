package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFieldsNoLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;

import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.model.CapabilityProfile;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ProtocolEndpoint;
import com.emc.storageos.db.client.model.StorageContainer;
import com.emc.storageos.db.client.model.VVol;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.adapters.StringMapAdapter;
import com.emc.storageos.model.vasa.CapabilityProfileCreateResponse;
import com.emc.storageos.model.vasa.ProtocolEndpointResponseParam;
import com.emc.storageos.model.vasa.StorageContainerCreateResponse;
import com.emc.storageos.model.vasa.VVolResponseParam;
import com.emc.storageos.model.vasa.VasaCommonRestResponse;

public class VasaObjectMapper {

    public static StorageContainerCreateResponse toStorageContainer(StorageContainer from) {
        if (from == null) {
            return null;
        }
        StorageContainerCreateResponse to = new StorageContainerCreateResponse();
        
        to.setType(from.getType());
        to.setDescription(from.getDescription());
        
        to.setProtocolEndPointType(from.getProtocolEndPointType());
        to.setMaxVvolSizeMB(from.getMaxVvolSizeMB());
        if (from.getStorageSystem() != null) {
            to.setStorageSystem(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageSystem()));
        }
        if (from.getVirtualArrays() != null) {
            for (String neighborhood : from.getVirtualArrays()) {
                to.getVirtualArrays().add(toRelatedResource(ResourceTypeEnum.VARRAY, URI.create(neighborhood)));
            }
        }
        
        if(from.getVirtualPools() != null) {
            for(String vPool : from.getVirtualPools()){
                to.getVpools().add(toRelatedResource(ResourceTypeEnum.VPOOL, URI.create(vPool)));
            }
        }
        
        if(from.getPhysicalStorageContainers() != null) {
            for(String physicalStorageContainer : from.getPhysicalStorageContainers()){
                to.getPhysicalStorageContainers().add(toRelatedResource(ResourceTypeEnum.STORAGE_CONTAINER, URI.create(physicalStorageContainer)));
            }
        }
        
        return mapCommonVasaFields(from, to);
    }

    private static <T extends VasaCommonRestResponse> T mapCommonVasaFields(DataObject from, T to) {
        mapDataObjectFieldsNoLink(from, to);
        ResourceTypeEnum type = getResource(from);
        to.setLink(new RestLinkRep("self", RestLinkFactory.newLink(type, from.getId())));       
        return to;
    }
    
    public static CapabilityProfileCreateResponse toCapabilityProfile(VirtualPool from){
        if(from == null){
            return null;
        }
        CapabilityProfileCreateResponse to = new CapabilityProfileCreateResponse();
        to.setType(from.getType());
        to.setDescription(from.getDescription());
        to.setProtocols(from.getProtocols());
        to.setProvisioningType(from.getSupportedProvisioningType());
        
        Object[] protocols = from.getProtocols().toArray();
        to.setProtocolEndPointType(protocols[0].toString());
        
        to.setQuotaGB(from.getQuota());
        to.setDriveType(from.getDriveType());
        to.setHighAvailability(from.getHighAvailability());
        
        return mapCommonVasaFields(from, to);
        
    }
    
    public static ProtocolEndpointResponseParam toProtocolEndpoint(ProtocolEndpoint from){
        if(from == null){
            return null;
        }
        ProtocolEndpointResponseParam to = new ProtocolEndpointResponseParam();
        
        mapCommonVasaFields(from, to);
        
        to.setDescription(from.getDescription());
        to.setProtocolEndpointType(from.getProtocolEndpointType());
        to.setLunId(from.getLunId());
        to.setIpAddress(from.getIpAddress());
        to.setStorageSystem(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageSystem()));
        to.setServerMount(from.getServerMount());
        to.setTransportIpAddress(from.getTransportIpAddress());
        to.setAuthType(from.getAuthType());
        to.setInBandCapability(from.getInBandCapability());
        to.setServerScope(from.getServerScope());
        to.setServerMajor(from.getServerMajor());
        to.setServerMinor(from.getServerMinor());
        
        return to;
        
    }
    
    public static VVolResponseParam toVVol(VVol from){
        if(from == null){
            return null;
        }
        VVolResponseParam to = new VVolResponseParam();
        mapCommonVasaFields(from, to);
        
        to.setDescription(from.getDescription());
        to.setProtocolEndpoint(toRelatedResource(ResourceTypeEnum.PROTOCOL_ENDPOINT,from.getProtocolEndpoint()));
        to.setvVolSecondaryId(from.getvVolSecondaryId());
        to.setExtensions(new StringMapAdapter().marshal(from.getExtensions()));
        
        
        
        return to;
        
    }
    
    private static ResourceTypeEnum getResource(DataObject obj){
        if(obj instanceof StorageContainer){
            return ResourceTypeEnum.STORAGE_CONTAINER;
        }else if(obj instanceof CapabilityProfile){
            return ResourceTypeEnum.CAPABILITY_PROFILE;
        }else if(obj instanceof ProtocolEndpoint){
            return ResourceTypeEnum.PROTOCOL_ENDPOINT;
        }else if(obj instanceof VVol){
            return ResourceTypeEnum.V_VOL;
        }
        return null;
        
    }
    

}
