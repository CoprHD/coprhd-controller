
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EntityStatisticsManifest complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="EntityStatisticsManifest">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="counterInfo" type="{http://statistics.data.vasa.vim.vmware.com/xsd}CounterInfo" maxOccurs="unbounded"/>
 *         &lt;element name="entityTypeName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="maxRetrievalSize" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="pollingInterval" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EntityStatisticsManifest", namespace = "http://statistics.data.vasa.vim.vmware.com/xsd", propOrder = {
    "counterInfo",
    "entityTypeName",
    "maxRetrievalSize",
    "pollingInterval"
})
public class EntityStatisticsManifest {

    @XmlElement(required = true)
    protected List<CounterInfo> counterInfo;
    @XmlElement(required = true)
    protected String entityTypeName;
    protected int maxRetrievalSize;
    protected int pollingInterval;

    /**
     * Gets the value of the counterInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the counterInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCounterInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link CounterInfo }
     * 
     * 
     */
    public List<CounterInfo> getCounterInfo() {
        if (counterInfo == null) {
            counterInfo = new ArrayList<CounterInfo>();
        }
        return this.counterInfo;
    }

    /**
     * Gets the value of the entityTypeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEntityTypeName() {
        return entityTypeName;
    }

    /**
     * Sets the value of the entityTypeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEntityTypeName(String value) {
        this.entityTypeName = value;
    }

    /**
     * Gets the value of the maxRetrievalSize property.
     * 
     */
    public int getMaxRetrievalSize() {
        return maxRetrievalSize;
    }

    /**
     * Sets the value of the maxRetrievalSize property.
     * 
     */
    public void setMaxRetrievalSize(int value) {
        this.maxRetrievalSize = value;
    }

    /**
     * Gets the value of the pollingInterval property.
     * 
     */
    public int getPollingInterval() {
        return pollingInterval;
    }

    /**
     * Sets the value of the pollingInterval property.
     * 
     */
    public void setPollingInterval(int value) {
        this.pollingInterval = value;
    }

}
