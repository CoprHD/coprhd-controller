
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProtocolEndpoint complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ProtocolEndpoint">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity">
 *       &lt;sequence>
 *         &lt;element name="authType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="inBandBindCapability" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="inBandId" type="{http://vvol.data.vasa.vim.vmware.com/xsd}ProtocolEndpointInbandId"/>
 *         &lt;element name="transportIpAddress" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ProtocolEndpoint", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "authType",
    "inBandBindCapability",
    "inBandId",
    "transportIpAddress"
})
public class ProtocolEndpoint
    extends BaseStorageEntity
{

    @XmlElementRef(name = "authType", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> authType;
    @XmlElement(required = true)
    protected String inBandBindCapability;
    @XmlElement(required = true)
    protected ProtocolEndpointInbandId inBandId;
    protected List<String> transportIpAddress;

    /**
     * Gets the value of the authType property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getAuthType() {
        return authType;
    }

    /**
     * Sets the value of the authType property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setAuthType(JAXBElement<String> value) {
        this.authType = value;
    }

    /**
     * Gets the value of the inBandBindCapability property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getInBandBindCapability() {
        return inBandBindCapability;
    }

    /**
     * Sets the value of the inBandBindCapability property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setInBandBindCapability(String value) {
        this.inBandBindCapability = value;
    }

    /**
     * Gets the value of the inBandId property.
     * 
     * @return
     *     possible object is
     *     {@link ProtocolEndpointInbandId }
     *     
     */
    public ProtocolEndpointInbandId getInBandId() {
        return inBandId;
    }

    /**
     * Sets the value of the inBandId property.
     * 
     * @param value
     *     allowed object is
     *     {@link ProtocolEndpointInbandId }
     *     
     */
    public void setInBandId(ProtocolEndpointInbandId value) {
        this.inBandId = value;
    }

    /**
     * Gets the value of the transportIpAddress property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the transportIpAddress property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTransportIpAddress().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getTransportIpAddress() {
        if (transportIpAddress == null) {
            transportIpAddress = new ArrayList<String>();
        }
        return this.transportIpAddress;
    }

}
