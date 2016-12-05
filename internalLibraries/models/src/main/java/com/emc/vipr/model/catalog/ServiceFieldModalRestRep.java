package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "service_field_modal")
public class ServiceFieldModalRestRep extends ServiceItemRestRep implements ServiceItemContainerRestRep {

    private List<ServiceItemRestRep> items;
    
    @XmlElementWrapper(name = "items")
    @XmlElements({
            @XmlElement(name = "field", type = ServiceFieldRestRep.class)
    })
    
    @Override
    public List<ServiceItemRestRep> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }

    public void setItems(List<ServiceItemRestRep> items) {
        this.items = items;
    }
}
