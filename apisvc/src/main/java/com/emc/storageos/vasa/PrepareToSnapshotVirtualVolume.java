
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
 *         &lt;element name="vvolId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="storageProfile" type="{http://profile.policy.data.vasa.vim.vmware.com/xsd}StorageProfile" minOccurs="0"/>
 *         &lt;element name="containerCookie" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="snapType" type="{http://www.w3.org/2001/XMLSchema}string"/>
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
    "vvolId",
    "storageProfile",
    "containerCookie",
    "snapType"
})
@XmlRootElement(name = "prepareToSnapshotVirtualVolume")
public class PrepareToSnapshotVirtualVolume {

    @XmlElement(required = true)
    protected String vvolId;
    @XmlElementRef(name = "storageProfile", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<StorageProfile> storageProfile;
    @XmlElementRef(name = "containerCookie", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> containerCookie;
    @XmlElement(required = true)
    protected String snapType;

    /**
     * Gets the value of the vvolId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVvolId() {
        return vvolId;
    }

    /**
     * Sets the value of the vvolId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVvolId(String value) {
        this.vvolId = value;
    }

    /**
     * Gets the value of the storageProfile property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link StorageProfile }{@code >}
     *     
     */
    public JAXBElement<StorageProfile> getStorageProfile() {
        return storageProfile;
    }

    /**
     * Sets the value of the storageProfile property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link StorageProfile }{@code >}
     *     
     */
    public void setStorageProfile(JAXBElement<StorageProfile> value) {
        this.storageProfile = value;
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

    /**
     * Gets the value of the snapType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSnapType() {
        return snapType;
    }

    /**
     * Sets the value of the snapType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSnapType(String value) {
        this.snapType = value;
    }

}
