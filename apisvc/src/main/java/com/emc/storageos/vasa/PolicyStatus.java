
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PolicyStatus complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PolicyStatus">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="currentValue" type="{http://capability.policy.data.vasa.vim.vmware.com/xsd}CapabilityInstance"/>
 *         &lt;element name="expectedValue" type="{http://capability.policy.data.vasa.vim.vmware.com/xsd}CapabilityInstance"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PolicyStatus", namespace = "http://compliance.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "currentValue",
    "expectedValue"
})
public class PolicyStatus {

    @XmlElement(required = true)
    protected CapabilityInstance currentValue;
    @XmlElement(required = true)
    protected CapabilityInstance expectedValue;

    /**
     * Gets the value of the currentValue property.
     * 
     * @return
     *     possible object is
     *     {@link CapabilityInstance }
     *     
     */
    public CapabilityInstance getCurrentValue() {
        return currentValue;
    }

    /**
     * Sets the value of the currentValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link CapabilityInstance }
     *     
     */
    public void setCurrentValue(CapabilityInstance value) {
        this.currentValue = value;
    }

    /**
     * Gets the value of the expectedValue property.
     * 
     * @return
     *     possible object is
     *     {@link CapabilityInstance }
     *     
     */
    public CapabilityInstance getExpectedValue() {
        return expectedValue;
    }

    /**
     * Sets the value of the expectedValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link CapabilityInstance }
     *     
     */
    public void setExpectedValue(CapabilityInstance value) {
        this.expectedValue = value;
    }

}
