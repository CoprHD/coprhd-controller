Given(/^I am logged in as the root user$/) do
  login 'root'
end

Given(/^they are logged in as the root user$/) do
  login 'root'
end

When(/^I complete the Initial Setup form$/) do
  setup
end

Then(/^I should be taken to the Dashboard page$/) do
  expect(page).to have_current_path('/dashboard', only_path: true)
end
