/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ExportGroup")
public class ExportGroup {
	@XmlElement
	String id;
    @XmlElement
	boolean inactive;
    @XmlElement
	String label;
    @XmlElement
	String project;
    @XmlElement
	boolean exported;
    @XmlElement
	StringMap volumes;
    @XmlElement
    String initiatorTargetHosts;
    
    @Override
	public String toString(){
    	String s = new String();
    	
    	s+= "Storageport"+"\n";
    	s += "\tid: "+id+"\n";
    	s += "\tinactive: "+Boolean.toString(inactive)+"\n";
    	s += "\tlabel: "+label+"\n";
    	s += "\tproject: "+project+"\n";
    	s += "\texported: "+Boolean.toString(exported)+"\n";
    	if (volumes!=null) {
    		s += "\tvolumes: \n"+volumes.toString();
    	}
    	s += "\tinitiatorTargetHosts: "+initiatorTargetHosts+"\n";
    	return s;
    }

}
