/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import models.datatable.CatalogImageDataTable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.libs.MimeTypes;
import play.mvc.Controller;
import play.mvc.With;
import util.CatalogImageUtils;
import util.MessagesUtils;
import util.ValidationResponse;
import util.datatable.DataTablesSupport;

import com.emc.vipr.model.catalog.CatalogImageCommonParam;
import com.emc.vipr.model.catalog.CatalogImageCreateParam;
import com.emc.vipr.model.catalog.CatalogImageRestRep;
import com.emc.vipr.model.catalog.CatalogImageUpdateParam;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN")})
public class CatalogImages extends Controller {
    protected static final String SAVED = "CatalogImage.saved";
    protected static final String DELETED = "CatalogImage.deleted";

    public static void view(String id) {
        CatalogImageRestRep image = CatalogImageUtils.getCatalogImage(uri(id));
        String name = image.getName();
        String contentType = image.getContentType();
        byte[] data = image.getData();
        int length = data.length;
        renderBinary(new ByteArrayInputStream(data), name, length, contentType, false);
    }

    
    public static void list() {
        CatalogImageDataTable dataTable = new CatalogImageDataTable();
        render(dataTable);
    }

    public static void listJson() {
        
        List<CatalogImageDataTable.ImageInfo> imageInfos = Lists.newArrayList();
        List<CatalogImageRestRep> catalogImages = CatalogImageUtils.getCatalogImages();
        for (CatalogImageRestRep catalogImage : catalogImages) {
            imageInfos.add(new CatalogImageDataTable.ImageInfo(catalogImage));
        }

        renderJSON(DataTablesSupport.createJSON(imageInfos, params));
    }

    public static void create() {
        render("@edit");
    }

    public static void edit(String id) {
        CatalogImageRestRep image = CatalogImageUtils.getCatalogImage(uri(id));
        String name = image.getName();
        render(id, name);
    }

    public static void delete(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                CatalogImageUtils.deleteCatalogImage(uri(id));
            }
            flash.success(MessagesUtils.get(DELETED));
        }
        list();
    }

    public static void save(String id, String name, File file, String referrerUrl) {
        if (StringUtils.isBlank(id) || file == null) {
            Validation.required("file", file);
        }
        
        if (file != null) {
            String contentType = MimeTypes.getContentType(file.getName());
            if (!StringUtils.startsWith(contentType, "image/")) {
                Validation.addError("file", Messages.get("catalogImage.invalidContentType", contentType));
            }     
        }

        if (Validation.hasErrors()) {
            Common.handleError();
        }

        CatalogImageRestRep image = save(id, name, file);
        flash.success(MessagesUtils.get(SAVED, image.getId(), image.getName()));
        
        if (!StringUtils.isEmpty(referrerUrl)) {
            redirect(referrerUrl);
        } else {
            list();
        }        
        
    }

    public static void saveJson(String name, @Required File file) {
        if (Validation.hasErrors()) {
            List<ValidationResponse> response = ValidationResponse.collectErrors();
            renderJSON(response);
        }

        CatalogImageRestRep image = save(null, name, file);
        renderJSON(ValidationResponse.valid(image.getId().toString()));
    }

    private static CatalogImageRestRep save(String id, String name, File file) {
        CatalogImageRestRep catalogImage = null;
        if (StringUtils.isNotBlank(id)) {
            CatalogImageUpdateParam updateParam = new CatalogImageUpdateParam();
            writeCommon(name, file, updateParam);
            catalogImage = CatalogImageUtils.updateCatalogImage(uri(id), updateParam);
        }
        else {
            CatalogImageCreateParam createParam = new CatalogImageCreateParam();
            createParam.setTenant(uri(Models.currentAdminTenant()));
            writeCommon(name, file, createParam);
            catalogImage = CatalogImageUtils.createCatalogImage(createParam);
        }
        return catalogImage;
    }
    
    private static void writeCommon(String name, File file, CatalogImageCommonParam commonParam) {
        if (file != null) {
            String label = StringUtils.defaultIfBlank(name, StringUtils.substringBeforeLast(file.getName(), "."));
            String contentType = MimeTypes.getContentType(file.getName());
            byte[] data = read(file);
            
            commonParam.setName(label);
            commonParam.setContentType(contentType);
            commonParam.setData(data);
        }
        else {
            commonParam.setName(name);
        }
    }

    private static byte[] read(File file) {
        byte[] data = null;
        try {
            data = FileUtils.readFileToByteArray(file);
        }
        catch (IOException e) {
            error(e);
        }
        return data;
    }
}
