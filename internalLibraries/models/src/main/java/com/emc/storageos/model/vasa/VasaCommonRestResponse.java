package com.emc.storageos.model.vasa;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.DataObjectRestRep;

public class VasaCommonRestResponse extends DataObjectRestRep{
     
    private String description;

    /**
     * 
     * User defined description
     * 
     * @valid none
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
