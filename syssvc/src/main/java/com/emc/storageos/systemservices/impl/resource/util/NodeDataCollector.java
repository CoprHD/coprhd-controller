/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.resource.util;

import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/**
 * Class that calls an URI on all nodes asynchronously.
 */
public class NodeDataCollector {
    private static final Logger _log = LoggerFactory.getLogger(NodeDataCollector.class);
    private static final int FIXED_THREAD_POOL_SIZE = 256;
    private static ExecutorService _executorService = Executors.newFixedThreadPool
            (FIXED_THREAD_POOL_SIZE);
    private static final int MAX_TASK_WAIT_MIN = 10;

    public static enum Action {
        POST,
        GET;
    }

    private static class NodeAPICaller<T> implements Callable<T> {

        private NodeInfo _nodeInfo;
        private URI _callURI;
        private Action _action;
        private Object _requestObj;
        private Class<T> _returnType;
        private String _acceptType;

        public NodeAPICaller(NodeInfo nodeInfo, URI callURI,
                             Action action, Object requestObj,
                             Class<T> returnType, MediaType acceptType) {
            _nodeInfo = nodeInfo;
            _callURI = callURI;
            _action = action;
            _requestObj = requestObj;
            _returnType = returnType;
            _acceptType = acceptType!=null?acceptType.getType():null;
        }

        public NodeInfo getNodeInfo() {
            return _nodeInfo;
        }

        @Override
        public T call() throws SysClientException {
            String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT,
                    _nodeInfo.getIpAddress(), _nodeInfo.getPort());
            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(URI
                    .create(baseNodeURL));
            switch (_action) {
                case POST:
                    return sysClient.post(_callURI, _returnType, _requestObj);
                case GET:
                    return sysClient.get(_callURI, _returnType, _acceptType);
                default:
                    _log.error("Action not supported: {}", _action);
                    throw SyssvcException.syssvcExceptions.sysClientError("Action not supported: "+_action);
            }
        }
    }

    /**
     * Calls the passed URI on the list of nodes asynchronously by creating a thread for
     * each call and returns the results as a map with node id as key.
     * If node is not reachable or we have exceeded the MAX_TASK_WAIT_MIN time the return map
     * will not contain the corresponding node results.
     *
     * @param nodeInfoList List of nodes on which the URI is called
     * @param callURI URI to call on the nodes
     * @param action URI action - GET, POST
     * @param requestObj request object to send with call. Null if nothing is required.
     * @param returnType class of the returned object
     * @param acceptType MediaType for the response. By default it is JSON.
     * @return Returns a Map<NodeId, returnObj>
     */
    public static <T> Map<String, T> getDataFromNodes(List<NodeInfo> nodeInfoList,
                                               String callURI, Action action,
                                               Object requestObj, Class<T> returnType,
                                               MediaType acceptType) {
        _log.info("Collecting data from URI {}", callURI);
        Map<String, Future<T>> futures = new HashMap<String, Future<T>>();
        // Submit all tasks, collect future objects by node
        for (NodeInfo node : nodeInfoList) {
            NodeAPICaller<T> task = new NodeAPICaller<T>(node, URI.create(callURI),
                    action, requestObj, returnType, acceptType);
            futures.put(task.getNodeInfo().getId(), _executorService.submit(task));
        }

        // Get results from future objects
        Map<String, T> nodeDataMap = new HashMap<String, T>();
        for (Map.Entry<String, Future<T>> entry: futures.entrySet()) {
            Future<T> future = entry.getValue();
            try{
                nodeDataMap.put(entry.getKey(), future.get(MAX_TASK_WAIT_MIN, TimeUnit.MINUTES));
            }
            catch (Exception e){
                if(future != null && !future.isDone()){
                    _log.error("Error occurred while getting data from URI {} on node {}: " +
                            e, callURI, entry.getKey());
                    future.cancel(false);
                }
            }
        }
        _log.info("Done collecting data for URI {}", callURI);
        return nodeDataMap;
    }
}
