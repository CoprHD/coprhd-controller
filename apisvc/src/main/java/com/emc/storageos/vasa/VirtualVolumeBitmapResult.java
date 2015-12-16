
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualVolumeBitmapResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualVolumeBitmapResult">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="chunkBitmap" type="{http://www.w3.org/2001/XMLSchema}base64Binary"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualVolumeBitmapResult", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "chunkBitmap"
})
public class VirtualVolumeBitmapResult {

    @XmlElement(required = true)
    protected byte[] chunkBitmap;

    /**
     * Gets the value of the chunkBitmap property.
     * 
     * @return
     *     possible object is
     *     byte[]
     */
    public byte[] getChunkBitmap() {
        return chunkBitmap;
    }

    /**
     * Sets the value of the chunkBitmap property.
     * 
     * @param value
     *     allowed object is
     *     byte[]
     */
    public void setChunkBitmap(byte[] value) {
        this.chunkBitmap = value;
    }

}
