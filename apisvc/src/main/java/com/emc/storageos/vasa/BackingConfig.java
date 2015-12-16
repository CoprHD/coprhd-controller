
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BackingConfig complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="BackingConfig">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="autoTieringEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="deduplicationBackingIdentifier" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="deduplicationEfficiency" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="performanceOptimizationInterval" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="thinProvisionBackingIdentifier" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BackingConfig", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "autoTieringEnabled",
    "deduplicationBackingIdentifier",
    "deduplicationEfficiency",
    "performanceOptimizationInterval",
    "thinProvisionBackingIdentifier"
})
public class BackingConfig {

    protected Boolean autoTieringEnabled;
    @XmlElementRef(name = "deduplicationBackingIdentifier", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> deduplicationBackingIdentifier;
    protected Long deduplicationEfficiency;
    protected Long performanceOptimizationInterval;
    @XmlElementRef(name = "thinProvisionBackingIdentifier", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> thinProvisionBackingIdentifier;

    /**
     * Gets the value of the autoTieringEnabled property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isAutoTieringEnabled() {
        return autoTieringEnabled;
    }

    /**
     * Sets the value of the autoTieringEnabled property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setAutoTieringEnabled(Boolean value) {
        this.autoTieringEnabled = value;
    }

    /**
     * Gets the value of the deduplicationBackingIdentifier property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getDeduplicationBackingIdentifier() {
        return deduplicationBackingIdentifier;
    }

    /**
     * Sets the value of the deduplicationBackingIdentifier property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setDeduplicationBackingIdentifier(JAXBElement<String> value) {
        this.deduplicationBackingIdentifier = value;
    }

    /**
     * Gets the value of the deduplicationEfficiency property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getDeduplicationEfficiency() {
        return deduplicationEfficiency;
    }

    /**
     * Sets the value of the deduplicationEfficiency property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setDeduplicationEfficiency(Long value) {
        this.deduplicationEfficiency = value;
    }

    /**
     * Gets the value of the performanceOptimizationInterval property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getPerformanceOptimizationInterval() {
        return performanceOptimizationInterval;
    }

    /**
     * Sets the value of the performanceOptimizationInterval property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setPerformanceOptimizationInterval(Long value) {
        this.performanceOptimizationInterval = value;
    }

    /**
     * Gets the value of the thinProvisionBackingIdentifier property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getThinProvisionBackingIdentifier() {
        return thinProvisionBackingIdentifier;
    }

    /**
     * Sets the value of the thinProvisionBackingIdentifier property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setThinProvisionBackingIdentifier(JAXBElement<String> value) {
        this.thinProvisionBackingIdentifier = value;
    }

}
