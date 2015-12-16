
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
 *         &lt;element name="OutOfResource" type="{http://fault.vasa.vim.vmware.com/xsd}OutOfResource" minOccurs="0"/>
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
    "outOfResource"
})
@XmlRootElement(name = "OutOfResource")
public class OutOfResource2 {

    @XmlElementRef(name = "OutOfResource", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<OutOfResource> outOfResource;

    /**
     * Gets the value of the outOfResource property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link OutOfResource }{@code >}
     *     
     */
    public JAXBElement<OutOfResource> getOutOfResource() {
        return outOfResource;
    }

    /**
     * Sets the value of the outOfResource property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link OutOfResource }{@code >}
     *     
     */
    public void setOutOfResource(JAXBElement<OutOfResource> value) {
        this.outOfResource = value;
    }

}
