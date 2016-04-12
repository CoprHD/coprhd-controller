package com.emc.storageos.model.pe;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@XmlRootElement(name="createStorageGroupParam")
@JsonRootName(value="createStorageGroupParam")
public class CreateStorageGroupParam {

    @XmlElement
    private String storageGroupId;

    public String getStorageGroupId ()
    {
        return storageGroupId;
    }

    public void setStorageGroupId (String storageGroupId)
    {
        this.storageGroupId = storageGroupId;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [storageGroupId = "+storageGroupId+"]";
    }
}
