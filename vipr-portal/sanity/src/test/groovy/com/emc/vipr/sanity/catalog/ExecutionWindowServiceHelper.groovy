/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import com.emc.storageos.model.BulkIdParam
import com.emc.vipr.client.core.util.ResourceUtils
import com.emc.vipr.model.catalog.ExecutionWindowCreateParam
import com.emc.vipr.model.catalog.ExecutionWindowRestRep
import com.emc.vipr.model.catalog.ExecutionWindowUpdateParam


class ExecutionWindowServiceHelper {

    static List<URI> createdExecutionWindows;

    static createExecutionWindow(URI tenantId) {
        ExecutionWindowCreateParam ewCreate = new ExecutionWindowCreateParam();
        ewCreate.setDayOfMonth(1);
        ewCreate.setDayOfWeek(2);
        ewCreate.setExecutionWindowLength(3);
        ewCreate.setExecutionWindowLengthType("HOURS");
        ewCreate.setExecutionWindowType("DAILY");
        ewCreate.setHourOfDayInUTC(4);
        ewCreate.setLastDayOfMonth(false);
        ewCreate.setMinuteOfHourInUTC(5);
        ewCreate.setTenant(tenantId);
        ewCreate.setName("testing");
        return catalog.executionWindows().create(ewCreate);
    }

    static createAnotherExecutionWindow(URI tenantId) {
        ExecutionWindowCreateParam ewCreate = new ExecutionWindowCreateParam();
        ewCreate.setDayOfMonth(6);
        ewCreate.setDayOfWeek(7);
        ewCreate.setExecutionWindowLength(1);
        ewCreate.setExecutionWindowLengthType("DAYS");
        ewCreate.setExecutionWindowType("WEEKLY");
        ewCreate.setHourOfDayInUTC(9);
        ewCreate.setLastDayOfMonth(true);
        ewCreate.setMinuteOfHourInUTC(10);
        ewCreate.setTenant(tenantId);
        ewCreate.setName("testing1");
        return catalog.executionWindows().create(ewCreate);
    }

    static updateExecutionWindow(URI windowId) {
        ExecutionWindowUpdateParam ewUpdate =
                new ExecutionWindowUpdateParam();
        ewUpdate.setDayOfMonth(1);
        ewUpdate.setDayOfWeek(1);
        ewUpdate.setExecutionWindowLength(1);
        ewUpdate.setExecutionWindowLengthType("DAYS");
        ewUpdate.setExecutionWindowType("WEEKLY");
        ewUpdate.setHourOfDayInUTC(1);
        ewUpdate.setLastDayOfMonth(true);
        ewUpdate.setMinuteOfHourInUTC(1);
        ewUpdate.setName("testUpdate");
        return catalog.executionWindows().update(windowId, ewUpdate);
    }

    static void executionWindowServiceTest() {

        println "  ## Execution Window Test ## "
        createdExecutionWindows = new ArrayList<URI>();

        println "Getting tenantId to create execution window"
        URI tenantId = catalog.getUserTenantId();
        println ""

        println "tenantId: " + tenantId
        println ""

        println "Creating exeuction window"
        ExecutionWindowRestRep createdWindow =
                createExecutionWindow(tenantId);
        createdExecutionWindows.add(createdWindow.getId());
        println ""

        println "createdWindowId: " + createdWindow.getId();
        println ""

        println "Creating another execution window"
        ExecutionWindowRestRep anotherWindow =
                createAnotherExecutionWindow(tenantId);
        createdExecutionWindows.add(anotherWindow.getId());
        println ""

        println "createdWindowId: " + anotherWindow.getId();
        println ""

        assertNotNull(createdWindow);
        assertNotNull(createdWindow.id);
        assertEquals(1, createdWindow.getDayOfMonth());
        assertEquals(2, createdWindow.getDayOfWeek());
        assertEquals(3, createdWindow.getExecutionWindowLength());
        assertEquals("HOURS", createdWindow.getExecutionWindowLengthType());
        assertEquals("DAILY", createdWindow.getExecutionWindowType());
        assertEquals(4, createdWindow.getHourOfDayInUTC());
        assertEquals(Boolean.FALSE, createdWindow.getLastDayOfMonth());
        assertEquals(5, createdWindow.getMinuteOfHourInUTC());
        assertEquals(tenantId, createdWindow.getTenant().getId());
        assertEquals("testing", createdWindow.getName());


        List<URI> windowIds = new ArrayList<URI>();
        windowIds.add(createdWindow.getId());
        windowIds.add(anotherWindow.getId());

        println "Listing bulk resources - execution Windows"
        println ""

        BulkIdParam bulkIds = new BulkIdParam();
        bulkIds.setIds(windowIds);

        List<ExecutionWindowRestRep> windows =
                catalog.executionWindows().getBulkResources(bulkIds);

        assertNotNull(windows);
        assertEquals(2, windows.size());
        assertEquals(Boolean.TRUE, windowIds.contains(windows.get(0).getId()));
        assertEquals(Boolean.TRUE, windowIds.contains(windows.get(1).getId()));

        println "Listing execution windows by tenant"
        println ""

        windows =
                catalog.executionWindows().getByTenant(tenantId);

        List<URI> retrievedWindows = ResourceUtils.ids(windows);

        assertNotNull(windows);
        assertEquals(Boolean.TRUE, windows.size() >= 2);
        assertEquals(Boolean.TRUE, retrievedWindows.contains(windowIds.get(0)));
        assertEquals(Boolean.TRUE, retrievedWindows.contains(windowIds.get(1)));

        println "Getting execution window " + createdWindow.getId();

        ExecutionWindowRestRep retrievedWindow =
                catalog.executionWindows().get(createdWindow.getId());
        println ""

        assertEquals(createdWindow.getId(), retrievedWindow.getId());

        println "Updating executionWindow " + retrievedWindow.getId();
        println ""

        ExecutionWindowRestRep updatedWindow =
                updateExecutionWindow(retrievedWindow.getId());

        assertNotNull(updatedWindow);
        assertEquals(1, updatedWindow.getDayOfMonth());
        assertEquals(1, updatedWindow.getDayOfWeek());
        assertEquals(1, updatedWindow.getExecutionWindowLength());
        assertEquals("DAYS", updatedWindow.getExecutionWindowLengthType());
        assertEquals("WEEKLY", updatedWindow.getExecutionWindowType());
        assertEquals(1, updatedWindow.getHourOfDayInUTC());
        assertEquals(Boolean.TRUE, updatedWindow.getLastDayOfMonth());
        assertEquals(1, updatedWindow.getMinuteOfHourInUTC());
        assertEquals("testUpdate", updatedWindow.getName());

        println "Deleting execution windows";
        println ""

        catalog.executionWindows().deactivate(updatedWindow.getId());
        catalog.executionWindows().deactivate(anotherWindow.getId());

        println "Getting deactivated execution window " + updatedWindow.getId();
        updatedWindow = catalog.executionWindows().get(updatedWindow.getId());
        println ""

        if (updatedWindow != null) {
            assertEquals(true, updatedWindow.getInactive());
        }
    }

    static void executionWindowServiceTearDown() {
        println "  ## Execution Window Test Clean up ## "

        println "Getting created execution windows"
        println ""
        if (createdExecutionWindows != null) {

            createdExecutionWindows.each {
                println "Getting test executionWindows: " + it;
                println ""
                ExecutionWindowRestRep windowToDelete =
                        catalog.executionWindows().get(it);
                if (windowToDelete != null
                && !windowToDelete.getInactive()) {
                    println "Deleting test window: " + it;
                    println ""
                    catalog.executionWindows().deactivate(it);
                }
            }
        }

        println "Cleanup Complete.";
        println ""
    }
}
