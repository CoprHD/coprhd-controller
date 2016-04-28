/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

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