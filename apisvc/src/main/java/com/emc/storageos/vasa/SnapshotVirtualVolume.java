
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
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
 *         &lt;element name="snapshotInfo" type="{http://vvol.data.vasa.vim.vmware.com/xsd}VirtualVolumeInfo" maxOccurs="unbounded"/>
 *         &lt;element name="timeoutMS" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="containerCookie" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
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
    "snapshotInfo",
    "timeoutMS",
    "containerCookie"
})
@XmlRootElement(name = "snapshotVirtualVolume")
public class SnapshotVirtualVolume {

    @XmlElement(required = true)
    protected List<VirtualVolumeInfo> snapshotInfo;
    protected long timeoutMS;
    @XmlElementRef(name = "containerCookie", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> containerCookie;

    /**
     * Gets the value of the snapshotInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the snapshotInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSnapshotInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VirtualVolumeInfo }
     * 
     * 
     */
    public List<VirtualVolumeInfo> getSnapshotInfo() {
        if (snapshotInfo == null) {
            snapshotInfo = new ArrayList<VirtualVolumeInfo>();
        }
        return this.snapshotInfo;
    }

    /**
     * Gets the value of the timeoutMS property.
     * 
     */
    public long getTimeoutMS() {
        return timeoutMS;
    }

    /**
     * Sets the value of the timeoutMS property.
     * 
     */
    public void setTimeoutMS(long value) {
        this.timeoutMS = value;
    }

    /**
     * Gets the value of the containerCookie property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getContainerCookie() {
        return containerCookie;
    }

    /**
     * Sets the value of the containerCookie property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setContainerCookie(JAXBElement<String> value) {
        this.containerCookie = value;
    }

}
