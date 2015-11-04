package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlTransient;

import org.codehaus.jackson.map.annotate.JsonRootName;

import com.emc.storageos.model.NamedRelatedResourceRep;

@JsonRootName(value="transfers")
@XmlRootElement(name="transfers")
public class CinderOsVolumeTransferRestResp {
    private List<CinderVolumeTransfer> transfers;

    @XmlElement(name="transfer")
    public List<CinderVolumeTransfer> getTransfers() {
        if (transfers== null) {
        	transfers = new ArrayList<CinderVolumeTransfer>();
        }
        return transfers;
    }

    public void setTransfers(List<CinderVolumeTransfer> lsttansfers) {
        this.transfers= lsttansfers;
    }
       
}