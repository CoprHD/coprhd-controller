
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
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
 *         &lt;element name="srcVVolId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="srcBaseVVolId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="dstVVolId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="containerCookie" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
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
    "srcVVolId",
    "srcBaseVVolId",
    "dstVVolId",
    "containerCookie"
})
@XmlRootElement(name = "copyDiffsToVirtualVolume")
public class CopyDiffsToVirtualVolume {

    @XmlElement(required = true)
    protected String srcVVolId;
    @XmlElement(required = true)
    protected String srcBaseVVolId;
    @XmlElement(required = true)
    protected String dstVVolId;
    @XmlElementRef(name = "containerCookie", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> containerCookie;

    /**
     * Gets the value of the srcVVolId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSrcVVolId() {
        return srcVVolId;
    }

    /**
     * Sets the value of the srcVVolId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSrcVVolId(String value) {
        this.srcVVolId = value;
    }

    /**
     * Gets the value of the srcBaseVVolId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSrcBaseVVolId() {
        return srcBaseVVolId;
    }

    /**
     * Sets the value of the srcBaseVVolId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSrcBaseVVolId(String value) {
        this.srcBaseVVolId = value;
    }

    /**
     * Gets the value of the dstVVolId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDstVVolId() {
        return dstVVolId;
    }

    /**
     * Sets the value of the dstVVolId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDstVVolId(String value) {
        this.dstVVolId = value;
    }

    /**
     * Gets the value of the containerCookie property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getContainerCookie() {
        return containerCookie;
    }

    /**
     * Sets the value of the containerCookie property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setContainerCookie(JAXBElement<String> value) {
        this.containerCookie = value;
    }

}
