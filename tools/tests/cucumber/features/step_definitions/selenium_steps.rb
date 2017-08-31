Given(/^I have not set the IN_BROWSER environment variable$/) do
  expect(ENV['IN_BROWSER']).to be_nil
end

Given(/^I have set the IN_BROWSER environment variable$/) do
  expect(ENV['IN_BROWSER']).to_not be_nil
end

When(/^I navigate to my app host/) do 
  step "I navigate to \"#{Capybara.app_host}\""
end

When(/^I navigate to "([^"]*)"$/) do |host|
  visit host
  fill_in 'username', with: 'root'
  fill_in 'password', with: APP_PASSWORD
end

Then(/^I should see "([^"]*)"$/) do |arg1|
  expect(page).to have_content(arg1)
end

