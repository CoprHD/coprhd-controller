
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BatchReturnStatus complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="BatchReturnStatus">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="errorResult" type="{http://vvol.data.vasa.vim.vmware.com/xsd}BatchErrorResult" minOccurs="0"/>
 *         &lt;element name="uniqueId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BatchReturnStatus", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "errorResult",
    "uniqueId"
})
public class BatchReturnStatus {

    @XmlElementRef(name = "errorResult", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<BatchErrorResult> errorResult;
    @XmlElement(required = true)
    protected String uniqueId;

    /**
     * Gets the value of the errorResult property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link BatchErrorResult }{@code >}
     *     
     */
    public JAXBElement<BatchErrorResult> getErrorResult() {
        return errorResult;
    }

    /**
     * Sets the value of the errorResult property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link BatchErrorResult }{@code >}
     *     
     */
    public void setErrorResult(JAXBElement<BatchErrorResult> value) {
        this.errorResult = value;
    }

    /**
     * Gets the value of the uniqueId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUniqueId() {
        return uniqueId;
    }

    /**
     * Sets the value of the uniqueId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUniqueId(String value) {
        this.uniqueId = value;
    }

}
