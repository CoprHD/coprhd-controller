/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;

/**
 * Generic serialization for an Object
 * @author watson
 *
 */
public class GenericSerializer {
	private static final Logger _log = LoggerFactory.getLogger(GenericSerializer.class);
	private static int MAX_OBJECT_SIZE_IN_BYTES = 250000;
	static public byte[] serialize(Object object) {
		try {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			ObjectOutputStream ostream = new ObjectOutputStream(stream);
			ostream.writeObject(object);
			byte[] byteArray = stream.toByteArray();
			if(byteArray.length > MAX_OBJECT_SIZE_IN_BYTES){
			    _log.error("Byte Array length is "+ byteArray.length + " which is more than default limit "+ MAX_OBJECT_SIZE_IN_BYTES);
			    throw CoordinatorException.fatals.exceedingLimit("byte array size", MAX_OBJECT_SIZE_IN_BYTES);
			}
			return byteArray;
		} catch (Exception ex) {
            throw CoordinatorException.fatals.failedToSerialize(ex);
		}
	}
	static public Object deserialize(byte[] data) {
		try {
			ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(data));
			Object object = stream.readObject();
			return object;
		} catch (Exception ex) {
            throw CoordinatorException.fatals.failedToDeserialize(ex);
		}
	}
}
