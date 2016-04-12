package com.emc.storageos.model.pe;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value="hostOrHostGroupSelection")
@XmlRootElement(name="hostOrHostGroupSelection")
public class HostOrHostGroupSelection {

    @XmlElement
    private UseExistingHostParam useExistingHostParam;

    public UseExistingHostParam getUseExistingHostParam ()
    {
        return useExistingHostParam;
    }

    public void setUseExistingHostParam (UseExistingHostParam useExistingHostParam)
    {
        this.useExistingHostParam = useExistingHostParam;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [useExistingHostParam = "+useExistingHostParam+"]";
    }
}
