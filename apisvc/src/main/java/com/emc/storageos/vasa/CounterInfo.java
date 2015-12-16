
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CounterInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CounterInfo">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="counterId" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="nameValue" type="{http://data.vasa.vim.vmware.com/xsd}NameValuePair" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CounterInfo", namespace = "http://statistics.data.vasa.vim.vmware.com/xsd", propOrder = {
    "counterId",
    "nameValue"
})
public class CounterInfo {

    protected int counterId;
    protected List<NameValuePair> nameValue;

    /**
     * Gets the value of the counterId property.
     * 
     */
    public int getCounterId() {
        return counterId;
    }

    /**
     * Sets the value of the counterId property.
     * 
     */
    public void setCounterId(int value) {
        this.counterId = value;
    }

    /**
     * Gets the value of the nameValue property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the nameValue property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getNameValue().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link NameValuePair }
     * 
     * 
     */
    public List<NameValuePair> getNameValue() {
        if (nameValue == null) {
            nameValue = new ArrayList<NameValuePair>();
        }
        return this.nameValue;
    }

}
