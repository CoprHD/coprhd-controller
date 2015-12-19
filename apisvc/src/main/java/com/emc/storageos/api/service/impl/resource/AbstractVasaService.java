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
import com.emc.storageos.db.client.model.StorageContainer.ProtocolEndpointTypeEnum;
import com.emc.storageos.db.client.model.StorageContainer.ProtocolType;
import com.emc.storageos.db.client.model.StorageContainer.ProvisioningType;
import com.emc.storageos.db.client.model.StorageContainer.SystemType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.vasa.StorageContainerRequestParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public abstract class AbstractVasaService extends TaggedResource{
    
    protected static final String PROTOCOL_NFS = "NFS";
    protected static final String PROTOCOL_CIFS = "CIFS";
    protected static final String PROTOCOL_FC = "FC";
    protected static final String PROTOCOL_SCSI = "SCSI";
    protected static final String PROTOCOL_SCALEIO = "ScaleIO";
    protected static final String PROTOCOL_NFS4X = "NFS4x";

    protected static final String PROVISIONING_TYPE = "provisioning_type";
    protected static final String PROTOCOL_ENDPOINT_TYPE = "protocolEndPointType";
    protected static final String PROTOCOLS = "protocols";
    protected static final String NAME = "name";
    protected static final String DESCRIPTION = "description";
    protected static final String MAXVVOLSIZEMB = "maxVvolSizeMB";
    protected static final String SYSTEM_TYPE = "system_type";
    protected static final String QUOTA_GB = "quotaGB";
    protected static final String TYPE = "type";
    
    private static Set<String> protocols = new HashSet<String>();
    static {
        protocols.add(PROTOCOL_NFS);
        protocols.add(PROTOCOL_CIFS);
        protocols.add(PROTOCOL_FC);
        protocols.add(PROTOCOL_SCSI);
        protocols.add(PROTOCOL_SCALEIO);
        protocols.add(PROTOCOL_NFS4X);
    }

    private static final Logger _log = LoggerFactory.getLogger(AbstractVasaService.class);


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
    

    
    protected boolean validateProtocol(String type, Set<String> protocolsParam) {
        if (null != protocolsParam && !protocolsParam.isEmpty()) {
            if(protocols.containsAll(protocolsParam)){
                return true;
            }else {
                throw APIException.badRequests.invalidProtocols(type, protocolsParam);
            }
        }
        return false;
    }

}
