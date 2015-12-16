
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
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
 *         &lt;element name="containerId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="vvolType" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="storageProfile" type="{http://profile.policy.data.vasa.vim.vmware.com/xsd}StorageProfile" minOccurs="0"/>
 *         &lt;element name="sizeInMB" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="metadata" type="{http://data.vasa.vim.vmware.com/xsd}NameValuePair" maxOccurs="unbounded"/>
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
    "containerId",
    "vvolType",
    "storageProfile",
    "sizeInMB",
    "metadata",
    "containerCookie"
})
@XmlRootElement(name = "createVirtualVolume")
public class CreateVirtualVolume {

    @XmlElement(required = true)
    protected String containerId;
    @XmlElement(required = true)
    protected String vvolType;
    @XmlElementRef(name = "storageProfile", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<StorageProfile> storageProfile;
    protected long sizeInMB;
    @XmlElement(required = true)
    protected List<NameValuePair> metadata;
    @XmlElementRef(name = "containerCookie", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> containerCookie;

    /**
     * Gets the value of the containerId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContainerId() {
        return containerId;
    }

    /**
     * Sets the value of the containerId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContainerId(String value) {
        this.containerId = value;
    }

    /**
     * Gets the value of the vvolType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVvolType() {
        return vvolType;
    }

    /**
     * Sets the value of the vvolType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVvolType(String value) {
        this.vvolType = value;
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
     * Gets the value of the sizeInMB property.
     * 
     */
    public long getSizeInMB() {
        return sizeInMB;
    }

    /**
     * Sets the value of the sizeInMB property.
     * 
     */
    public void setSizeInMB(long value) {
        this.sizeInMB = value;
    }

    /**
     * Gets the value of the metadata property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the metadata property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMetadata().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link NameValuePair }
     * 
     * 
     */
    public List<NameValuePair> getMetadata() {
        if (metadata == null) {
            metadata = new ArrayList<NameValuePair>();
        }
        return this.metadata;
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
