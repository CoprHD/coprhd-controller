/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
Copyright (c) 2012 EMC Corporation
All Rights Reserved

This software contains the intellectual property of EMC Corporation
or is licensed to EMC Corporation from third parties.  Use of this
software and the intellectual property contained therein is expressly
imited to the terms and conditions of the License Agreement under which
it is provided by or on behalf of EMC.
*/
package com.emc.storageos.vasa.data.internal;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Neighborhood")
public class Neighborhood {
	@XmlElement
	String id;
    @XmlElement
	boolean inactive;
    @XmlElement(name="name")
	String label;
    
    @Override
	public String toString(){
    	String s = new String();
    	
    	s+= "Neighborhood"+"\n";
    	s += "\tid: "+id+"\n";
    	s += "\tinactive: "+Boolean.toString(inactive)+"\n";
    	s += "\tlabel: "+label+"\n";
    	return s;
    }
    
    @XmlRootElement(name = "neighborhoodList")
    public static class NeighborhoodList {
    	
    	@XmlElement(name="id")
        private List<URI> ids;
        
        
		public List<URI> getIds() {
			return ids;
		}
        
        
        
    }

}
