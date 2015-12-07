package com.emc.storageos.api.service.impl.resource;

import java.util.EnumSet;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.CapabilityProfile;
import com.emc.storageos.db.client.model.StorageContainer.ProtocolEndpointTypeEnum;
import com.emc.storageos.db.client.model.StorageContainer.ProtocolType;
import com.emc.storageos.db.client.model.StorageContainer.ProvisioningType;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vasa.CapabilityProfileCreateRequestParam;
import com.emc.storageos.model.vasa.VasaCommonRestRequest;

public abstract class AbstractCapabilityProfileService extends AbstractVasaService{

    public <T extends VasaCommonRestRequest> void populateCommonFields(CapabilityProfile capabilityProfile, T param) throws DatabaseException{
     // Validate the name for not null and non-empty values
        if (StringUtils.isNotEmpty(param.getName())) {
            capabilityProfile.setLabel(param.getName());
        }
        if (StringUtils.isNotEmpty(param.getDescription())) {
            capabilityProfile.setDescription(param.getDescription());
        }
        
        ArgValidator.checkFieldNotEmpty(param.getProtocolType(), PROTOCOL_TYPE);
        ArgValidator.checkFieldValueFromEnum(param.getProtocolType(), PROTOCOL_TYPE,
                EnumSet.of(ProtocolType.block, ProtocolType.file));
        
        if (null != param.getProtocolType()) {
            capabilityProfile.setProtocolType(param.getProtocolType());
        }


        ArgValidator.checkFieldNotEmpty(param.getProvisionType(), PROVISIONING_TYPE);
        ArgValidator.checkFieldValueFromEnum(param.getProvisionType(), PROVISIONING_TYPE,
                EnumSet.of(ProvisioningType.Thick, ProvisioningType.Thin));

        capabilityProfile.setId(URIUtil.createId(CapabilityProfile.class));
        if (null != param.getProvisionType()) {
            capabilityProfile.setProvisioningType(param.getProvisionType());
        }
        
        capabilityProfile.setProtocols(new StringSet());

        // Validate the protocols for not null and non-empty values
        ArgValidator.checkFieldNotEmpty(param.getProtocols(), PROTOCOLS);
        // Validate the protocols for type of capabilityProfile.
        validateProtocol(capabilityProfile.getProtocolType(), param.getProtocols());
        capabilityProfile.getProtocols().addAll(param.getProtocols());

    }
    
    public void populateCommonCapabilityProfileFields(CapabilityProfile capabilityProfile,
            CapabilityProfileCreateRequestParam param) throws DatabaseException{
        
        ArgValidator.checkFieldNotEmpty(param.getProtocolEndPointType(), PROTOCOL_ENDPOINT_TYPE);
        ArgValidator.checkFieldValueFromEnum(param.getProtocolEndPointType(), PROTOCOL_ENDPOINT_TYPE,
                EnumSet.of(ProtocolEndpointTypeEnum.NFS, ProtocolEndpointTypeEnum.NFS4x, ProtocolEndpointTypeEnum.SCSI));
        if(null != param.getProtocolEndPointType()){
            capabilityProfile.setProtocolEndPointType(param.getProtocolEndPointType());
        }
        
        ArgValidator.checkFieldMaximum(param.getQuotaGB(), 2000, QUOTA_GB);
        capabilityProfile.setQuotaGB(param.getQuotaGB());
        
        if(null != param.getDriveType()){
            capabilityProfile.setDriveType(param.getDriveType());
        }
        
        if(null != param.getHighAvailability()){
            capabilityProfile.setHighAvailability(param.getHighAvailability());
        }
        
          
    }
}
