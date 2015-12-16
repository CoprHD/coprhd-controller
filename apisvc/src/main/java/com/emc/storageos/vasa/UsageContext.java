
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for UsageContext complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="UsageContext">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="chapUsername" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="hostGuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="hostInitiator" type="{http://data.vasa.vim.vmware.com/xsd}HostInitiatorInfo" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="hostIoIpAddress" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="iscsiInitiatorIpAddress" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="mountPoint" type="{http://data.vasa.vim.vmware.com/xsd}MountInfo" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="sessionTimeoutInSeconds" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="subscribeEvent" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="vcGuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UsageContext", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "chapUsername",
    "hostGuid",
    "hostInitiator",
    "hostIoIpAddress",
    "iscsiInitiatorIpAddress",
    "mountPoint",
    "sessionTimeoutInSeconds",
    "subscribeEvent",
    "vcGuid"
})
public class UsageContext {

    protected List<String> chapUsername;
    @XmlElementRef(name = "hostGuid", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> hostGuid;
    protected List<HostInitiatorInfo> hostInitiator;
    protected List<String> hostIoIpAddress;
    protected List<String> iscsiInitiatorIpAddress;
    protected List<MountInfo> mountPoint;
    protected Long sessionTimeoutInSeconds;
    protected List<String> subscribeEvent;
    @XmlElementRef(name = "vcGuid", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> vcGuid;

    /**
     * Gets the value of the chapUsername property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the chapUsername property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getChapUsername().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getChapUsername() {
        if (chapUsername == null) {
            chapUsername = new ArrayList<String>();
        }
        return this.chapUsername;
    }

    /**
     * Gets the value of the hostGuid property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getHostGuid() {
        return hostGuid;
    }

    /**
     * Sets the value of the hostGuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setHostGuid(JAXBElement<String> value) {
        this.hostGuid = value;
    }

    /**
     * Gets the value of the hostInitiator property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostInitiator property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostInitiator().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostInitiatorInfo }
     * 
     * 
     */
    public List<HostInitiatorInfo> getHostInitiator() {
        if (hostInitiator == null) {
            hostInitiator = new ArrayList<HostInitiatorInfo>();
        }
        return this.hostInitiator;
    }

    /**
     * Gets the value of the hostIoIpAddress property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostIoIpAddress property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostIoIpAddress().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getHostIoIpAddress() {
        if (hostIoIpAddress == null) {
            hostIoIpAddress = new ArrayList<String>();
        }
        return this.hostIoIpAddress;
    }

    /**
     * Gets the value of the iscsiInitiatorIpAddress property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the iscsiInitiatorIpAddress property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getIscsiInitiatorIpAddress().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getIscsiInitiatorIpAddress() {
        if (iscsiInitiatorIpAddress == null) {
            iscsiInitiatorIpAddress = new ArrayList<String>();
        }
        return this.iscsiInitiatorIpAddress;
    }

    /**
     * Gets the value of the mountPoint property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the mountPoint property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMountPoint().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link MountInfo }
     * 
     * 
     */
    public List<MountInfo> getMountPoint() {
        if (mountPoint == null) {
            mountPoint = new ArrayList<MountInfo>();
        }
        return this.mountPoint;
    }

    /**
     * Gets the value of the sessionTimeoutInSeconds property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getSessionTimeoutInSeconds() {
        return sessionTimeoutInSeconds;
    }

    /**
     * Sets the value of the sessionTimeoutInSeconds property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setSessionTimeoutInSeconds(Long value) {
        this.sessionTimeoutInSeconds = value;
    }

    /**
     * Gets the value of the subscribeEvent property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the subscribeEvent property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSubscribeEvent().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getSubscribeEvent() {
        if (subscribeEvent == null) {
            subscribeEvent = new ArrayList<String>();
        }
        return this.subscribeEvent;
    }

    /**
     * Gets the value of the vcGuid property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getVcGuid() {
        return vcGuid;
    }

    /**
     * Sets the value of the vcGuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setVcGuid(JAXBElement<String> value) {
        this.vcGuid = value;
    }

}
