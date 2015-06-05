/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.datatable;

public class RenderedColumn extends DataTableColumn {
    public RenderedColumn(String name, String renderFunction) {
        super(name);
        setProperty(null);
        setSortable(false);
        setRenderFunction(renderFunction);
    }

    public RenderedColumn(String name, String renderFunction, String cssClass) {
        this(name, renderFunction);
        setCssClass(cssClass);
    }
}
