/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.refIds;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.ResourceUtils;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.Common;

@With(Common.class)
public class AffectedResources extends Controller {
    @Util
    public static ResourceDetails resourceDetails(String resourceId) {
        URI id = uri(resourceId);
        switch (ResourceType.fromResourceId(resourceId)) {
            case VOLUME:
                return new VolumeDetails(id);
            case EXPORT_GROUP:
                return new ExportGroupDetails(id);
            case FILE_SHARE:
                return new FileSystemDetails(id);
            case FILE_SNAPSHOT:
                return new FileSnapshotDetails(id);
            case BLOCK_SNAPSHOT:
                return new BlockSnapshotDetails(id);
            case BLOCK_SNAPSHOT_SESSION:
                return new BlockSnapshotSessionDetails(id);
            case CONSISTENCY_GROUP:
                return new BlockConsistencyGroupDetails(id);
            case HOST:
                return new HostDetails(id);
            case CLUSTER:
                return new ClusterDetails(id);
            default:
                return null;
        }
    }

    // ----- ViPR access methods -----

    private static VolumeRestRep getVolume(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.blockVolumes().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve volume: %s", id);
        }
        return null;
    }

    private static HostRestRep getHost(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.hosts().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve host: %s", id);
        }
        return null;
    }

    private static ClusterRestRep getCluster(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.clusters().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve host: %s", id);
        }
        return null;
    }

    private static List<HostRestRep> getClusterHosts(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                List<NamedRelatedResourceRep> hosts = client.hosts().listByCluster(id);
                return client.hosts().getByIds(refIds(hosts));
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve hosts for cluster: %s", id);
        }
        return null;
    }

    private static ExportGroupRestRep getExportGroup(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.blockExports().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve export group: %s", id);
        }
        return null;
    }

    private static VirtualArrayRestRep getVirtualArray(ViPRCoreClient client, BlockObjectRestRep blockObj) {
        if (blockObj != null) {
            return getVirtualArray(client, id(blockObj.getVirtualArray()));
        }
        else {
            return null;
        }
    }

    private static VirtualArrayRestRep getVirtualArray(ViPRCoreClient client, ExportGroupRestRep export) {
        if (export != null) {
            return getVirtualArray(client, id(export.getVirtualArray()));
        }
        else {
            return null;
        }
    }

    private static VirtualArrayRestRep getVirtualArray(ViPRCoreClient client, FileShareRestRep fileShare) {
        if (fileShare != null) {
            return getVirtualArray(client, id(fileShare.getVirtualArray()));
        }
        else {
            return null;
        }
    }

    private static BlockVirtualPoolRestRep getBlockVirtualPool(ViPRCoreClient client, VolumeRestRep volume) {
        if (volume != null) {
            return getBlockVirtualPool(client, id(volume.getVirtualPool()));
        }
        else {
            return null;
        }
    }

    private static FileVirtualPoolRestRep getFileVirtualPool(ViPRCoreClient client, FileShareRestRep fileShare) {
        if (fileShare != null) {
            return getFileVirtualPool(client, id(fileShare.getVirtualPool()));
        }
        else {
            return null;
        }
    }

    private static VirtualArrayRestRep getVirtualArray(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.varrays().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve virtual array: %s", id);
        }
        return null;
    }

    private static BlockVirtualPoolRestRep getBlockVirtualPool(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.blockVpools().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve block virtual pool: %s", id);
        }
        return null;
    }

    private static FileVirtualPoolRestRep getFileVirtualPool(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.fileVpools().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve file virtual pool: %s", id);
        }
        return null;
    }

    private static FileShareRestRep getFileShare(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.fileSystems().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve file system: %s", id);
        }
        return null;
    }

    private static List<FileSystemExportParam> getNfsExports(ViPRCoreClient client, URI fileSystemId) {
        try {
            if (fileSystemId != null) {
                return client.fileSystems().getExports(fileSystemId);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve exports for file system: %s", fileSystemId);
        }
        return Lists.newArrayList();
    }

    private static List<SmbShareResponse> getCifsShares(ViPRCoreClient client, URI fileSystemId) {
        try {
            if (fileSystemId != null) {
                return client.fileSystems().getShares(fileSystemId);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve shares for file system: %s", fileSystemId);
        }
        return Lists.newArrayList();
    }

    private static BlockSnapshotRestRep getBlockSnapshot(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.blockSnapshots().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve block snapshot: %s", id);
        }
        return null;
    }
    
    private static BlockSnapshotSessionRestRep getBlockSnapshotSession(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.blockSnapshotSessions().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve block snapshot session: %s", id);
        }
        return null;
    }

    private static BlockMirrorRestRep getBlockContinuousCopy(ViPRCoreClient client, URI volumeId, URI mirrorId) {
        try {
            if (volumeId != null && mirrorId != null) {
                return client.blockVolumes().getContinuousCopy(volumeId, mirrorId);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve block continuous copy: %s for volume: %s", mirrorId, volumeId);
        }
        return null;
    }

    private static BlockConsistencyGroupRestRep getConsistencyGroup(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.blockConsistencyGroups().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve block consistency group: %s", id);
        }
        return null;
    }

    private static VolumeRestRep getVolume(ViPRCoreClient client, BlockSnapshotRestRep blockSnapshot) {
        if (blockSnapshot != null) {
            return getVolume(client, id(blockSnapshot.getParent()));
        }
        else {
            return null;
        }
    }
    
    private static VolumeRestRep getVolume(ViPRCoreClient client, BlockSnapshotSessionRestRep blockSnapshotSession) {
        if (blockSnapshotSession != null) {
            return getVolume(client, id(blockSnapshotSession.getParent()));
        }
        else {
            return null;
        }
    }

    private static FileSnapshotRestRep getFileSnapshot(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.fileSnapshots().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve file snapshot: %s", id);
        }
        return null;
    }

    private static FileShareRestRep getFileShare(ViPRCoreClient client, FileSnapshotRestRep fileSnapshot) {
        if (fileSnapshot != null) {
            return getFileShare(client, id(fileSnapshot.getParent()));
        }
        else {
            return null;
        }
    }

    private static String getMountPoint(ViPRCoreClient client, VolumeRestRep volume) {
        if (volume != null) {
            return "";
            // return KnownMachineTags.getBlockVolumeMountPoint(volume);
        }
        else {
            return null;
        }
    }

    private static String getDatastore(ViPRCoreClient client, VolumeRestRep volume) {
        if (volume != null) {
            return "";
            // return KnownMachineTags.getBlockVolumeVMFSDatastore(volume);
        }
        else {
            return null;
        }
    }

    private static BlockConsistencyGroupRestRep getConsistencyGroup(ViPRCoreClient client, VolumeRestRep volume) {
        if (volume != null) {
            return getConsistencyGroup(client, id(volume.getConsistencyGroup()));
        }
        else {
            return null;
        }
    }

    private static Collection<String> getDatastores(ViPRCoreClient client, FileShareRestRep fileSystem) {
        if (fileSystem != null) {
            return Lists.newArrayList();
            // return MachineTagUtils.getDatastoresOnFilesystem(fileSystem).values();
        }
        else {
            return null;
        }
    }

    private static BlockConsistencyGroupRestRep getBlockConsistencyGroup(ViPRCoreClient client, URI id) {
        try {
            if (id != null) {
                return client.blockConsistencyGroups().get(id);
            }
        } catch (Exception e) {
            Logger.debug(e, "Failed to retrieve block consistency group: %s", id);
        }
        return null;
    }

    private static List<VolumeRestRep> getVolumes(ViPRCoreClient client, BlockConsistencyGroupRestRep blockConsistencyGroup) {
        if (blockConsistencyGroup != null) {
            return client.blockVolumes().getByRefs(blockConsistencyGroup.getVolumes());
        }
        else {
            return null;
        }
    }

    public static class ResourceDetails {
        protected ViPRCoreClient client;
        public String resourceId;
        public ResourceType type;

        public ResourceDetails(URI resourceId, ResourceType type) {
            this(getViprClient(), resourceId, type);
        }

        public ResourceDetails(ViPRCoreClient client, URI resourceId, ResourceType type) {
            this.resourceId = resourceId.toString();
            this.client = client;
            this.type = type;
            if (this.type == null) {
                try {
                    this.type = ResourceType.fromResourceId(resourceId.toString());
                } catch (RuntimeException e) {
                    Logger.warn(e, "Unable to determine the type of the given resource %s", resourceId);
                    this.type = ResourceType.UNKNOWN;
                }
            }
        }

        public ResourceDetails(ViPRCoreClient client, URI resourceId) {
            this(client, resourceId, null);
        }
    }

    public static class VolumeDetails extends ResourceDetails {
        public VolumeRestRep volume;
        public VirtualArrayRestRep neighborhood;
        public VirtualPoolCommonRestRep cos;
        public String mountPoint;
        public String datastore;
        public BlockConsistencyGroupRestRep consistencyGroup;

        public VolumeDetails(URI resourceId) {
            super(resourceId, ResourceType.VOLUME);
            volume = getVolume(client, resourceId);
            neighborhood = getVirtualArray(client, volume);
            cos = getBlockVirtualPool(client, volume);
            mountPoint = getMountPoint(client, volume);
            datastore = getDatastore(client, volume);
            consistencyGroup = getConsistencyGroup(client, volume);
        }

        public List<ResourceUtils.HostExport> getHostExports() {
            List<ITLRestRep> itls = client.blockVolumes().getExports(volume.getId());
            return ResourceUtils.getHostExports(itls);
        }

        public List<BlockSnapshotDetails> getSnapshots() {
            List<BlockSnapshotDetails> snapshots = Lists.newArrayList();
            for (NamedRelatedResourceRep res : client.blockSnapshots().listByVolume(volume.getId())) {
                snapshots.add(new BlockSnapshotDetails(client, res.getId()));
            }
            return snapshots;
        }
        
        public List<BlockSnapshotSessionDetails> getSnapshotSessions() {
            List<BlockSnapshotSessionDetails> snapshotSessions = Lists.newArrayList();
            for (NamedRelatedResourceRep res : client.blockSnapshotSessions().listByVolume(volume.getId())) {
                snapshotSessions.add(new BlockSnapshotSessionDetails(client, res.getId()));
            }
            return snapshotSessions;
        }

        public Collection<BlockContinuousCopyDetails> getContinuousCopies() {
            List<BlockContinuousCopyDetails> copies = Lists.newArrayList();
            for (NamedRelatedResourceRep res : client.blockVolumes().listContinuousCopies(volume.getId())) {
                copies.add(new BlockContinuousCopyDetails(client, volume.getId(), res.getId()));
            }
            return copies;
        }
    }

    public static class HostDetails extends ResourceDetails {
        public HostRestRep host;

        public HostDetails(URI resourceId) {
            super(resourceId, ResourceType.HOST);
            host = getHost(client, resourceId);
        }
    }

    public static class ClusterDetails extends ResourceDetails {
        public ClusterRestRep cluster;
        public List<HostRestRep> hosts;

        public ClusterDetails(URI resourceId) {
            super(resourceId, ResourceType.CLUSTER);
            cluster = getCluster(client, resourceId);
            hosts = getClusterHosts(client, resourceId);
        }
    }

    public static class ExportGroupDetails extends ResourceDetails {
        public ExportGroupRestRep exportGroup;
        public VirtualArrayRestRep neighborhood;

        public ExportGroupDetails(URI resourceId) {
            super(resourceId, ResourceType.EXPORT_GROUP);
            exportGroup = getExportGroup(client, resourceId);
            neighborhood = getVirtualArray(client, exportGroup);
        }
    }

    public static class FileSystemDetails extends ResourceDetails {
        public FileShareRestRep fileShare;
        public VirtualArrayRestRep neighborhood;
        public VirtualPoolCommonRestRep cos;
        public List<FileSystemExportParam> exports;
        public List<SmbShareResponse> smbShares;
        public Collection<String> datastores;

        public FileSystemDetails(URI resourceId) {
            super(resourceId, ResourceType.FILE_SHARE);
            fileShare = getFileShare(client, resourceId);
            neighborhood = getVirtualArray(client, fileShare);
            cos = getFileVirtualPool(client, fileShare);
            exports = getNfsExports(client, resourceId);
            smbShares = getCifsShares(client, resourceId);
            datastores = getDatastores(client, fileShare);
        }

        public List<FileSnapshotDetails> getSnapshots() {
            List<FileSnapshotDetails> snapshots = Lists.newArrayList();
            for (NamedRelatedResourceRep res : client.fileSnapshots().listByFileSystem(fileShare.getId())) {
                snapshots.add(new FileSnapshotDetails(client, res.getId()));
            }
            return snapshots;
        }
    }

    public static class FileSnapshotDetails extends ResourceDetails {
        public FileSnapshotRestRep fileSnapshot;
        public FileShareRestRep fileShare;
        public VirtualArrayRestRep neighborhood;

        public FileSnapshotDetails(URI resourceId) {
            super(resourceId, ResourceType.FILE_SNAPSHOT);
            fileSnapshot = getFileSnapshot(client, resourceId);
            fileShare = getFileShare(client, fileSnapshot);
            neighborhood = getVirtualArray(client, fileShare);
        }

        public FileSnapshotDetails(ViPRCoreClient client, URI resourceId) {
            super(client, resourceId);
            fileSnapshot = getFileSnapshot(client, resourceId);
            fileShare = getFileShare(client, fileSnapshot);
            neighborhood = getVirtualArray(client, fileShare);
        }
    }

    public static class BlockSnapshotDetails extends ResourceDetails {
        public BlockSnapshotRestRep blockSnapshot;
        public VolumeRestRep volume;
        public VirtualArrayRestRep neighborhood;

        public BlockSnapshotDetails(URI resourceId) {
            super(resourceId, ResourceType.BLOCK_SNAPSHOT);
            blockSnapshot = getBlockSnapshot(client, resourceId);
            volume = getVolume(client, blockSnapshot);
            neighborhood = getVirtualArray(client, blockSnapshot);
        }

        public BlockSnapshotDetails(ViPRCoreClient client, URI resourceId) {
            super(client, resourceId);
            blockSnapshot = getBlockSnapshot(client, resourceId);
            volume = getVolume(client, blockSnapshot);
            neighborhood = getVirtualArray(client, blockSnapshot);
        }

        public List<ResourceUtils.HostExport> getHostExports() {
            List<ITLRestRep> itls = client.blockSnapshots().listExports(uri(resourceId));
            return ResourceUtils.getHostExports(itls);
        }
    }
    
    public static class BlockSnapshotSessionDetails extends ResourceDetails {
        public BlockSnapshotSessionRestRep blockSnapshotSession;
        public VolumeRestRep volume;
        public VirtualArrayRestRep neighborhood;

        public BlockSnapshotSessionDetails(URI resourceId) {
            super(resourceId, ResourceType.BLOCK_SNAPSHOT_SESSION);
            blockSnapshotSession = getBlockSnapshotSession(client, resourceId);
            volume = getVolume(client, blockSnapshotSession);
            neighborhood = getVirtualArray(client, blockSnapshotSession);
        }

        public BlockSnapshotSessionDetails(ViPRCoreClient client, URI resourceId) {
            super(client, resourceId);
            blockSnapshotSession = getBlockSnapshotSession(client, resourceId);
            volume = getVolume(client, blockSnapshotSession);
            neighborhood = getVirtualArray(client, blockSnapshotSession);
        }
    }

    public static class BlockConsistencyGroupDetails extends ResourceDetails {
        public BlockConsistencyGroupRestRep blockConsistencyGroup;
        public List<VolumeRestRep> volumes;
        public List<BlockSnapshotDetails> snapshots;
        public List<BlockSnapshotSessionDetails> snapshotSessions;

        public BlockConsistencyGroupDetails(URI resourceId) {
            super(resourceId, ResourceType.CONSISTENCY_GROUP);
            blockConsistencyGroup = getBlockConsistencyGroup(client, resourceId);
            volumes = getVolumes(client, blockConsistencyGroup);
        }

        public BlockConsistencyGroupDetails(ViPRCoreClient client, URI resourceId) {
            super(client, resourceId);
            blockConsistencyGroup = getBlockConsistencyGroup(client, resourceId);
            volumes = getVolumes(client, blockConsistencyGroup);
            snapshots = getSnapshots();
            snapshotSessions = getSnapshotSessions();
        }

        public List<BlockSnapshotDetails> getSnapshots() {
            List<BlockSnapshotDetails> snapshots = Lists.newArrayList();
            for (NamedRelatedResourceRep res : client.blockSnapshots().listByConsistencyGroup(blockConsistencyGroup.getId())) {
                snapshots.add(new BlockSnapshotDetails(client, res.getId()));
            }
            return snapshots;
        }
        
        public List<BlockSnapshotSessionDetails> getSnapshotSessions() {
            List<BlockSnapshotSessionDetails> snapshotSessions = Lists.newArrayList();
            for(NamedRelatedResourceRep res : client.blockSnapshotSessions().listByConsistencyGroup(blockConsistencyGroup.getId())) {
                snapshotSessions.add(new BlockSnapshotSessionDetails(client, res.getId()));
            }
            return snapshotSessions;
        }
    }

    public static class BlockContinuousCopyDetails extends ResourceDetails {
        public BlockMirrorRestRep blockContinuousCopy;
        public VolumeRestRep volume;
        public VirtualArrayRestRep varray;
        public VirtualPoolCommonRestRep vpool;

        public BlockContinuousCopyDetails(URI volumeId, URI continuousCopyId) {
            super(continuousCopyId, ResourceType.BLOCK_CONTINUOUS_COPY);
            init(client, volumeId, continuousCopyId);
        }

        public BlockContinuousCopyDetails(ViPRCoreClient client, URI volumeId, URI continuousCopyId) {
            super(client, continuousCopyId, ResourceType.BLOCK_CONTINUOUS_COPY);
            init(client, volumeId, continuousCopyId);
        }

        private void init(ViPRCoreClient client, URI volumeId, URI continuousCopyId) {
            blockContinuousCopy = getBlockContinuousCopy(client, volumeId, continuousCopyId);
            volume = getVolume(client, volumeId);
            varray = getVirtualArray(client, volume);
        }
    }

}
