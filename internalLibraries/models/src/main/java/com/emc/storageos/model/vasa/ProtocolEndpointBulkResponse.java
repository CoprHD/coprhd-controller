package com.emc.storageos.model.vasa;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="bulk_protocol_endpoint")
public class ProtocolEndpointBulkResponse {

    List<ProtocolEndpointResponseParam> protocolEndpointResponseParam;

    @XmlElement(name="protocol_endpoint")
    public List<ProtocolEndpointResponseParam> getProtocolEndpointResponseParam() {
        if(null != protocolEndpointResponseParam){
            protocolEndpointResponseParam = new ArrayList<ProtocolEndpointResponseParam>();
        }
        return protocolEndpointResponseParam;
    }

    public void setProtocolEndpointResponseParam(List<ProtocolEndpointResponseParam> protocolEndpointResponseParam) {
        this.protocolEndpointResponseParam = protocolEndpointResponseParam;
    }
}
