
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProtocolEndpointInbandId complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ProtocolEndpointInbandId">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="ipAddress" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="lunId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="protocolEndpointType" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="serverMajor" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="serverMinor" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="serverMount" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="serverScope" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ProtocolEndpointInbandId", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "ipAddress",
    "lunId",
    "protocolEndpointType",
    "serverMajor",
    "serverMinor",
    "serverMount",
    "serverScope"
})
public class ProtocolEndpointInbandId {

    @XmlElementRef(name = "ipAddress", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> ipAddress;
    @XmlElementRef(name = "lunId", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> lunId;
    @XmlElement(required = true)
    protected String protocolEndpointType;
    @XmlElementRef(name = "serverMajor", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> serverMajor;
    @XmlElementRef(name = "serverMinor", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> serverMinor;
    @XmlElementRef(name = "serverMount", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> serverMount;
    @XmlElementRef(name = "serverScope", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> serverScope;

    /**
     * Gets the value of the ipAddress property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getIpAddress() {
        return ipAddress;
    }

    /**
     * Sets the value of the ipAddress property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setIpAddress(JAXBElement<String> value) {
        this.ipAddress = value;
    }

    /**
     * Gets the value of the lunId property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getLunId() {
        return lunId;
    }

    /**
     * Sets the value of the lunId property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setLunId(JAXBElement<String> value) {
        this.lunId = value;
    }

    /**
     * Gets the value of the protocolEndpointType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProtocolEndpointType() {
        return protocolEndpointType;
    }

    /**
     * Sets the value of the protocolEndpointType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProtocolEndpointType(String value) {
        this.protocolEndpointType = value;
    }

    /**
     * Gets the value of the serverMajor property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getServerMajor() {
        return serverMajor;
    }

    /**
     * Sets the value of the serverMajor property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setServerMajor(JAXBElement<String> value) {
        this.serverMajor = value;
    }

    /**
     * Gets the value of the serverMinor property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getServerMinor() {
        return serverMinor;
    }

    /**
     * Sets the value of the serverMinor property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setServerMinor(JAXBElement<String> value) {
        this.serverMinor = value;
    }

    /**
     * Gets the value of the serverMount property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getServerMount() {
        return serverMount;
    }

    /**
     * Sets the value of the serverMount property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setServerMount(JAXBElement<String> value) {
        this.serverMount = value;
    }

    /**
     * Gets the value of the serverScope property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getServerScope() {
        return serverScope;
    }

    /**
     * Sets the value of the serverScope property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setServerScope(JAXBElement<String> value) {
        this.serverScope = value;
    }

}
