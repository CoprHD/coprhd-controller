/* Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

public class ListOfResourceEntry {

    private List<ResourceEntry> list = new ArrayList<ResourceEntry>();
    
    @XmlElement(name = "resource_entry")
    public List<ResourceEntry> getList(){
        return this.list;
    }
    public void setList(List<ResourceEntry> list){
        this.list = list;
    }
}

