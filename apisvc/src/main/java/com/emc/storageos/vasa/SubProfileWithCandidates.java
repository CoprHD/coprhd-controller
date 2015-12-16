
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SubProfileWithCandidates complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SubProfileWithCandidates">
 *   &lt;complexContent>
 *     &lt;extension base="{http://profile.policy.data.vasa.vim.vmware.com/xsd}SubProfile">
 *       &lt;sequence>
 *         &lt;element name="candidateCapabilityProfiles" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="forceProvision" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SubProfileWithCandidates", namespace = "http://profile.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "candidateCapabilityProfiles",
    "forceProvision"
})
public class SubProfileWithCandidates
    extends SubProfile
{

    protected List<String> candidateCapabilityProfiles;
    protected boolean forceProvision;

    /**
     * Gets the value of the candidateCapabilityProfiles property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the candidateCapabilityProfiles property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCandidateCapabilityProfiles().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getCandidateCapabilityProfiles() {
        if (candidateCapabilityProfiles == null) {
            candidateCapabilityProfiles = new ArrayList<String>();
        }
        return this.candidateCapabilityProfiles;
    }

    /**
     * Gets the value of the forceProvision property.
     * 
     */
    public boolean isForceProvision() {
        return forceProvision;
    }

    /**
     * Sets the value of the forceProvision property.
     * 
     */
    public void setForceProvision(boolean value) {
        this.forceProvision = value;
    }

}
