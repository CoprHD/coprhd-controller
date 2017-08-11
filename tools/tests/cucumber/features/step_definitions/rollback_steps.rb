Given(/^I have a CoprHD setup for (.+) rollback testing$/) do |system|
  setup system: system
end

Given(/^I am logged in as ([^\s]+)$/) do |user|
  login user
end

When(/^I create a volume that fails at the final workflow step$/) do
  snapshot_database
  set_artificial_failure 'failure_004'
  @name = "volume-#{rand(9999)}"
  create_block_volume name: @name
end

When(/^I create a volume that fails with (.*)$/) do |failure|
  snapshot_database slot: 1, cfs: 'Volume'
  set_artificial_failure failure
  @name = "volume-#{rand(9999)}"
  create_block_volume name: @name
end

#Then(/^the order should fail$/) do
#  verify_last_task_failed!
#end

Then(/^the database should not have left anything behind$/) do
  snapshot_database slot: 2, cfs: 'Volume'
  validate_db slot_1: 1, slot_2: 2, cfs: 'Volume'
end

Then(/^I can retry the order successfully$/) do
  clear_artificial_failures
  create_block_volume name: @name
  verify_last_task_succeeded!
end

Then(/^retrying the order will fail with '(.*)'$/) do |error|
  clear_artificial_failures
  create_block_volume name: @name
  verify_last_task_failed! with_message: error
end
