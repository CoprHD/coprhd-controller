
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
 *         &lt;element name="LostAlarm" type="{http://fault.vasa.vim.vmware.com/xsd}LostAlarm" minOccurs="0"/>
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
    "lostAlarm"
})
@XmlRootElement(name = "LostAlarm")
public class LostAlarm2 {

    @XmlElementRef(name = "LostAlarm", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<LostAlarm> lostAlarm;

    /**
     * Gets the value of the lostAlarm property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link LostAlarm }{@code >}
     *     
     */
    public JAXBElement<LostAlarm> getLostAlarm() {
        return lostAlarm;
    }

    /**
     * Sets the value of the lostAlarm property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link LostAlarm }{@code >}
     *     
     */
    public void setLostAlarm(JAXBElement<LostAlarm> value) {
        this.lostAlarm = value;
    }

}
