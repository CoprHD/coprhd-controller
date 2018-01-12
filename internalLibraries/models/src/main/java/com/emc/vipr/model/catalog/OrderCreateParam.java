/*
 * Copyright 2015-2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.vipr.model.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "order_create")
public class OrderCreateParam extends OrderCommonParam {

	private URI tenantId;

    private URI scheduledEventId;

    private String scheduledTime;

    private URI executionWindow;
    
    private String workflowDocument;

    // order specific schedule info - for snapshot/fullcopy orders, it indicates "max number of retained copies"
    private String additionalScheduleInfo;
    
    @XmlElement(name = "tenantId")
    public URI getTenantId() {
        return tenantId;
    }

    public void setTenantId(URI tenantId) {
        this.tenantId = tenantId;
    }

    @XmlElement(name = "scheduledEventId")
    public URI getScheduledEventId() {
        return scheduledEventId;
    }

    public void setScheduledEventId(URI scheduledEventId) {
        this.scheduledEventId = scheduledEventId;
    }


    @XmlElement(name = "scheduledTime")
    public String getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    @XmlElement(name = "executionWindow")
    public URI getExecutionWindow() {
        return executionWindow;
    }

    public void setExecutionWindow(URI executionWindow) {
        this.executionWindow = executionWindow;
    }

	@XmlElement(name = "additionalScheduleInfo")
    public String getAdditionalScheduleInfo() {
        return additionalScheduleInfo;
    }

    public void setAdditionalScheduleInfo(String additionalScheduleInfo) {
        this.additionalScheduleInfo = additionalScheduleInfo;
    }
    
    @XmlElement(name = "workflowDocument")
    public String getWorkflowDocument() {
        return workflowDocument;
    }
    
    public void setWorkflowDocument(final String workflowDocument) {
        this.workflowDocument = workflowDocument;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        try {
            out.writeObject(this);
        } finally {
            out.close();
        }
        return bos.toByteArray();
    }
    public static OrderCreateParam deserialize(byte[] data) throws IOException,
            ClassNotFoundException {
        Object obj = null;
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        try {
            obj = in.readObject();
        } finally {
            in.close();
        }
        return (OrderCreateParam) obj;
    }

}
