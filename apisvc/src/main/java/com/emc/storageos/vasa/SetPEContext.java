
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
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
 *         &lt;element name="listOfHostPE" type="{http://vvol.data.vasa.vim.vmware.com/xsd}ProtocolEndpoint" maxOccurs="unbounded" minOccurs="0"/>
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
    "listOfHostPE"
})
@XmlRootElement(name = "setPEContext")
public class SetPEContext {

    protected List<ProtocolEndpoint> listOfHostPE;

    /**
     * Gets the value of the listOfHostPE property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the listOfHostPE property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getListOfHostPE().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProtocolEndpoint }
     * 
     * 
     */
    public List<ProtocolEndpoint> getListOfHostPE() {
        if (listOfHostPE == null) {
            listOfHostPE = new ArrayList<ProtocolEndpoint>();
        }
        return this.listOfHostPE;
    }

}
