
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualVolumeUnsharedChunksResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualVolumeUnsharedChunksResult">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="chunkSizeBytes" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="scannedChunks" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="unsharedChunks" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualVolumeUnsharedChunksResult", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "chunkSizeBytes",
    "scannedChunks",
    "unsharedChunks"
})
public class VirtualVolumeUnsharedChunksResult {

    protected long chunkSizeBytes;
    protected long scannedChunks;
    protected long unsharedChunks;

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

    /**
     * Gets the value of the scannedChunks property.
     * 
     */
    public long getScannedChunks() {
        return scannedChunks;
    }

    /**
     * Sets the value of the scannedChunks property.
     * 
     */
    public void setScannedChunks(long value) {
        this.scannedChunks = value;
    }

    /**
     * Gets the value of the unsharedChunks property.
     * 
     */
    public long getUnsharedChunks() {
        return unsharedChunks;
    }

    /**
     * Sets the value of the unsharedChunks property.
     * 
     */
    public void setUnsharedChunks(long value) {
        this.unsharedChunks = value;
    }

}
