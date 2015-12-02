#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

param(  
    [Parameter(Mandatory=$false, ParameterSetName="Help")]
    [switch]$help,
    
    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$mode,

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vip="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vip6="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr_1="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr_2="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr_3="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr_4="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr_5="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr6_1="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr6_2="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr6_3="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr6_4="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipaddr6_5="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$gateway="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$gateway6="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipv6prefixlength="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$netmask="",

	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$nodeCount,
	
    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$nodeId,
 
    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$librarypath,
    
    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vmhostname,

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vmpath,
   
    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$disktype,
    
    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vSwitch,

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vlanid,
    	
    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$net,

	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$cpuCount="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$memory="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vmPrefix="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vmName="",

	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [switch]$powerOn,
	
    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [switch]$interactive,

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$file,

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ipsec_key
)

$result="succeed"
$releaseversion='${product_version}.${product_release}'
$clusterVersion=""
$scriptPath=split-path -parent $MyInvocation.MyCommand.Definition
$disk4NamePrefix="${product_name}-$releaseVersion-disk4-"
$scriptName=$MyInvocation.MyCommand.Name   
$Script:acceptAllEulas=$false
$Script:isCPUCountGiven=$false
$Script:isMemoryGiven=$false
$Script:isNetmaskGiven=$false

function CleanUp() {
	Get-ChildItem -path $scriptPath | Where{$_.Name.Contains("header")} | Remove-Item
	Get-ChildItem -path $scriptPath | Where{$_.Name.Contains("trailer")} | Remove-Item
}

function Usage() {
    Write-Host "Usage: $scriptName -help | -mode [ install | redeploy ] [options]"
    Write-Host "-mode: "
    Write-Host "         install      install a new cluster"
    Write-Host "         redeploy     redeploy a VM in a cluster"
    Write-Host "	"
    Write-Host "Install mode options: "
    Write-Host "    -vip:               Public virtual IPv4 address"
    Write-Host "    -ipaddr_1:          IPv4 address of node 1"
	Write-Host "    -ipaddr_2:          IPv4 address of node 2"
	Write-Host "    -ipaddr_3:          IPv4 address of node 3"
	Write-Host "    -ipaddr_4:          IPv4 address of node 4"
	Write-Host "    -ipaddr_5:          IPv4 address of node 5"
    Write-Host "    -gateway:           IPv4 default gateway"
	Write-Host "    -netmask:           Network netmask"
	Write-Host "	"
	Write-Host "    -vip6:              Public virtual IPv6 address"
    Write-Host "    -ipaddr6_1:         IPv6 address of node 1"
	Write-Host "    -ipaddr6_2:         IPv6 address of node 2"
	Write-Host "    -ipaddr6_3:         IPv6 address of node 3"
	Write-Host "    -ipaddr6_4:         IPv6 address of node 4"
	Write-Host "    -ipaddr6_5:         IPv6 address of node 5"	
    Write-Host "    -gateway6:          IPv6 default gateway"
    Write-Host "    -ipv6prefixlength:  (Optional)IPv6 network prefix length"
    Write-Host "    "
    Write-Host "    -nodeid:            Specific node to be deployed, please input a number (start from 1)"
	Write-Host "    -nodecount          Node counts of the cluster (valid value is 1 or 3 or 5), please note 1+0 cluster is a evaluation variant with no production support"
	Write-Host "    -net:               Network name"
	Write-Host "    -vswitch:           Virtual switch name"
	Write-Host "    -librarypath:       Library path shared in SCVMM"
	Write-Host "    -vmhostname:        Destination Hyper-V sever for VM deployment"
	Write-Host "    -vmpath:            Target file path location on Hyper-V server, for deployment of VM"
	Write-Host "    -disktype:          (Optional) Type of the virtual hard disk (dynamic or fixed)"
	Write-Host "    -vlanid:            (Optional) A numerical identifier to a virtual network adapter on a virtual machine, by default the value is -1 which means vlanid is not set."
    Write-Host "    -vmprefix:          (Optional) Prefix of virtual machine name"
	Write-Host "    -vmname             (Optional) Virtual machine name"
	Write-Host "    -cpucount:          (Optional) Number of virtual CPUs for each VM (default is 2)"
	Write-Host "    -memory:            (Optional) Amount of memory for each VM (default is 8192)"
    Write-Host "    -poweron:           (Optional) Auto power on the VM after deploy, (no power on by default)"
	Write-Host "    -file:              (Optional) The settings file"
    Write-Host "    -interactive:       (Optional) Interactive way to deploy"
	Write-Host "	"
	Write-Host "    example: .\$scriptName -mode install -vip 1.2.3.0 -ipaddr_1 1.2.3.1 -ipaddr_2 1.2.3.2 -ipaddr_3 1.2.3.3 -gateway 1.1.1.1 -netmask 255.255.255.0 -nodeid 1 -nodecount 3 -net network_name -vswitch virtual_switch_name -librarypath library_path -vmhostname vm_host_name -vmpath vm_path -disktype fixed -vlanid vlan_id -vmprefix vmprefix- -cpucount 2 -memory 8192 -poweron"
	Write-Host ""
    Write-Host "Redeploy mode options: "
	Write-Host "    -file:              The setting file"
    Write-Host "    -nodeid:            Specific node to be deployed, please input a number (start from 1)"
	Write-Host "    -net:               Network name"
	Write-Host "    -vswitch:           Virtual switch name"
	Write-Host "    -librarypath:       Library path shared in SCVMM"
	Write-Host "    -vmhostname:        Destination Hyper-V sever for VM deployment"
	Write-Host "    -vmpath:            Target file path location on Hyper-V server, for deployment of VM"
	Write-Host "    -disktype:          (Optional) Type of the virtual hard disk (dynamic or fixed)"
	Write-Host "    -vlanid:            (Optional) A numerical identifier to a virtual network adapter on a virtual machine, by default the value is -1 which means vlanid is not set."
    Write-Host "    -vmprefix:          (Optional) Prefix of virtual machine name"
	Write-Host "    -vmname             (Optional) Virtual machine name"
	Write-Host "    -cpucount:          (Optional) Number of virtual CPUs for each VM (default is 2)"
	Write-Host "    -memory:            (Optional) Amount of memory for each VM (default is 8192)"
    Write-Host "    -poweron:           (Optional) Auto power on the VM after deploy, (no power on by default)"
    Write-Host "    -interactive:       (Optional) Interactive way to deploy"
    Write-Host "    -ipsec_key:         (Optional) IPSec pre-shared key"
	Write-Host ""
	Write-Host "    example: .\$scriptName -mode redeploy -file your_setting_file_path -nodeid 1 -nodecount 3 -net network_name -vswitch virtual_switch_name -librarypath library_path -vmhostname vm_host_name -vmpath vm_path -disktype fixed -vlanid vlan_id -vmprefix vmprefix- -cpucount 2 -memory 8192 -poweron"
	Write-Host ""
}

function CheckPrerequisetes() {
	$psSupportMajorVersion=4
	if ($host.Version.Major -lt $psSupportMajorVersion) {
		throw "Please use powershell 4.0 or later."
	}
}

${include="common-functions.ps1"}

function CheckRequiredParams($isInteractive) {
	$Script:vSwitch=CheckParam $Script:vSwitch "Virtual switch name" $isInteractive $false IsValidNotNullParam
	$Script:vmpath=CheckParam $Script:vmpath "VM path" $isInteractive $false IsValidNotNullParam
	$Script:vmhostname=CheckParam $Script:vmhostname "VM host name" $isInteractive $false IsValidNotNullParam
	$Script:librarypath=CheckParam $Script:librarypath "Library path" $isInteractive $false IsValidLibraryPath
	$Script:net=CheckParam $Script:net "Network name" $isInteractive $false IsValidNotNullParam
	$Script:nodeId=CheckParam $Script:nodeId "Node ID" $isInteractive $false IsValidNodeId
}

function CheckOtherParams($isInteractive) {
	$Script:disktype=CheckParam $Script:disktype "Virtual hard disk type" $isInteractive $false IsValidDiskType
	$Script:cpuCount=CheckParam $Script:cpuCount "CPU count" $isInteractive $false IsValidCPUCount
	$Script:memory=CheckParam $Script:memory "Memory" $isInteractive $false IsValidMemory
	$Script:vlanid=CheckParam $Script:vlanid "Vlan id" $isInteractive $false IsValidVlan
		
	$defaultNodeId="${product_name}$Script:nodeId"
	if ([String]::IsNullOrEmpty($Script:vmName)) {
		if ([String]::IsNullOrEmpty($Script:vmPrefix)) {
			$Script:vmName=$defaultNodeId
		}
		else {
			if (-not $Script:vmPrefix.EndsWith("-")) {
					$Script:vmPrefix=$Script:vmPrefix+"-"
			}
			$Script:vmName=$Script:vmPrefix+$defaultNodeId
		}
	}
}

function CheckFreshInstallParams() {
	CheckNetWorkProperties $false
	SetDefaultValueOfCPUAndMemory
    CheckRequiredParams $false
	CheckOtherParams $false
}

function CheckVMSettingsInteractively($isInteractive) {
	$Script:nodeId=CheckParam $Script:nodeId "Node ID" $isInteractive $false IsValidNodeId
	$Script:vmName=CheckParam $Script:vmName "VM name" $isInteractive $false IsValidNotNullParam
	$Script:vSwitch=CheckParam $Script:vSwitch "Virtual switch name" $isInteractive $false IsValidNotNullParam
	$Script:net=CheckParam $Script:net "Network name" $isInteractive $false IsValidNotNullParam
	$Script:vmpath=CheckParam $Script:vmpath "VM path" $isInteractive $false IsValidNotNullParam
	$Script:vmhostname=CheckParam $Script:vmhostname "VM host name" $isInteractive $false IsValidNotNullParam
	$Script:librarypath=CheckParam $Script:librarypath "Library path" $isInteractive $false IsValidLibraryPath
	$Script:vlanid=CheckParam $Script:vlanid "Vlan id" $isInteractive $false IsValidVlan
	$Script:disktype=CheckParam $Script:disktype "Virtual hard disk type" $isInteractive $false IsValidDiskType
	$Script:cpuCount=CheckParam $Script:cpuCount "CPU count" $isInteractive $false IsValidCPUCount
	$Script:memory=CheckParam $Script:memory "Memory" $isInteractive $false IsValidMemory
	[string]$poweronValue="no" 
	if ($Script:powerOn -eq $true) {
		$poweronValue="yes"
	}
	$poweronValue=CheckParam $poweronValue "Power on" $isInteractive $false IsValidBooleanParam
	$Script:powerOn=$false
	if ($poweronValue -eq "yes") {
		$Script:powerOn=$true
	}
}

function ReadParamsFromFile($file, $isDotSettingsFile) {
	try {
		foreach ($line in [System.IO.File]::ReadLines($file)) {
			if ([String]::IsNullOrEmpty($line) -or $line.StartsWith('#')) {
				continue;
			}
			ElseIf  ($line.StartsWith('-')) {
				$idx=$line.IndexOf('=')
				$key=$line.Substring(0, $idx)
				$value=$line.Substring($idx + 1)
				if ($idx -gt 0) {
					Switch ($key.ToLower()) {
						"-vip" {
							if ([String]::IsNullOrEmpty($Script:vip)) {
								# Handle special case if user give whole IPv6 properties from command line but .settings has IPv4 properties
								# will take value of IPv4 properties from .settings file and let user to confirm
								if (($isDotSettingsFile -eq $true) -and ($value -ne "0.0.0.0")) {
									$Script:interactive=$true
								}
								$Script:vip=$value
							}
						}
						"-ipaddr_1" {
							if ([String]::IsNullOrEmpty($Script:ipaddr_1)) {
								$Script:ipaddr_1=$value
							}
						}
						"-ipaddr_2" {
							if ([String]::IsNullOrEmpty($Script:ipaddr_2)) {
								$Script:ipaddr_2=$value
							}
						}
						"-ipaddr_3" {
							if ([String]::IsNullOrEmpty($Script:ipaddr_3)) {
								$Script:ipaddr_3=$value
							}
						}
						"-ipaddr_4" {
							if ([String]::IsNullOrEmpty($Script:ipaddr_4)) {
								$Script:ipaddr_4=$value
							}
						}
						"-ipaddr_5" {
							if ([String]::IsNullOrEmpty($Script:ipaddr_5)) {
								$Script:ipaddr_5=$value
							}
						}
						"-gateway" {
							if ([String]::IsNullOrEmpty($Script:gateway)) {
								$Script:gateway=$value
							}
						}
						"-netmask" {
							if ([String]::IsNullOrEmpty($Script:netmask)) {
								$Script:netmask=$value
							}
						}
						"-vip6" {
							if ([String]::IsNullOrEmpty($Script:vip6)) {
								# Handle special case if user give whole IPv4 properties from command line but .settings has IPv6 properties
								# will take value of IPv6 properties from .settings file and let user to confirm
								if (($isDotSettingsFile -eq $true) -and ($value -ne "::0")) {
									$Script:interactive=$true
								}
								$Script:vip6=$value
							}
						}
						"-ipaddr6_1" {
							if ([String]::IsNullOrEmpty($Script:ipaddr6_1)) {
								$Script:ipaddr6_1=$value
							}
						}
						"-ipaddr6_2" {
							if ([String]::IsNullOrEmpty($Script:ipaddr6_2)) {
								$Script:ipaddr6_2=$value
							}
						}
						"-ipaddr6_3" {
							if ([String]::IsNullOrEmpty($Script:ipaddr6_3)) {
								$Script:ipaddr6_3=$value
							}
						}
						"-ipaddr6_4" {
							if ([String]::IsNullOrEmpty($Script:ipaddr6_4)) {
								$Script:ipaddr6_4=$value
							}
						}
						"-ipaddr6_5" {
							if ([String]::IsNullOrEmpty($Script:ipaddr6_5)) {
								$Script:ipaddr6_5=$value
							}
						}
						"-gateway6" {
							if ([String]::IsNullOrEmpty($Script:gateway6)) {
								$Script:gateway6=$value
							}
						}
						"-ipv6prefixlength" {
							if ([String]::IsNullOrEmpty($Script:ipv6prefixlength)) {
								$Script:ipv6prefixlength=$value
							}
						}
						"-nodecount" {
							if ([String]::IsNullOrEmpty($Script:nodeCount)) {
								$Script:nodeCount=$value
							}
						}
						"-nodeid" {
							if ([String]::IsNullOrEmpty($Script:nodeId)) {
								$Script:nodeId=$value
							}
						}
						"-vmprefix" {
							if ([String]::IsNullOrEmpty($Script:vmPrefix)) {
								$Script:vmPrefix=$value
							}
						}
						"-vmname" {
							if ([String]::IsNullOrEmpty($Script:vmName)) {
								$Script:vmName=$value
							}
						}
						"-cpucount" {
							if ([String]::IsNullOrEmpty($Script:cpuCount)) {
								$Script:cpuCount=$value
							}
						}
						"-memory" {
							if ([String]::IsNullOrEmpty($Script:memory)) {
								$Script:memory=$value
							}
						}
						"-librarypath" {
							if ([String]::IsNullOrEmpty($Script:librarypath)) {
								$Script:librarypath=$value
							}
						}
						"-vswitch" {
							if ([String]::IsNullOrEmpty($Script:vSwitch)) {
								$Script:vSwitch=$value
							}
						}
						"-net" {
							if ([String]::IsNullOrEmpty($Script:net)) {
								$Script:net=$value
							}
						}
						"-vmhostname" {
							if ([String]::IsNullOrEmpty($Script:vmhostname)) {
								$Script:vmhostname=$value
							}
						}
						"-vmpath" {
							if ([String]::IsNullOrEmpty($Script:vmpath)) {
								$Script:vmpath=$value
							}
						}						
						"-disktype" {
							if ([String]::IsNullOrEmpty($Script:disktype)) {
								$Script:disktype=$value
							}
						}
						"-vlanid" {
							if ([String]::IsNullOrEmpty($Script:vlanid)) {
								$Script:vlanid=$value
							}
						}					
						"-powerOn" {
							if ($Script:powerOn -eq $false) {
								if ($value = "yes") {
									$Script:powerOn=$true
								}
							}
						}
						"-interactive" {
							if ([String]::IsNullOrEmpty($Script:interactive)) {
								if ($value = "yes") {
									$Script:interactive=$true
								}					
							}
						}
						"-clusterversion" {
							$Script:clusterVersion=$value
						}
						"-ipsec_key" {
							$Script:ipsec_key=$value
						}
						default {
							Write-Host "WARNNING: Unknown line" -ForegroundColor Yellow
						}
					}
				}
			}
			else {
				Write-Host "WARNNING: Unknown line" -ForegroundColor Yellow
			}
		}
	}
	catch {
		Write-Host "WARNNING: Unknown file" -ForegroundColor Yellow
	}
}

# Check if required parameters are empty from command line input or user specified file, if empty, enter interactive mode
function CheckParamsIsEmpty() {
	if ([String]::IsNullOrEmpty($Script:nodeCount)) {
		$Script:interactive=$true
	}	
	
	if ([String]::IsNullOrEmpty($Script:vip) -and [String]::IsNullOrEmpty($Script:vip6)) {
		$Script:interactive=$true
	}
	
	if (-not [String]::IsNullOrEmpty($Script:vip)) {
		if ([String]::IsNullOrEmpty($Script:ipaddr_1) -or [String]::IsNullOrEmpty($Script:netmask) -or 
			[String]::IsNullOrEmpty($Script:gateway)) {
				$Script:interactive=$true
		}
		if (($Script:nodeCount -ne "1") -and ([String]::IsNullOrEmpty($Script:ipaddr_2) -or [String]::IsNullOrEmpty($Script:ipaddr_3))) {
			$Script:interactive=$true
		}	
		if (($Script:nodeCount -eq "5") -and ([String]::IsNullOrEmpty($Script:ipaddr_4) -or [String]::IsNullOrEmpty($Script:ipaddr_5))) {
			$Script:interactive=$true
		}	
	}
	
	if (-not [String]::IsNullOrEmpty($Script:netmask)) {
			$Script:isNetmaskGiven=$true
	}
	
	if (-not [String]::IsNullOrEmpty($Script:vip6)) {
		if ([String]::IsNullOrEmpty($Script:ipaddr6_1) -or [String]::IsNullOrEmpty($Script:gateway6)) {
				$Script:interactive=$true
		}
		if (($Script:nodeCount -ne "1") -and ([String]::IsNullOrEmpty($Script:ipaddr6_2) -or [String]::IsNullOrEmpty($Script:ipaddr6_3))) {
			$Script:interactive=$true
		}
		if (($Script:nodeCount -eq "5") -and ([String]::IsNullOrEmpty($Script:ipaddr6_4) -or [String]::IsNullOrEmpty($Script:ipaddr6_5))) {
			$Script:interactive=$true
		}	
	}
	
	if (-not [String]::IsNullOrEmpty($Script:nodeCount)) {
		if ($Script:nodeCount -eq "1") {
			$Script:ipaddr_2=""
			$Script:ipaddr_3=""
			$Script:ipaddr_4=""
			$Script:ipaddr_5=""
			$Script:ipaddr6_2=""
			$Script:ipaddr6_3=""
			$Script:ipaddr6_4=""
			$Script:ipaddr6_5=""
		}
		if ($Script:nodeCount -eq "3") {
			$Script:ipaddr_4=""
			$Script:ipaddr_5=""
			$Script:ipaddr6_4=""
			$Script:ipaddr6_5=""
		}
	}

	if (([String]::IsNullOrEmpty($Script:net)) -or ([String]::IsNullOrEmpty($Script:nodeId)) -or 
		([String]::IsNullOrEmpty($Script:librarypath)) -or ([String]::IsNullOrEmpty($Script:vmhostname)) -or 
		([String]::IsNullOrEmpty($Script:vmpath)) -or ([String]::IsNullOrEmpty($Script:vswitch))){
		$Script:interactive=$true
	}
	
	if (-not [String]::IsNullOrEmpty($Script:cpuCount)) {
		$Script:isCPUCountGiven=$true
	}
	
	if (-not [String]::IsNullOrEmpty($Script:memory)) {
		$Script:isMemoryGiven=$true
	}
}

function SetDefaultValueToNullParams() {
	$defaultIPv4Value="0.0.0.0"
	if ([String]::IsNullOrEmpty($Script:vip)) {
		$Script:vip=$defaultIPv4Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr_1)) {
		$Script:ipaddr_1=$defaultIPv4Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr_2)) {
		$Script:ipaddr_2=$defaultIPv4Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr_3)) {
		$Script:ipaddr_3=$defaultIPv4Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr_4)) {
		$Script:ipaddr_4=$defaultIPv4Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr_5)) {
		$Script:ipaddr_5=$defaultIPv4Value
	}
	if ([String]::IsNullOrEmpty($Script:gateway)) {
		$Script:gateway=$defaultIPv4Value
	}
	if ([String]::IsNullOrEmpty($Script:netmask)) {
		$Script:netmask="255.255.255.0"
	}	
	$defaultIPv6Value="::0"
	if ([String]::IsNullOrEmpty($Script:vip6)) {
		$Script:vip6=$defaultIPv6Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr6_1)) {
		$Script:ipaddr6_1=$defaultIPv6Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr6_2)) {
		$Script:ipaddr6_2=$defaultIPv6Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr6_3)) {
		$Script:ipaddr6_3=$defaultIPv6Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr6_4)) {
		$Script:ipaddr6_4=$defaultIPv6Value
	}
	if ([String]::IsNullOrEmpty($Script:ipaddr6_5)) {
		$Script:ipaddr6_5=$defaultIPv6Value
	}
	if ([String]::IsNullOrEmpty($Script:gateway6)) {
		$Script:gateway6=$defaultIPv6Value
	}
	if ([String]::IsNullOrEmpty($Script:ipv6prefixlength)) {
		$Script:ipv6prefixlength=64
	}
	if ([String]::IsNullOrEmpty($Script:vlanid)) {
		$Script:vlanid="-1"
	}
	if ([String]::IsNullOrEmpty($Script:disktype)) {
		$Script:disktype="fixed"
	}
	if ([String]::IsNullOrEmpty($Script:file)) {
		$Script:file="/"
	}
}

function LoadParamsFromFile() {
	if ((-not [String]::IsNullOrEmpty($Script:file)) -and (Test-Path $Script:file)) {
		ReadParamsFromFile $Script:file $false
	}
	
	CheckParamsIsEmpty
	
	$settigFilePath=$scriptPath+"\"+".settings"
	if (Test-Path $settigFilePath) {
		$Script:acceptAllEulas=$true
		ReadParamsFromFile $settigFilePath $true
	}
	
	SetDefaultValueToNullParams
}

function CheckRedeploymentParams() {
	CheckFreshInstallParams
}

function DisplaySummaryTitle () {
	Write-Host "******************************************************"
	Write-Host "                 Deployment settings                  "
    Write-Host "******************************************************"
}

function DisplayNetworkProperties() {
	Write-Host "Network properties"
	Write-Host "	Node count [ 1 (evaluation only) | 3 | 5 ]: $Script:nodeCount"
	Write-Host "	IPv4 Settings"
	Write-Host "		Public virtual IPv4 address: $Script:vip"
	Write-Host "		IPv4 default gateway: $Script:gateway"
	Write-Host "		Network netmask: $Script:netmask"
	Write-Host "		IPv4 address of node 1: $Script:ipaddr_1"
	if ($Script:nodeCount -ne "1") {
		Write-Host "		IPv4 address of node 2: $Script:ipaddr_2"
		Write-Host "		IPv4 address of node 3: $Script:ipaddr_3"
	}
	if ($Script:nodeCount -eq "5") {
		Write-Host "		IPv4 address of node 4: $Script:ipaddr_4"
		Write-Host "		IPv4 address of node 5: $Script:ipaddr_5"
	}
	Write-Host ""
	Write-Host "	IPv6 Settings"
	Write-Host "		Public virtual IPv6 address: $Script:vip6"
	Write-Host "		IPv6 default gateway: $Script:gateway6"
	Write-Host "		IPv6 prefix length: $Script:ipv6prefixlength"
	Write-Host "		IPv6 address of node 1: $Script:ipaddr6_1"
	if ($Script:nodeCount -ne "1") {
		Write-Host "		IPv6 address of node 2: $Script:ipaddr6_2"
		Write-Host "		IPv6 address of node 3: $Script:ipaddr6_3"
	}
	if ($Script:nodeCount -eq "5") {
		Write-Host "		IPv6 address of node 4: $Script:ipaddr6_4"
		Write-Host "		IPv6 address of node 5: $Script:ipaddr6_5"
	}
	Write-Host ""
}

function DisplayVMSettings() {
	Write-Host "VM Settings"
	Write-Host "	Mode [ install | redeploy ]: $Script:mode"
	Write-Host "	Node ID: $Script:nodeId"
	Write-Host "	Virtual machine name: $Script:vmName"
	Write-Host "	Virtual switch name: $Script:vSwitch"
	Write-Host "	Network name: $Script:net"
	Write-Host "	VM path: $Script:vmpath"
	Write-Host "	VM host name: $Script:vmhostname"
	Write-Host "	Library path: $Script:librarypath"
	Write-Host "	Vlan id: $Script:vlanid"	
	Write-Host "	Virtual hard disk type: $Script:disktype"
	Write-Host "	CPUs: $Script:cpuCount"
	Write-Host "	Memory: $Script:memory"
	$displayValueOfPowerOn="no"
	if ($Script:powerOn -eq $true) {
		$displayValueOfPowerOn="yes"
	}
	Write-Host "	Power on: $displayValueOfPowerOn"
	Write-Host ""
}

function DisplaySummary() {
	DisplaySummaryTitle
	DisplayNetworkProperties
	DisplayVMSettings
}

function InteractiveMode () {
	while ($true) {
		if (-not (AskUserDecisionRecurr "these settings")) {
			while ($true) {
				if (-not (AskUserDecisionRecurr "Network properties")) {
					CheckNetWorkProperties $true
					Write-Host ""
					DisplayNetworkProperties
				}
				else {
					break;
				}
			}
			while ($true) {
				if (-not (AskUserDecisionRecurr "VM Settings")) {
					CheckVMSettingsInteractively $true
					Write-Host ""
					DisplayVMSettings
				}
				else {
					$Script:isCPUCountGiven=$true
					$Script:isMemoryGiven=$true
					break;
				}
			}
			Write-Host ""
			DisplaySummary
		} 
		else {
			break;
		}
	}
}

function GenerateHeaderAndTrailerFile() {
    $headerContent='${include="disk4-vhdx-header"}'
    $trailerContent='${include="disk4-vhdx-trailer"}'
	$headerZipContent=[System.Convert]::FromBase64String($headerContent)
	$trailerZipContent=[System.Convert]::FromBase64String($trailerContent)	
	$zipHeaderPath=$scriptPath+"\disk4-vhdx-header.zip"
	$zipTrailerPath=$scriptPath+"\disk4-vhdx-trailer.zip"
	[io.file]::WriteAllBytes($zipHeaderPath, $headerZipContent)
	[io.file]::WriteAllBytes($zipTrailerPath, $trailerZipContent)
	
	$shell_app=new-object -com shell.application
	$headerZipFileName = "disk4-vhdx-header.zip"
	$trailerZipFileName = "disk4-vhdx-trailer.zip"
	
	$zip_file = $shell_app.namespace((Get-Location).Path + "\$headerZipFileName")
	$destination = $shell_app.namespace((Get-Location).Path)
	$destination.Copyhere($zip_file.items())
	
	$zip_file = $shell_app.namespace((Get-Location).Path + "\$trailerZipFileName")
	$destination = $shell_app.namespace((Get-Location).Path)
	$destination.Copyhere($zip_file.items())
}

function GenerateDisk4($currentNodeId, $ovfProperties) {
	$fixedContentLength=4196
	$headerFilePath=$scriptPath+"\disk4-vhdx-header"
    $trailerFilePath=$scriptPath+"\disk4-vhdx-trailer"
	$headerFileBytes=[System.IO.File]::ReadAllBytes($headerFilePath)
    $trailerFileBytes=[System.IO.File]::ReadAllBytes($trailerFilePath) 	

    CheckPropertiesContentLength $outputProperties $fixedContentLength
	
	$outputFilePath=$scriptPath+"\"+$disk4NamePrefix+$currentNodeId+".vhdx"
        
	$enc = [system.Text.Encoding]::ASCII   
	$currentProperitiesContent=$enc.GetBytes($ovfProperties) 
				
	$paddingContentLength=$fixedContentLength-$ovfProperties.Length
	$paddingContent=New-Object byte[] $paddingContentLength
	
	$isoFile=$headerFileBytes+$currentProperitiesContent+$paddingContent+$trailerFileBytes
	[io.file]::WriteAllBytes($outputFilePath, $isoFile)
}

function UploadCommonDisks() {
	# 0. connect to local scvmm server
	$hostname = hostname
	get-scvmmserver -ComputerName $hostname

	# 1. Import the Vipr disk images to library
	for($i=1; $i -le 3; $i++) {
		$diskname = "${product_name}-" + $Script:releaseversion + "-disk" + $i +".vhdx";
		if (-not $Script:librarypath.EndsWith("\")) {
			$Script:librarypath=$Script:librarypath+"\"
		}
		$importPath=$Script:librarypath+$diskname
		if (Test-Path $importPath) {
			Write-Host "$diskname is already exist, skip import ..."
		}
		else {
			Write-Host "importing $diskname to $librarypath ..."
			Import-SCLibraryPhysicalResource -SourcePath "$PSScriptRoot\$diskname" -SharePath $Script:librarypath  -OverwriteExistingFiles
		}
	}
}

function UploadDisk4($currentNodeId) {
	$hostname = hostname
	get-scvmmserver -ComputerName $hostname
	$diskname=$disk4NamePrefix+$currentNodeId+".vhdx"
	Write-Host "importing $diskname to $librarypath ..."
	Import-SCLibraryPhysicalResource -SourcePath "$PSScriptRoot\$diskname" -SharePath $Script:librarypath  -OverwriteExistingFiles
}

function CreateSubfolderAndMoveFiles($currentNodeId) {
	$subfolderPath=$scriptPath+"\${product_name}-$releaseVersion-$currentNodeId"
	$returnValue=(New-Item $subfolderPath -type directory -Force)
	# move disk4 filefor that node to sub-folder
	Move-Item $scriptPath"\"$disk4NamePrefix$currentNodeId".vhdx" $scriptPath"\${product_name}-$releaseVersion-$currentNodeId" -Force
}

function DisplayLicense() {
	try {
		[string]$license="${include="storageos-license-ps.txt"}" | out-host -paging
	} 
	catch {
		# Do nothing
	}
	finally {
		while ($true) {
			Write-Host "Would you like to accept ViPR license? (Y/y/N/n): " -NoNewline
			$userDecision=Read-Host
			if ($userDecision.ToLower() -eq "y") {
				$Script:acceptAllEulas=$true
				break;
			}
			if ($userDecision.ToLower() -eq "n"){
				$Script:acceptAllEulas=$false
				break;
			}
		}
	}
}

function SaveUserSettings() {
	$displayPowerOn="no"
	if ($Script:powerOn -eq $true) {
		$displayPowerOn="yes"
	}
	$displayInteractive="no"
	if ($Script:interactive -eq $true) {
		$displayInteractive="yes"
	}
	$userSettings="-vip=$Script:vip
-ipaddr_1=$Script:ipaddr_1
-ipaddr_2=$Script:ipaddr_2
-ipaddr_3=$Script:ipaddr_3
-ipaddr_4=$Script:ipaddr_4
-ipaddr_5=$Script:ipaddr_5
-gateway=$Script:gateway
-netmask=$Script:netmask
-vip6=$Script:vip6
-ipaddr6_1=$Script:ipaddr6_1
-ipaddr6_2=$Script:ipaddr6_2
-ipaddr6_3=$Script:ipaddr6_3
-ipaddr6_4=$Script:ipaddr6_4
-ipaddr6_5=$Script:ipaddr6_5
-gateway6=$Script:gateway6
-ipv6prefixlength=$Script:ipv6prefixlength
-nodeCount=$Script:nodeCount
-librarypath=$Script:librarypath
-vSwitch=$Script:vSwitch
-net=$Script:net
-vmpath=$Script:vmpath
-vmhostname=$Script:vmhostname
"	
	$enc = [system.Text.Encoding]::ASCII   
	$userSettingsContent=$enc.GetBytes($userSettings)
	$outputFilePath=$scriptPath+"\.settings"	
	[io.file]::WriteAllBytes($outputFilePath, $userSettingsContent)
}

function CreateVM($currentNodeId) {
	$vmname=$Script:vmName

	# 0. connect to local scvmm server
	$hostname = hostname
	Write-Host "Connecting to local scvmm server ...";
	get-scvmmserver -ComputerName $hostname

	# 1. Get the info of all vipr disks
	$viprdisks = @()
	for($i=1; $i -le 3; $i++) {
		$diskname = "${product_name}-" + $releaseversion +  "-disk" + $i +".vhdx";
		$tmpdiskifo = Get-SCVirtualHardDisk -Name $diskname
		$viprdisks += $tmpdiskifo;
	}

	$disk4Name=$disk4NamePrefix+$currentNodeId+".vhdx";
	$tmpDisk4Info=Get-SCVirtualHardDisk -Name $disk4Name
	$viprdisks+=$tmpDisk4Info

	# 2. Get an available backend hyper-V host
	Write-Host "Getting VM host info from $vmhostname ...";
	$VMHost = Get-SCVMHost -ComputerName $Script:vmhostname

	# 3. Get network info
	Write-Host "Fetching virtual switch ($Script:vSwitch) info...";
	$VirtualSwitch = Get-SCVirtualNetwork -name $Script:vSwitch -VMHost $VMHost

	Write-Host "Fetching VM network ($Script:net) info...";
	$VmNetwork = Get-SCVMNetwork -VMMServer $hostname -Name $Script:net

	# 4. create virtual machine(s)
	Write-Host "Creating virtual machine ($vmname) ...";
	$cpuCountInt=[Convert]::ToInt32($Script:cpuCount, 10)
	$memoryInt=[Convert]::ToInt32($Script:memory, 10)

	New-SCVirtualMachine -Name $vmname -VirtualHardDisk $viprdisks[0] -VMHost $VMHost -CPUCount $cpuCountInt -MemoryMB $memoryInt -Path $Script:vmpath -DynamicMemoryEnabled $true -DynamicMemoryMaximumMB $memoryInt

	# 4.1. Attach more disks (disk2/3/4) to the VM.
	Write-Host "Attaching disk 2 (cow disk) to the virtual machine";
	New-SCVirtualDiskDrive -Bus 0 -IDE -LUN 1 -VirtualHardDisk $viprdisks[1] -VM $vmname
	Write-Host "Attaching disk 3 (data disk)to the virtual machine";
	New-SCVirtualDiskDrive -Bus 1 -IDE -LUN 0 -VirtualHardDisk $viprdisks[2] -VM $vmname
	Write-Host "Attaching disk 4 (ovf-env disk)to the virtual machine";
	New-SCVirtualDiskDrive -Bus 1 -IDE -LUN 1 -VirtualHardDisk $viprdisks[3] -VM $vmname

	# Convert the disks to fixed typeif the disktype, no nee to convert the ovf-env disk because we don't change its content
	if ( $disktype -eq "fixed" ) {
		Write-Host "Chose thick provisioning, need to convert the first 3 disks from dynamic to fixed";
		$VirtDiskDrive = Get-SCVirtualDiskDrive -VM (Get-SCVirtualMachine -Name $vmname)
		Write-Host "Converting disk 1 (bootfs disk) to fixed size";
		Convert-SCVirtualDiskDrive -VirtualDiskDrive $VirtDiskDrive[0] -Fixed
		Write-Host "Converting disk 2 (cow disk) to fixed size";
		Convert-SCVirtualDiskDrive -VirtualDiskDrive $VirtDiskDrive[1] -Fixed
		Write-Host "Converting disk 3 (data disk) to fixed size, will expand it to 500GB, it will take some time to finish";
		Convert-SCVirtualDiskDrive -VirtualDiskDrive $VirtDiskDrive[2] -Fixed
	}

	# 4.2. Create virtual adapter and attach it to the VM and the virtual network
	$vlanidInt=[Convert]::ToInt32($Script:vlanid, 10)
	if ($vlanidInt -eq -1) {
		Write-Host "Creating virtual network adapter (no vlanid) and attaching to VM ...";
		New-SCVirtualNetworkAdapter -VirtualNetwork $VirtualSwitch -VM $vmname -Synthetic -VMNetwork $VmNetwork
	} else {
		Write-Host "Creating virtual network adapter with vlanid ($vlanid) and attaching to VM ...";
		New-SCVirtualNetworkAdapter -VirtualNetwork $VirtualSwitch -VM $vmname -VLANEnabled $true -VLANID $vlanidInt -Synthetic -VMNetwork $VmNetwork
	}

	# 4.3 disable time sync service
	Write-Host "Disabling hyper-v built-in time synchronization ...";
	Set-SCVirtualMachine -VM $vmname -EnableTimeSync $false

	if ($Script:powerOn) {
		# 5 start virtual machine
		Write-Host "Starting virtual machine";
		Start-SCVirtualMachine -VM $vmname
	}
}

if($help) {
    Usage
    return
}

try {
    CheckPrerequisetes
	
    switch ($mode.ToLower()) {
        "install" {
			# 0. Validate necessary parameters integrity and correctness
			Write-Host ""
			Write-Host "****** Checking parameters of Hyper-V install ******"
			LoadParamsFromFile
				
			# 0.1 Display license to let user confirm
			if ($Script:acceptAllEulas -eq $false) {	
				DisplayLicense
			}
			if ($Script:acceptAllEulas -eq $false) {
				return
			}
			
			CheckFreshInstallParams
        }
        "redeploy" {
			# 0. Validate necessary parameters integrity
			Write-Host ""
			Write-Host "****** Checking parameters of Hyper-V redeploy ******"

			# 0.1 Display license to let user confirm
			$Script:file=CheckParam $Script:file "Config file" $false $false IsValidNotNullParam
			LoadParamsFromFile
			if ($Script:acceptAllEulas -eq $false) {	
				DisplayLicense
			}
			if ($Script:acceptAllEulas -eq $false) {
				return
			}		
			CheckVersion
			CheckRedeploymentParams
        }
        default {
            throw "Invalid mode"
        }
    }
	
	# 1.0 Display summary, activate interactive mode if needed 
	DisplaySummary
	if ($Script:interactive) {
		InteractiveMode
	}
	
	# 2. Generate ovf-properties
	$currentNodeId="${product_name}$nodeId"
	[array]$outputProperties=GenerateOvfenvProperties
	
	# 3. Generate header and trailer zip file
	GenerateHeaderAndTrailerFile
	
	# 4. Generate disk4 for current node
	Write-Host ""			
	Write-Host "****** Generating disk4 vhdx files for current node ******"
	$idx=[Convert]::ToInt32($Script:nodeId, 10)-1
	GenerateDisk4 $currentNodeId $outputProperties[$idx]
	
	# 5. Upload disks to SCVMM server
	UploadCommonDisks
	UploadDisk4 $currentNodeId

	# 6. Create subfoler and move disk4 
	CreateSubfolderAndMoveFiles $currentNodeId
	
	# 7. Save user settings
	SaveUserSettings

	# 8. Deploy single node
	CreateVM $currentNodeId
	if ($Script:nodeCount -eq "1") {
		Write-Host "WARNNING: 1+0 cluster is a evaluation variant with no production support." -ForegroundColor Yellow
	}
} 
catch {
    write-host "Caught an exception:" -ForegroundColor Red
    write-host "Exception Type: $($_.Exception.GetType().FullName)" -ForegroundColor Red
    write-host "Exception Message: $($_.Exception.Message)" -ForegroundColor Red
    $result="failed"
}
CleanUp
Write-Host ""
Write-Host "****** Deployment done with status: $result ******"

