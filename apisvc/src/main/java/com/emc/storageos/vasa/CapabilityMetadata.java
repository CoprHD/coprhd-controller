
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
 * <p>Java class for CapabilityMetadata complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CapabilityMetadata">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="allowMultipleConstraints" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="capabilityId" type="{http://capability.policy.data.vasa.vim.vmware.com/xsd}CapabilityId"/>
 *         &lt;element name="hint" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="keyId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="mandatory" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="propertyMetadata" type="{http://capability.policy.data.vasa.vim.vmware.com/xsd}PropertyMetadata" maxOccurs="unbounded"/>
 *         &lt;element name="summary" type="{http://policy.data.vasa.vim.vmware.com/xsd}ExtendedElementDescription"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CapabilityMetadata", namespace = "http://capability.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "allowMultipleConstraints",
    "capabilityId",
    "hint",
    "keyId",
    "mandatory",
    "propertyMetadata",
    "summary"
})
public class CapabilityMetadata {

    protected Boolean allowMultipleConstraints;
    @XmlElement(required = true)
    protected CapabilityId capabilityId;
    protected Boolean hint;
    @XmlElementRef(name = "keyId", namespace = "http://capability.policy.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> keyId;
    protected Boolean mandatory;
    @XmlElement(required = true)
    protected List<PropertyMetadata> propertyMetadata;
    @XmlElement(required = true)
    protected ExtendedElementDescription summary;

    /**
     * Gets the value of the allowMultipleConstraints property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isAllowMultipleConstraints() {
        return allowMultipleConstraints;
    }

    /**
     * Sets the value of the allowMultipleConstraints property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setAllowMultipleConstraints(Boolean value) {
        this.allowMultipleConstraints = value;
    }

    /**
     * Gets the value of the capabilityId property.
     * 
     * @return
     *     possible object is
     *     {@link CapabilityId }
     *     
     */
    public CapabilityId getCapabilityId() {
        return capabilityId;
    }

    /**
     * Sets the value of the capabilityId property.
     * 
     * @param value
     *     allowed object is
     *     {@link CapabilityId }
     *     
     */
    public void setCapabilityId(CapabilityId value) {
        this.capabilityId = value;
    }

    /**
     * Gets the value of the hint property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isHint() {
        return hint;
    }

    /**
     * Sets the value of the hint property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setHint(Boolean value) {
        this.hint = value;
    }

    /**
     * Gets the value of the keyId property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getKeyId() {
        return keyId;
    }

    /**
     * Sets the value of the keyId property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setKeyId(JAXBElement<String> value) {
        this.keyId = value;
    }

    /**
     * Gets the value of the mandatory property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isMandatory() {
        return mandatory;
    }

    /**
     * Sets the value of the mandatory property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setMandatory(Boolean value) {
        this.mandatory = value;
    }

    /**
     * Gets the value of the propertyMetadata property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the propertyMetadata property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPropertyMetadata().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PropertyMetadata }
     * 
     * 
     */
    public List<PropertyMetadata> getPropertyMetadata() {
        if (propertyMetadata == null) {
            propertyMetadata = new ArrayList<PropertyMetadata>();
        }
        return this.propertyMetadata;
    }

    /**
     * Gets the value of the summary property.
     * 
     * @return
     *     possible object is
     *     {@link ExtendedElementDescription }
     *     
     */
    public ExtendedElementDescription getSummary() {
        return summary;
    }

    /**
     * Sets the value of the summary property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExtendedElementDescription }
     *     
     */
    public void setSummary(ExtendedElementDescription value) {
        this.summary = value;
    }

}
