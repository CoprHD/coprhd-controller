When(/^they order a volume$/) do
  steps %Q{
    Given they are logged in as the root user
    Given they visit the Service Catalog
    Given they select the emcworld tenant
    Given they choose Block Storage Services
    Given they choose Create Block Volume
  }

  select_virtual_array 'nh'
  select_virtual_pool @vpool_subject['name']
  select_project 'project'

  set_volume_name 'test'
  set_volume_size 1

  step "they click Order"
end

Then(/^the order should succeed$/) do
  expect(page).to have_css('span#orderStatus', text: 'Order Successfully Fulfilled', wait: 60)
end

def select_virtual_array(name)
  # Select the 'nh' virtual array
  find('input[name=virtualArray]:enabled + div').click
  find('input[name=virtualArray]:enabled + div ul.chosen-results > li', text: name).click
end

def select_virtual_pool(name)
  # Select a virtual pool
  find('input[name=virtualPool]:enabled + div').click
  find('input[name=virtualPool]:enabled + div ul.chosen-results > li', text: name).click
end

def select_project(name)
  # Select the first project in the dropdown
  find('input[name=project]:enabled + div').click
  first('input[name=project]:enabled + div ul.chosen-results > li', text: name).click
end

def set_volume_name(name)
  fill_in 'volumes[0].name', with: name
end

def set_volume_size(size)
  fill_in 'volumes[0].size', with: '1'
end
