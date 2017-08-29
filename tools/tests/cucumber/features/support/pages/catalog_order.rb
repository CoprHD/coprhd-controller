module Page
  class CatalogOrder
    include RSpec::Matchers
    include Capybara::DSL

    attr_accessor :tenant
    attr_accessor :category
    attr_accessor :service
    attr_accessor :fields
    attr_accessor :textfields

    def order!
      visit_catalog
      select_tenant if @tenant
      choose_category
      choose_service
      fill_in_fields unless fields.empty?
      fill_in_text_fields unless textfields.empty?
      fill_in_volumes unless volumes.empty?
      fill_in_datastores unless datastores.empty?

      click_order
    end

    def fields
      @fields ||= []
    end

    def textfields
      @textfields ||= []
    end

    def volumes
      @volumes ||= []
    end

    def datastores
      @datastores ||= []
    end

    def successful?
      has_css?('span#orderStatus', text: 'Order Successfully Fulfilled', wait: 900)
    end
    
    def has_failures?
      has_css?('span#orderStatus', text: 'Error Occurred Processing Order', wait: 900)
    end

    private

    def visit_catalog
      visit '/Catalog#ServiceCatalog'
    end

    def select_tenant
      find('#tenantId_chosen').click
      find('ul.chosen-results > li', text: /^#{@tenant}$/).click
    end

    def choose_category
      choose_catalog_item @category
    end

    def choose_service
      choose_catalog_item @service
    end

    def choose_catalog_item item
      find(".catalog-item[data-name=#{item}]").click
    end

    # TODO Refactor!
    def fill_in_fields
      @fields.each do |name, value|
        input = find(input_selector(name), visible: false, match: :first)
        # Open the dropdown menu, unless it's a checkbox
        if input[:type] == 'checkbox'
          input.click
          next
        else
          input = find(input_div_selector(name))
        end       
	input.click

        if value == :first
          first(input_value_selector(name)).click
        elsif value.is_a? Regexp
          first(input_value_selector(name), text: value).click
        else
          find(input_value_selector(name), text: value).click
        end
        sleep 4
      end
    end

    def fill_in_text_fields
      @textfields.each do |name, value|
	fill_in name , :with => value
      end
    end

    def fill_in_volumes
      @volumes.each_with_index do |vol_hash, i|
        vol_hash.each { |k,v| fill_in "volumes[#{i}].#{k}", with: v }
      end
    end

    def fill_in_datastores
      @datastores.each_with_index do |datastore_hash, i|
        datastore_hash.each { |k,v| fill_in "datastores[#{i}].#{k}", with: v }
      end
    end

    def input_selector name
      "input[name=#{name}]:enabled"
    end

    def input_div_selector name
      "#{input_selector(name)} + div"
    end

    def input_value_selector name
      "#{input_div_selector(name)} ul.chosen-results > li"
    end

    def click_order
      # COP-34140: The UI enables Order too quickly.
      sleep 2
      find('button[type=submit] span', text: 'Order').click
    end
  end
end
