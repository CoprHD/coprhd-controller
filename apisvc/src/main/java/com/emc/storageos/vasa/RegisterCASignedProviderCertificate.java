
package com.emc.storageos.vasa;

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
 *         &lt;element name="providerCert" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="forceApply" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
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
    "providerCert",
    "forceApply"
})
@XmlRootElement(name = "registerCASignedProviderCertificate")
public class RegisterCASignedProviderCertificate {

    @XmlElement(required = true)
    protected String providerCert;
    protected boolean forceApply;

    /**
     * Gets the value of the providerCert property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProviderCert() {
        return providerCert;
    }

    /**
     * Sets the value of the providerCert property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProviderCert(String value) {
        this.providerCert = value;
    }

    /**
     * Gets the value of the forceApply property.
     * 
     */
    public boolean isForceApply() {
        return forceApply;
    }

    /**
     * Sets the value of the forceApply property.
     * 
     */
    public void setForceApply(boolean value) {
        this.forceApply = value;
    }

}
