package com.emc.storageos.model.pe;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value = "")
@XmlRootElement
public class CreateProtocolEndpoint {

    @XmlElement
    private CreateStorageGroupParam createStorageGroupParam;

    @XmlElement
    private HostOrHostGroupSelection hostOrHostGroupSelection;

    @XmlElement
    private PortGroupSelection portGroupSelection;

    @XmlElement
    private String maskingViewId;

    public CreateStorageGroupParam getCreateStorageGroupParam ()
    {
        return createStorageGroupParam;
    }

    public void setCreateStorageGroupParam (CreateStorageGroupParam createStorageGroupParam)
    {
        this.createStorageGroupParam = createStorageGroupParam;
    }

    public HostOrHostGroupSelection getHostOrHostGroupSelection ()
    {
        return hostOrHostGroupSelection;
    }

    public void setHostOrHostGroupSelection (HostOrHostGroupSelection hostOrHostGroupSelection)
    {
        this.hostOrHostGroupSelection = hostOrHostGroupSelection;
    }

    public PortGroupSelection getPortGroupSelection ()
    {
        return portGroupSelection;
    }

    public void setPortGroupSelection (PortGroupSelection portGroupSelection)
    {
        this.portGroupSelection = portGroupSelection;
    }

    public String getMaskingViewId ()
    {
        return maskingViewId;
    }

    public void setMaskingViewId (String maskingViewId)
    {
        this.maskingViewId = maskingViewId;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [createStorageGroupParam = "+createStorageGroupParam+", hostOrHostGroupSelection = "+hostOrHostGroupSelection+", portGroupSelection = "+portGroupSelection+", maskingViewId = "+maskingViewId+"]";
    }
}
