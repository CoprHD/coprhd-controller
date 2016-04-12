package com.emc.storageos.model.pe;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@XmlRootElement(name="portGroupSelection")
@JsonRootName(value="portGroupSelection")
public class PortGroupSelection {

    private UseExistingPortGroupParam useExistingPortGroupParam;

    @XmlElement
    public UseExistingPortGroupParam getUseExistingPortGroupParam ()
    {
        return useExistingPortGroupParam;
    }

    public void setUseExistingPortGroupParam (UseExistingPortGroupParam useExistingPortGroupParam)
    {
        this.useExistingPortGroupParam = useExistingPortGroupParam;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [useExistingPortGroupParam = "+useExistingPortGroupParam+"]";
    }
}
