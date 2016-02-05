/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getCatalogClient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.bind.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import models.ACLs;
import models.BreadCrumb;
import models.RoleAssignmentType;

import org.apache.commons.beanutils.BeanComparator;
import org.apache.commons.lang.StringUtils;
//import org.jdom.Document;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import play.Logger;
import play.Play;
import play.data.validation.Check;
import play.data.validation.CheckWith;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.exceptions.UnexpectedException;
import play.i18n.Messages;
import play.mvc.Catch;
import play.mvc.Util;
import play.mvc.With;
import play.vfs.VirtualFile;
import util.ACLUtils;
import util.AssetOptionUtils;
import util.CatalogCategoryUtils;
import util.CatalogImageUtils;
import util.CatalogServiceUtils;
import util.EnumOption;
import util.ExecutionWindowUtils;
import util.MessagesUtils;
import util.ServiceDescriptorUtils;
import util.StringComparator;
import util.descriptor.ServiceFieldValidator;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.model.catalog.AssetOption;
import com.emc.vipr.model.catalog.CatalogCategoryCommonParam;
import com.emc.vipr.model.catalog.CatalogCategoryCreateParam;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogCategoryUpdateParam;
import com.emc.vipr.model.catalog.CatalogImageRestRep;
import com.emc.vipr.model.catalog.CatalogServiceCommonParam;
import com.emc.vipr.model.catalog.CatalogServiceCreateParam;
import com.emc.vipr.model.catalog.CatalogServiceFieldParam;
import com.emc.vipr.model.catalog.CatalogServiceFieldRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.CatalogServiceUpdateParam;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.Option;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.tenant.AclEntryForm;
import controllers.tenant.TenantSelector;
import controllers.util.AbstractRestRepForm;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class EditCatalog extends ServiceCatalog {

	protected static final String CATEGORY_MODEL_NAME = "CatalogCategory";
	protected static final String SERVICE_MODEL_NAME = "CatalogService";

	protected static final String CREATE_SERVICE_KEY = "Service.create.title";
	protected static final String CREATE_CATEGORY_KEY = "Category.create.title";
	protected static final String EDIT_CATEGORY_KEY = "Category.edit.title";

	public static void edit() {
		Map<String, CategoryDef> catalog = getCatalog(Models
				.currentAdminTenant());
		Map<String, List<BreadCrumb>> breadcrumbs = createBreadCrumbs(catalog);

		boolean catalogUpdateAvailable = CatalogCategoryUtils
				.isUpdateAvailable(uri(Models.currentAdminTenant()));
		TenantSelector.addRenderArgs();

		render(catalog, breadcrumbs, catalogUpdateAvailable);
	}

	/**
	 * Gets the URL for accessing to a particular category using the base edit
	 * catalog URL with the category path as the URL fragment.
	 * 
	 * @param categoryId
	 *            the ID of the category.
	 * @return the URL for accessing the category in the catalog.
	 */
	private static String getCategoryUrl(String tenantId, String categoryId) {
		Map<String, CategoryDef> catalog = getCatalog(tenantId);
		CategoryDef category = catalog.get(categoryId);
		String path = category != null ? category.path : "";
		return Common.reverseRoute(EditCatalog.class, "edit") + "#" + path;
	}

	/**
	 * Sends the user back to the catalog at the given category with a success
	 * message.
	 * 
	 * @param categoryId
	 *            the ID of the category to show the user.
	 * @param message
	 *            the success message.
	 * @param args
	 *            the success message arguments.
	 */
	private static void catalogUpdated(String categoryId, String messageKey,
			Object... args) {
		String tenantId = Models.currentAdminTenant();
		flash.success(Messages.get(messageKey, args));
		catalogModified(tenantId);
		showCategory(tenantId, categoryId);
	}

	@Util
	private static void addACLsToRenderArgs() {
		renderArgs.put("acls", ACLs.options(ACLs.USE));

		Set<EnumOption> aclTypes = new TreeSet<EnumOption>();
		aclTypes.addAll(Arrays.asList(EnumOption.options(
				RoleAssignmentType.values(), "RoleAssignmentType")));
		renderArgs.put("aclTypes", aclTypes);
	}

	private static void addImagesToRenderArgs() {
		renderArgs.put("images", loadImageOptions());
	}

	private static List<Option> loadImageOptions() {
		VirtualFile imageDir = Play.getVirtualFile("public/img/serviceCatalog");
		List<Option> images = Lists.newArrayList();
		for (VirtualFile f : imageDir.list()) {
			String label = f.getName().replaceAll("(icon_|.png)", "");
			images.add(new Option(f.getName(), label));
		}
		for (CatalogImageRestRep image : CatalogImageUtils.getCatalogImages()) {
			String filename = StringUtils.substringBeforeLast(image.getName(),
					".");
			images.add(new Option(image.getId().toString(), filename));
		}
		Collections.sort(images, new BeanComparator("value",
				new StringComparator(false)));
		return images;
	}

	public static void imagesJson() {
		renderJSON(loadImageOptions());
	}

	private static void addExecutionWindowsToRenderArgs() {
		List<ExecutionWindowRestRep> executionWindows = ExecutionWindowUtils
				.getExecutionWindows();
		renderArgs.put("executionWindows", executionWindows);
	}

	private static void addBaseServicesToRenderArgs() {
		Map<String, List<ServiceDescriptorRestRep>> descriptors = Maps
				.newTreeMap();
		for (ServiceDescriptorRestRep descriptor : ServiceDescriptorUtils
				.getDescriptors()) {
			String category = StringUtils.defaultString(descriptor
					.getCategory());
			List<ServiceDescriptorRestRep> values = descriptors.get(category);
			if (values == null) {
				values = Lists.newArrayList();
				descriptors.put(category, values);
			}
			values.add(descriptor);
		}
		for (List<ServiceDescriptorRestRep> values : descriptors.values()) {
			Collections.sort(values, new ServiceDescriptorComparator());
		}
		renderArgs.put("baseServices", descriptors);
	}

	private static void addBreadCrumbToRenderArgs(String tenantId,
			CategoryForm form) {
		Map<String, CategoryDef> catalog = getCatalog(tenantId);
		if (form.id != null) {
			List<BreadCrumb> breadcrumbs = createBreadCrumbs(form.id, catalog);
			addBreadCrumb(breadcrumbs, MessagesUtils.get(EDIT_CATEGORY_KEY));
			renderArgs.put("breadcrumbs", breadcrumbs);
		} else {
			List<BreadCrumb> breadcrumbs = createBreadCrumbs(form.parentId,
					catalog);
			addBreadCrumb(breadcrumbs, MessagesUtils.get(CREATE_CATEGORY_KEY));
			renderArgs.put("breadcrumbs", breadcrumbs);
		}

		addBackUrlToRenderArgs(Models.currentAdminTenant(),
				(form.id != null) ? form.id : form.parentId);
	}

	private static void addBreadCrumbToRenderArgs(String tenantId,
			ServiceForm form) {
		Map<String, CategoryDef> catalog = getCatalog(tenantId);
		List<BreadCrumb> breadcrumbs = createBreadCrumbs(form.owningCategoryId,
				catalog);
		if (form.id != null) {
			addBreadCrumb(breadcrumbs, form.title);
		} else {
			addBreadCrumb(breadcrumbs, MessagesUtils.get(CREATE_SERVICE_KEY));
		}
		renderArgs.put("breadcrumbs", breadcrumbs);

		addBackUrlToRenderArgs(tenantId, form.owningCategoryId);
	}

	private static void addBackUrlToRenderArgs(String tenantId,
			String categoryId) {
		String backUrl = request.params.get("return");
		if (StringUtils.isBlank(backUrl)) {
			backUrl = getCategoryUrl(tenantId, categoryId);
		}
		renderArgs.put("backUrl", backUrl);
	}

	private static void addCategoriesToRenderArgs() {
		addCategoriesToRenderArgs(Models.currentAdminTenant(), null);
	}

	private static void addCategoriesToRenderArgs(String tenantId,
			String categoryId) {
		List<CategoryPath> categories = new ArrayList<CategoryPath>();
		Map<String, CategoryDef> catalog = getCatalog(tenantId);
		for (CategoryDef category : catalog.values()) {
			// Do not add descendants of the specified category, it would cause
			// a circular reference if you tried
			// assigning a descendant as its parent
			if (!isDescendantOrSelf(category, categoryId, catalog)) {
				categories.add(new CategoryPath(category));
			}
		}
		Collections.sort(categories);
		renderArgs.put("categories", categories);
	}

	/**
	 * Determines if one category is a descendant of another, or the same.
	 * 
	 * @param category
	 *            the category that is being tested as a descendant.
	 * @param ancestorId
	 *            the ID of the ancestor category.
	 * @param catalog
	 *            the entire catalog.
	 * @return true if the category is a descendant of the ancestor category
	 *         specified.
	 */
	private static boolean isDescendantOrSelf(CategoryDef category,
			String ancestorId, Map<String, CategoryDef> catalog) {
		if ((category == null) || (ancestorId == null)) {
			return false;
		}
		if (StringUtils.equals(category.id, ancestorId)) {
			return true;
		}
		CategoryDef parent = catalog.get(category.parentId);
		return isDescendantOrSelf(parent, ancestorId, catalog);
	}

	private static void addFieldOptions(String baseService) {
		// Base service may be null for a new service
		if (StringUtils.isNotBlank(baseService)) {
			ServiceDescriptorRestRep serviceDescriptor = getServiceDescriptorForEditing(baseService);

			// Load any Asset Options for root fields so they are rendered
			// directly onto the form
			List<ServiceFieldRestRep> fields = ServiceDescriptorUtils
					.getAllFieldList(serviceDescriptor.getItems());
			for (ServiceFieldRestRep field : fields) {
				if (field.isAsset() && field.isLockable()) {
					try {
						List<AssetOption> options = AssetOptionUtils
								.getAssetOptions(field.getAssetType());
						request.current().args.put(
								field.getType() + "-options", options);
					} catch (RuntimeException e) {
						request.current().args.put(field.getType() + "-error",
								e.getMessage());
					}
				}
			}
		}
	}

	private static void showCategory(String tenantId, String categoryId) {
		if (StringUtils.isNotBlank(categoryId)) {
			CategoryDef category = getCatalog(tenantId).get(categoryId);
			if (category != null) {
				flash.put("categoryId", categoryId);
				flash.put("categoryPath", category.path);
			}
		}
		edit();
	}

	private static void edit(CategoryForm category) {
		addImagesToRenderArgs();
		addExecutionWindowsToRenderArgs();
		addCategoriesToRenderArgs(Models.currentAdminTenant(), category.id);
		addBreadCrumbToRenderArgs(Models.currentAdminTenant(), category);
		addACLsToRenderArgs();

		render("@editCategory", category);
	}

	public static void createCategory(String parentId, String fromId) {
		CatalogCategoryRestRep parent = CatalogCategoryUtils
				.getCatalogCategory(uri(parentId));
		CategoryForm category = new CategoryForm();
		category.parentId = getId(parent);
		category.fromId = StringUtils.defaultIfBlank(fromId, category.parentId);
		edit(category);
	}

	public static void editCategory(String categoryId, String fromId) {
		CatalogCategoryRestRep category = CatalogCategoryUtils
				.getCatalogCategory(uri(categoryId));
		CategoryForm form = new CategoryForm(category);
		form.fromId = StringUtils.defaultIfBlank(fromId, form.parentId);
		edit(form);
	}

	public static void saveCategory(CategoryForm category) {
		// Set name before validation
		if (StringUtils.isNotBlank(category.title)) {
			category.name = category.title.replaceAll(" ", "");
		}
		category.validate("category");
		if (Validation.hasErrors()) {
			params.flash();
			Validation.keep();
			if (category.isNew()) {
				createCategory(category.parentId, category.fromId);
			} else {
				editCategory(category.id, category.fromId);
			}
		} else {
			CatalogCategoryRestRep catalogCategory = category.save();
			String parentId = getParentId(catalogCategory.getCatalogCategory());
			String fromId = StringUtils.defaultIfBlank(category.fromId,
					parentId);
			catalogUpdated(fromId, "Saved category: %s",
					catalogCategory.getTitle());
		}
	}

	public static void deleteCategory(String categoryId) {
		CatalogCategoryRestRep category = CatalogCategoryUtils
				.getCatalogCategory(uri(categoryId));
		String title = category.getTitle();
		String parentId = getParentId(category.getCatalogCategory());

		CatalogCategoryUtils.deleteCatalogCategory(category.getId());

		catalogUpdated(parentId, "Category deleted: %s", title);
	}

	public static void restoreCatalog() {
		String tenantId = Models.currentAdminTenant();
		try {
			CatalogCategoryUtils.resetCatalogCategory(uri(tenantId));
			catalogModified(tenantId);
			flash.success(Messages.get("EditCatalog.restored"));
			edit();
		} catch (RuntimeException e) {
			Logger.error(e, "Failed to restore catalog");
			flash.error(Messages.get("EditCatalog.failed"));
			edit();
		}
	}

	private static void edit(ServiceForm service) {
		addImagesToRenderArgs();
		addExecutionWindowsToRenderArgs();
		addBaseServicesToRenderArgs();
		addCategoriesToRenderArgs();
		addBreadCrumbToRenderArgs(Models.currentAdminTenant(), service);
		addACLsToRenderArgs();

		// Add information for service fields
		ServiceDescriptorRestRep serviceDescriptor = null;
		if (service.baseService != null) {
			serviceDescriptor = getServiceDescriptorForEditing(service.baseService);
			addFieldOptions(service.baseService);
		}

		render("@editService", service, serviceDescriptor);
	}

	protected static ServiceDescriptorRestRep getServiceDescriptorForEditing(
			String name) {
		ServiceDescriptorRestRep serviceDescriptor = ServiceDescriptorUtils
				.getDescriptor(name);
		if (serviceDescriptor == null) {
			serviceDescriptor = new CorruptedServiceDescriptor();
		}
		return serviceDescriptor;
	}

	public static void createCustomService(String serviceId) throws Exception {
//		File jsonFile = new File("../com.emc.sa.common/src/java/com/emc/sa/catalog/default-catalog.json");
//		JSONParser parser = new JSONParser();
//		FileReader fileReader = new FileReader(jsonFile);
//		JSONObject json = (JSONObject) parser.parse(fileReader);
//		JSONArray characters = (JSONArray) json.get("categories");
//
//		JSONObject jsonPart = (JSONObject) parser
//				.parse("{\"services\":"+ readServicesFromXML() +",\"title\":\"CustomService\",\"description\":\"mydesc\",\"image\":\"icon_data_services.png\"}");
//		if (characters.get(characters.size() - 1).toString()
//				.contains("CustomService")) {
//			characters.remove(characters.size() - 1);
//		}
//
//		characters.add(jsonPart);
//
//		Gson gson = new GsonBuilder().setPrettyPrinting().create();
//		String jsonPrint = gson.toJson(json);
//		FileWriter fw = null;
//		File file = null;
//		file = new File("../com.emc.sa.common/src/java/com/emc/sa/catalog/default-catalog.json");
//		fw = new FileWriter(file);
//		fw.write(jsonPrint);
//		fw.flush();
//		fw.close();
//		System.out.println("File written Succesfully");
//		edit();
	}

	private static String readServicesFromXML() {
		String outputServices = "";
//		try {
//			File XmlFile = new File("../CustomService/CustomServices.xml");
//			DocumentBuilderFactory dbFactory = DocumentBuilderFactory
//					.newInstance();
//			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
//			org.w3c.dom.Document doc = dBuilder.parse(XmlFile);
//			doc.getDocumentElement().normalize();
//
//			NodeList nList = doc.getElementsByTagName("customservice");
//
//			StringBuffer outputService = new StringBuffer();
//
//			for (int temp = 0; temp < nList.getLength(); temp++) {
//				Node nNode = nList.item(temp);
//				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
//					org.w3c.dom.Element eElement = (org.w3c.dom.Element) nNode;
//					outputService.append("{\"baseService\":\""
//							+ eElement.getElementsByTagName("taskname").item(0)
//									.getTextContent()
//							+ "\",\"image\":\""
//							+ eElement.getElementsByTagName("img").item(0)
//									.getTextContent() + "\"},");
//				}
//			}
//			String removeCommaOutputService = outputService.toString()
//					.substring(0, outputService.toString().lastIndexOf(','));
//			outputServices = "[" + removeCommaOutputService + "]";
//
//		} catch (Exception e) {
//			System.out.println(e.getMessage());
//		}
		return outputServices;
	}
	
	public static void createService(String parentId, String fromId) {
		CatalogCategoryRestRep parentCategory = CatalogCategoryUtils
				.getCatalogCategory(uri(parentId));

		ServiceForm service = new ServiceForm();
		service.owningCategoryId = getId(parentCategory);
		service.fromId = StringUtils.defaultIfBlank(fromId,
				service.owningCategoryId);
		edit(service);
	}

	public static void editService(String serviceId, String fromId) {
		CatalogServiceRestRep catalogService = CatalogServiceUtils
				.getCatalogService(uri(serviceId));

		ServiceForm service = new ServiceForm(catalogService);
		service.fromId = StringUtils.defaultIfBlank(fromId,
				service.owningCategoryId);
		edit(service);
	}

	public static void copyService(String serviceId, String fromId) {
		CatalogServiceRestRep catalogService = CatalogServiceUtils
				.getCatalogService(uri(serviceId));

		ServiceForm service = new ServiceForm(catalogService);
		service.id = null;
		service.title = Messages.get("EditCatalog.copyOf", service.title);
		service.fromId = StringUtils.defaultIfBlank(fromId,
				service.owningCategoryId);
		edit(service);
	}

	public static void deleteService(String serviceId) {
		CatalogServiceRestRep catalogService = CatalogServiceUtils
				.getCatalogService(uri(serviceId));
		deleteService(catalogService);
	}

	private static void deleteService(CatalogServiceRestRep catalogService) {
		String title = catalogService.getTitle();
		String parentId = getParentId(catalogService.getCatalogCategory());

		CatalogServiceUtils.deleteCatalogService(catalogService.getId());

		catalogUpdated(parentId, "EditCatalog.deletedService", title);
	}

	public static void saveService(ServiceForm service) {
		// Set name before validation
		if (StringUtils.isNotBlank(service.title)) {
			service.name = service.title.replaceAll(" ", "");
		}
		service.validate("service");
		if (validation.hasErrors()) {
			params.flash();
			validation.keep();
			if (service.isNew()) {
				createService(service.owningCategoryId, service.fromId);
			} else {
				editService(service.id, service.fromId);
			}
		} else {
			CatalogServiceRestRep catalogService = service.save();
			String parentId = getParentId(catalogService.getCatalogCategory());
			String fromId = StringUtils
					.defaultIfBlank(service.fromId, parentId);
			catalogUpdated(fromId, "EditCatalog.savedService",
					catalogService.getTitle());
		}
	}

	public static void serviceFields(String baseService, String serviceId) {
		ServiceDescriptorRestRep serviceDescriptor = ServiceDescriptorUtils
				.getDescriptor(baseService);
		ServiceForm service = null;
		if (StringUtils.isNotBlank(serviceId)) {
			CatalogServiceRestRep dbService = CatalogServiceUtils
					.getCatalogService(uri(serviceId));
			if (dbService != null) {
				service = new ServiceForm(dbService);
			} else {
				service = new ServiceForm();
				service.baseService = baseService;
			}
		}
		addFieldOptions(baseService);
		render(serviceDescriptor, service);
	}

	public static void services(String id) {
		List<CatalogServiceRestRep> catalogServices = null;
		if (StringUtils.isNotBlank(id)) {
			CatalogCategoryRestRep parent = CatalogCategoryUtils
					.getCatalogCategory(uri(id));
			catalogServices = CatalogServiceUtils.getCatalogServices(parent);
		}
		render(catalogServices);
	}

	public static void categories(String id) {
		List<CatalogCategoryRestRep> catalogCategories = null;
		if (StringUtils.isNotBlank(id)) {
			CatalogCategoryRestRep parent = CatalogCategoryUtils
					.getCatalogCategory(uri(id));
			catalogCategories = CatalogCategoryUtils
					.getCatalogCategories(parent);
		}
		render(catalogCategories);
	}

	public static void moveUpService(String id) {
		CatalogServiceRestRep catalogService = CatalogServiceUtils
				.getCatalogService(uri(id));

		CatalogServiceUtils.moveUpService(catalogService.getId());

		services(catalogService.getCatalogCategory().getId().toString());
	}

	public static void moveDownService(String id) {
		CatalogServiceRestRep catalogService = CatalogServiceUtils
				.getCatalogService(uri(id));

		CatalogServiceUtils.moveDownService(catalogService.getId());

		services(catalogService.getCatalogCategory().getId().toString());
	}

	public static void moveUpCategory(String id) {
		CatalogCategoryRestRep catalogCategory = CatalogCategoryUtils
				.getCatalogCategory(uri(id));

		CatalogCategoryUtils.moveUpCategory(catalogCategory.getId());

		categories(catalogCategory.getCatalogCategory().getId().toString());
	}

	public static void moveDownCategory(String id) {
		CatalogCategoryRestRep catalogCategory = CatalogCategoryUtils
				.getCatalogCategory(uri(id));

		CatalogCategoryUtils.moveDownCategory(catalogCategory.getId());

		categories(catalogCategory.getCatalogCategory().getId().toString());
	}

	/**
	 * Gets the ID from a model, or null if the model is null.
	 * 
	 * @param model
	 *            the model.
	 * @return the ID.
	 */
	public static String getId(DataObjectRestRep model) {
		return model != null && model.getId() != null ? model.getId()
				.toString() : null;
	}

	/**
	 * Represents the service edited in the forms.
	 * 
	 * @author jonnymiller
	 */
	public static class ServiceForm extends
			AbstractRestRepForm<CatalogServiceRestRep> {
		/** The category that this edit was launched from. */
		public String fromId;

		@Required
		public String name;

		@Required
		@CheckWith(CatalogServiceNameUniqueCheck.class)
		@MaxSize(128)
		@MinSize(2)
		public String title;

		@Required
		@MaxSize(255)
		public String description;

		@Required
		@MaxSize(255)
		public String image;

		@Required
		@MaxSize(255)
		public String baseService;

		public String owningCategoryId;

		@Min(1)
		public Integer maxSize;

		public Boolean approvalRequired;

		public Boolean executionWindowRequired;

		public String defaultExecutionWindowId;

		public List<AclEntryForm> aclEntries = Lists.newArrayList();

		@CheckWith(ServiceFieldsCheck.class)
		public List<ServiceFieldForm> serviceFields = Lists.newArrayList();

		public ServiceForm() {
		}

		public ServiceForm(CatalogServiceRestRep service) {
			this();
			readFrom(service);
		}

		@Override
		public void doReadFrom(CatalogServiceRestRep service) {
			this.id = service.getId().toString();
			this.name = service.getName();
			this.title = service.getTitle();
			this.description = service.getDescription();
			this.image = service.getImage();
			this.baseService = service.getBaseService();
			this.maxSize = (service.getMaxSize() != null && service
					.getMaxSize() > 0) ? service.getMaxSize() : null;

			if (service.getCatalogCategory() != null) {
				this.owningCategoryId = service.getCatalogCategory().getId()
						.toString();
			}
			this.approvalRequired = service.isApprovalRequired();
			this.executionWindowRequired = service.isExecutionWindowRequired();
			if (service.getDefaultExecutionWindow() != null) {
				this.defaultExecutionWindowId = service
						.getDefaultExecutionWindow().getId().toString();
			}

			this.serviceFields.clear();

			List<CatalogServiceFieldRestRep> catalogServiceFields = service
					.getCatalogServiceFields();
			if (catalogServiceFields != null) {
				for (CatalogServiceFieldRestRep catalogServiceField : catalogServiceFields) {
					ServiceFieldForm serviceFieldForm = new ServiceFieldForm(
							catalogServiceField);
					this.serviceFields.add(serviceFieldForm);
				}
			}

			this.aclEntries.clear();
			this.aclEntries.addAll(ACLUtils
					.convertToAclEntryForms(CatalogServiceUtils.getACLs(id)));
		}

		@Override
		protected void doValidation(String fieldName) {
			ACLUtils.validateAclEntries(fieldName + ".aclEntries",
					this.aclEntries);

			// ensure the service descriptor is valid
			ServiceDescriptorRestRep descriptor = ServiceDescriptorUtils
					.getDescriptor(this.baseService);
			if (descriptor == null) {
				String fieldPath = fieldName + ".baseService";
				Validation.addError(fieldPath, "service.baseService.notFound");
			}
		}

		@Override
		protected CatalogServiceRestRep doCreate() {

			CatalogServiceRestRep catalogService = null;

			try {
				CatalogServiceCreateParam createParam = new CatalogServiceCreateParam();

				writeCommon(createParam);

				catalogService = CatalogServiceUtils
						.createCatalogService(createParam);

				ACLUtils.updateACLs(getCatalogClient().services(),
						catalogService.getId(), this.aclEntries);

			} catch (Exception e) {
				Common.flashException(e);
				Common.handleError();
			}
			return catalogService;
		}

		@Override
		protected CatalogServiceRestRep doUpdate() {

			CatalogServiceRestRep catalogService = null;

			try {
				CatalogServiceUpdateParam updateParam = new CatalogServiceUpdateParam();

				writeCommon(updateParam);

				catalogService = CatalogServiceUtils.updateCatalogService(
						uri(this.id), updateParam);

				ACLUtils.updateACLs(getCatalogClient().services(),
						catalogService.getId(), this.aclEntries);

			} catch (Exception e) {
				Common.flashException(e);
				Common.handleError();
			}

			return catalogService;
		}

		private void writeCommon(CatalogServiceCommonParam commonParam) {
			commonParam.setName(title.replaceAll(" ", ""));
			commonParam.setTitle(title);
			commonParam.setDescription(description);
			commonParam.setImage(image);
			commonParam.setBaseService(baseService);
			if (StringUtils.isNotBlank(owningCategoryId)) {
				CatalogCategoryRestRep parent = CatalogCategoryUtils
						.getCatalogCategory(uri(this.owningCategoryId));
				if (parent != null) {
					commonParam.setCatalogCategory(uri(owningCategoryId));
				}
			}
			commonParam.setApprovalRequired(this.approvalRequired);
			commonParam
					.setExecutionWindowRequired(this.executionWindowRequired);
			commonParam.setMaxSize(this.maxSize != null ? this.maxSize : 0);
			if (StringUtils.isNotBlank(this.defaultExecutionWindowId)
					&& "NEXT".equalsIgnoreCase(this.defaultExecutionWindowId) == false) {
				ExecutionWindowRestRep executionWindow = ExecutionWindowUtils
						.getExecutionWindow(uri(this.defaultExecutionWindowId));
				if (executionWindow != null) {
					commonParam
							.setDefaultExecutionWindow(uri(this.defaultExecutionWindowId));
				} else {
					commonParam.setDefaultExecutionWindow(null);
				}
			} else {
				commonParam.setDefaultExecutionWindow(null);
			}

			for (ServiceFieldForm serviceFieldForm : this.serviceFields) {
				CatalogServiceFieldParam fieldParam = new CatalogServiceFieldParam();
				serviceFieldForm.writeTo(fieldParam);
				commonParam.getCatalogServiceFields().add(fieldParam);
			}
		}

		/*
		 * Used by validation
		 */
		public void writeTo(CatalogServiceRestRep service) {
			service.setName(title.replaceAll(" ", ""));
			service.setTitle(title);
			service.setDescription(description);
			service.setImage(image);
			service.setBaseService(baseService);
			// if (StringUtils.isNotBlank(owningCategoryId)) {
			// CatalogCategoryRestRep parent =
			// CatalogCategoryUtils.getCatalogCategory(uri(this.owningCategoryId));
			// if (parent != null) {
			// service.setCatalogCategoryId(uri(owningCategoryId));
			// }
			// }
			service.setApprovalRequired(this.approvalRequired);
			service.setExecutionWindowRequired(this.executionWindowRequired);
			service.setMaxSize(this.maxSize != null ? this.maxSize : 0);
			// if (this.defaultExecutionWindowId != null) {
			// ExecutionWindowRestRep executionWindow =
			// ExecutionWindowUtils.getExecutionWindow(uri(this.defaultExecutionWindowId));
			// if (executionWindow != null) {
			// commonParam.setDefaultExecutionWindow(uri(this.defaultExecutionWindowId));
			// }
			// else {
			// commonParam.setDefaultExecutionWindow(null);
			// }
			// }
			// else {
			// commonParam.setDefaultExecutionWindow(null);
			// }
			//
			// for (ServiceFieldForm serviceFieldForm : this.serviceFields) {
			// CatalogServiceFieldParam fieldParam = new
			// CatalogServiceFieldParam();
			// serviceFieldForm.writeTo(fieldParam);
			// commonParam.getCatalogServiceFields().add(fieldParam);
			// }
		}

	}

	public static boolean isSameCategory(URI categoryId, String id) {
		return (categoryId != null && categoryId.toString().equals(id));
	}

	public static class ServiceFieldForm {

		@Required
		public String name;

		public boolean override = false;

		public String value;

		public ServiceFieldForm() {
		}

		public ServiceFieldForm(CatalogServiceFieldRestRep catalogServiceField) {
			this();
			doReadFrom(catalogServiceField);
		}

		public void doReadFrom(CatalogServiceFieldRestRep catalogServiceField) {
			this.name = catalogServiceField.getName();
			this.override = catalogServiceField.getOverride();
			this.value = catalogServiceField.getValue();
		}

		public void writeTo(CatalogServiceFieldParam fieldParam) {
			fieldParam.setName(this.name);
			fieldParam.setOverride(this.override);
			fieldParam.setValue(this.value);
		}

	}

	/**
	 * Represents the category edited in the forms.
	 * 
	 * @author jonnymiller
	 */
	public static class CategoryForm extends
			AbstractRestRepForm<CatalogCategoryRestRep> {
		/** The category that this edit was launched from. */
		public String fromId;

		@Required
		public String name;

		@Required
		@MaxSize(128)
		@MinSize(2)
		@CheckWith(CatalogCategoryNameUniqueCheck.class)
		public String title;

		@Required
		@MaxSize(255)
		public String description;

		@Required
		@MaxSize(255)
		public String image;

		public String parentId;

		public List<AclEntryForm> aclEntries = Lists.newArrayList();

		public CategoryForm() {
		}

		public CategoryForm(CatalogCategoryRestRep category) {
			this();
			readFrom(category);
		}

		public boolean isCatalogRoot() {
			return parentId == null;
		}

		@Override
		public void doReadFrom(CatalogCategoryRestRep category) {
			this.name = category.getName();
			this.title = category.getTitle();
			this.description = category.getDescription();
			this.image = category.getImage();
			if (category.getCatalogCategory() != null) {
				this.parentId = category.getCatalogCategory().getId()
						.toString();
			}

			this.aclEntries.clear();
			this.aclEntries.addAll(ACLUtils
					.convertToAclEntryForms(CatalogCategoryUtils
							.getACLs(this.id)));
		}

		@Override
		protected void doValidation(String fieldName) {
			ACLUtils.validateAclEntries(fieldName + ".aclEntries",
					this.aclEntries);
		}

		@Override
		protected CatalogCategoryRestRep doCreate() {
			CatalogCategoryRestRep catalogCategory = null;

			try {
				CatalogCategoryCreateParam createParam = new CatalogCategoryCreateParam();
				createParam.setTenantId(getCategoryTenant());
				writeCommon(createParam);

				catalogCategory = CatalogCategoryUtils
						.createCatalogCategory(createParam);

				ACLUtils.updateACLs(getCatalogClient().categories(),
						catalogCategory.getId(), this.aclEntries);

			} catch (Exception e) {
				Common.flashException(e);
				Common.handleError();
			}

			return catalogCategory;
		}

		@Override
		protected CatalogCategoryRestRep doUpdate() {

			CatalogCategoryRestRep catalogCategory = null;

			try {
				CatalogCategoryUpdateParam updateParam = new CatalogCategoryUpdateParam();

				writeCommon(updateParam);

				catalogCategory = CatalogCategoryUtils.updateCatalogCategory(
						uri(this.id), updateParam);

				ACLUtils.updateACLs(getCatalogClient().categories(),
						catalogCategory.getId(), this.aclEntries);
			} catch (Exception e) {
				Common.flashException(e);
				Common.handleError();
			}

			return catalogCategory;
		}

		private void writeCommon(CatalogCategoryCommonParam commonParam) {
			commonParam.setName(title.replaceAll(" ", ""));
			commonParam.setTitle(title);
			commonParam.setDescription(description);
			commonParam.setImage(image);
			if (!isCatalogRoot()) {
				commonParam.setCatalogCategoryId(uri(parentId));
			}
		}

		private String getCategoryTenant() {
			String tenantId = Models.currentAdminTenant();
			if (StringUtils.isBlank(tenantId)) {
				tenantId = Security.getUserInfo().getTenant();
			}
			return tenantId;
		}
	}

	/**
	 * Class for selecting parent categories.
	 * 
	 * @author jonnymiller
	 */
	public static class CategoryPath implements Comparable<CategoryPath> {
		public String id;
		public String title;
		public String label;
		public String path;

		public CategoryPath(CategoryDef category) {
			this.id = category.id;
			this.title = category.title;
			this.path = category.path;

			int depth = StringUtils.countMatches(path, "/");
			this.label = StringUtils.repeat("-", depth) + " " + category.title;
		}

		@Override
		public int compareTo(CategoryPath arg0) {
			return path.compareToIgnoreCase(arg0.path);
		}
	}

	/**
	 * Determines if the service name is unique within a parent category.
	 * 
	 * @param id
	 *            the service ID, if editing a service.
	 * @param name
	 *            the service name.
	 * @param parentId
	 *            the parent category ID.
	 * @return true if the service name is unique.
	 */
	private static boolean isUniqueServiceName(String id, String name,
			String parentId) {
		CatalogCategoryRestRep parentCatalogCategory = CatalogCategoryUtils
				.getCatalogCategory(uri(parentId));
		if (parentCatalogCategory != null) {
			List<CatalogServiceRestRep> catalogServices = CatalogServiceUtils
					.getCatalogServices(parentCatalogCategory);
			for (CatalogServiceRestRep catalogService : catalogServices) {
				if (catalogService.getId().toString().equals(id) == false
						&& name.equalsIgnoreCase(catalogService.getName())) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Determines if the category name is unique within a parent category.
	 * 
	 * @param id
	 *            the category ID.
	 * @param name
	 *            the category name.
	 * @param parentId
	 *            the parent category ID.
	 * @return true if the category name is unique.
	 */
	private static boolean isUniqueCategoryName(String id, String name,
			String parentId) {
		if (parentId == null) {
			return true;
		}
		CatalogCategoryRestRep parentCatalogCategory = CatalogCategoryUtils
				.getCatalogCategory(uri(parentId));
		if (parentCatalogCategory != null) {
			List<CatalogCategoryRestRep> subCatalogCategories = CatalogCategoryUtils
					.getCatalogCategories(parentCatalogCategory);
			for (CatalogCategoryRestRep subCatalogCategory : subCatalogCategories) {
				if (subCatalogCategory.getId().toString().equals(id) == false
						&& name.equalsIgnoreCase(subCatalogCategory.getName())) {
					return false;
				}
			}
		}
		return true;
	}

	public class CatalogServiceNameUniqueCheck extends Check {

		@Override
		public boolean isSatisfied(Object validatedObject, Object value) {
			if (validatedObject instanceof ServiceForm) {
				ServiceForm serviceForm = (ServiceForm) validatedObject;
				String title = (String) value;

				if (title != null) {
					String name = title.replaceAll(" ", "");
					if (isUniqueName(serviceForm, name)) {
						return true;
					}
				}

			}
			setMessage("catalogService.name.notUnique");
			return false;
		}

		/**
		 * Determines if the service name is unique within the parent category.
		 * 
		 * @param service
		 *            the service form input.
		 * @param name
		 *            the service name.
		 * @return true if the name is unique.
		 */
		private boolean isUniqueName(ServiceForm service, String name) {
			String parentId = service.owningCategoryId;
			return isUniqueServiceName(service.id, name, parentId)
					&& isUniqueCategoryName(null, name, parentId);
		}
	}

	public class ServiceFieldsCheck extends Check {
		public ServiceFieldsCheck() {
			setMessage("service.field.validation");
		}

		@Override
		public boolean isSatisfied(Object validatedObject, Object value) {
			if (validatedObject instanceof ServiceForm) {
				ServiceForm serviceForm = (ServiceForm) validatedObject;
				List<ServiceFieldForm> serviceFields = (List<ServiceFieldForm>) value;

				// The catalog service may have not been saved, create a
				// temporary one
				CatalogServiceRestRep catalogServiceTemp = new CatalogServiceRestRep();
				serviceForm.writeTo(catalogServiceTemp);

				ServiceDescriptorRestRep serviceDescriptor = ServiceDescriptorUtils
						.getDescriptor(serviceForm.baseService);
				if (serviceDescriptor != null) {
					for (ServiceFieldForm field : serviceFields) {
						if (!field.override) {
							continue;
						}
						String fieldName = field.name;
						String fieldValue = field.value;

						ServiceFieldRestRep descriptorField = ServiceDescriptorUtils
								.getField(serviceDescriptor, fieldName);
						if (descriptorField != null) {
							ServiceFieldValidator.validateField(
									catalogServiceTemp, descriptorField,
									fieldValue);
						}
					}
				}
			}
			return true;
		}
	}

	public class CatalogCategoryNameUniqueCheck extends Check {

		@Override
		public boolean isSatisfied(Object validatedObject, Object value) {
			if (validatedObject instanceof CategoryForm) {
				CategoryForm categoryForm = (CategoryForm) validatedObject;
				if (categoryForm.isCatalogRoot()) {
					return true;
				}
				String title = (String) value;

				if (title != null) {
					String name = title.replaceAll(" ", "");
					if (isUniqueName(categoryForm, name)) {
						return true;
					}
				}
			}
			setMessage("catalogCategory.name.notUnique");
			return false;
		}

		/**
		 * Determines if the category name is unique within the parent category.
		 * 
		 * @param category
		 *            the category form input.
		 * @return true if the name is unique.
		 */
		private boolean isUniqueName(CategoryForm category, String name) {
			String parentId = category.parentId;
			return isUniqueCategoryName(category.id, name, parentId)
					&& isUniqueServiceName(null, name, parentId);
		}
	}

	public static class ServiceDescriptorComparator implements
			Comparator<ServiceDescriptorRestRep> {
		@Override
		public int compare(ServiceDescriptorRestRep a,
				ServiceDescriptorRestRep b) {
			int result = stringCompare(a.getCategory(), b.getCategory());
			if (result == 0) {
				result = stringCompare(a.getDescription(), b.getDescription());
			}
			return result;
		}

		private int stringCompare(String a, String b) {
			a = StringUtils.defaultString(a);
			b = StringUtils.defaultString(b);
			int result = a.compareTo(b);
			if (result == 0) {
				return a.compareToIgnoreCase(b);
			}
			return result;
		}
	}

	public static class CorruptedServiceDescriptor extends
			ServiceDescriptorRestRep {

		private static final String UNKNOWN_VALUE = "UNKNOWN";

		public CorruptedServiceDescriptor() {
			setServiceId(UNKNOWN_VALUE);
			setCategory(UNKNOWN_VALUE);
			setTitle(UNKNOWN_VALUE);
			setDescription(UNKNOWN_VALUE);
		};
	}

	public static void updateCatalog() throws Exception {
		CatalogCategoryUtils.upgradeCatalog();
		catalogModified(Models.currentAdminTenant());
		flash.success(Messages.get("EditCatalog.updated"));
		edit();
	}

	/**
	 * Handles errors that might arise during JSON requests and returns the
	 * error message.
	 * 
	 * @param e
	 */
	@Catch({ UnexpectedException.class, ViPRException.class })
	public static void handleJsonError(Exception e) {
		if (request.isAjax()
				|| StringUtils.endsWithIgnoreCase(request.action, "json")) {
			Throwable cause = Common.unwrap(e);
			String message = Common.getUserMessage(cause);
			Logger.error(e, "AJAX request failed: %s.%s [%s]",
					request.controller, request.action, message);
			error(message);
		}
	}

}