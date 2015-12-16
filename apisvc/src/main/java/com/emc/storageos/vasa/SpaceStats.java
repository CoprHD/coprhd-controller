
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SpaceStats complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SpaceStats">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="committed" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="logical" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="objectId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="unshared" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="unsharedValid" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SpaceStats", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "committed",
    "logical",
    "objectId",
    "unshared",
    "unsharedValid"
})
public class SpaceStats {

    protected long committed;
    protected long logical;
    @XmlElement(required = true)
    protected String objectId;
    protected long unshared;
    protected boolean unsharedValid;

    /**
     * Gets the value of the committed property.
     * 
     */
    public long getCommitted() {
        return committed;
    }

    /**
     * Sets the value of the committed property.
     * 
     */
    public void setCommitted(long value) {
        this.committed = value;
    }

    /**
     * Gets the value of the logical property.
     * 
     */
    public long getLogical() {
        return logical;
    }

    /**
     * Sets the value of the logical property.
     * 
     */
    public void setLogical(long value) {
        this.logical = value;
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
     * Gets the value of the unshared property.
     * 
     */
    public long getUnshared() {
        return unshared;
    }

    /**
     * Sets the value of the unshared property.
     * 
     */
    public void setUnshared(long value) {
        this.unshared = value;
    }

    /**
     * Gets the value of the unsharedValid property.
     * 
     */
    public boolean isUnsharedValid() {
        return unsharedValid;
    }

    /**
     * Sets the value of the unsharedValid property.
     * 
     */
    public void setUnsharedValid(boolean value) {
        this.unsharedValid = value;
    }

}
