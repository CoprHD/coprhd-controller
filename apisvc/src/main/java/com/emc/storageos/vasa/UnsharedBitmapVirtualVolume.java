
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
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
 *         &lt;element name="baseVvolId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="segmentStartOffsetBytes" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="segmentLengthBytes" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="chunkSizeBytes" type="{http://www.w3.org/2001/XMLSchema}long"/>
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
    "baseVvolId",
    "segmentStartOffsetBytes",
    "segmentLengthBytes",
    "chunkSizeBytes"
})
@XmlRootElement(name = "unsharedBitmapVirtualVolume")
public class UnsharedBitmapVirtualVolume {

    @XmlElement(required = true)
    protected String vvolId;
    @XmlElement(required = true)
    protected String baseVvolId;
    protected long segmentStartOffsetBytes;
    protected long segmentLengthBytes;
    protected long chunkSizeBytes;

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
     * Gets the value of the baseVvolId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBaseVvolId() {
        return baseVvolId;
    }

    /**
     * Sets the value of the baseVvolId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBaseVvolId(String value) {
        this.baseVvolId = value;
    }

    /**
     * Gets the value of the segmentStartOffsetBytes property.
     * 
     */
    public long getSegmentStartOffsetBytes() {
        return segmentStartOffsetBytes;
    }

    /**
     * Sets the value of the segmentStartOffsetBytes property.
     * 
     */
    public void setSegmentStartOffsetBytes(long value) {
        this.segmentStartOffsetBytes = value;
    }

    /**
     * Gets the value of the segmentLengthBytes property.
     * 
     */
    public long getSegmentLengthBytes() {
        return segmentLengthBytes;
    }

    /**
     * Sets the value of the segmentLengthBytes property.
     * 
     */
    public void setSegmentLengthBytes(long value) {
        this.segmentLengthBytes = value;
    }

    /**
     * Gets the value of the chunkSizeBytes property.
     * 
     */
    public long getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    /**
     * Sets the value of the chunkSizeBytes property.
     * 
     */
    public void setChunkSizeBytes(long value) {
        this.chunkSizeBytes = value;
    }

}
