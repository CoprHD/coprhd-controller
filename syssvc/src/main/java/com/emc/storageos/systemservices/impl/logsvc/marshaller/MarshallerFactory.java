/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.marshaller;

import java.io.OutputStream;

import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class MarshallerFactory {
	private static final Logger _log = LoggerFactory
			.getLogger(MarshallerFactory.class);

	private MarshallerFactory() {
		throw new AssertionError();
	}

	public static Marshaller getLogMarshaller(MediaType type, OutputStream outputStream) {
		if (MediaType.APPLICATION_JSON_TYPE.equals(type)) {
			try {
				return new JSONMarshaller(outputStream);
			} catch (Exception e) {
				_log.warn(
						"Cannot create writer instance for JSON media type. So changing to XML. {}",
						e);
				type = MediaType.APPLICATION_XML_TYPE;
			}
		}
		if (MediaType.APPLICATION_XML_TYPE.equals(type)) {
			return new XMLMarshaller(outputStream);
		} else if (MediaType.TEXT_PLAIN_TYPE.equals(type)) {
			return new TextMarshaller(outputStream);
		} else {
			throw APIException.badRequests
					.theMediaTypeHasNoMarshallerDefined(type.toString());
		}
	}

}
