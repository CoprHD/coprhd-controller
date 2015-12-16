
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageLun complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StorageLun">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity">
 *       &lt;sequence>
 *         &lt;element name="alternateIdentifier" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="backingConfig" type="{http://data.vasa.vim.vmware.com/xsd}BackingConfig" minOccurs="0"/>
 *         &lt;element name="capacityInMB" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="displayName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="drsManagementPermitted" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="esxLunIdentifier" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="thinProvisioned" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="thinProvisioningStatus" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="usedSpaceInMB" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StorageLun", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "alternateIdentifier",
    "backingConfig",
    "capacityInMB",
    "displayName",
    "drsManagementPermitted",
    "esxLunIdentifier",
    "thinProvisioned",
    "thinProvisioningStatus",
    "usedSpaceInMB"
})
public class StorageLun
    extends BaseStorageEntity
{

    protected List<String> alternateIdentifier;
    @XmlElementRef(name = "backingConfig", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<BackingConfig> backingConfig;
    protected Long capacityInMB;
    @XmlElementRef(name = "displayName", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> displayName;
    protected Boolean drsManagementPermitted;
    @XmlElementRef(name = "esxLunIdentifier", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> esxLunIdentifier;
    protected Boolean thinProvisioned;
    @XmlElementRef(name = "thinProvisioningStatus", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> thinProvisioningStatus;
    protected Long usedSpaceInMB;

    /**
     * Gets the value of the alternateIdentifier property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the alternateIdentifier property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAlternateIdentifier().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getAlternateIdentifier() {
        if (alternateIdentifier == null) {
            alternateIdentifier = new ArrayList<String>();
        }
        return this.alternateIdentifier;
    }

    /**
     * Gets the value of the backingConfig property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link BackingConfig }{@code >}
     *     
     */
    public JAXBElement<BackingConfig> getBackingConfig() {
        return backingConfig;
    }

    /**
     * Sets the value of the backingConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link BackingConfig }{@code >}
     *     
     */
    public void setBackingConfig(JAXBElement<BackingConfig> value) {
        this.backingConfig = value;
    }

    /**
     * Gets the value of the capacityInMB property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getCapacityInMB() {
        return capacityInMB;
    }

    /**
     * Sets the value of the capacityInMB property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setCapacityInMB(Long value) {
        this.capacityInMB = value;
    }

    /**
     * Gets the value of the displayName property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getDisplayName() {
        return displayName;
    }

    /**
     * Sets the value of the displayName property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setDisplayName(JAXBElement<String> value) {
        this.displayName = value;
    }

    /**
     * Gets the value of the drsManagementPermitted property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isDrsManagementPermitted() {
        return drsManagementPermitted;
    }

    /**
     * Sets the value of the drsManagementPermitted property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setDrsManagementPermitted(Boolean value) {
        this.drsManagementPermitted = value;
    }

    /**
     * Gets the value of the esxLunIdentifier property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getEsxLunIdentifier() {
        return esxLunIdentifier;
    }

    /**
     * Sets the value of the esxLunIdentifier property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setEsxLunIdentifier(JAXBElement<String> value) {
        this.esxLunIdentifier = value;
    }

    /**
     * Gets the value of the thinProvisioned property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isThinProvisioned() {
        return thinProvisioned;
    }

    /**
     * Sets the value of the thinProvisioned property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setThinProvisioned(Boolean value) {
        this.thinProvisioned = value;
    }

    /**
     * Gets the value of the thinProvisioningStatus property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getThinProvisioningStatus() {
        return thinProvisioningStatus;
    }

    /**
     * Sets the value of the thinProvisioningStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setThinProvisioningStatus(JAXBElement<String> value) {
        this.thinProvisioningStatus = value;
    }

    /**
     * Gets the value of the usedSpaceInMB property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getUsedSpaceInMB() {
        return usedSpaceInMB;
    }

    /**
     * Sets the value of the usedSpaceInMB property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setUsedSpaceInMB(Long value) {
        this.usedSpaceInMB = value;
    }

}
