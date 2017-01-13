#!/usr/bin/perl -w

use strict;
use warnings;
use VMware::VILib;
use VMware::VIRuntime;

my %opts = (
   datastore => {
      type => "=s",
      help => "Name of specific datastore to rename",
      required => 0,
   },
   hostlist => {
      type => "=s",
      help => "Lists of ESX(i) hosts to perform operation _IF_ they're being managed by vCenter (default is ALL hosts in vCenter)",
      required => 0,
   },
   operation => { 
      type => "=s",
      help => "Operation to perform [remove|dryrun]",
      required => 0,
      default => 'dryrun',
   },
);

Opts::add_options(%opts);
Opts::parse();
Opts::validate();
Util::connect();

my $datastore = Opts::get_option('datastore');
my $hostlist = Opts::get_option('hostlist');
my $operation = Opts::get_option('operation');

my (@hosts,$content,$host_view,$host_views,$hostname,$datastores,$new_dsname,$hostdatastoresystem,$configmanager,$rescan);

$content = Vim::get_service_content();

if($content->about->apiType eq 'HostAgent') {
	$host_view = Vim::find_entity_view(view_type => 'HostSystem');
	
	$configmanager = $host_view->configManager;
	$rescan = Vim::get_view( mo_ref => $configmanager->storageSystem );
	$rescan->RescanAllHba();
	$rescan->RescanVmfs();
	if(defined($host_view->summary->managementServerIp)) {
                Util::disconnect();
                print "ESX(i) host is currently being managed by a vCenter Server, to properly rename datastore, please connect to vCenter and specify --vihost param!\n";
                exit 1;
        }
	&getDatastores($host_view);
} else {
	if($hostlist) {
		&processFile($hostlist);
		foreach(@hosts) {
			$host_view = Vim::find_entity_view(view_type => 'HostSystem', filter => { 'name' => $_});
			print $host_view->name . "\n";
			&getDatastores($host_view);
		}
	} else {
		$host_views = Vim::find_entity_views(view_type => 'HostSystem');
		foreach(@$host_views) {
			&getDatastores($_);
		}	
	}
}


Util::disconnect();

####################################
#       HELPER FUNCTIONS
####################################

sub getDatastores {
	my ($host) = @_;

	$hostname = &getShortHostname($host);
 	$hostdatastoresystem = &getHostDatastoreSystem($host); 
        $datastores = Vim::get_views(mo_ref_array => $host->datastore);
        &searchDS($hostname,$datastores,$hostdatastoresystem);
}

sub renameDS {
        my ($ds,$hostdatastoresystem) = @_;

	if($operation ne 'dryrun') {
		print "\tRemoving \"" . $ds->name ."\" ";
        	my $task = $hostdatastoresystem->RemoveDatastore(datastore => $ds);
        	my $msg = "\tSucessfully removed datastore!\n";
        	&getStatus($task,$msg);
	} else {
		print "\tDRYRUN - Removing \"" . $ds->name ."\" \n";
	}	
}

sub searchDS {
        my ($hostname,$datastores,$hostdatastoresystem) = @_;
	my $datastoreCount = 1;

        foreach(@$datastores) {
		if($datastore) {
			if($_->name eq $datastore) {
				                &renameDS($_,$hostdatastoresystem);
                                $datastoreCount++;
				}
		} else {
                	if($_->summary->type eq 'VMFS' && $_->name =~ m/^datastore/) {
                        	&renameDS($_,$hostdatastoresystem);
				$datastoreCount++;
			}
                }
        }
}

sub getShortHostname {
	my ($host) = @_;
	my $shortname = $host->name;

	my $networkSys = Vim::get_view(mo_ref => $host->configManager->networkSystem);

	if($networkSys->dnsConfig->hostName) {
		$shortname = $networkSys->dnsConfig->hostName;
	}	

	return $shortname;
}
 
sub getHostDatastoreSystem {
	my ($host) = @_;
	my $hds = Vim::get_view(mo_ref=> $host->configManager->datastoreSystem);
	return $hds;

}

# Subroutine to process the input file
sub processFile {
        my ($list,$type) =  @_;
        my $HANDLE;
        open (HANDLE, $list) or die("ERROR: Can not locate or open \"$list\" input file!\n");
        my @lines = <HANDLE>;
        my @errorArray;
        my $line_no = 0;

        close(HANDLE);
        foreach my $line (@lines) {
                $line_no++;
                &TrimSpaces($line);

                if($line) {
                        if($line =~ /^\s*:|:\s*$/){
                                print "Error in Parsing File at line: $line_no\n";
                                print "Continuing to the next line\n";
                                next;
                        }
                        my $entry = $line;
                        &TrimSpaces($entry);
                        push @hosts,$entry;
                }
        }
}

sub TrimSpaces {
        foreach (@_) {
                s/^\s+|\s*$//g
        }
}

sub getStatus {
        my ($taskRef,$message) = @_;

        my $task_view = Vim::get_view(mo_ref => $taskRef);
        my $taskinfo = $task_view->info->state->val;
        my $continue = 1;
        while ($continue) {
                my $info = $task_view->info;
                if ($info->state->val eq 'success') {
                        print $message,"\n";
                        return $info->result;
                        $continue = 0;
                } elsif ($info->state->val eq 'error') {
                        my $soap_fault = SoapFault->new;
                        $soap_fault->name($info->error->fault);
                        $soap_fault->detail($info->error->fault);
                        $soap_fault->fault_string($info->error->localizedMessage);
                        die "$soap_fault\n";
                }
                sleep 5;
                $task_view->ViewBase::update_view_data();
        }
}
