package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.EnumSet;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageContainer;
import com.emc.storageos.db.client.model.StorageContainer.ProtocolEndpointTypeEnum;
import com.emc.storageos.db.client.model.StorageContainer.Type;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vasa.StorageContainerRequestParam;

public class AbstractStorageContainerService extends AbstractVasaService{
    
    private static final Logger _log = LoggerFactory.getLogger(AbstractStorageContainerService.class);
    
    /**
     * This method is used to set the common StorageContainer create params in
     * StorageContainer model.
     * @param storageContainer StorageContianer Model
     * @param param
     * @throws DatabaseException
     */
     protected void populateCommonStorageContainerCreateParams(StorageContainer storageContainer,
             StorageContainerRequestParam param) throws DatabaseException {
         // Validate the name for not null and non-empty values
         if (StringUtils.isNotEmpty(param.getName())) {
             storageContainer.setLabel(param.getName());
         }
         if (StringUtils.isNotEmpty(param.getDescription())) {
             storageContainer.setDescription(param.getDescription());
         }
         
         storageContainer.setId(URIUtil.createId(StorageContainer.class));
         
         ArgValidator.checkFieldNotEmpty(param.getType(), TYPE);
         ArgValidator.checkFieldValueFromEnum(param.getType(), TYPE,
                 EnumSet.of(Type.geo, Type.physical));
         
         ArgValidator.checkFieldNotEmpty(param.getProtocolEndPointType(), PROTOCOL_ENDPOINT_TYPE);
         ArgValidator.checkFieldValueFromEnum(param.getProtocolEndPointType(), PROTOCOL_ENDPOINT_TYPE,
                 EnumSet.of(ProtocolEndpointTypeEnum.NFS, ProtocolEndpointTypeEnum.NFS4x, ProtocolEndpointTypeEnum.SCSI));
         if(null != param.getProtocolEndPointType()){
             storageContainer.setProtocolEndPointType(param.getProtocolEndPointType());
         }
         
         ArgValidator.checkFieldMaximum(param.getMaxVvolSizeMB(), 2000000, MAXVVOLSIZEMB);
         storageContainer.setMaxVvolSizeMB(param.getMaxVvolSizeMB());
         
         //validate and set storage system
         if(param.getStorageSystem() != null){
             URI storageSystemURI = URI.create(param.getStorageSystem());
             ArgValidator.checkUri(storageSystemURI);
             StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);
             ArgValidator.checkEntity(storageSystem, storageSystemURI, isIdEmbeddedInURL(storageSystemURI));
             storageContainer.setStorageSystem(storageSystemURI);
         }

         // validate and set neighborhoods
         if (param.getVarrays() != null) {
             storageContainer.setVirtualArrays(new StringSet());
             for (String neighborhood : param.getVarrays()) {
                 URI neighborhoodURI = URI.create(neighborhood);
                 ArgValidator.checkUri(neighborhoodURI);
                 VirtualArray varray = _dbClient.queryObject(VirtualArray.class, neighborhoodURI);
                 ArgValidator.checkEntity(varray, neighborhoodURI, isIdEmbeddedInURL(neighborhoodURI));
                 storageContainer.getVirtualArrays().add(neighborhood);
             }
         }
         
         //validate and set virtual pools
         if(param.getvPools() != null) {
             storageContainer.setVirtualPools(new StringSet());
             for(String vPool : param.getvPools()){
                 URI vPoolURI = URI.create(vPool);
                 ArgValidator.checkUri(vPoolURI);
                 VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, vPoolURI);
                 ArgValidator.checkEntity(virtualPool, vPoolURI, isIdEmbeddedInURL(vPoolURI));
                 storageContainer.getVirtualPools().add(vPool);
             }
         }
         
         if(param.getPhysicalStorageContainers() != null){
             storageContainer.setPhysicalStorageContainers(new StringSet());
             for(String physicalStorageContainer : param.getPhysicalStorageContainers()){
                 URI physicalStorageContainerURI = URI.create(physicalStorageContainer);
                 ArgValidator.checkUri(physicalStorageContainerURI);
                 StorageContainer storageContainerObj = _dbClient.queryObject(StorageContainer.class, physicalStorageContainerURI);
                 ArgValidator.checkEntity(storageContainerObj, physicalStorageContainerURI, isIdEmbeddedInURL(physicalStorageContainerURI));
                 if(storageContainerObj.getType().equals("physical") && storageContainer.getType().equals("geo")){
                     storageContainer.getPhysicalStorageContainers().add(physicalStorageContainer);
                 }
             }
         }
         
     }

}
