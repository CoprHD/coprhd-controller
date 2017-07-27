Given(/^I visit the Create Block Volume service catalog$/) do
  steps %Q{
    Given I visit the Service Catalog
    And I select the emcworld tenant
    And I choose Block Storage Services
    And I choose Create Block Volume
  }
end

When(/^I request a single volume with name (.*)$/) do |name|
  # Select the 'nh' virtual array
  find('input[name=virtualArray]:enabled + div').click
  find('li', text: 'nh').click

  # Select a virtual pool
  find('input[name=virtualPool]:enabled + div').click
  find('input[name=virtualPool]:enabled + div li', text: /vpool$/).click

  # Select the first project in the dropdown
  find('input[name=project]:enabled + div').click
  first('input[name=project]:enabled + div li').click

  fill_in 'volumes[0].name', with: name
  fill_in 'volumes[0].size', with: '1'

  sleep 5
end

When(/^(?:I )?click Order$/) do
  find('span', text: 'Order').click
end

Then(/^I(?: should)? see the order succeed$/) do
  expect(page).to have_content('Order Successfully Fulfilled', wait: 60)
end

Then(/^I(?: should)? see the order fail$/) do
  expect(page).to have_content('Error Occurred Processing Order', wait: 120)
end

Given(/^I visit the Service Catalog$/) do
  visit '/Catalog#ServiceCatalog'
  expect(page).to have_current_path('/Catalog')
end

Given(/^I select the emcworld tenant$/) do
  find('#tenantId_chosen').click
  find('li', text: 'emcworld').click
end

Given(/^I choose Block Storage Services$/) do
  find('.catalog-item[data-name=BlockStorageServices]').click
end

Given(/^I choose Create Block Volume$/) do
  find('.catalog-item[data-name=CreateBlockVolume]').click
end

When(/^I retry the order$/) do
  step 'I set artificial failure none'
  click_button 'Order'
  sleep 5
  step 'click Order'
end
