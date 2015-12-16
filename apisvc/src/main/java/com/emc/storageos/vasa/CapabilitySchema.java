
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CapabilitySchema complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CapabilitySchema">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="capabilityMetadataPerCategory" type="{http://provider.capability.policy.data.vasa.vim.vmware.com/xsd}CapabilityMetadataPerCategory" maxOccurs="unbounded"/>
 *         &lt;element name="namespaceInfo" type="{http://provider.capability.policy.data.vasa.vim.vmware.com/xsd}NamespaceInfo"/>
 *         &lt;element name="schemaId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="vendorInfo" type="{http://provider.capability.policy.data.vasa.vim.vmware.com/xsd}VendorInfo"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CapabilitySchema", namespace = "http://provider.capability.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "capabilityMetadataPerCategory",
    "namespaceInfo",
    "schemaId",
    "vendorInfo"
})
public class CapabilitySchema {

    @XmlElement(required = true)
    protected List<CapabilityMetadataPerCategory> capabilityMetadataPerCategory;
    @XmlElement(required = true)
    protected NamespaceInfo namespaceInfo;
    @XmlElement(required = true)
    protected String schemaId;
    @XmlElement(required = true)
    protected VendorInfo vendorInfo;

    /**
     * Gets the value of the capabilityMetadataPerCategory property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the capabilityMetadataPerCategory property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCapabilityMetadataPerCategory().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link CapabilityMetadataPerCategory }
     * 
     * 
     */
    public List<CapabilityMetadataPerCategory> getCapabilityMetadataPerCategory() {
        if (capabilityMetadataPerCategory == null) {
            capabilityMetadataPerCategory = new ArrayList<CapabilityMetadataPerCategory>();
        }
        return this.capabilityMetadataPerCategory;
    }

    /**
     * Gets the value of the namespaceInfo property.
     * 
     * @return
     *     possible object is
     *     {@link NamespaceInfo }
     *     
     */
    public NamespaceInfo getNamespaceInfo() {
        return namespaceInfo;
    }

    /**
     * Sets the value of the namespaceInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link NamespaceInfo }
     *     
     */
    public void setNamespaceInfo(NamespaceInfo value) {
        this.namespaceInfo = value;
    }

    /**
     * Gets the value of the schemaId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSchemaId() {
        return schemaId;
    }

    /**
     * Sets the value of the schemaId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSchemaId(String value) {
        this.schemaId = value;
    }

    /**
     * Gets the value of the vendorInfo property.
     * 
     * @return
     *     possible object is
     *     {@link VendorInfo }
     *     
     */
    public VendorInfo getVendorInfo() {
        return vendorInfo;
    }

    /**
     * Sets the value of the vendorInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link VendorInfo }
     *     
     */
    public void setVendorInfo(VendorInfo value) {
        this.vendorInfo = value;
    }

}
