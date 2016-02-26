package com.emc.storageos.model.alerts;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name="notification")
public class AlertsCreateResponse extends DataObjectRestRep {
    
    private String affectedResourceID;
    private String problemDescription;
    
    public String getAffectedResourceID() {
		return affectedResourceID;
	}

	public void setAffectedResourceID(String affectedResourceID) {
		this.affectedResourceID = affectedResourceID;
	}

	@XmlElement
    public String getProblemDescription() {
        return problemDescription;
    }

    public void setProblemDescription(String problemDescription) {
        this.problemDescription = problemDescription;
    }
 }
