
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ContainerUsage complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ContainerUsage">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="capacityInfo" type="{http://placement.policy.data.vasa.vim.vmware.com/xsd}CapacityMetric"/>
 *         &lt;element name="containerId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ContainerUsage", namespace = "http://placement.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "capacityInfo",
    "containerId"
})
public class ContainerUsage {

    @XmlElement(required = true)
    protected CapacityMetric capacityInfo;
    @XmlElement(required = true)
    protected String containerId;

    /**
     * Gets the value of the capacityInfo property.
     * 
     * @return
     *     possible object is
     *     {@link CapacityMetric }
     *     
     */
    public CapacityMetric getCapacityInfo() {
        return capacityInfo;
    }

    /**
     * Sets the value of the capacityInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link CapacityMetric }
     *     
     */
    public void setCapacityInfo(CapacityMetric value) {
        this.capacityInfo = value;
    }

    /**
     * Gets the value of the containerId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContainerId() {
        return containerId;
    }

    /**
     * Sets the value of the containerId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContainerId(String value) {
        this.containerId = value;
    }

}
