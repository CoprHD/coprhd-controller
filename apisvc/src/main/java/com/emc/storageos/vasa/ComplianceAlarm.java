
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ComplianceAlarm complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ComplianceAlarm">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}StorageAlarm">
 *       &lt;sequence>
 *         &lt;element name="complianceStatus" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="operationalStatus" type="{http://compliance.policy.data.vasa.vim.vmware.com/xsd}OperationalStatus" minOccurs="0"/>
 *         &lt;element name="profileId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="violatedPolicy" type="{http://compliance.policy.data.vasa.vim.vmware.com/xsd}PolicyStatus" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ComplianceAlarm", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "complianceStatus",
    "operationalStatus",
    "profileId",
    "violatedPolicy"
})
public class ComplianceAlarm
    extends StorageAlarm
{

    @XmlElement(required = true)
    protected String complianceStatus;
    @XmlElementRef(name = "operationalStatus", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<OperationalStatus> operationalStatus;
    @XmlElement(required = true)
    protected String profileId;
    protected List<PolicyStatus> violatedPolicy;

    /**
     * Gets the value of the complianceStatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getComplianceStatus() {
        return complianceStatus;
    }

    /**
     * Sets the value of the complianceStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setComplianceStatus(String value) {
        this.complianceStatus = value;
    }

    /**
     * Gets the value of the operationalStatus property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link OperationalStatus }{@code >}
     *     
     */
    public JAXBElement<OperationalStatus> getOperationalStatus() {
        return operationalStatus;
    }

    /**
     * Sets the value of the operationalStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link OperationalStatus }{@code >}
     *     
     */
    public void setOperationalStatus(JAXBElement<OperationalStatus> value) {
        this.operationalStatus = value;
    }

    /**
     * Gets the value of the profileId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Sets the value of the profileId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileId(String value) {
        this.profileId = value;
    }

    /**
     * Gets the value of the violatedPolicy property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the violatedPolicy property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getViolatedPolicy().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PolicyStatus }
     * 
     * 
     */
    public List<PolicyStatus> getViolatedPolicy() {
        if (violatedPolicy == null) {
            violatedPolicy = new ArrayList<PolicyStatus>();
        }
        return this.violatedPolicy;
    }

}
