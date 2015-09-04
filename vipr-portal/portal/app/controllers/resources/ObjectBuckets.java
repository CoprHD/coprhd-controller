package controllers.resources;

import static com.emc.sa.util.ResourceType.BUCKET;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;
import models.datatable.ObjectBucketsDataTable;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ViPRHttpException;

import play.data.binding.As;
import play.i18n.Messages;
import play.mvc.Util;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;
import controllers.Common;
import controllers.util.FlashException;

@With(Common.class)
public class ObjectBuckets extends ResourceController {

    private static final String UNKNOWN = "resources.buckets.unknown";

    private static ObjectBucketsDataTable objectbucketsDataTable = new ObjectBucketsDataTable();

    public static void buckets(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", objectbucketsDataTable);
        addReferenceData();
        render();
    }

    public static void bucketsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<ObjectBucketsDataTable.Bucket> buckets = ObjectBucketsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(buckets, params));
    }

    public static void bucket(String bucketId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        BucketRestRep bucket = null;
        if (isBucketId(bucketId)) {
            try {
                bucket = client.objectBuckets().get(uri(bucketId));
            } catch (ViPRHttpException e) {
                if (e.getHttpCode() == 404) {
                    flash.error(MessagesUtils.get(UNKNOWN, bucketId));
                    buckets(null);
                }
                throw e;
            }
        }
        if (bucket == null) {
            notFound(Messages.get("resources.bucket.notfound"));
        }

        if (bucket.getVirtualArray() != null) { // NOSONAR
                                                // ("Suppressing Sonar violation of Possible null pointer dereference of volume. When volume is null, the previous if condition handles with throw")
            renderArgs.put("virtualArray", VirtualArrayUtils.getVirtualArrayRef(bucket.getVirtualArray()));
        }
        if (bucket.getVirtualPool() != null) {
            renderArgs.put("virtualPool", VirtualPoolUtils.getBlockVirtualPoolRef(bucket.getVirtualPool()));
        }

        Tasks<BucketRestRep> tasksResponse = client.objectBuckets().getTasks(bucket.getId());
        List<Task<BucketRestRep>> tasks = tasksResponse.getTasks();
        renderArgs.put("tasks", tasks);

        render(bucket);
    }


    @FlashException("buckets")
    public static void delete(@As(",") String[] ids, VolumeDeleteTypeEnum type) {
        delete(uris(ids), type);
    }

    @FlashException(referrer = { "bucket" })
    public static void deleteBucket(String bucketId, VolumeDeleteTypeEnum type) {
        if (StringUtils.isNotBlank(bucketId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();

            Task<BucketRestRep> task = client.objectBuckets().deactivate(uri(bucketId), type);
            flash.put("info", MessagesUtils.get("resources.bucket.deactivate"));
        }
        bucket(bucketId);
    }

    private static void delete(List<URI> ids, VolumeDeleteTypeEnum type) {
        if (ids != null) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            Tasks<BucketRestRep> tasks = client.objectBuckets().deactivate(ids, type);
            flash.put("info", MessagesUtils.get("resources.buckets.deactivate", tasks.getTasks().size()));
        }
        buckets(null);
    }

    @Util
    private static boolean isBucketId(String id) {
        return ResourceType.isType(BUCKET, id);
    }
   
}
