/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.rdfgroup;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "rdf_groups")
public class RDFGroupList {
    private List<NamedRelatedResourceRep> rdfGroups;
    
    public RDFGroupList() {}
    
    public RDFGroupList(List<NamedRelatedResourceRep> rdfGroups) {
        this.rdfGroups = rdfGroups;
    }
    /**
     * List of RDF Groups
     * 
     * @valid none
     */
    @XmlElement(name = "rdf_group")
	public List<NamedRelatedResourceRep> getRdfGroups() {
    	 if (rdfGroups == null) {
         	rdfGroups = new ArrayList<NamedRelatedResourceRep>();
         }
		return rdfGroups;
	}

	public void setRdfGroups(List<NamedRelatedResourceRep> rdfGroups) {
		this.rdfGroups = rdfGroups;
	}
}
