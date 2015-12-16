
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PlacementSolution complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PlacementSolution">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="containerUsage" type="{http://placement.policy.data.vasa.vim.vmware.com/xsd}ContainerUsage" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="subjectAssignment" type="{http://placement.policy.data.vasa.vim.vmware.com/xsd}SubjectAssignment" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PlacementSolution", namespace = "http://placement.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "containerUsage",
    "subjectAssignment"
})
public class PlacementSolution {

    protected List<ContainerUsage> containerUsage;
    @XmlElement(required = true)
    protected List<SubjectAssignment> subjectAssignment;

    /**
     * Gets the value of the containerUsage property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the containerUsage property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getContainerUsage().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ContainerUsage }
     * 
     * 
     */
    public List<ContainerUsage> getContainerUsage() {
        if (containerUsage == null) {
            containerUsage = new ArrayList<ContainerUsage>();
        }
        return this.containerUsage;
    }

    /**
     * Gets the value of the subjectAssignment property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the subjectAssignment property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSubjectAssignment().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link SubjectAssignment }
     * 
     * 
     */
    public List<SubjectAssignment> getSubjectAssignment() {
        if (subjectAssignment == null) {
            subjectAssignment = new ArrayList<SubjectAssignment>();
        }
        return this.subjectAssignment;
    }

}
