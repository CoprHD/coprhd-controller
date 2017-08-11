package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.block.BlockPerformancePolicyCreate;
import com.emc.storageos.model.block.BlockPerformancePolicyRestRep;
import com.emc.storageos.model.block.BlockPerformancePolicyUpdate;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.datatable.BlockPerformancePoliciesDataTable;
import play.Logger;
import play.data.binding.As;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.With;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class BlockPerformancePolicies extends ViprResourceController {

    protected static final String UNKNOWN = "BlockPerformancePolicies.unknown";

    public static void list() {
        BlockPerformancePoliciesDataTable dataTable = createBlockPerformancePoliciesDataTable();
        render(dataTable);
    }

    private static BlockPerformancePoliciesDataTable createBlockPerformancePoliciesDataTable() {
        BlockPerformancePoliciesDataTable dataTable = new BlockPerformancePoliciesDataTable();
        return dataTable;
    }

    public static class BlockPerformancePolicyForm {
        public String id;
        public String name;
        public String description;
        public String autoTieringPolicyName;
        public Boolean compressionEnabled;
        public Boolean dedupCapable;
        public Boolean fastExpansion;
        public Integer hostIOLimitBandwidth;
        public Integer hostIOLimitIOPs;
        public Integer thinVolumePreAllocationPercentage;

        public BlockPerformancePolicyForm load(BlockPerformancePolicyRestRep restRep) {
            this.id = restRep.getId() != null ? restRep.getId().toString() : null;
            this.name = restRep.getName();
            this.description = restRep.getDescription();
            this.autoTieringPolicyName = restRep.getAutoTieringPolicyName();
            this.compressionEnabled = restRep.getCompressionEnabled();
            this.dedupCapable = restRep.getDedupCapable();
            this.fastExpansion = restRep.getFastExpansion();
            this.hostIOLimitBandwidth = restRep.getHostIOLimitBandwidth();
            this.hostIOLimitIOPs = restRep.getHostIOLimitIOPs();
            this.thinVolumePreAllocationPercentage = restRep.getThinVolumePreAllocationPercentage();

            return this;
        }

        public void validate(String formName) {
            Validation.required(formName + ".name", name);
            Validation.required(formName + ".description", description);
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    }

    public static void blockPerformancePolices() {

        BlockPerformancePoliciesDataTable dataTable = new BlockPerformancePoliciesDataTable();
        renderArgs.put("dataTable", dataTable);
        BlockPerformancePolicyForm blockPerformancePolicyForm = new BlockPerformancePolicyForm();
        render("@list", dataTable, blockPerformancePolicyForm);
    }

    public static void blockPerformancePoliciesJson() {
        List<BlockPerformancePoliciesDataTable.BlockPerformancePoliciesModel> results = Lists.newArrayList();
        List<BlockPerformancePolicyRestRep> blockPerformancePolicies = getViprClient().blockPerformancePolicies()
                .getBlockPerformancePoliciesList();

        for (BlockPerformancePolicyRestRep blockPerformancePolicy : blockPerformancePolicies) {
            results.add(new BlockPerformancePoliciesDataTable.BlockPerformancePoliciesModel(
                    blockPerformancePolicy.getId(), blockPerformancePolicy.getName(),
                    blockPerformancePolicy.getDescription(), blockPerformancePolicy.getAutoTieringPolicyName(),
                    blockPerformancePolicy.getCompressionEnabled(), blockPerformancePolicy.getDedupCapable(),
                    blockPerformancePolicy.getFastExpansion(), blockPerformancePolicy.getHostIOLimitBandwidth(),
                    blockPerformancePolicy.getHostIOLimitIOPs(), blockPerformancePolicy.getThinVolumePreAllocationPercentage()));
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static void itemDetails(String id) {
        BlockPerformancePolicyRestRep blockPerformancePolicy = getViprClient().blockPerformancePolicies().get(uri(id));
        if (blockPerformancePolicy == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        render(blockPerformancePolicy);
    }

    // @FlashException(value = "blockPerformancePolicies", keep = true)
    public static void addBlockPerformancePolicy(String storageSystemId) {
        BlockPerformancePolicyForm blockPerformancePolicyForm = new BlockPerformancePolicyForm();
        render("@edit", blockPerformancePolicyForm);
    }

    // @FlashException(value = "blockPerformancePolicies", keep = true)
    public static void edit(String id) {
        BlockPerformancePolicyRestRep blockPerformancePolicyRestRep = getViprClient().blockPerformancePolicies().get(uri(id));
        renderArgs.put("blockPerformancePolicyId", id);

        if (blockPerformancePolicyRestRep != null) {
            renderArgs.put("blockPerformancePolicy", blockPerformancePolicyRestRep);
            BlockPerformancePolicyForm blockPerformancePolicyForm = new BlockPerformancePolicyForm().load(blockPerformancePolicyRestRep);
            render(blockPerformancePolicyForm);
        } else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            blockPerformancePolices();
        }
    }

    public static void duplicate(String ids) {
        BlockPerformancePolicyRestRep blockPerformancePolicyRestRep = getViprClient().blockPerformancePolicies().get(uri(ids));
        if (blockPerformancePolicyRestRep == null) {
            flash.error(MessagesUtils.get(UNKNOWN, ids));
            blockPerformancePolices();
        }
        BlockPerformancePolicyForm blockPerformancePolicy = new BlockPerformancePolicyForm().load(blockPerformancePolicyRestRep);
        blockPerformancePolicy.id = null;
        blockPerformancePolicy.name = Messages.get("blockPerformancePolicy.duplicate.name", blockPerformancePolicy.name);
        render("@edit", blockPerformancePolicy);
    }

    // @FlashException("blockPerformancePolicies")
    public static void deleteBlockPerformancePolicy(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                getViprClient().blockPerformancePolicies().delete(uri(id));
            }
            flash.success(MessagesUtils.get("blockPerformancePolicy.deleted"));
        }
        blockPerformancePolices();
    }

    @FlashException(keep = true, referrer = { "edit" })
    public static void saveBlockPerformancePolicy(BlockPerformancePolicyForm blockPerformancePolicy) {
        if (blockPerformancePolicy == null) {
            Logger.error("No block performance policy provided");
            badRequest("No block performance policy provided");
            return;
        }

        blockPerformancePolicy.id = params.get("id");
        if (blockPerformancePolicy.isNew()) {

            BlockPerformancePolicyCreate input = createBlockPerformancePolicy(blockPerformancePolicy);
            getViprClient().blockPerformancePolicies().create(input); // FIXME: had "true" second arg
        } else {
            BlockPerformancePolicyRestRep blockPerformancePolicyRestRep = getViprClient().blockPerformancePolicies()
                    .get(uri(blockPerformancePolicy.id));
            BlockPerformancePolicyUpdate input = updateBlockPerformancePolicy(blockPerformancePolicy);
            getViprClient().blockPerformancePolicies().update(blockPerformancePolicyRestRep.getId(), input);
        }
        flash.success(MessagesUtils.get("blockPerformancePolicy.saved", blockPerformancePolicy.name));
        blockPerformancePolices();
    }

    public static BlockPerformancePolicyCreate createBlockPerformancePolicy(BlockPerformancePolicyForm blockPerformancePolicyForm) {
        BlockPerformancePolicyCreate blockPerformancePolicyCreate = new BlockPerformancePolicyCreate();
        blockPerformancePolicyCreate.setName(blockPerformancePolicyForm.name.trim());
        blockPerformancePolicyCreate.setDescription(blockPerformancePolicyForm.description.trim());
        blockPerformancePolicyCreate.setAutoTieringPolicyName(blockPerformancePolicyForm.autoTieringPolicyName.trim());
        blockPerformancePolicyCreate.setCompressionEnabled(blockPerformancePolicyForm.compressionEnabled);
        blockPerformancePolicyCreate.setDedupCapable(blockPerformancePolicyForm.dedupCapable);
        blockPerformancePolicyCreate.setFastExpansion(blockPerformancePolicyForm.fastExpansion);
        blockPerformancePolicyCreate.setHostIOLimitBandwidth(blockPerformancePolicyForm.hostIOLimitBandwidth);
        blockPerformancePolicyCreate.setHostIOLimitIOPs(blockPerformancePolicyForm.hostIOLimitIOPs);
        blockPerformancePolicyCreate.setThinVolumePreAllocationPercentage(blockPerformancePolicyForm.thinVolumePreAllocationPercentage);
        return blockPerformancePolicyCreate;
    }

    public static BlockPerformancePolicyUpdate updateBlockPerformancePolicy(BlockPerformancePolicyForm blockPerformancePolicyForm) {
        BlockPerformancePolicyUpdate blockPerformancePolicyUpdate = new BlockPerformancePolicyUpdate();
        blockPerformancePolicyUpdate.setName(blockPerformancePolicyForm.name.trim());
        blockPerformancePolicyUpdate.setDescription(blockPerformancePolicyForm.description.trim());
        blockPerformancePolicyUpdate.setAutoTieringPolicyName(blockPerformancePolicyForm.autoTieringPolicyName.trim());
        blockPerformancePolicyUpdate.setCompressionEnabled(blockPerformancePolicyForm.compressionEnabled);
        blockPerformancePolicyUpdate.setDedupCapable(blockPerformancePolicyForm.dedupCapable);
        blockPerformancePolicyUpdate.setFastExpansion(blockPerformancePolicyForm.fastExpansion);
        blockPerformancePolicyUpdate.setHostIOLimitBandwidth(blockPerformancePolicyForm.hostIOLimitBandwidth);
        blockPerformancePolicyUpdate.setHostIOLimitIOPs(blockPerformancePolicyForm.hostIOLimitIOPs);
        blockPerformancePolicyUpdate.setThinVolumePreAllocationPercentage(blockPerformancePolicyForm.thinVolumePreAllocationPercentage);
        return blockPerformancePolicyUpdate;
    }

}