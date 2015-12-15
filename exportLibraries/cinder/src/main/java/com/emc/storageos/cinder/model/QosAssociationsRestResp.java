/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
// REST response for the List volume associations query
@XmlRootElement(name = "qos_associations")
public class QosAssociationsRestResp {

	private List<CinderQosAssociation> associations;

	@XmlElementRef(name="qos_associations")
	public List<CinderQosAssociation> getAssociation(){
		if (associations == null){
			associations = new ArrayList<CinderQosAssociation>();
		}
		return associations;
	}
	
}
