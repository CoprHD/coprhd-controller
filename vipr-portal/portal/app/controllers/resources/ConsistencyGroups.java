/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import models.datatable.BlockConsistencyGroupDataTable;
import models.datatable.BlockConsistencyGroupDataTable.BlockConsistencyGroup;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.BlockConsistencyGroupUtils;
import util.MessagesUtils;
import util.ProjectUtils;
import util.datatable.DataTablesSupport;
import util.validation.CommonFormValidator;

import com.emc.storageos.model.block.BlockConsistencyGroupCreate;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN"), @Restrict("PROJECT_ADMIN") })
public class ConsistencyGroups extends Controller {
	private static final String ACTIVE_PROJECT_ID = "activeProjectId";

	public static void list() {
		BlockConsistencyGroupDataTable dataTable = new BlockConsistencyGroupDataTable();

		List<ProjectRestRep> projects = ProjectUtils.getProjects(Models.currentAdminTenant());
		Collections.sort(projects, new Comparator<ProjectRestRep>() {
			public int compare(ProjectRestRep proj1, ProjectRestRep proj2) {
				return proj1.getName().compareTo(proj2.getName());
			}
		});
		String activeProjectId = flash.get(ACTIVE_PROJECT_ID);
		if (activeProjectId == null && !projects.isEmpty()) {
			activeProjectId = projects.get(0).getId().toString();
		}

		TenantSelector.addRenderArgs();

		render(dataTable, projects, activeProjectId);
	}

	public static void listJson(String projectId) {
		List<BlockConsistencyGroup> items = Lists.newArrayList();

		if (StringUtils.isNotBlank(projectId)) {
			for (BlockConsistencyGroupRestRep cg : BlockConsistencyGroupUtils.getBlockConsistencyGroups(projectId)) {
				items.add(new BlockConsistencyGroup(cg));
			}
		}
		renderJSON(DataTablesSupport.createJSON(items, params));
	}

	/**
	 * NOTE: This isn't used at the moment as your not able to update a
	 * consistency group name
	 */
	public static void edit(String id) {
		list();
	}

	@FlashException(referrer = { "create", "edit" })
	public static void save(ConsistencyGroupForm consistencyGroup) {

		flash.put(ACTIVE_PROJECT_ID, consistencyGroup.projectId);

		consistencyGroup.validate("consistencyGroup");
		if (Validation.hasErrors()) {
			Common.handleError();
		}

		// NOTE : Only Create is supported at this time
		if (consistencyGroup.isNew()) {
			BlockConsistencyGroupCreate createParam = new BlockConsistencyGroupCreate();
			createParam.setName(consistencyGroup.name);
			createParam.setProject(uri(consistencyGroup.projectId));
			createParam.setArrayConsistency(consistencyGroup.arrayConsistency);

			BlockConsistencyGroupUtils.create(createParam);
		}

		flash.success(MessagesUtils.get("consistencyGroups.saved", consistencyGroup.name));
		if (StringUtils.isNotBlank(consistencyGroup.referrerUrl)) {
			redirect(consistencyGroup.referrerUrl);
		} else {
			list();
		}
	}

	public static void create(String projectId) {
		ConsistencyGroupForm consistencyGroup = new ConsistencyGroupForm(projectId);
		consistencyGroup.arrayConsistency = true;
		render("@edit", consistencyGroup);
	}

	@FlashException("list")
	public static void delete(@As(",") String[] ids) {
		delete(uris(ids));
	}

	private static void delete(List<URI> ids) {
		if (!ids.isEmpty()) {
			BlockConsistencyGroupRestRep cg = BlockConsistencyGroupUtils.getBlockConsistencyGroup(ids.get(0));
			if (cg != null) {
				flash.put(ACTIVE_PROJECT_ID, cg.getProject().getId().toString());
			}
		}
		for (URI id : ids) {
			BlockConsistencyGroupUtils.deactivate(id);
		}
		flash.success(MessagesUtils.get("consistencyGroups.deleted"));
		list();
	}

	public static class ConsistencyGroupForm {
		public String id;

		@Required
		@MaxSize(64)
		@MinSize(2)
		public String name;

		@Required
		public String projectId;

		public String referrerUrl;

		public boolean arrayConsistency;

		public ConsistencyGroupForm(String projectId) {
			this.projectId = projectId;
		}

		public ConsistencyGroupForm from(BlockConsistencyGroupRestRep from) {
			this.id = from.getId().toString();
			this.name = from.getName();
			this.arrayConsistency = from.getArrayConsistency();
			return this;
		}

		public boolean isNew() {
			return StringUtils.isBlank(id);
		}

		public void validate(String formName) {
			Validation.valid(formName, this);
			if (!validateCGName(this.name)) {
				Validation.addError(formName + ".name", "consistencyGroups.invalid.name.error");
			}
		}

		private static boolean validateCGName(String cgName) {
			return CommonFormValidator.isAlphaNumericOrUnderscoreUnordered(cgName);
		}
	}
}
