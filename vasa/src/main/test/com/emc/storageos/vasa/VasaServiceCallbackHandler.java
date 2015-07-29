/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 * VasaServiceCallbackHandler.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */

package com.emc.storageos.vasa;

/**
 * VasaServiceCallbackHandler Callback class, Users can extend this class and implement
 * their own receiveResult and receiveError methods.
 */
public abstract class VasaServiceCallbackHandler {

    protected Object clientData;

    /**
     * User can pass in any object that needs to be accessed once the NonBlocking
     * Web service call is finished and appropriate method of this CallBack is called.
     * 
     * @param clientData Object mechanism by which the user can pass in user data
     *            that will be avilable at the time this callback is called.
     */
    public VasaServiceCallbackHandler(Object clientData) {
        this.clientData = clientData;
    }

    /**
     * Please use this constructor if you don't want to set any clientData
     */
    public VasaServiceCallbackHandler() {
        this.clientData = null;
    }

    /**
     * Get the client data
     */

    public Object getClientData() {
        return clientData;
    }

    /**
     * auto generated Axis2 call back method for queryStorageCapabilities method
     * override this method for handling normal response from queryStorageCapabilities operation
     */
    public void receiveResultqueryStorageCapabilities(
            com.emc.storageos.vasa.VasaServiceStub.QueryStorageCapabilitiesResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryStorageCapabilities operation
     */
    public void receiveErrorqueryStorageCapabilities(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for getNumberOfEntities method
     * override this method for handling normal response from getNumberOfEntities operation
     */
    public void receiveResultgetNumberOfEntities(
            com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntitiesResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from getNumberOfEntities operation
     */
    public void receiveErrorgetNumberOfEntities(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for getAlarms method
     * override this method for handling normal response from getAlarms operation
     */
    public void receiveResultgetAlarms(
            com.emc.storageos.vasa.VasaServiceStub.GetAlarmsResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from getAlarms operation
     */
    public void receiveErrorgetAlarms(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryUniqueIdentifiersForFileSystems method
     * override this method for handling normal response from queryUniqueIdentifiersForFileSystems operation
     */
    public void receiveResultqueryUniqueIdentifiersForFileSystems(
            com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForFileSystemsResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryUniqueIdentifiersForFileSystems operation
     */
    public void receiveErrorqueryUniqueIdentifiersForFileSystems(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryAssociatedProcessorsForArray method
     * override this method for handling normal response from queryAssociatedProcessorsForArray operation
     */
    public void receiveResultqueryAssociatedProcessorsForArray(
            com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedProcessorsForArrayResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryAssociatedProcessorsForArray operation
     */
    public void receiveErrorqueryAssociatedProcessorsForArray(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryAssociatedLunsForPort method
     * override this method for handling normal response from queryAssociatedLunsForPort operation
     */
    public void receiveResultqueryAssociatedLunsForPort(
            com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedLunsForPortResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryAssociatedLunsForPort operation
     */
    public void receiveErrorqueryAssociatedLunsForPort(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryStoragePorts method
     * override this method for handling normal response from queryStoragePorts operation
     */
    public void receiveResultqueryStoragePorts(
            com.emc.storageos.vasa.VasaServiceStub.QueryStoragePortsResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryStoragePorts operation
     */
    public void receiveErrorqueryStoragePorts(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for getEvents method
     * override this method for handling normal response from getEvents operation
     */
    public void receiveResultgetEvents(
            com.emc.storageos.vasa.VasaServiceStub.GetEventsResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from getEvents operation
     */
    public void receiveErrorgetEvents(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryAssociatedPortsForProcessor method
     * override this method for handling normal response from queryAssociatedPortsForProcessor operation
     */
    public void receiveResultqueryAssociatedPortsForProcessor(
            com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedPortsForProcessorResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryAssociatedPortsForProcessor operation
     */
    public void receiveErrorqueryAssociatedPortsForProcessor(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for setContext method
     * override this method for handling normal response from setContext operation
     */
    public void receiveResultsetContext(
            com.emc.storageos.vasa.VasaServiceStub.SetContextResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from setContext operation
     */
    public void receiveErrorsetContext(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryUniqueIdentifiersForLuns method
     * override this method for handling normal response from queryUniqueIdentifiersForLuns operation
     */
    public void receiveResultqueryUniqueIdentifiersForLuns(
            com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForLunsResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryUniqueIdentifiersForLuns operation
     */
    public void receiveErrorqueryUniqueIdentifiersForLuns(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryStorageFileSystems method
     * override this method for handling normal response from queryStorageFileSystems operation
     */
    public void receiveResultqueryStorageFileSystems(
            com.emc.storageos.vasa.VasaServiceStub.QueryStorageFileSystemsResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryStorageFileSystems operation
     */
    public void receiveErrorqueryStorageFileSystems(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryAssociatedCapabilityForLun method
     * override this method for handling normal response from queryAssociatedCapabilityForLun operation
     */
    public void receiveResultqueryAssociatedCapabilityForLun(
            com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedCapabilityForLunResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryAssociatedCapabilityForLun operation
     */
    public void receiveErrorqueryAssociatedCapabilityForLun(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryArrays method
     * override this method for handling normal response from queryArrays operation
     */
    public void receiveResultqueryArrays(
            com.emc.storageos.vasa.VasaServiceStub.QueryArraysResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryArrays operation
     */
    public void receiveErrorqueryArrays(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryUniqueIdentifiersForEntity method
     * override this method for handling normal response from queryUniqueIdentifiersForEntity operation
     */
    public void receiveResultqueryUniqueIdentifiersForEntity(
            com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntityResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryUniqueIdentifiersForEntity operation
     */
    public void receiveErrorqueryUniqueIdentifiersForEntity(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for registerVASACertificate method
     * override this method for handling normal response from registerVASACertificate operation
     */
    public void receiveResultregisterVASACertificate(
            com.emc.storageos.vasa.VasaServiceStub.RegisterVASACertificateResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from registerVASACertificate operation
     */
    public void receiveErrorregisterVASACertificate(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryCatalog method
     * override this method for handling normal response from queryCatalog operation
     */
    public void receiveResultqueryCatalog(
            com.emc.storageos.vasa.VasaServiceStub.QueryCatalogResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryCatalog operation
     */
    public void receiveErrorqueryCatalog(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryDRSMigrationCapabilityForPerformance method
     * override this method for handling normal response from queryDRSMigrationCapabilityForPerformance operation
     */
    public void receiveResultqueryDRSMigrationCapabilityForPerformance(
            com.emc.storageos.vasa.VasaServiceStub.QueryDRSMigrationCapabilityForPerformanceResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryDRSMigrationCapabilityForPerformance operation
     */
    public void receiveErrorqueryDRSMigrationCapabilityForPerformance(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryAssociatedCapabilityForFileSystem method
     * override this method for handling normal response from queryAssociatedCapabilityForFileSystem operation
     */
    public void receiveResultqueryAssociatedCapabilityForFileSystem(
            com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedCapabilityForFileSystemResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryAssociatedCapabilityForFileSystem operation
     */
    public void receiveErrorqueryAssociatedCapabilityForFileSystem(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryStorageLuns method
     * override this method for handling normal response from queryStorageLuns operation
     */
    public void receiveResultqueryStorageLuns(
            com.emc.storageos.vasa.VasaServiceStub.QueryStorageLunsResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryStorageLuns operation
     */
    public void receiveErrorqueryStorageLuns(java.lang.Exception e) {
    }

    /**
     * auto generated Axis2 call back method for queryStorageProcessors method
     * override this method for handling normal response from queryStorageProcessors operation
     */
    public void receiveResultqueryStorageProcessors(
            com.emc.storageos.vasa.VasaServiceStub.QueryStorageProcessorsResponse result
            ) {
    }

    /**
     * auto generated Axis2 Error handler
     * override this method for handling error response from queryStorageProcessors operation
     */
    public void receiveErrorqueryStorageProcessors(java.lang.Exception e) {
    }

    // No methods generated for meps other than in-out

}
