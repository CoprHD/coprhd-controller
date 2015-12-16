
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BackingStoragePool complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="BackingStoragePool">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity">
 *       &lt;sequence>
 *         &lt;element name="capacityInMB" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="type" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="usedSpaceInMB" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BackingStoragePool", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "capacityInMB",
    "type",
    "usedSpaceInMB"
})
public class BackingStoragePool
    extends BaseStorageEntity
{

    protected long capacityInMB;
    @XmlElement(required = true)
    protected String type;
    protected long usedSpaceInMB;

    /**
     * Gets the value of the capacityInMB property.
     * 
     */
    public long getCapacityInMB() {
        return capacityInMB;
    }

    /**
     * Sets the value of the capacityInMB property.
     * 
     */
    public void setCapacityInMB(long value) {
        this.capacityInMB = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setType(String value) {
        this.type = value;
    }

    /**
     * Gets the value of the usedSpaceInMB property.
     * 
     */
    public long getUsedSpaceInMB() {
        return usedSpaceInMB;
    }

    /**
     * Sets the value of the usedSpaceInMB property.
     * 
     */
    public void setUsedSpaceInMB(long value) {
        this.usedSpaceInMB = value;
    }

}
