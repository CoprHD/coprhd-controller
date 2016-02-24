package com.emc.storageos.ceph;

import java.util.List;

import com.emc.storageos.ceph.model.ClusterInfo;
import com.emc.storageos.ceph.model.PoolInfo;
import com.emc.storageos.ceph.model.SnapInfo;

public interface CephClient {

    public ClusterInfo getClusterInfo() throws CephException;
    public List<PoolInfo> getPools() throws CephException;
    public void createImage(String pool, String name, long size) throws CephException;
    public void deleteImage(String pool, String name) throws CephException;
    public void resizeImage(String pool, String name, long size) throws CephException;
    public void flattenImage(String pool, String image) throws CephException;
    public void createSnap(String pool, String imageName, String snapName) throws CephException;
    public void deleteSnap(String pool, String imageName, String snapName) throws CephException;
    public List<SnapInfo> getSnapshots(String pool, String image) throws CephException;
    public void cloneSnap(String pool, String parentImage, String parentSnap, String childName) throws CephException;
    public void protectSnap(String pool, String parentImage, String snapName) throws CephException;
    public void unprotectSnap(String pool, String parentImage, String snapName) throws CephException;
    public boolean snapIsProtected(String pool, String parentImage, String snapName) throws CephException;
    public List<String> getChildren(String pool, String parentImage, String snapName) throws CephException;
}
