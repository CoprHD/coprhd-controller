module Page
  class CatalogOrder
    def initialize(service)
      @service = service
    end

    def create(options={})
      visit '/Catalog#ServiceCatalog'

    end
  end
end
