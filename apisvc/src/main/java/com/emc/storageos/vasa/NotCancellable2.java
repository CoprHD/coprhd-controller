
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
 *         &lt;element name="NotCancellable" type="{http://fault.vasa.vim.vmware.com/xsd}NotCancellable" minOccurs="0"/>
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
    "notCancellable"
})
@XmlRootElement(name = "NotCancellable")
public class NotCancellable2 {

    @XmlElementRef(name = "NotCancellable", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<NotCancellable> notCancellable;

    /**
     * Gets the value of the notCancellable property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link NotCancellable }{@code >}
     *     
     */
    public JAXBElement<NotCancellable> getNotCancellable() {
        return notCancellable;
    }

    /**
     * Sets the value of the notCancellable property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link NotCancellable }{@code >}
     *     
     */
    public void setNotCancellable(JAXBElement<NotCancellable> value) {
        this.notCancellable = value;
    }

}
