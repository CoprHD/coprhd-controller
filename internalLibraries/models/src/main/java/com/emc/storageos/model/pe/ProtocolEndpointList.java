package com.emc.storageos.model.pe;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@XmlRootElement(name = "protocolEndpointList")
@JsonRootName(value = "protocolEndpointList")
public class ProtocolEndpointList {
    
    private String num_of_protocol_endpoints;

    private String[] protocolEndpointId;

    private String success;

    public String getNum_of_protocol_endpoints ()
    {
        return num_of_protocol_endpoints;
    }

    public void setNum_of_protocol_endpoints (String num_of_protocol_endpoints)
    {
        this.num_of_protocol_endpoints = num_of_protocol_endpoints;
    }

    public String[] getProtocolEndpointId ()
    {
        return protocolEndpointId;
    }

    public void setProtocolEndpointId (String[] protocolEndpointId)
    {
        this.protocolEndpointId = protocolEndpointId;
    }

    public String getSuccess ()
    {
        return success;
    }

    public void setSuccess (String success)
    {
        this.success = success;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [num_of_protocol_endpoints = "+num_of_protocol_endpoints+", protocolEndpointId = "+protocolEndpointId+", success = "+success+"]";
    }

}
