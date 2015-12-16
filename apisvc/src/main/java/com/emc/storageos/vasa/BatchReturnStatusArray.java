
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BatchReturnStatusArray complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="BatchReturnStatusArray">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="batchReturnStatusArray" type="{http://vvol.data.vasa.vim.vmware.com/xsd}BatchReturnStatus" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BatchReturnStatusArray", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "batchReturnStatusArray"
})
public class BatchReturnStatusArray {

    @XmlElement(required = true)
    protected List<BatchReturnStatus> batchReturnStatusArray;

    /**
     * Gets the value of the batchReturnStatusArray property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the batchReturnStatusArray property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getBatchReturnStatusArray().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link BatchReturnStatus }
     * 
     * 
     */
    public List<BatchReturnStatus> getBatchReturnStatusArray() {
        if (batchReturnStatusArray == null) {
            batchReturnStatusArray = new ArrayList<BatchReturnStatus>();
        }
        return this.batchReturnStatusArray;
    }

}
