Given(/^the customer is logged in as root$/) do
  step "they are logged in as the root user"
end

Given(/^they have ordered a volume using the Create Block Volume catalog service$/) do |table|
  step 'they order a volume using the Create Block Volume catalog service', table
  step 'the order should succeed'
end

When(/^they order a volume using the Create Block Volume catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new

  @order.tenant = 'emcworld'
  @order.category = 'BlockStorageServices'
  @order.service = 'CreateBlockVolume'
  fields = {
    'virtualArray' => 'nh',
    'virtualPool' => /^#{data['Virtual Pool']}$/,
    'project' => data['Project'],
  }

  rdfg = data['RDF Group']
  if rdfg && rdfg != 'none'
    fields['rdfGroup'] = /0 Vols/ 
  end

  @order.fields = fields

  @last_vol_name = "test-#{rand(1000..9999)}"
  @order.volumes << { 'name' => @last_vol_name, 'size' => 1 }

  @order.order!
end

When(/^they add SRDF using the Change Volume Virtual Pool catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new
  @order.tenant = 'emcworld'
  @order.category = 'BlockStorageServices'
  @order.service = 'ChangeVolumeVirtualPool'
  fields = {
    'project' => 'NPRDF19',
    'volume' => /#{@last_vol_name}/,
    'virtualPoolChangeOperation' => 'Add SRDF Protection',
    'targetVirtualPool' => /#{data['To']}/
  }

  rdfg = data['RDF Group']
  if rdfg && rdfg != 'none'
    fields['rdfGroup'] = /0 Vols/ 
  end

  @order.fields = fields

  @order.order!
end

When(/^they add SRDF using the Change Virtual Pool catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new
  @order.tenant = 'emcworld'
  @order.category = 'BlockStorageServices'
  @order.service = 'ChangeVirtualPool'
  fields = {
    'project' => 'NPRDF19',
    'virtualPool' => 'vpool4change',
    'volumeFilter' => 'All Volumes',
    'volume' => /#{@last_vol_name}/,
    'virtualPoolChangeOperation' => 'Add SRDF Protection',
    'targetVirtualPool' => /#{data['To']}/
  }

  rdfg = data['RDF Group']
  if rdfg && rdfg != 'none'
    fields['rdfGroup'] = /0 Vols/ 
  end

  @order.fields = fields

  @order.order!
end

Then(/^the order should succeed$/) do
  expect(@order).to be_successful
end

Then(/^the order should fail$/) do
  expect(@order).to have_failures
end
