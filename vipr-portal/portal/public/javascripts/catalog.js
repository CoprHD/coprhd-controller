function Catalog(authentiticyToken) {
    var categorySelector = '.catalog-items';
    var breadcrumbSelector = '.breadcrumb';
    var catalogItemSelector = '.catalog-item';
    var categoryItemSelector = '.category-item';
    var serviceItemSelector = '.service-item';
    var my = {};
    
    function getVisibleCategoryId() {
        return $(categorySelector+':visible').first().data('id');
    }
    function getVisibleCategoryPath() {
        return $(categorySelector+':visible').first().data('path');
    }
    function getRootCategoryId() {
        return $(categorySelector+'[data-parent=""]').first().data('id');
    }
    function getRootCategoryPath() {
        return $(categorySelector+'[data-parent=""]').first().data('path');
    }
    function getPathForId(id) {
        return $(categorySelector+'[data-id="'+id+'"]').data('path');
    }
    function getIdForPath(path) {
        return $(categorySelector+'[data-path="'+path+'"]').data('id');
    }
    function getLocationCategoryPath() {
        var hash = window.location.hash;
        if (hash && hash.length > 1) {
            return hash.substring(1);
        }
        else {
            return null;
        }
    }
    function isValidCategoryPath(path) {
        return $(categorySelector+'[data-path="'+path+'"]').length > 0;
    }
    function isValidCategoryId(id) {
        return $(categorySelector+'[data-id="'+id+'"]').length > 0;
    }
    function showRootCategory() {
        showCategory(getRootCategoryId());
    }
    function showBreadCrumb(id) {
        $(breadcrumbSelector+':visible').hide();
        $(breadcrumbSelector+'[data-id="'+id+'"]').show();
    }
    function showCategoryById(id) {
        var currentCategory = $(categorySelector+':visible');
        var selectedCategory = $(categorySelector+'[data-id="'+id+'"]');
        
        if (currentCategory.length > 0) {
            currentCategory.fadeOut(100, function() {
                showBreadCrumb(id);
                selectedCategory.fadeIn(100);
            });
        }
        else {
            showBreadCrumb(id);
            selectedCategory.show();
        }
    }
    function post(url) {
        var authenticityField = "<input type='hidden' name='authenticityToken' value='"+authentiticyToken+"'>";
        var form = $("<form action=\""+url+"\" method=\"post\" style=\"display:none\">"+authenticityField+"</form>").appendTo('body')[0];
        form.submit();
        form.remove();
    }
    function showCategory(id) {
        var valid = isValidCategoryId(id);
        var notDisplayed = (id != getVisibleCategoryId());
        
        if (valid && notDisplayed) {
            showCategoryById(id);
            var path = getPathForId(id);
            window.location.hash = path;
            return true;
        }
        else {
            return false;
        }
    }
    function addNavigationHandling() {
        $(window).on('hashchange', function() {
            my.updateLocation();
        });
        $(document).ready(function() {
            my.updateLocation();
        });
    }
    
    
    my.getCurrentCategoryId = function() {
        return getVisibleCategoryId();
    }
    my.showCategory = function(id) {
        return showCategory(id);
    }
    my.showService = function(id, back) {
        var url = routes.ServiceCatalog_showService({serviceId: id});
        if (back) {
            if (url.indexOf("?") > -1) {
                url += "&return="+back;
            }
            else {
                url += "?return="+back;
            }
        }
        window.location.href = url;
    }
    my.createService = function(parentId) {
        var fromId = my.getCurrentCategoryId();
        var url = routes.ServiceCatalog_createService({parentId: parentId, fromId: fromId});
        window.location.href = url;
    }
    my.editService = function(id) {
        var fromId = my.getCurrentCategoryId();
        var url = routes.ServiceCatalog_editService({serviceId: id, fromId: fromId});
        window.location.href = url;
    }
    my.copyService = function(id) {
        var fromId = my.getCurrentCategoryId();
        var url = routes.ServiceCatalog_copyService({serviceId: id, fromId: fromId});
        window.location.href = url;
    }
    my.deleteService = function(id) {
        var url = routes.ServiceCatalog_deleteService({serviceId: id});
        post(url);
    }
    my.createCategory = function(parentId) {
        var fromId = my.getCurrentCategoryId();
        var url = routes.ServiceCatalog_createCategory({parentId: parentId, fromId: fromId});
        window.location.href = url;
    }
    my.editCategory = function(id) {
        var fromId = my.getCurrentCategoryId();
        var url = routes.ServiceCatalog_editCategory({categoryId: id, fromId: fromId});
        window.location.href = url;
    }
    my.deleteCategory = function(id) {
        var url = routes.ServiceCatalog_deleteCategory({categoryId: id});
        post(url);
    }
    my.restoreCatalog = function() {
        var url = routes.ServiceCatalog_restoreCatalog();
        post(url);
    }
    my.updateCatalog = function() {
        var url = routes.ServiceCatalog_updateCatalog();
        post(url);
    }
    my.findItem = function(context) {
        return $(context).closest(catalogItemSelector);
    }
    my.findData = function(context, name) {
        return my.findItem(context).data(name);
    }
    my.findId = function(context) {
        return my.findData(context, 'id');
    }
    my.updateLocation = function() {
        var desiredId = getIdForPath(getLocationCategoryPath());
        if (desiredId) {
            showCategory(desiredId);
        }
        else {
            showRootCategory();
        }
    }

    /**
     * Initializes view mode.
     */
    my.initViewMode = function(selector) {
        addNavigationHandling();
        var container = $(selector);
        container.on('click', categoryItemSelector, function(event) {
            var id = my.findId(this);
            my.showCategory(id);
        });
        container.on('click', serviceItemSelector, function(event) {
            var id = my.findId(this);
            my.showService(id);
        });
    }
    
    /**
     * Initializes edit mode.
     */
    my.initEditMode = function(selector, deleteServiceConfirm, deleteCategoryConfirm) {
        addNavigationHandling();
        var container = $(selector);
        container.on('click', categoryItemSelector, function(event) {
            var id = my.findId(this);
            my.showCategory(id);
        });
        container.on('click', serviceItemSelector, function(event) {
            var id = my.findId(this);
            my.editService(id);
        });
        
        container.on('click', serviceItemSelector+' .edit.btn', function(event) {
            event.stopPropagation();
            var id = my.findId(this);
            my.editService(id);
        });
        container.on('click', serviceItemSelector+' .copy.btn', function(event) {
            event.stopPropagation();
            var id = my.findId(this);
            my.copyService(id);
        });
        container.on('click', serviceItemSelector+' .delete.btn', function(event) {
            event.stopPropagation();
            var id = my.findId(this);
            var title = my.findData(this, 'title');
            var message = deleteServiceConfirm.replace("%s", title);
            if (confirm(message)) {
                my.deleteService(id);
            }
        });
        
        container.on('click', categoryItemSelector+' .edit.btn', function(event) {
            event.stopPropagation();
            var id = my.findId(this);
            my.editCategory(id);
        });
        container.on('click', categoryItemSelector+' .delete.btn', function(event) {
            event.stopPropagation();
            var id = my.findId(this);
            var title = my.findData(this, 'title');
            var message = deleteCategoryConfirm.replace("%s", title);
            if (confirm(message)) {
                my.deleteCategory(id);
            }
        });
    }
    
    /**
     * Initializes standalone mode. 
     */
    my.initStandaloneMode = function(selector) {
        var container = $(selector);
        container.on('click', serviceItemSelector, function(event) {
            var id = my.findId(this);
            my.showService(id, window.location.pathname);
        });
    }
    
    return my;
}
