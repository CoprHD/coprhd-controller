
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostInitiatorInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostInitiatorInfo">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity">
 *       &lt;sequence>
 *         &lt;element name="iscsiIdentifier" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="nodeWwn" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="portWwn" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostInitiatorInfo", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "iscsiIdentifier",
    "nodeWwn",
    "portWwn"
})
public class HostInitiatorInfo
    extends BaseStorageEntity
{

    @XmlElementRef(name = "iscsiIdentifier", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> iscsiIdentifier;
    @XmlElementRef(name = "nodeWwn", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> nodeWwn;
    @XmlElementRef(name = "portWwn", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> portWwn;

    /**
     * Gets the value of the iscsiIdentifier property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getIscsiIdentifier() {
        return iscsiIdentifier;
    }

    /**
     * Sets the value of the iscsiIdentifier property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setIscsiIdentifier(JAXBElement<String> value) {
        this.iscsiIdentifier = value;
    }

    /**
     * Gets the value of the nodeWwn property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getNodeWwn() {
        return nodeWwn;
    }

    /**
     * Sets the value of the nodeWwn property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setNodeWwn(JAXBElement<String> value) {
        this.nodeWwn = value;
    }

    /**
     * Gets the value of the portWwn property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getPortWwn() {
        return portWwn;
    }

    /**
     * Sets the value of the portWwn property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setPortWwn(JAXBElement<String> value) {
        this.portWwn = value;
    }

}
