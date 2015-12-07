package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_policy")
public class FilePolicyRestRep {

    private URI policyId;

    private String policyName;

    private String policySchedule;

    private String policyExipration;

    @XmlElement(name = "policy_id")
    public URI getPolicyId() {
        return policyId;
    }

    public void setPolicyId(URI policyId) {
        this.policyId = policyId;
    }

    @XmlElement(name = "policy_name")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(name = "policy_schedule")
    public String getPolicySchedule() {
        return policySchedule;
    }

    public void setPolicySchedule(String policySchedule) {
        this.policySchedule = policySchedule;
    }

    @XmlElement(name = "policy_expire")
    public String getPolicyExipration() {
        return policyExipration;
    }

    public void setPolicyExipration(String policyExipration) {
        this.policyExipration = policyExipration;
    }
}
