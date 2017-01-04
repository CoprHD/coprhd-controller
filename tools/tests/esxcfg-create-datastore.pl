#!/usr/bin/perl -w

#import runtime libraries 
use strict;
use warnings;
use VMware::VIRuntime;
use VMware::VILib;


# read and validate command-line parameters
Opts::parse();
Opts::validate();

# connect to the server and login
Util::connect();

my $host_view = Vim::find_entity_view(
  view_type => 'HostSystem',
);

# configManager property of host
my $configManager = $host_view->configManager;

# datastore Manager
my $ds_view = Vim::get_view( mo_ref => $configManager->datastoreSystem );

# query to list disks on Datastore Manager
my $hostScsiDiskList = $ds_view->QueryAvailableDisksForVmfs();


my $newDatastoreOptions = $ds_view->QueryVmfsDatastoreCreateOptions( devicePath => @$hostScsiDiskList[0]->devicePath ) ;
@$newDatastoreOptions[0]->spec->vmfs->volumeName('ecdtestds1');
my $newDatastore = $ds_view->CreateVmfsDatastore( spec => @$newDatastoreOptions[0]->spec );

# close server connection
Util::disconnect();

 

