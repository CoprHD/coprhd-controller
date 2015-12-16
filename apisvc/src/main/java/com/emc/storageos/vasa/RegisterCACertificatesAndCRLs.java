
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
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
 *         &lt;element name="providerUserName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="providerPassword" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="caRootCert" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded"/>
 *         &lt;element name="caCRL" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
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
    "providerUserName",
    "providerPassword",
    "caRootCert",
    "caCRL"
})
@XmlRootElement(name = "registerCACertificatesAndCRLs")
public class RegisterCACertificatesAndCRLs {

    @XmlElement(required = true)
    protected String providerUserName;
    @XmlElement(required = true)
    protected String providerPassword;
    @XmlElement(required = true)
    protected List<String> caRootCert;
    protected List<String> caCRL;

    /**
     * Gets the value of the providerUserName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProviderUserName() {
        return providerUserName;
    }

    /**
     * Sets the value of the providerUserName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProviderUserName(String value) {
        this.providerUserName = value;
    }

    /**
     * Gets the value of the providerPassword property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProviderPassword() {
        return providerPassword;
    }

    /**
     * Sets the value of the providerPassword property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProviderPassword(String value) {
        this.providerPassword = value;
    }

    /**
     * Gets the value of the caRootCert property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the caRootCert property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCaRootCert().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getCaRootCert() {
        if (caRootCert == null) {
            caRootCert = new ArrayList<String>();
        }
        return this.caRootCert;
    }

    /**
     * Gets the value of the caCRL property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the caCRL property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCaCRL().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getCaCRL() {
        if (caCRL == null) {
            caCRL = new ArrayList<String>();
        }
        return this.caCRL;
    }

}
