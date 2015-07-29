/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import javax.xml.bind.JAXBElement;

public interface ClientHttpMethods {

    public String getServiceURI();

    public <T> T postEntity(JAXBElement<?> jaxbElement, Class<T> returnType) throws ClientGeneralException;

    public void close() throws ClientGeneralException;
}
