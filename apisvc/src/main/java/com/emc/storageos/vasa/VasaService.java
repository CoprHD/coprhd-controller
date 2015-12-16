
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for vasaService complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="vasaService">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="PEContext" type="{http://vvol.data.vasa.vim.vmware.com/xsd}ProtocolEndpoint" maxOccurs="unbounded"/>
 *         &lt;element name="statisticsContext" type="{http://statistics.data.vasa.vim.vmware.com/xsd}ArrayStatisticsManifest" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "vasaService", namespace = "http://vasa.vim.vmware.com/xsd", propOrder = {
    "peContext",
    "statisticsContext"
})
public class VasaService {

    @XmlElement(name = "PEContext", required = true)
    protected List<ProtocolEndpoint> peContext;
    @XmlElement(required = true)
    protected List<ArrayStatisticsManifest> statisticsContext;

    /**
     * Gets the value of the peContext property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the peContext property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPEContext().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProtocolEndpoint }
     * 
     * 
     */
    public List<ProtocolEndpoint> getPEContext() {
        if (peContext == null) {
            peContext = new ArrayList<ProtocolEndpoint>();
        }
        return this.peContext;
    }

    /**
     * Gets the value of the statisticsContext property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the statisticsContext property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getStatisticsContext().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ArrayStatisticsManifest }
     * 
     * 
     */
    public List<ArrayStatisticsManifest> getStatisticsContext() {
        if (statisticsContext == null) {
            statisticsContext = new ArrayList<ArrayStatisticsManifest>();
        }
        return this.statisticsContext;
    }

}
