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

@JsonRootName(value="services")
@XmlRootElement(name="services")
public class CinderOsServicesRestResp {
    private List<CinderOsService> services;

    @XmlElement(name="services")
    public List<CinderOsService> getServices() {
        if (services== null) {
        	services = new ArrayList<CinderOsService>();
        }
        return services;
    }

    public void setServices(List<CinderOsService> lstServices) {
        this.services= lstServices;
    }
       
}