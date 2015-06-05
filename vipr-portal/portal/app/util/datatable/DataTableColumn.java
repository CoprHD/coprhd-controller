/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.datatable;

public class DataTableColumn {
    private String name;
    private String cssClass;
    private boolean sortable;
    private boolean visible;
    private String renderFunction;
    private boolean useRendered;
    private String property;
    private int sortDataColumn;
    private boolean searchable;

    public DataTableColumn(String name) {
        this.name = name;
        this.cssClass = name.replaceAll("\\.", "_");
        this.sortable = false;
        this.visible = true;
        this.property = name;
        this.sortDataColumn = -1;
        this.searchable = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    public boolean isSortable() {
        return sortable;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getRenderFunction() {
        return renderFunction;
    }

    public void setRenderFunction(String renderFunction) {
        this.renderFunction = renderFunction;
    }

    public boolean isUseRendered() {
        return useRendered;
    }

    public void setUseRendered(boolean useRendered) {
        this.useRendered = useRendered;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }


    public int getSortDataColumn() {
        return sortDataColumn;
    }

    /**
     Sets the data the sort will take place on to a column other than this one.
     This is useful if you have a text representation of a number, but sorting the text would give the wrong
     sort order.  You can specify the sort to take place on a column containing the numerical value.
     */
    public void setSortDataColumn(int sortDataColumn) {
        this.sortDataColumn = sortDataColumn;
    }

	public boolean isSearchable() {
		return searchable;
	}

	public void setSearchable(boolean searchable) {
		this.searchable = searchable;
	}
        
}
