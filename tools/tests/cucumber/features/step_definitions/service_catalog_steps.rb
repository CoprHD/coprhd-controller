Given(/^the customer is logged in as root$/) do
  step "they are logged in as the root user"
end

When(/^they order a volume using the Create Block Volume catalog service$/) do |table|
  visit_create_block_volume_catalog

  data = table.hashes.first

  select_option 'virtualArray', 'nh'
  select_option 'virtualPool', /^#{data['Virtual Pool']}$/
  select_option 'project', data['Project']
  if data['RDF Group'] == "any"
    select_first_option 'rdfGroup', /0 Vols/
  end

  @volume_name = "test-#{rand(1000..9999)}"
  set_volume_name @volume_name
  set_volume_size 1

  step "they click Order"
end

When(/^they order a volume$/) do |table|
  pending
  order_page = Page::CatalogOrder.new('BlockStorageServices/CreateBlockVolume')

  data = table.rows_hash
  options = {
    tenant: 'emcworld',
    virtual_array: 'nh',
    virtual_pool:  nil,
    project: data['Project']
  }
  if data['Replication Group']
    options['rdfGroup'] = /0 Vols/
  end

  order_page.create(options)
end

Then(/^the order should succeed$/) do
  expect(page).to have_css('span#orderStatus', text: 'Order Successfully Fulfilled', wait: 60)
end

Then(/^the order should not succeed$/) do
  expect(page).to have_css('span#orderStatus', text: 'Error Occurred Processing Order', wait: 60)
end

def visit_create_block_volume_catalog
  steps %Q{
    Given they visit the Service Catalog
    Given they select the emcworld tenant
    Given they choose Block Storage Services
    Given they choose Create Block Volume
  }
end

def select_first_option(name, value)
  find(input_selector(name)).click
  first(input_value_selector(name), text: value).click
end

def select_option(name, value)
  find(input_selector(name)).click
  find(input_value_selector(name), text: value).click
end

def set_volume_name(name)
  fill_in 'volumes[0].name', with: name
end

def set_volume_size(size)
  fill_in 'volumes[0].size', with: '1'
end

def input_selector name
  "input[name=#{name}]:enabled + div"
end

def input_value_selector name
  "#{input_selector(name)} ul.chosen-results > li"
end
