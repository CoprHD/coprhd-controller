
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualVolumeHandle complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualVolumeHandle">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity">
 *       &lt;sequence>
 *         &lt;element name="peInBandId" type="{http://vvol.data.vasa.vim.vmware.com/xsd}ProtocolEndpointInbandId"/>
 *         &lt;element name="vvolSecondaryId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualVolumeHandle", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "peInBandId",
    "vvolSecondaryId"
})
public class VirtualVolumeHandle
    extends BaseStorageEntity
{

    @XmlElement(required = true)
    protected ProtocolEndpointInbandId peInBandId;
    @XmlElement(required = true)
    protected String vvolSecondaryId;

    /**
     * Gets the value of the peInBandId property.
     * 
     * @return
     *     possible object is
     *     {@link ProtocolEndpointInbandId }
     *     
     */
    public ProtocolEndpointInbandId getPeInBandId() {
        return peInBandId;
    }

    /**
     * Sets the value of the peInBandId property.
     * 
     * @param value
     *     allowed object is
     *     {@link ProtocolEndpointInbandId }
     *     
     */
    public void setPeInBandId(ProtocolEndpointInbandId value) {
        this.peInBandId = value;
    }

    /**
     * Gets the value of the vvolSecondaryId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVvolSecondaryId() {
        return vvolSecondaryId;
    }

    /**
     * Sets the value of the vvolSecondaryId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVvolSecondaryId(String value) {
        this.vvolSecondaryId = value;
    }

}
