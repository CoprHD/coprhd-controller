/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.plugins.metering.vnxfile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.Query;
import com.emc.nas.vnxfile.xmlapi.QueryEx;
import com.emc.nas.vnxfile.xmlapi.QueryStats;
import com.emc.nas.vnxfile.xmlapi.Request;
import com.emc.nas.vnxfile.xmlapi.RequestEx;
import com.emc.nas.vnxfile.xmlapi.RequestPacket;
import com.emc.nas.vnxfile.xmlapi.Task;
import com.emc.nas.vnxfile.xmlapi.APIVersion;


public class VNXFileInputRequestBuilder {
    
    private static final Logger _logger = LoggerFactory
            .getLogger(VNXFileInputRequestBuilder.class);

	private Marshaller _marshaller = null;

	/**
	 * Marshal the RequestPacket java object for the given QueryStats
	 * argument. This will be used when client wants to use QueryStats to retrieve information.
	 * @param stats : QueryStats
	 * @return : Stream of Input XML generated.
	 * @throws JAXBException
	 */
	public InputStream getSingleQueryStatsPacket(QueryStats stats)
            throws JAXBException {
        InputStream inputStream = null;
        List<Request> requests = new ArrayList<Request>(1);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RequestPacket requestPacket = new RequestPacket();
        try {
            Request request = new Request();
            request.setQueryStats(stats);
            requests.add(request);
            requestPacket.getRequestOrRequestEx().addAll(requests);
            _marshaller.marshal(requestPacket, outputStream);
            inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return inputStream;
    }

	/**
	 * This method responsible to generate a multiple request packets
	 * for the given list of QueryStats.
	 * @param stats : list of QueryStats.
	 * @return      : stream
	 */
	public InputStream getMultiRequestQueryStatsPacket(List<QueryStats> stats)
            throws JAXBException {
        InputStream inputStream = null;
        List<Request> requests = new ArrayList<Request>(stats.size());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RequestPacket requestPacket = new RequestPacket();
        try {
            // Create a new Request object for each QueryStat
            for (QueryStats querStat : stats) {
                Request request = new Request();
                request.setQueryStats(querStat);
                requests.add(request);
            }
            requestPacket.getRequestOrRequestEx().addAll(requests);
            _marshaller.marshal(requestPacket, outputStream);
            inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                _logger.error("IOException occurred while querying due to ", e);
            }
        }
        return inputStream;
    }

	/**
	 * Marshal the generated XML for a given QueryParam.
	 * @param queryParam : queryParam object.
	 * @return
	 */
	public InputStream getQueryParamPacket(Object queryParam, boolean is_1_2_VerionToSet)
            throws JAXBException {
        InputStream inputStream = null;
        List<Request> requests = new ArrayList<Request>(1);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RequestPacket requestPacket = new RequestPacket();
        try {
            Query query = new Query();
            query.getQueryRequestChoice().add(queryParam);
            Request request = new Request();
            request.setQuery(query);
            requests.add(request);
            _logger.info("API Version to set {}", is_1_2_VerionToSet);
            if(is_1_2_VerionToSet){
                _logger.info("Setting the API Version on Request Packet");
                APIVersion apiVer = APIVersion.V_1_2;
                requestPacket.setApiVersion(apiVer);
            }
            requestPacket.getRequestOrRequestEx().addAll(requests);
            _marshaller.marshal(requestPacket, outputStream);
            inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                _logger.error("Exception occurred while closing the stream due to ", e);
            }
        }
        return inputStream;
    }

    public InputStream getQueryExParamPacket( Object queryParam) throws JAXBException {
		InputStream inputStream = null;
        List<RequestEx> requests = new ArrayList<RequestEx>(1);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RequestPacket requestPacket = new RequestPacket();
        try {
            QueryEx query = new QueryEx();
            query.getQueryRequestChoiceEx().add(queryParam);
            RequestEx request = new RequestEx();
            request.setQuery(query);
            requests.add(request);
            APIVersion apiVer = APIVersion.V_1_1;
            requestPacket.setApiVersion(apiVer);
            requestPacket.getRequestOrRequestEx().addAll(requests);
            _marshaller.marshal(requestPacket, outputStream);
            inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                _logger.error("Exception occurred while closing the stream due to ", e);
            }
        }
        return inputStream;
    }

    public InputStream getTaskParamPacket( Object taskParam ) throws JAXBException {
        return getTaskParamPacket(taskParam, false);
    }
    
    public InputStream getTaskParamPacket( Object taskParam, boolean setApiVersion ) throws JAXBException {
        InputStream inputStream = null;
        List<Request> requests = new ArrayList<Request>(1);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RequestPacket reqPacket = new RequestPacket();

        try {
            Task task = (Task)taskParam;
            task.setTimeout(3000L);

            Request request = new Request();
            request.setStartTask(task);
            requests.add(request);
            
            if (setApiVersion) {
                APIVersion apiVer = APIVersion.V_1_1;
                reqPacket.setApiVersion(apiVer);
            }
            reqPacket.getRequestOrRequestEx().addAll(requests);
            _marshaller.marshal(reqPacket, outputStream);
            inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                _logger.error("Exception occurred while closing the stream due to ", e);
            }
        }

        return inputStream;
    }

	/**
	 * Set the marshaller instance.
	 * @param marshaller
	 */
	public void setMarshaller(Marshaller marshaller) {
		_marshaller = marshaller;
	}
}
