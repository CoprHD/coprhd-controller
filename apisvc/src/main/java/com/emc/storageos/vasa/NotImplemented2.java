
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
 *         &lt;element name="NotImplemented" type="{http://fault.vasa.vim.vmware.com/xsd}NotImplemented" minOccurs="0"/>
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
    "notImplemented"
})
@XmlRootElement(name = "NotImplemented")
public class NotImplemented2 {

    @XmlElementRef(name = "NotImplemented", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<NotImplemented> notImplemented;

    /**
     * Gets the value of the notImplemented property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link NotImplemented }{@code >}
     *     
     */
    public JAXBElement<NotImplemented> getNotImplemented() {
        return notImplemented;
    }

    /**
     * Sets the value of the notImplemented property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link NotImplemented }{@code >}
     *     
     */
    public void setNotImplemented(JAXBElement<NotImplemented> value) {
        this.notImplemented = value;
    }

}
