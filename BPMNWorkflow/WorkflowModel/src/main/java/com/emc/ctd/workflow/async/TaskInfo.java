
package com.emc.ctd.workflow.async;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;


/**
 * <p>Java class for TaskInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="TaskInfo">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="arrayId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="cancelable" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *         &lt;element name="cancelled" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *         &lt;element name="error" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/>
 *         &lt;element name="estimatedTimeToComplete" type="{http://www.w3.org/2001/XMLSchema}dateTime" minOccurs="0"/>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="progress" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="progressUpdateAvailable" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *         &lt;element name="result" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/>
 *         &lt;element name="startTime" type="{http://www.w3.org/2001/XMLSchema}dateTime"/>
 *         &lt;element name="taskId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="taskState" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TaskInfo", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "arrayId",
    "cancelable",
    "cancelled",
    "error",
    "estimatedTimeToComplete",
    "name",
    "progress",
    "progressUpdateAvailable",
    "result",
    "startTime",
    "taskId",
    "taskState"
})
public class TaskInfo {

    @XmlElementRef(name = "arrayId", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> arrayId;
    protected boolean cancelable;
    protected boolean cancelled;
    @XmlElementRef(name = "error", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<Object> error;
    @XmlElementRef(name = "estimatedTimeToComplete", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<XMLGregorianCalendar> estimatedTimeToComplete;
    @XmlElement(required = true)
    protected String name;
    protected int progress;
    protected boolean progressUpdateAvailable;
    @XmlElementRef(name = "result", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<Object> result;
    @XmlElement(required = true)
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar startTime;
    @XmlElement(required = true)
    protected String taskId;
    @XmlElement(required = true)
    protected String taskState;

    /**
     * Gets the value of the arrayId property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getArrayId() {
        return arrayId;
    }

    /**
     * Sets the value of the arrayId property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setArrayId(JAXBElement<String> value) {
        this.arrayId = value;
    }

    /**
     * Gets the value of the cancelable property.
     * 
     */
    public boolean isCancelable() {
        return cancelable;
    }

    /**
     * Sets the value of the cancelable property.
     * 
     */
    public void setCancelable(boolean value) {
        this.cancelable = value;
    }

    /**
     * Gets the value of the cancelled property.
     * 
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets the value of the cancelled property.
     * 
     */
    public void setCancelled(boolean value) {
        this.cancelled = value;
    }

    /**
     * Gets the value of the error property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Object }{@code >}
     *     
     */
    public JAXBElement<Object> getError() {
        return error;
    }

    /**
     * Sets the value of the error property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Object }{@code >}
     *     
     */
    public void setError(JAXBElement<Object> value) {
        this.error = value;
    }

    /**
     * Gets the value of the estimatedTimeToComplete property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}
     *     
     */
    public JAXBElement<XMLGregorianCalendar> getEstimatedTimeToComplete() {
        return estimatedTimeToComplete;
    }

    /**
     * Sets the value of the estimatedTimeToComplete property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}
     *     
     */
    public void setEstimatedTimeToComplete(JAXBElement<XMLGregorianCalendar> value) {
        this.estimatedTimeToComplete = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the progress property.
     * 
     */
    public int getProgress() {
        return progress;
    }

    /**
     * Sets the value of the progress property.
     * 
     */
    public void setProgress(int value) {
        this.progress = value;
    }

    /**
     * Gets the value of the progressUpdateAvailable property.
     * 
     */
    public boolean isProgressUpdateAvailable() {
        return progressUpdateAvailable;
    }

    /**
     * Sets the value of the progressUpdateAvailable property.
     * 
     */
    public void setProgressUpdateAvailable(boolean value) {
        this.progressUpdateAvailable = value;
    }

    /**
     * Gets the value of the result property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Object }{@code >}
     *     
     */
    public JAXBElement<Object> getResult() {
        return result;
    }

    /**
     * Sets the value of the result property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Object }{@code >}
     *     
     */
    public void setResult(JAXBElement<Object> value) {
        this.result = value;
    }

    /**
     * Gets the value of the startTime property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getStartTime() {
        return startTime;
    }

    /**
     * Sets the value of the startTime property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setStartTime(XMLGregorianCalendar value) {
        this.startTime = value;
    }

    /**
     * Gets the value of the taskId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Sets the value of the taskId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTaskId(String value) {
        this.taskId = value;
    }

    /**
     * Gets the value of the taskState property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTaskState() {
        return taskState;
    }

    /**
     * Sets the value of the taskState property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTaskState(String value) {
        this.taskState = value;
    }

}
