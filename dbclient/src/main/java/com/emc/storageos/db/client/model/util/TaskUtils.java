/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.util;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.*;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Task;
import com.google.common.collect.Lists;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

public class TaskUtils {

    public static Task findTaskForRequestId(DbClient dbClient, URI resourceId, String requestId) {
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getTasksByRequestIdConstraint(requestId), results);

        Iterator<URI> it = results.iterator();
        while(it.hasNext()) {
            Task task = dbClient.queryObject(Task.class, it.next());
            if (task.getResource().getURI().equals(resourceId)) {
                return task;
            }
        }

        return null;
    }

    public static List<Task> findTasksForRequestId(DbClient dbClient, String requestId) {
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getTasksByRequestIdConstraint(requestId), results);

        List<Task> tasks = Lists.newArrayList();
        Iterator<URI> it = results.iterator();
        while(it.hasNext()) {
            Task task = dbClient.queryObject(Task.class, it.next());
            tasks.add(task);
        }

        return tasks;
    }

    public static List<Task> findResourceTasks(DbClient dbClient, URI resourceId) {
        return getTasks(dbClient, ContainmentConstraint.Factory.getResourceTaskConstraint(resourceId));
    }

    public static List<NamedURI> findResourceTaskIds(DbClient dbClient, URI resourceId) {
        NamedElementQueryResultList results = new NamedElementQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getResourceTaskConstraint(resourceId), results);

        List<NamedURI> uris = Lists.newArrayList();

        Iterator<NamedElementQueryResultList.NamedElement> it = results.iterator();
        while (it.hasNext()) {
            NamedElementQueryResultList.NamedElement element = it.next();
            uris.add(new NamedURI(element.id, element.name));
        }

        return uris;
    }

    public static ObjectQueryResult<Task> findTenantTasks(DbClient dbClient, URI tenantId) {
        ContainmentConstraint constraint = ContainmentConstraint.Factory.getTenantOrgTaskConstraint(tenantId);
        ObjectQueryResult<Task> queryResult = new ObjectQueryResult(dbClient, constraint);
        queryResult.executeQuery();

        return queryResult;
    }

    public static Operation createOperation(Task task) {
        Operation op = new Operation();
        // Operation is backed by Hashtable. Need to check for null values since Hashtable does not allow null values.
        if (task.getLabel() != null) {
            op.setName(task.getLabel());
        }
        if (task.getDescription() != null) {
            op.setDescription(task.getDescription());
        }
        if (task.getMessage() != null) {
            op.setMessage(task.getMessage());
        }
        if (task.getServiceCode() != null) {
            op.setServiceCode(task.getServiceCode());
        }
        if (task.getAssociatedResources() != null) {
            op.setAssociatedResourcesField(task.getAssociatedResources());
        }
        if (task.getProgress() != null) {
            op.setProgress(task.getProgress());
        }
        if (task.getStartTime() != null) {
            op.setStartTime(task.getStartTime());
        }
        if (task.getEndTime() != null) {
            op.setEndTime(task.getEndTime());
        }
        if (task.getStatus() != null) {
            op.setStatus(task.getStatus());
        }
        return op;
    }


    private static List<Task> getTasks(DbClient dbClient, Constraint constraint) {
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(constraint, results);

        List<Task> tasks = Lists.newArrayList();
        Iterator<URI> it = results.iterator();
        while (it.hasNext()) {
            Task task = dbClient.queryObject(Task.class, it.next());
            if (task != null) {
                tasks.add(task);
            }
        }

        return tasks;
    }

    /** An efficient way of querying for Task objects based on the result of an index query */
    public static class ObjectQueryResult<T extends DataObject> implements Iterator<T> {
        private final ContainmentConstraint constraint;
        private final DbClient dbClient;
        private Iterator<URI> iterator;

        public ObjectQueryResult(DbClient dbClient, ContainmentConstraint constraint) {
            this.dbClient = dbClient;
            this.constraint = constraint;
        }

        public void executeQuery() {
            if (iterator != null) {
                throw new IllegalStateException("Execute can only be called once!");
            }

            URIQueryResultList results = new URIQueryResultList();
            dbClient.queryByConstraint(constraint, results);
            this.iterator = results.iterator();
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public T next() {
            return (T)dbClient.queryObject(iterator.next());
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
