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

public class ResourceEntry {
    private NamedRelatedResourceRep key;

    private List<NamedRelatedResourceRep> list = new ArrayList<NamedRelatedResourceRep>();
    
    @XmlElement(name = "key")
    public NamedRelatedResourceRep getKey(){
       return key; 
    }
    public void setKey(NamedRelatedResourceRep key ){
       this.key = key;
    }
   @XmlElementWrapper(name = "list")
   @XmlElement(name="resource")
    public List<NamedRelatedResourceRep> getList(){
        return list; 
    }
    public void setList(List<NamedRelatedResourceRep> list){
        this.list = list;
    }
}

