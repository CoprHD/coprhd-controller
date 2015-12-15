package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.EnumSet;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageContainer;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.StorageContainer.ProtocolEndpointTypeEnum;
import com.emc.storageos.db.client.model.StorageContainer.ProtocolType;
import com.emc.storageos.db.client.model.StorageContainer.ProvisioningType;
import com.emc.storageos.db.client.model.StorageContainer.SystemType;
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
         
         ArgValidator.checkFieldNotEmpty(param.getProtocolEndPointType(), PROTOCOL_ENDPOINT_TYPE);
         ArgValidator.checkFieldValueFromEnum(param.getProtocolEndPointType(), PROTOCOL_ENDPOINT_TYPE,
                 EnumSet.of(ProtocolEndpointTypeEnum.NFS, ProtocolEndpointTypeEnum.NFS4x, ProtocolEndpointTypeEnum.SCSI));
         if(null != param.getProtocolEndPointType()){
             storageContainer.setProtocolEndPointType(param.getProtocolEndPointType());
         }
         
         ArgValidator.checkFieldMaximum(param.getMaxVvolSizeMB(), 2000000, MAXVVOLSIZEMB);
         storageContainer.setMaxVvolSizeMB(param.getMaxVvolSizeMB());
         
         ArgValidator.checkFieldNotEmpty(param.getSystemType(), SYSTEM_TYPE);
         ArgValidator.checkFieldValueFromEnum(param.getSystemType(), SYSTEM_TYPE,
                 EnumSet.of(SystemType.vmax, SystemType.vnxe, SystemType.vnxblock));
         
         if (null != param.getSystemType()) {
             storageContainer.setSystemType(param.getSystemType());
         }
         
         
         if (null != param.getProtocolType()) {
             storageContainer.setProtocolType(param.getProtocolType());
         }


         ArgValidator.checkFieldNotEmpty(param.getProvisionType(), PROVISIONING_TYPE);
         ArgValidator.checkFieldValueFromEnum(param.getProvisionType(), PROVISIONING_TYPE,
                 EnumSet.of(ProvisioningType.Thick, ProvisioningType.Thin));

         storageContainer.setId(URIUtil.createId(StorageContainer.class));
         if (null != param.getProvisionType()) {
             storageContainer.setProvisioningType(param.getProvisionType());
         }
         
         storageContainer.setProtocols(new StringSet());

         // Validate the protocols for not null and non-empty values
         ArgValidator.checkFieldNotEmpty(param.getProtocols(), PROTOCOLS);
         // Validate the protocols for type of StorageContianer.
         validateProtocol(storageContainer.getProtocolType(), param.getProtocols());
         storageContainer.getProtocols().addAll(param.getProtocols());
         
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
         
     }

}
