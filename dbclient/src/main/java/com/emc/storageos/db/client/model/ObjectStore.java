/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;


@Cf("ObjectStore")
@DbKeyspace(Keyspaces.GLOBAL)
public class ObjectStore extends DataObject {

    // brief description for this CoS
    private String _description;

    private Integer _currentEpoch;

    private Integer _prevEpoch;

    private String _type; // used for licensing

    // number of directories created for this cos
    private Integer _numOfDirectories;

    // max # of hashes used for spreading listing entries
    private Integer _maxHashesForListingPerParent;

    // total # of hashes used
    private Integer _totalNumOfHashes;

    public static enum Type{
        OBJ,
        HDFS,
        OBJ_AND_HDFS
    }

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
        setChanged("description");
    }

    @Name("currentEpoch")
    public Integer getCurrentEpoch() {
        return _currentEpoch;
    }

    public void setCurrentEpoch(Integer currentEpoch) {
        _currentEpoch = currentEpoch;
        setChanged("currentEpoch");
    }

    @Name("prevEpoch")
    public Integer getPrevEpoch() {
        return _prevEpoch;
    }

    public void setPrevEpoch(Integer prevEpoch) {
        _prevEpoch = prevEpoch;
        setChanged("prevEpoch");
    }

    @Name("type")
    public String getType(){
       return _type == null ? Type.OBJ.name() : _type;
    }

    public void setType(String type){
        if(type == null){
            return;
        }
        _type = type;
        setChanged("type");
    }

    public void setType(Type type){
        setType(type.toString());
    }

    @Name("numOfDirectories")
    public Integer getNumOfDirectories(){
        return _numOfDirectories;
    }

    public void setNumOfDirectories(Integer numOfDirectories){
        _numOfDirectories = numOfDirectories;
        setChanged("numOfDirectories");
    }

    @Name("maxHashesForListingPerParent")
    public Integer getMaxHashesForListingPerParent(){
        return _maxHashesForListingPerParent;
    }

    public void setMaxHashesForListingPerParent(Integer maxHashesForListingPerParent){
        _maxHashesForListingPerParent = maxHashesForListingPerParent;
        setChanged("maxHashesForListingPerParent");
    }

    @Name("totalNumOfHashes")
    public Integer getTotalNumOfHashes(){
        return _totalNumOfHashes;
    }

    public void setTotalNumOfHashes(Integer totalNumOfHashes){
        _totalNumOfHashes = totalNumOfHashes;
        setChanged("totalNumOfHashes");
    }

}
