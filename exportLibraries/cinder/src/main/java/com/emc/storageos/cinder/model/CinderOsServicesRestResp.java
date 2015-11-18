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