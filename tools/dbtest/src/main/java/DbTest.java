/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.net.URI;

public class DbTest {

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    private DbClient dbClient;

    static URI projectId = URI.create("Project1");
    static URI tenantId = URI.create("Tenant1");

    public void test1() throws Exception {
        Event event = genEvent();
        String bucketId = dbClient.insertTimeSeries(EventTimeSeries.class, event);
        System.out.println("insert ok " + bucketId);
    }

    public Event genEvent() throws Exception {

        Event event = new Event();

        URI objId = URI.create("Volume:" + System.nanoTime());

        event.setEventType(RecordableEventManager.EventType.VolumeCreated.name());
        event.setService("block");
        event.setResourceId(objId);
        event.setNativeGuid(objId.toString());
        event.setVirtualPool(URI.create("VirtualPool1"));
        event.setEventId(RecordableBourneEvent.getUniqueEventId());
        event.setTimeInMillis(System.currentTimeMillis());
        event.setTenantId(tenantId);
        event.setProjectId(projectId);
        event.setUserId(URI.create("root"));
        event.setDescription("");
        event.setExtensions("");
        event.setAlertType("");
        event.setRecordType("Event");
        event.setSeverity("");
        event.setOperationalStatusCodes("");
        event.setOperationalStatusDescriptions("");

        return event;
    }

    public static void main(String[] args) throws Exception {

        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/dbtest-conf.xml");
        DbTest dbTest = (DbTest) ctx.getBean("dbtest");

        dbTest.test1();
    }
}
