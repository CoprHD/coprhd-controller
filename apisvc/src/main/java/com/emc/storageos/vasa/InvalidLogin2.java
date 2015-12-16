
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
 *         &lt;element name="InvalidLogin" type="{http://fault.vasa.vim.vmware.com/xsd}InvalidLogin" minOccurs="0"/>
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
    "invalidLogin"
})
@XmlRootElement(name = "InvalidLogin")
public class InvalidLogin2 {

    @XmlElementRef(name = "InvalidLogin", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<InvalidLogin> invalidLogin;

    /**
     * Gets the value of the invalidLogin property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link InvalidLogin }{@code >}
     *     
     */
    public JAXBElement<InvalidLogin> getInvalidLogin() {
        return invalidLogin;
    }

    /**
     * Sets the value of the invalidLogin property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link InvalidLogin }{@code >}
     *     
     */
    public void setInvalidLogin(JAXBElement<InvalidLogin> value) {
        this.invalidLogin = value;
    }

}
