package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "rdf_group")
public class RDFGroupRestRep extends DataObjectRestRep {

    private RelatedResourceRep storageController;

    /**
     * Related storage controller
     * 
     */
    @XmlElement(name = "storage_controller")
    public RelatedResourceRep getStorageController() {
        return storageController;
    }

    public void setStorageController(RelatedResourceRep storageController) {
        this.storageController = storageController;
    }

}
