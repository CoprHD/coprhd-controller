package com.emc.storageos.ceph;

import java.util.ArrayList;
import java.util.List;

import com.ceph.rados.IoCTX;
import com.ceph.rados.Rados;
import com.ceph.rados.exceptions.ErrorCode;
import com.ceph.rados.exceptions.RadosException;
import com.ceph.rados.exceptions.RadosInvalidArgumentException;
import com.ceph.rados.exceptions.RadosPermissionException;
import com.ceph.rados.jna.RadosClusterInfo;
import com.ceph.rbd.jna.RbdSnapInfo;
import com.ceph.rbd.Rbd;
import com.ceph.rbd.RbdException;
import com.ceph.rbd.RbdImage;

import com.emc.storageos.ceph.model.ClusterInfo;
import com.emc.storageos.ceph.model.PoolInfo;
import com.emc.storageos.ceph.model.SnapInfo;

public class CephNativeClient implements CephClient {

    private static final long LAYERING = 1;
    private Rados rados;

    public CephNativeClient(final String monitorHost, final String userName, final String userKey) {
        try {
            rados = new Rados(userName);
            rados.confSet("mon_host", monitorHost);
            rados.confSet("key", userKey);
            rados.connect();
        } catch (RadosPermissionException | RadosInvalidArgumentException e) {
            throw CephException.exceptions.invalidCredentialsError(e);
        } catch (RadosException e) {
            throw CephException.exceptions.connectionError(e);
        }
    }

    private interface RadosOperationT<T> {
        public abstract T call() throws RadosException, RbdException;
    }

    private interface RbdOperation {
        public abstract void call(Rbd rbd) throws RadosException, RbdException;
    }

    private interface RbdOperationT<T> {
        public abstract T call(Rbd rbd) throws RadosException, RbdException;
    }

    private interface RbdImageOperationT<T> {
        public abstract T call(RbdImage image) throws RadosException, RbdException;
    }

    private interface RbdImageOperation {
        public abstract void call(RbdImage image) throws RadosException, RbdException;
    }

    private static String convertErrorMessage(RbdException e, String errorMsg, final Object... errorMsgArgs) {
        int errorCode = e.getReturnValue();
        String errorName = ErrorCode.getErrorName(errorCode);
        String errorMessage = ErrorCode.getErrorMessage(errorCode);
        return String.format("%s; %s: %s", String.format(errorMsg, errorMsgArgs), errorName, errorMessage);
    }

    private <T> T doCall(final RadosOperationT<T> op, final String errorMsg, final Object... errorMsgArgs) {
        try {
            return op.call();
        } catch (RbdException e) {
            throw CephException.exceptions.operationException(convertErrorMessage(e, errorMsg, errorMsgArgs));
        } catch (RadosException e) {
            throw CephException.exceptions.operationException(e);
        }
    }

    private <T> T doCall(final String pool, final RbdOperationT<T> rbdOp, final String errorMsg, final Object... errorMsgArgs) {
        RadosOperationT<T> op = () -> {
                IoCTX ioCtx = null;
                try {
                    ioCtx = rados.ioCtxCreate(pool);
                    Rbd rbd = new Rbd(ioCtx);
                    return rbdOp.call(rbd);
                } finally {
                    if (ioCtx != null) {
                        rados.ioCtxDestroy(ioCtx);
                        ioCtx = null;
                    }
                }
            };
        return doCall(op, errorMsg, errorMsgArgs);
    }

    private void doCall(final String pool, final RbdOperation rbdOp, final String errorMsg, final Object... errorMsgArgs) {
        RbdOperationT<Object> op = (Rbd rbd) -> {
                rbdOp.call(rbd);
                return null;
            };
        doCall(pool, op, errorMsg, errorMsgArgs);
    }

    private <T> T doCall(final String pool, final String imageName, final RbdImageOperationT<T> rbdImageOp, final String errorMsg,
            final Object... errorMsgArgs) {
        RbdOperationT<T> op = (Rbd rbd) -> {
                RbdImage image = null;
                try {
                    image = rbd.open(imageName);
                    return rbdImageOp.call(image);
                } finally {
                    rbd.close(image);
                }
            };
        return doCall(pool, op, errorMsg, errorMsgArgs);
    }

    private void doCall(final String pool, final String imageName, final RbdImageOperation rbdImageOp, final String errorMsg,
            final Object... errorMsgArgs) {
        RbdImageOperationT<Object> op = (RbdImage image) -> {
                rbdImageOp.call(image);
                return null;
            };
        doCall(pool, imageName, op, errorMsg, errorMsgArgs);
    }

    @Override
    public ClusterInfo getClusterInfo() {
        RadosOperationT<ClusterInfo> op = () -> {
                ClusterInfo info = new ClusterInfo();
                info.setFsid(rados.clusterFsid());
                RadosClusterInfo stat = rados.clusterStat();
                info.setKb(stat.kb);
                info.setKbAvail(stat.kb_avail);
                return info;
            };
        return doCall(op, "Failed to get Ceph cluster info");
    }

    @Override
    public List<PoolInfo> getPools() {
        RadosOperationT<List<PoolInfo>> op = () -> {
                List<PoolInfo> pools = new ArrayList<>();
                String[] poolNames = rados.poolList();
                for (String poolName : poolNames) {
                    PoolInfo poolInfo = new PoolInfo();
                    poolInfo.setName(poolName);
                    poolInfo.setId(rados.poolLookup(poolName));
                    pools.add(poolInfo);
                }
                return pools;
            };
        return doCall(op, "Failed to get Ceph pools");
    }

    @Override
    public void createImage(String pool, final String name, final long size) {
        doCall(pool, (Rbd rbd) -> rbd.create(name, size, LAYERING),
                "Failed to create Ceph image %s/%s with size %s", pool, name, size);
    }

    @Override
    public void deleteImage(String pool, final String name) {
        doCall(pool, (Rbd rbd) -> rbd.remove(name),
                "Failed to delete Ceph image %s/%s", pool, name);
    }

    @Override
    public void resizeImage(String pool, String name, final long size) {
        doCall(pool, name, (RbdImage image) -> image.resize(size),
                "Failed to resize Ceph image %s/%s to size %s", pool, name, size);
    }

    @Override
    public void createSnap(String pool, String imageName, final String snapName) {
        doCall(pool, imageName, (RbdImage image) -> image.snapCreate(snapName),
                "Failed to create snapshot %s for Ceph image %s/%s", snapName, pool, imageName);
    }

    @Override
    public void deleteSnap(String pool, String imageName, final String snapName) {
        doCall(pool, imageName, (RbdImage image) -> image.snapRemove(snapName),
                "Failed to remove Ceph snapshot %s/%s@%s", pool, imageName, snapName);
    }

    @Override
    public void cloneSnap(final String pool, final String parentImage, final String parentSnap, final String childName) {
        RbdOperation op = (Rbd rbd) -> {
            IoCTX childIOCtx = null;
            try {
                childIOCtx = rados.ioCtxCreate(pool);
                long features = LAYERING;
                int order = 0;
                rbd.clone(parentImage, parentSnap, childIOCtx, childName, features, order);
            } finally {
                if (childIOCtx != null)
                    rados.ioCtxDestroy(childIOCtx);
            }
        };
        doCall(pool, op,
                "Failed to clone Ceph snapshot %s/%s@%s with name %s", pool, parentImage, parentSnap, childName);
    }

    @Override
    public void protectSnap(String pool, String parentImage, final String snapName) {
        doCall(pool, parentImage, (RbdImage image) -> image.snapProtect(snapName),
                "Failed to protect Ceph snapshot %s/%s@%s", pool, parentImage, snapName);
    }

    @Override
    public void unprotectSnap(String pool, String parentImage, final String snapName) {
        doCall(pool, parentImage, (RbdImage image) -> image.snapUnprotect(snapName),
                "Failed to unprotect Ceph snapshot %s/%s@%s", pool, parentImage, snapName);
    }

    @Override
    public boolean snapIsProtected(String pool, String parentImage, final String snapName) {
        return doCall(pool, parentImage, (RbdImage image) -> image.snapIsProtected(snapName),
                "Failed to get is_protected status for Ceph snapshot %s/%s@%s", pool, parentImage, snapName);
    }

    @Override
    public void flattenImage(String pool, String imageName) {
        doCall(pool, imageName, (RbdImage image) -> image.flatten(),
                "Failed to flatten Ceph image %s/%s", pool, imageName);
    }

    @Override
    public List<SnapInfo> getSnapshots(String pool, String imageName) {
        RbdImageOperationT<List<SnapInfo>> op = (RbdImage image) -> {
            List<SnapInfo> result = new ArrayList<>();
            List<RbdSnapInfo> snapList = image.snapList();
            for (RbdSnapInfo snap : snapList) {
                SnapInfo snapInfo = new SnapInfo();
                snapInfo.setId(snap.id);
                snapInfo.setName(snap.name);
                result.add(snapInfo);
            }
            return result;
        };
        return doCall(pool, imageName, op,
                "Failed to list snapshots for Ceph image %s/%s", pool, imageName);
    }

    @Override
    public List<String> getChildren(String pool, String parentImage, final String snapName) {
        return doCall(pool, parentImage, (RbdImage image) -> image.listChildren(snapName),
                "Failed to list children for Ceph snapshot %s/%s@%s", pool, parentImage, snapName);
    }
}
