
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
 * <p>Java class for VasaProviderInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VasaProviderInfo">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="defaultNamespace" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="defaultSessionTimeoutInSeconds" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="maxBatchSize" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="maxConcurrentRequestsPerSession" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="needsExplicitActivation" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="retainVasaProviderCertificate" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="sessionId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="supportedProfile" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded"/>
 *         &lt;element name="supportedVendorModel" type="{http://data.vasa.vim.vmware.com/xsd}VendorModel" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="uid" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="vasaApiVersion" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="vasaProviderVersion" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VasaProviderInfo", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "defaultNamespace",
    "defaultSessionTimeoutInSeconds",
    "maxBatchSize",
    "maxConcurrentRequestsPerSession",
    "name",
    "needsExplicitActivation",
    "retainVasaProviderCertificate",
    "sessionId",
    "supportedProfile",
    "supportedVendorModel",
    "uid",
    "vasaApiVersion",
    "vasaProviderVersion"
})
public class VasaProviderInfo {

    @XmlElementRef(name = "defaultNamespace", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> defaultNamespace;
    protected Long defaultSessionTimeoutInSeconds;
    protected Long maxBatchSize;
    protected Long maxConcurrentRequestsPerSession;
    @XmlElementRef(name = "name", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> name;
    protected Boolean needsExplicitActivation;
    protected Boolean retainVasaProviderCertificate;
    @XmlElementRef(name = "sessionId", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> sessionId;
    @XmlElement(required = true)
    protected List<String> supportedProfile;
    protected List<VendorModel> supportedVendorModel;
    @XmlElement(required = true)
    protected String uid;
    @XmlElementRef(name = "vasaApiVersion", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> vasaApiVersion;
    @XmlElementRef(name = "vasaProviderVersion", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> vasaProviderVersion;

    /**
     * Gets the value of the defaultNamespace property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getDefaultNamespace() {
        return defaultNamespace;
    }

    /**
     * Sets the value of the defaultNamespace property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setDefaultNamespace(JAXBElement<String> value) {
        this.defaultNamespace = value;
    }

    /**
     * Gets the value of the defaultSessionTimeoutInSeconds property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getDefaultSessionTimeoutInSeconds() {
        return defaultSessionTimeoutInSeconds;
    }

    /**
     * Sets the value of the defaultSessionTimeoutInSeconds property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setDefaultSessionTimeoutInSeconds(Long value) {
        this.defaultSessionTimeoutInSeconds = value;
    }

    /**
     * Gets the value of the maxBatchSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMaxBatchSize() {
        return maxBatchSize;
    }

    /**
     * Sets the value of the maxBatchSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMaxBatchSize(Long value) {
        this.maxBatchSize = value;
    }

    /**
     * Gets the value of the maxConcurrentRequestsPerSession property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getMaxConcurrentRequestsPerSession() {
        return maxConcurrentRequestsPerSession;
    }

    /**
     * Sets the value of the maxConcurrentRequestsPerSession property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setMaxConcurrentRequestsPerSession(Long value) {
        this.maxConcurrentRequestsPerSession = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setName(JAXBElement<String> value) {
        this.name = value;
    }

    /**
     * Gets the value of the needsExplicitActivation property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isNeedsExplicitActivation() {
        return needsExplicitActivation;
    }

    /**
     * Sets the value of the needsExplicitActivation property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setNeedsExplicitActivation(Boolean value) {
        this.needsExplicitActivation = value;
    }

    /**
     * Gets the value of the retainVasaProviderCertificate property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isRetainVasaProviderCertificate() {
        return retainVasaProviderCertificate;
    }

    /**
     * Sets the value of the retainVasaProviderCertificate property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setRetainVasaProviderCertificate(Boolean value) {
        this.retainVasaProviderCertificate = value;
    }

    /**
     * Gets the value of the sessionId property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getSessionId() {
        return sessionId;
    }

    /**
     * Sets the value of the sessionId property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setSessionId(JAXBElement<String> value) {
        this.sessionId = value;
    }

    /**
     * Gets the value of the supportedProfile property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the supportedProfile property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSupportedProfile().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getSupportedProfile() {
        if (supportedProfile == null) {
            supportedProfile = new ArrayList<String>();
        }
        return this.supportedProfile;
    }

    /**
     * Gets the value of the supportedVendorModel property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the supportedVendorModel property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSupportedVendorModel().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VendorModel }
     * 
     * 
     */
    public List<VendorModel> getSupportedVendorModel() {
        if (supportedVendorModel == null) {
            supportedVendorModel = new ArrayList<VendorModel>();
        }
        return this.supportedVendorModel;
    }

    /**
     * Gets the value of the uid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUid() {
        return uid;
    }

    /**
     * Sets the value of the uid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUid(String value) {
        this.uid = value;
    }

    /**
     * Gets the value of the vasaApiVersion property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getVasaApiVersion() {
        return vasaApiVersion;
    }

    /**
     * Sets the value of the vasaApiVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setVasaApiVersion(JAXBElement<String> value) {
        this.vasaApiVersion = value;
    }

    /**
     * Gets the value of the vasaProviderVersion property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getVasaProviderVersion() {
        return vasaProviderVersion;
    }

    /**
     * Sets the value of the vasaProviderVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setVasaProviderVersion(JAXBElement<String> value) {
        this.vasaProviderVersion = value;
    }

}
