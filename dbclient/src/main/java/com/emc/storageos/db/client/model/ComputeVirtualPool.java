/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.model.valid.EnumType;

/**
 * Pool of compute elements.
 * 
 * @author dranov
 */
@Cf("ComputeVirtualPool")
public class ComputeVirtualPool extends DataObjectWithACLs implements GeoVisibleResource {

    // Brief description for this ComputeVirtualPool
    private String _description;
    // Reference to hold the ComputeElements recommended for this pool
    private StringSet _matchedComputeElements;
    // System Type
    private String _systemType;

    // VirtualArrays where this CoS is available
    private StringSet _virtualArrays;

    // flag tells whether to use recommended elements or not.
    private Boolean _useMatchedElements;

    // Limits for number of CPUs
    private Integer _minProcessors;
    private Integer _maxProcessors;

    // Limits for the total number of cores on a ComputeElement
    private Integer _minTotalCores;
    private Integer _maxTotalCores;

    // Limits for hyper-threaded core systems. For systems without hyper-threading,
    // the number of threads will equal the number of cores.
    private Integer _minTotalThreads;
    private Integer _maxTotalThreads;

    // Limits for CPU Speed in MHz
    private Integer _minCpuSpeed;
    private Integer _maxCpuSpeed;

    // Limits for RAM in GB
    private Integer _minMemory;
    private Integer _maxMemory;

    // Limits for number of network interfaces on a ComputeElement
    private Integer _minNics;
    private Integer _maxNics;

    // Limits for Host bus adapters on a ComputeElement including optional adapter cards
    private Integer _minHbas;
    private Integer _maxHbas;

    // For System type of Cisco_UCSM, these are the related Service Profile Templates
    private StringSet _serviceProfileTemplates;

    public static enum SupportedSystemTypes {
        Cisco_UCSM, Cisco_CSeries, Generic;
        private static final SupportedSystemTypes[] copyOfValues = values();

        public static SupportedSystemTypes lookup(final String name) {
            for (SupportedSystemTypes value : copyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(final String description) {
        _description = description;
        setChanged("description");
    }

    @Name("matchedComputeElements")
    @RelationIndex(cf = "MatchedComputeElementsToVCpool", type = ComputeElement.class)
    @IndexByKey
    public StringSet getMatchedComputeElements() {
        return _matchedComputeElements;
    }

    public void setMatchedComputeElements(final StringSet matchedComputeElements) {
        _matchedComputeElements = matchedComputeElements;
        setChanged("matchedComputeElements");
    }

    /**
     * Add all passed matched Compute Element URsI to ComputeVirtualPool.
     * 
     * @param matchedComputeElements
     */
    public void addMatchedComputeElements(final StringSet matchedComputeElements) {
        if (null != _matchedComputeElements) {
            _matchedComputeElements.replace(matchedComputeElements);
        } else {
            if (null != matchedComputeElements && !matchedComputeElements.isEmpty()) {
                setMatchedComputeElements(new StringSet());
                _matchedComputeElements.addAll(matchedComputeElements);
            }
        }
    }

    @EnumType(SupportedSystemTypes.class)
    @Name("systemType")
    public String getSystemType() {
        return _systemType;
    }

    public void setSystemType(String systemType) {
        _systemType = systemType;
        setChanged("systemType");
    }

    @Name("minProcessors")
    public Integer getMinProcessors() {
        return _minProcessors;
    }

    public void setMinProcessors(final Integer minProcessors) {
        _minProcessors = minProcessors;
        setChanged("minProcessors");
    }

    @Name("maxProcessors")
    public Integer getMaxProcessors() {
        return _maxProcessors;
    }

    public void setMaxProcessors(final Integer maxProcessors) {
        _maxProcessors = maxProcessors;
        setChanged("maxProcessors");
    }

    @Name("minTotalCores")
    public Integer getMinTotalCores() {
        return _minTotalCores;
    }

    public void setMinTotalCores(final Integer minTotalCores) {
        _minTotalCores = minTotalCores;
        setChanged("minTotalCores");
    }

    @Name("maxTotalCores")
    public Integer getMaxTotalCores() {
        return _maxTotalCores;
    }

    public void setMaxTotalCores(final Integer maxTotalCores) {
        _maxTotalCores = maxTotalCores;
        setChanged("maxTotalCores");
    }

    @Name("minTotalThreads")
    public Integer getMinTotalThreads() {
        return _minTotalThreads;
    }

    public void setMinTotalThreads(final Integer minTotalThreads) {
        _minTotalThreads = minTotalThreads;
        setChanged("minTotalThreads");
    }

    @Name("maxTotalThreads")
    public Integer getMaxTotalThreads() {
        return _maxTotalThreads;
    }

    public void setMaxTotalThreads(final Integer maxTotalThreads) {
        _maxTotalThreads = maxTotalThreads;
        setChanged("maxTotalThreads");
    }

    @Name("minCpuSpeed")
    public Integer getMinCpuSpeed() {
        return _minCpuSpeed;
    }

    public void setMinCpuSpeed(final Integer minCpuSpeed) {
        _minCpuSpeed = minCpuSpeed;
        setChanged("minCpuSpeed");
    }

    @Name("maxCpuSpeed")
    public Integer getMaxCpuSpeed() {
        return _maxCpuSpeed;
    }

    public void setMaxCpuSpeed(final Integer maxCpuSpeed) {
        _maxCpuSpeed = maxCpuSpeed;
        setChanged("maxCpuSpeed");
    }

    @Name("minMemory")
    public Integer getMinMemory() {
        return _minMemory;
    }

    public void setMinMemory(final Integer minMemory) {
        _minMemory = minMemory;
        setChanged("minMemory");
    }

    @Name("maxMemory")
    public Integer getMaxMemory() {
        return _maxMemory;
    }

    public void setMaxMemory(final Integer maxMemory) {
        _maxMemory = maxMemory;
        setChanged("maxMemory");
    }

    @Name("minNics")
    public Integer getMinNics() {
        return _minNics;
    }

    public void setMinNics(final Integer minNics) {
        _minNics = minNics;
        setChanged("minNics");
    }

    @Name("maxNics")
    public Integer getMaxNics() {
        return _maxNics;
    }

    public void setMaxNics(final Integer maxNics) {
        _maxNics = maxNics;
        setChanged("maxNics");
    }

    @Name("minHbas")
    public Integer getMinHbas() {
        return _minHbas;
    }

    public void setMinHbas(final Integer minHbas) {
        _minHbas = minHbas;
        setChanged("minHbas");
    }

    @Name("maxHbas")
    public Integer getMaxHbas() {
        return _maxHbas;
    }

    public void setMaxHbas(final Integer maxHbas) {
        _maxHbas = maxHbas;
        setChanged("maxHbas");
    }

    @RelationIndex(cf = "ComputeRelationIndex", type = VirtualArray.class)
    @IndexByKey
    @Name("virtualArrays")
    public StringSet getVirtualArrays() {
        return _virtualArrays;
    }

    public void setVirtualArrays(final StringSet virtualArrays) {
        _virtualArrays = virtualArrays;
        setChanged("virtualArrays");
    }

    public void addVirtualArrays(final Set<String> vArrayURIs) {
        if (vArrayURIs != null && !vArrayURIs.isEmpty()) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // addAll method is invoked, else base class method
            // is invoked.
            HashSet<String> addVarrays = new HashSet<String>();
            addVarrays.addAll(vArrayURIs);
            if (_virtualArrays == null) {
                setVirtualArrays(new StringSet());
                _virtualArrays.addAll(addVarrays);
            } else {
                _virtualArrays.addAll(addVarrays);
            }
        }
    }

    public void removeVirtualArrays(final Set<String> varrayURIs) {
        if (varrayURIs != null && !varrayURIs.isEmpty() && _virtualArrays != null) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // removeAll method is invoked, else base class method
            // is invoked.
            HashSet<String> removeVarrays = new HashSet<String>();
            removeVarrays.addAll(varrayURIs);
            _virtualArrays.removeAll(removeVarrays);
        }
    }

    @Name("useMatchedElements")
    public Boolean getUseMatchedElements() {
        return _useMatchedElements;
    }

    public void setUseMatchedElements(final Boolean useMatchedElements) {
        _useMatchedElements = useMatchedElements;
        setChanged("useMatchedElements");
    }

    @RelationIndex(cf = "ComputeRelationIndex", type = UCSServiceProfileTemplate.class)
    @IndexByKey
    @Name("serviceProfileTemplates")
    public StringSet getServiceProfileTemplates() {
        return _serviceProfileTemplates;
    }

    public void setServiceProfileTemplates(final StringSet serviceProfileTemplates) {
        _serviceProfileTemplates = serviceProfileTemplates;
        setChanged("serviceProfileTemplates");
    }

    public void addServiceProfileTemplates(final Set<String> sptURIs) {
        if (sptURIs != null && !sptURIs.isEmpty()) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // addAll method is invoked, else base class method
            // is invoked.
            HashSet<String> addSpts = new HashSet<String>();
            addSpts.addAll(sptURIs);
            if (_serviceProfileTemplates == null) {
                setServiceProfileTemplates(new StringSet());
                _serviceProfileTemplates.addAll(addSpts);
            } else {
                _serviceProfileTemplates.addAll(addSpts);
            }
        }
    }

    public void removeServiceProfileTemplates(final Set<String> sptURIs) {
        if (sptURIs != null && !sptURIs.isEmpty() && _serviceProfileTemplates != null) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // removeAll method is invoked, else base class method
            // is invoked.
            HashSet<String> removeSpts = new HashSet<String>();
            removeSpts.addAll(sptURIs);
            _serviceProfileTemplates.removeAll(removeSpts);
            setChanged("serviceProfileTemplates");
        }
    }

    public void removeServiceProfileTemplate(String sptURI) {
        if (sptURI != null && _serviceProfileTemplates != null) {
            boolean removed = _serviceProfileTemplates.remove(sptURI);
            if (removed) {
                setChanged("serviceProfileTemplates");
            }
        }
    }
    
    public void removeMatchedComputeElement(String ceURI) {
        if (ceURI != null && _matchedComputeElements != null) {
            boolean removed = _matchedComputeElements.remove(ceURI);
            if (removed) {
                setChanged("matchedComputeElements");
            }
        }
    }

}
