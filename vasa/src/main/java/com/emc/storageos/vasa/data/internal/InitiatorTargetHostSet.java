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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "InitiatorTargetHostSet")
public class InitiatorTargetHostSet {
	@XmlElement
	String[] endpoints;

    @Override
	public String toString(){
    	String s = "";
    	for (int i=0;i<endpoints.length;i++){
    		s+="\t"+endpoints[i];
    	}
    	return s;
    }

}
