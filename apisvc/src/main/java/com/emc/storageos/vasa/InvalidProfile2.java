
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
 *         &lt;element name="InvalidProfile" type="{http://fault.vasa.vim.vmware.com/xsd}InvalidProfile" minOccurs="0"/>
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
    "invalidProfile"
})
@XmlRootElement(name = "InvalidProfile")
public class InvalidProfile2 {

    @XmlElementRef(name = "InvalidProfile", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<InvalidProfile> invalidProfile;

    /**
     * Gets the value of the invalidProfile property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link InvalidProfile }{@code >}
     *     
     */
    public JAXBElement<InvalidProfile> getInvalidProfile() {
        return invalidProfile;
    }

    /**
     * Sets the value of the invalidProfile property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link InvalidProfile }{@code >}
     *     
     */
    public void setInvalidProfile(JAXBElement<InvalidProfile> value) {
        this.invalidProfile = value;
    }

}
