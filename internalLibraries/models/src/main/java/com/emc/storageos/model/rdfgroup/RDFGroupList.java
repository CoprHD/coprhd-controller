package com.emc.storageos.model.rdfgroup;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "rdf_groups")
public class RDFGroupList {
    private List<RDFGroupRestRep> rdfGroups;
    
    public RDFGroupList() {}
    
    public RDFGroupList(List<RDFGroupRestRep> rdfGroups) {
        this.rdfGroups = rdfGroups;
    }
    /**
     * List of RDF Groups
     * 
     */
    @XmlElement(name = "rdf_group")
	public List<RDFGroupRestRep> getRdfGroups() {
    	 if (rdfGroups == null) {
         	rdfGroups = new ArrayList<RDFGroupRestRep>();
         }
		return rdfGroups;
	}

	public void setRdfGroups(List<RDFGroupRestRep> rdfGroups) {
		this.rdfGroups = rdfGroups;
	}
}
