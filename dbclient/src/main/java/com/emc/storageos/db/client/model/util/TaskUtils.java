/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.util;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Task;
import com.google.common.collect.Lists;

public class TaskUtils {

    public static Task findTaskForRequestId(DbClient dbClient, URI resourceId, String requestId) {
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getTasksByRequestIdConstraint(requestId), results);
        Iterator<URI> it = results.iterator();
        while (it.hasNext()) {
            Task task = dbClient.queryObject(Task.class, it.next());
            if (task.getResource().getURI().equals(resourceId)) {
                return task;
            }
        }

        return null;
    }

    public static Task findTaskForRequestIdAssociatedResource(DbClient dbClient, URI resourceId, String requestId) {
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getTasksByRequestIdConstraint(requestId), results);
        Iterator<URI> it = results.iterator();
        while (it.hasNext()) {
            Task task = dbClient.queryObject(Task.class, it.next());
            if (task.getAssociatedResourcesList().contains(resourceId)) {
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
        while (it.hasNext()) {
            Task task = dbClient.queryObject(Task.class, it.next());
            tasks.add(task);
        }

        return tasks;
    }
    
    public static void setWorkflowForRequestTasks(DbClient dbClient, String requestId, URI workflowURI) {
        List<Task> tasks = findTasksForRequestId(dbClient, requestId);
        for (Task task : tasks) {
            task.setWorkflow(workflowURI);
        }
        dbClient.updateObject(tasks);
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
            uris.add(new NamedURI(element.getId(), element.getName()));
        }

        return uris;
    }

    /**
     * This method will find all tasks associated with a tenant.
     * NOTE: This method does NOT work well to scale if a single tenant is performing many
     * thousands of operations. Consider other constraint criteria depending on your needs.
     * 
     * @param dbClient
     *            db client
     * @param tenantId
     *            tenant URI
     * @return list of Task objects associated with that tenant
     */
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

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            return (T) dbClient.queryObject(iterator.next());
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
