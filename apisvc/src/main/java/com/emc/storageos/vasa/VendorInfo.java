
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VendorInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VendorInfo">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="info" type="{http://policy.data.vasa.vim.vmware.com/xsd}ExtendedElementDescription"/>
 *         &lt;element name="vendorUuid" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VendorInfo", namespace = "http://provider.capability.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "info",
    "vendorUuid"
})
public class VendorInfo {

    @XmlElement(required = true)
    protected ExtendedElementDescription info;
    @XmlElement(required = true)
    protected String vendorUuid;

    /**
     * Gets the value of the info property.
     * 
     * @return
     *     possible object is
     *     {@link ExtendedElementDescription }
     *     
     */
    public ExtendedElementDescription getInfo() {
        return info;
    }

    /**
     * Sets the value of the info property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExtendedElementDescription }
     *     
     */
    public void setInfo(ExtendedElementDescription value) {
        this.info = value;
    }

    /**
     * Gets the value of the vendorUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVendorUuid() {
        return vendorUuid;
    }

    /**
     * Sets the value of the vendorUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVendorUuid(String value) {
        this.vendorUuid = value;
    }

}
