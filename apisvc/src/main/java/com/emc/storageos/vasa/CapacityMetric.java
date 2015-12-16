
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CapacityMetric complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CapacityMetric">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="capacityCategory" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="percentageRemaining" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CapacityMetric", namespace = "http://placement.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "capacityCategory",
    "percentageRemaining"
})
public class CapacityMetric {

    @XmlElement(required = true)
    protected String capacityCategory;
    protected long percentageRemaining;

    /**
     * Gets the value of the capacityCategory property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCapacityCategory() {
        return capacityCategory;
    }

    /**
     * Sets the value of the capacityCategory property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCapacityCategory(String value) {
        this.capacityCategory = value;
    }

    /**
     * Gets the value of the percentageRemaining property.
     * 
     */
    public long getPercentageRemaining() {
        return percentageRemaining;
    }

    /**
     * Sets the value of the percentageRemaining property.
     * 
     */
    public void setPercentageRemaining(long value) {
        this.percentageRemaining = value;
    }

}
