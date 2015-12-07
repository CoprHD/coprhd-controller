package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("FilePolicy")
public class FilePolicy extends DiscoveredDataObject {
    private URI policyId;
    private String policyName;
    private String policySchedule;
    private String policyExpiration;

    @Name("policyId")
    public URI getPolicyId() {
        return policyId;
    }

    public void setPolicyId(URI policyId) {
        this.policyId = policyId;
        setChanged("policyId");
    }

    @Name("policyName")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
        setChanged("policyName");
    }

    @Name("policySchedule")
    public String getPolicySchedule() {
        return policySchedule;
    }

    public void setPolicySchedule(String policySchedule) {
        this.policySchedule = policySchedule;
        setChanged("policySchedule");
    }

    @Name("policyExpiration")
    public String getPolicyExpiration() {
        return policyExpiration;
    }

    public void setPolicyExpiration(String policyExpiration) {
        this.policyExpiration = policyExpiration;
        setChanged("policyExpiration");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Policy [name=");
        builder.append(policyName);
        builder.append(", schedule at=");
        builder.append(policySchedule);
        builder.append(", expire at=");
        builder.append(policyExpiration);
        builder.append("]");
        return builder.toString();
    }
}
