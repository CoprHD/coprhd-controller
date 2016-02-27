package com.emc.storageos.db.client.model;

@Cf("Alerts")
public class Alerts extends DataObject{
    
    protected String affectedResourceName;
    protected String affectedResourceId;
    protected String problemDescription;
    protected String affectedResourceType;
    protected String severity;
    protected String deviceType;
    protected String state;
   
    
    
    @Name("affectedResourceName")
    public String getAffectedResourceName() {
        return affectedResourceName;
    }
    public void setAffectedResourceName(String affectedResourceName) {
        this.affectedResourceName = affectedResourceName;
        setChanged("affectedResourceName");
    }
    @Name("affectedResourceId")
    public String getAffectedResourceId() {
        return affectedResourceId;
    }
    public void setAffectedResourceId(String affectedResourceId) {
        this.affectedResourceId = affectedResourceId;
        setChanged("affectedResourceId");
    }
    @Name("problemDescription")
    public String getProblemDescription() {
        return problemDescription;
    }
    public void setProblemDescription(String problemDescription) {
        this.problemDescription = problemDescription;
        setChanged("problemDescription");
    }
    @Name("affectedResourceType")
    public String getAffectedResourceType() {
        return affectedResourceType;
    }
    public void setAffectedResourceType(String affectedResourceType) {
        this.affectedResourceType = affectedResourceType;
        setChanged("affectedResourceType");
    }
    @Name("severity")
    public String getSeverity() {
        return severity;
    }
    public void setSeverity(String severity) {
        this.severity = severity;
        setChanged("severity");
    }
    @Name("deviceType")
    public String getDeviceType() {
        return deviceType;
    }
    public void setDeviceType(String problemType) {
        this.deviceType = problemType;
        setChanged("problemType");
    }
    @Name("state")
    public String getState() {
        return state;
    }
    public void setState(String state) {
        this.state = state;
        setChanged("state");
    }
    
   

}
