package com.emc.storageos.model.vasa;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.valid.Length;

public class VasaCommonRestRequest {

    private String name;
    private String description;
    
    /**
     * The name for the Storage Container.
     * 
     * @valid none
     */
    @XmlElement(required = false)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The description for the Storage Container.
     * 
     * @valid none
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
}
