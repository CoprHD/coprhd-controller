#!/usr/bin/perl -w

#import runtime libraries 
use strict;
use warnings;
use VMware::VIRuntime;
use VMware::VILib;

my %opts = (
  wwn => {
      type => "=s",
      help => "WWN of the volume on which datastore needs to be created",
      required => 1,
   },
   dsname => {
      type => "=s",
      help => "Name of the datastore",
      required => 1,
   },
);
# read and validate command-line parameters
Opts::add_options(%opts);
Opts::parse();
Opts::validate();

my $wwn = Opts::get_option('wwn');
my $dsname = Opts::get_option('dsname');

# connect to the server and login
Util::connect();

my $host_view = Vim::find_entity_view(
  view_type => 'HostSystem',
);

# configManager property of host
my $configManager = $host_view->configManager;

my $rescan = Vim::get_view( mo_ref => $configManager->storageSystem );
$rescan->RescanAllHba();
$rescan->RescanVmfs();
sleep(25);

# datastore Manager
my $ds_view = Vim::get_view( mo_ref => $configManager->datastoreSystem );

# query to list disks on Datastore Manager
my $hostScsiDiskList = $ds_view->QueryAvailableDisksForVmfs();
#print join(", ",@$hostScsiDiskList);

my $devpath =" ";

#Find the device path with disk wwn
foreach (@$hostScsiDiskList) { 
    if($_->canonicalName eq $wwn){
	$devpath = $_->devicePath;
	last;
}
}

my $newDatastoreOptions = $ds_view->QueryVmfsDatastoreCreateOptions( devicePath => $devpath ) ;
@$newDatastoreOptions[0]->spec->vmfs->volumeName($dsname);
my $newDatastore = $ds_view->CreateVmfsDatastore( spec => @$newDatastoreOptions[0]->spec );

# close server connection
Util::disconnect();

 

