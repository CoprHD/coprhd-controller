
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BatchVirtualVolumeHandleResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="BatchVirtualVolumeHandleResult">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="fault" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/>
 *         &lt;element name="vvolHandle" type="{http://vvol.data.vasa.vim.vmware.com/xsd}VirtualVolumeHandle" minOccurs="0"/>
 *         &lt;element name="vvolId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="vvolInfo" type="{http://vvol.data.vasa.vim.vmware.com/xsd}VirtualVolumeInfo" minOccurs="0"/>
 *         &lt;element name="vvolLogicalSize" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BatchVirtualVolumeHandleResult", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "fault",
    "vvolHandle",
    "vvolId",
    "vvolInfo",
    "vvolLogicalSize"
})
public class BatchVirtualVolumeHandleResult {

    @XmlElementRef(name = "fault", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<Object> fault;
    @XmlElementRef(name = "vvolHandle", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<VirtualVolumeHandle> vvolHandle;
    @XmlElement(required = true)
    protected String vvolId;
    @XmlElementRef(name = "vvolInfo", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<VirtualVolumeInfo> vvolInfo;
    protected Long vvolLogicalSize;

    /**
     * Gets the value of the fault property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Object }{@code >}
     *     
     */
    public JAXBElement<Object> getFault() {
        return fault;
    }

    /**
     * Sets the value of the fault property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Object }{@code >}
     *     
     */
    public void setFault(JAXBElement<Object> value) {
        this.fault = value;
    }

    /**
     * Gets the value of the vvolHandle property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link VirtualVolumeHandle }{@code >}
     *     
     */
    public JAXBElement<VirtualVolumeHandle> getVvolHandle() {
        return vvolHandle;
    }

    /**
     * Sets the value of the vvolHandle property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link VirtualVolumeHandle }{@code >}
     *     
     */
    public void setVvolHandle(JAXBElement<VirtualVolumeHandle> value) {
        this.vvolHandle = value;
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

    /**
     * Gets the value of the vvolInfo property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link VirtualVolumeInfo }{@code >}
     *     
     */
    public JAXBElement<VirtualVolumeInfo> getVvolInfo() {
        return vvolInfo;
    }

    /**
     * Sets the value of the vvolInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link VirtualVolumeInfo }{@code >}
     *     
     */
    public void setVvolInfo(JAXBElement<VirtualVolumeInfo> value) {
        this.vvolInfo = value;
    }

    /**
     * Gets the value of the vvolLogicalSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getVvolLogicalSize() {
        return vvolLogicalSize;
    }

    /**
     * Sets the value of the vvolLogicalSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setVvolLogicalSize(Long value) {
        this.vvolLogicalSize = value;
    }

}
