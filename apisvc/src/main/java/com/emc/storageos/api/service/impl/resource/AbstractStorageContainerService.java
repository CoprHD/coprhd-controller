package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageContainer;
import com.emc.storageos.db.client.model.StorageContainer.ProvisioningType;
import com.emc.storageos.db.client.model.StorageContainer.ProtocolEndpointTypeEnum;
import com.emc.storageos.db.client.model.StorageContainer.SystemType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.vasa.StorageContainerRequestParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public abstract class AbstractStorageContainerService extends TaggedResource{
    
    protected static final String PROTOCOL_NFS = "NFS";
    protected static final String PROTOCOL_CIFS = "CIFS";
    protected static final String PROTOCOL_FC = "FC";
    protected static final String PROTOCOL_ISCSI = "iSCSI";
    protected static final String PROTOCOL_SCALEIO = "ScaleIO";

    protected static final String PROVISIONING_TYPE = "provisioning_type";
    protected static final String PROTOCOL_ENDPOINT_TYPE = "protocolEndPointType";
    protected static final String PROTOCOLS = "protocols";
    protected static final String NAME = "name";
    protected static final String DESCRIPTION = "description";
    protected static final String MAXVVOLSIZEMB = "maxVvolSizeMB";
    protected static final String SYSTEM_TYPE = "system_type";
    
    private static Set<String> fileProtocols = new HashSet<String>();
    private static Set<String> blockProtocols = new HashSet<String>();
    static {
        // Initialize file type protocols
        fileProtocols.add(PROTOCOL_NFS);
        fileProtocols.add(PROTOCOL_CIFS);

        // initialize block protocols
        blockProtocols.add(PROTOCOL_FC);
        blockProtocols.add(PROTOCOL_ISCSI);
        blockProtocols.add(PROTOCOL_SCALEIO);
    }

    private static final Logger _log = LoggerFactory.getLogger(AbstractStorageContainerService.class);


    @Override
    protected DataObject queryResource(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        // TODO Auto-generated method stub
        return null;
    }
    
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
        validateVirtualPoolProtocol(storageContainer.getType(), param.getProtocols());
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
    
    protected void validateVirtualPoolProtocol(String type, Set<String> protocols) {
        if (null != protocols && !protocols.isEmpty()) {
            // Validate the protocols for type of VirtualPool.
            switch (VirtualPool.Type.lookup(type)) {
                case file:
                    if (!fileProtocols.containsAll(protocols)) {
                        throw APIException.badRequests.invalidProtocolsForVirtualPool(type, protocols, PROTOCOL_NFS,
                                PROTOCOL_CIFS);
                    }
                    break;
                case block:
                    if (!blockProtocols.containsAll(protocols)) {
                        throw APIException.badRequests.invalidProtocolsForVirtualPool(type, protocols, PROTOCOL_FC,
                                PROTOCOL_ISCSI, PROTOCOL_SCALEIO);
                    }
                default:
                    break;
            }
        }
    }

}
