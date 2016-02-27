package com.emc.storageos.model.alerts;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name="notification")
public class AlertsCreateResponse extends DataObjectRestRep {
    

    protected String affectedResourceName;
    protected String affectedResourceID;
    protected String problemDescription;
    protected String affectedResourceType;
    protected String severity;
    protected String deviceType;
    protected String state;
    
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

    @XmlElement
    public String getAffectedResourceName() {
        return affectedResourceName;
    }

    public void setAffectedResourceName(String affectedResourceName) {
        this.affectedResourceName = affectedResourceName;
    }
    
    @XmlElement
    public String getAffectedResourceType() {
        return affectedResourceName;
    }

    public void setAffectedResourceType(String affectedResourceType) {
        this.affectedResourceType = affectedResourceType;
    }

    @XmlElement
    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    @XmlElement
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
    @XmlElement
    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }
}
