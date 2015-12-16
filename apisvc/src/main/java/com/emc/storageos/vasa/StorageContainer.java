
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageContainer complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StorageContainer">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity">
 *       &lt;sequence>
 *         &lt;element name="maxVvolSizeMB" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="protocolEndPointType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StorageContainer", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "maxVvolSizeMB",
    "name",
    "protocolEndPointType"
})
public class StorageContainer
    extends BaseStorageEntity
{

    protected long maxVvolSizeMB;
    @XmlElement(required = true)
    protected String name;
    @XmlElementRef(name = "protocolEndPointType", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> protocolEndPointType;

    /**
     * Gets the value of the maxVvolSizeMB property.
     * 
     */
    public long getMaxVvolSizeMB() {
        return maxVvolSizeMB;
    }

    /**
     * Sets the value of the maxVvolSizeMB property.
     * 
     */
    public void setMaxVvolSizeMB(long value) {
        this.maxVvolSizeMB = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the protocolEndPointType property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getProtocolEndPointType() {
        return protocolEndPointType;
    }

    /**
     * Sets the value of the protocolEndPointType property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setProtocolEndPointType(JAXBElement<String> value) {
        this.protocolEndPointType = value;
    }

}
