
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualVolumeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualVolumeInfo">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity">
 *       &lt;sequence>
 *         &lt;element name="metadata" type="{http://data.vasa.vim.vmware.com/xsd}NameValuePair" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="vvolId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualVolumeInfo", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "metadata",
    "vvolId"
})
public class VirtualVolumeInfo
    extends BaseStorageEntity
{

    protected List<NameValuePair> metadata;
    @XmlElement(required = true)
    protected String vvolId;

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

}
