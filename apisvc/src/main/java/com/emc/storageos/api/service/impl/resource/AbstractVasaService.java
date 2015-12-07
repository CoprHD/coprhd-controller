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
    protected static final String PROTOCOL_ISCSI = "iSCSI";
    protected static final String PROTOCOL_SCALEIO = "ScaleIO";

    protected static final String PROVISIONING_TYPE = "provisioning_type";
    protected static final String PROTOCOL_ENDPOINT_TYPE = "protocolEndPointType";
    protected static final String PROTOCOLS = "protocols";
    protected static final String NAME = "name";
    protected static final String DESCRIPTION = "description";
    protected static final String MAXVVOLSIZEMB = "maxVvolSizeMB";
    protected static final String SYSTEM_TYPE = "system_type";
    protected static final String PROTOCOL_TYPE = "protocolType";
    protected static final String QUOTA_GB = "quotaGB";
    
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
    

    
    protected void validateProtocol(String type, Set<String> protocols) {
        if (null != protocols && !protocols.isEmpty()) {
            // Validate the protocols for type of VirtualPool.
            switch (StorageContainer.ProtocolType.lookup(type)) {
                case file:
                    if (!fileProtocols.containsAll(protocols)) {
                        throw APIException.badRequests.invalidProtocols(type, protocols, PROTOCOL_NFS,
                                PROTOCOL_CIFS);
                    }
                    break;
                case block:
                    if (!blockProtocols.containsAll(protocols)) {
                        throw APIException.badRequests.invalidProtocols(type, protocols, PROTOCOL_FC,
                                PROTOCOL_ISCSI, PROTOCOL_SCALEIO);
                    }
                default:
                    break;
            }
        }
    }

}
