When(/^they order a volume$/) do
  steps %Q{
    Given they are logged in as the root user
    Given they visit the Service Catalog
    Given they select the emcworld tenant
    Given they choose Block Storage Services
    Given they choose Create Block Volume
  }

  select_option 'virtualArray', 'nh'
  select_option 'virtualPool', @vpool_subject['name']
  select_option 'project', 'project'

  @volume_name = "test-#{rand(1000..9999)}"
  set_volume_name @volume_name
  set_volume_size 1

  step "they click Order"
end

Then(/^the order should succeed$/) do
  expect(page).to have_css('span#orderStatus', text: 'Order Successfully Fulfilled', wait: 60)
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
