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

When(/^they order a volume and VMware datastore using the Create Volume and VMware Datastore catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new

  @order.tenant = 'emcworld'
  @order.category = 'BlockServicesforVMwarevCenter'
  @order.service = 'CreateVolumeandDatastore'
  fields = {
    'vcenter' => 'vcenter1',
    'datacenter' => //,
    'blockStorageType' => 'Shared',
    'host' => //,
    'virtualArray' => 'nh',
    'virtualPool' => /^#{data['Virtual Pool']}$/,
    'project' => data['Project'],
  }

  rdfg = data['RDF Group']
  if rdfg && rdfg != 'none'
    fields['rdfGroup'] = /0 Vols/ 
  end

  @order.fields = fields

  @datastore_name = "ds-#{rand(1000..9999)}"
  @last_vol_name = "test-#{rand(1000..9999)}"
  @order.datastores << { 'datastoreName' => @datastore_name, 'name' => @last_vol_name, 'size' => 1 }

  @order.order!
end

When(/^they order a volume using the Create Volume for VMware catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new

  @order.tenant = 'emcworld'
  @order.category = 'BlockServicesforVMwarevCenter'
  @order.service = 'CreateVolumeforVMware'
  fields = {
    'vcenter' => 'vcenter1',
    'datacenter' => //,
    'blockStorageType' => 'Shared',
    'host' => //,
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

When(/^they order a volume using the Create and Mount Volume on Linux Host catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new

  @order.tenant = 'emcworld'
  @order.category = 'BlockServicesforLinux'
  @order.service = 'CreateAndMountBlockVolume'
  fields = {
    'host' => 'linuxhost1',
    'virtualArray' => 'nh',
    'virtualPool' => /^#{data['Virtual Pool']}$/,
    'project' => data['Project'],
    'fileSystemType' => 'ext3',
  }

  textfields = {
    'name' => "test-#{rand(1000..9999)}",
    'size' => 1,
    'mountPoint' => "/mnt/share#{rand(10..99)}",    
  }

  rdfg = data['RDF Group']
  if rdfg && rdfg != 'none'
    fields['rdfGroup'] = /0 Vols/ 
  end

  @order.fields = fields
  @order.textfields = textfields

  @order.order!
end

When(/^they order a volume using the Create and Mount Volume on HP-UX Host catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new

  @order.tenant = 'emcworld'
  @order.category = 'BlockServicesforHP-UX'
  @order.service = 'CreateAndMountBlockVolume'
  fields = {
    'host' => //,
    'virtualArray' => 'nh',
    'virtualPool' => /^#{data['Virtual Pool']}$/,
    'project' => data['Project'],
  }

  textfields = {
    'name' => "test-#{rand(1000..9999)}",
    'size' => 1,
    'mountPoint' => "/mnt/share#{rand(10..99)}",    
  }

  rdfg = data['RDF Group']
  if rdfg && rdfg != 'none'
    fields['rdfGroup'] = /0 Vols/ 
  end

  @order.fields = fields
  @order.textfields = textfields

  @order.order!
end

When(/^they order a volume using the Create and Mount Volume on Windows Host catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new

  @order.tenant = 'emcworld'
  @order.category = 'BlockServicesforWindows'
  @order.service = 'CreateandMountVolume'
  fields = {
    'blockStorageType' => 'Exclusive',
    'host' => 'winhost1',
    'virtualArray' => 'nh',
    'virtualPool' => /^#{data['Virtual Pool']}$/,
    'project' => data['Project'],
    'fileSystemType' => 'ntfs',
    'partitionType' => 'GPT',
    'blockSize' => 'Default',
  }

  textfields = {
    'name' => "test-#{rand(1000..9999)}",
    'size' => 1,    
  }

  rdfg = data['RDF Group']
  if rdfg && rdfg != 'none'
    fields['rdfGroup'] = /0 Vols/ 
  end

  @order.fields = fields
  @order.textfields = textfields

  @order.order!
end

When(/^they order a volume using the Create and Mount Volume on AIX Host catalog service$/) do |table|
  data = table.hashes.first

  @order = Page::CatalogOrder.new

  @order.tenant = 'emcworld'
  @order.category = 'BlockServicesforAIX'
  @order.service = 'CreateAndMountBlockVolume'
  fields = {
    'host' => //,
    'virtualArray' => 'nh',
    'virtualPool' => /^#{data['Virtual Pool']}$/,
    'project' => data['Project'],

  }

  textfields = {
    'name' => "test-#{rand(1000..9999)}",
    'size' => 1,
    'mountPoint' => "/mnt/share#{rand(10..99)}",    
  }

  rdfg = data['RDF Group']
  if rdfg && rdfg != 'none'
    fields['rdfGroup'] = /0 Vols/ 
  end

  @order.fields = fields
  @order.textfields = textfields

  @order.order!
end


Then(/^the order should succeed$/) do
  expect(@order).to be_successful
end

Then(/^the order should fail$/) do
  expect(@order).to have_failures
end
