package com.emc.storageos.model.rdfgroup;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

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
