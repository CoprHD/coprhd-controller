/**
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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Snapshot")
public class Snapshot {

	
	@XmlElement
	String id;
    @XmlElement
	boolean inactive;
    @XmlElement
	String label;
    @XmlElement
	String mountPath;
    @XmlElement
	String timestamp;
    @XmlElement
	String parent;
    @XmlElement
	String storageController;
    
    @Override
	public String toString(){
    	String s = new String();
    	
    	s+= "Snapshot"+"\n";
    	s += "\tid: "+id+"\n";
    	s += "\tinactive: "+Boolean.toString(inactive)+"\n";
    	s += "\tlabel: "+label+"\n";
    	s += "\tmountPath: "+mountPath+"\n";
    	s += "\ttimestamp: "+timestamp+"\n";
    	s += "\tparent: "+parent+"\n";
    	s += "\tstorageController: "+storageController+"\n";
    	return s;
    }

}
