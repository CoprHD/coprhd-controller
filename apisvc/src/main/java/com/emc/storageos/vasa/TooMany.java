
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for TooMany complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="TooMany">
 *   &lt;complexContent>
 *     &lt;extension base="{http://com.vmware.vim.vasa/2.0/xsd}Exception">
 *       &lt;sequence>
 *         &lt;element name="maxBatchSize" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TooMany", namespace = "http://fault.vasa.vim.vmware.com/xsd", propOrder = {
    "maxBatchSize"
})
public class TooMany
    extends Exception
{

    protected Long maxBatchSize;

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

}
