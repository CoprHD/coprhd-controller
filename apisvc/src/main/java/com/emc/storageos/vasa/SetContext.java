
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="usageContext" type="{http://data.vasa.vim.vmware.com/xsd}UsageContext" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "usageContext"
})
@XmlRootElement(name = "setContext")
public class SetContext {

    @XmlElementRef(name = "usageContext", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<UsageContext> usageContext;

    /**
     * Gets the value of the usageContext property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link UsageContext }{@code >}
     *     
     */
    public JAXBElement<UsageContext> getUsageContext() {
        return usageContext;
    }

    /**
     * Sets the value of the usageContext property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link UsageContext }{@code >}
     *     
     */
    public void setUsageContext(JAXBElement<UsageContext> value) {
        this.usageContext = value;
    }

}
