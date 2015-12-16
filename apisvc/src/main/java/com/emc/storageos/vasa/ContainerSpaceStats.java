
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ContainerSpaceStats complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ContainerSpaceStats">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="logicalFree" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="logicalLimit" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="logicalUsed" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="objectId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="physicalFree" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="physicalTotal" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="physicalUsed" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ContainerSpaceStats", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "logicalFree",
    "logicalLimit",
    "logicalUsed",
    "objectId",
    "physicalFree",
    "physicalTotal",
    "physicalUsed"
})
public class ContainerSpaceStats {

    protected long logicalFree;
    protected long logicalLimit;
    protected long logicalUsed;
    @XmlElement(required = true)
    protected String objectId;
    protected long physicalFree;
    protected long physicalTotal;
    protected long physicalUsed;

    /**
     * Gets the value of the logicalFree property.
     * 
     */
    public long getLogicalFree() {
        return logicalFree;
    }

    /**
     * Sets the value of the logicalFree property.
     * 
     */
    public void setLogicalFree(long value) {
        this.logicalFree = value;
    }

    /**
     * Gets the value of the logicalLimit property.
     * 
     */
    public long getLogicalLimit() {
        return logicalLimit;
    }

    /**
     * Sets the value of the logicalLimit property.
     * 
     */
    public void setLogicalLimit(long value) {
        this.logicalLimit = value;
    }

    /**
     * Gets the value of the logicalUsed property.
     * 
     */
    public long getLogicalUsed() {
        return logicalUsed;
    }

    /**
     * Sets the value of the logicalUsed property.
     * 
     */
    public void setLogicalUsed(long value) {
        this.logicalUsed = value;
    }

    /**
     * Gets the value of the objectId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getObjectId() {
        return objectId;
    }

    /**
     * Sets the value of the objectId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setObjectId(String value) {
        this.objectId = value;
    }

    /**
     * Gets the value of the physicalFree property.
     * 
     */
    public long getPhysicalFree() {
        return physicalFree;
    }

    /**
     * Sets the value of the physicalFree property.
     * 
     */
    public void setPhysicalFree(long value) {
        this.physicalFree = value;
    }

    /**
     * Gets the value of the physicalTotal property.
     * 
     */
    public long getPhysicalTotal() {
        return physicalTotal;
    }

    /**
     * Sets the value of the physicalTotal property.
     * 
     */
    public void setPhysicalTotal(long value) {
        this.physicalTotal = value;
    }

    /**
     * Gets the value of the physicalUsed property.
     * 
     */
    public long getPhysicalUsed() {
        return physicalUsed;
    }

    /**
     * Sets the value of the physicalUsed property.
     * 
     */
    public void setPhysicalUsed(long value) {
        this.physicalUsed = value;
    }

}
