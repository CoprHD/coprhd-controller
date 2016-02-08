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
    [string]$vmPrefix="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vmName="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$cpuCount="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$memory="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$username="",
	
	[Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$password="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$targetUri,

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$ds, 

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$net,

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$dm="",

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [switch]$powerOn,

    [Parameter(Mandatory=$false, ParameterSetName="Operation")]
    [string]$vmFolder="",

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
$Script:savedUri=""
$Script:isCPUCountGiven=$false
$Script:isMemoryGiven=$false
$Script:isNetmaskGiven=$false
$Script:isUsernameGiven=$false
$Script:isPasswordGiven=$false
$Script:isVMX=$false

# Usability
function Usage() {
    Write-Host "Usage: $scriptName -help | -mode [ install | redeploy | install-vmx ] [options]"
    Write-Host "-mode: "
    Write-Host "         install              Install a new cluster"
    Write-Host "         redeploy             Redeploy a VM in a cluster"
	Write-Host "         install-vmx          Create a VM at VMware workstation"
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
    Write-Host "    -targeturi:         Target locator of vsphere e.g. vi://username:password@vsphere_host_url"
	Write-Host "	"
    Write-Host "    -ds:                Data store name"
    Write-Host "    -net:               Network name"
    Write-Host "    -vmprefix:          (Optional) prefix of virtual machine name"
	Write-Host "    -vmname             (Optional) Virtual machine name"
    Write-Host "    -vmfolder:          (Optional) Target virtual machine folder"
    Write-Host "    -dm:                (Optional) Disk format:thin, lazyzeroedthick, zeroedthick (default)"
	Write-Host "    -cpucount:          (Optional) Number of virtual CPUs for each VM (default is 2)"
	Write-Host "    -memory:            (Optional) Amount of memory for each VM (default is 8192)"
    Write-Host "    -poweron:           (Optional) Auto power on the VM after deploy, (no power on by default)"
	Write-Host "    -file:              (Optional) The settings file"
	Write-Host "    -username           (Optional) Username of vSphere client"
	Write-Host "    -password           (Optional) Password of vSphere client"
    Write-Host "    -interactive:       (Optional) Interactive way to deploy"
	Write-Host "	"
	Write-Host "    example: .\$scriptName -mode install -vip 1.2.3.0 -ipaddr_1 1.2.3.1 -ipaddr_2 1.2.3.2 -ipaddr_3 1.2.3.3 -gateway 1.1.1.1 -netmask 255.255.255.0 -nodeid 1 -nodecount 3 -targeturi vi://username:password@vsphere_host_url -ds datastore_name -net network_name -vmprefix vmprefix- -vmfolder vm_folder -dm zeroedthick -cpucount 2 -memory 8192 -poweron"
	Write-Host ""
    Write-Host "Redeploy mode options: "
	Write-Host "    -file:              (Optional) The settings file"
    Write-Host "    -nodeid:            Specific node to be deployed, please input a number (start from 1)"
    Write-Host "    -targeturi:         Target locator of vsphere e.g. vi://username:password@vsphere_host_url"
    Write-Host "    -ds:                Data store name"
    Write-Host "    -net:               (Optional) Network name"
    Write-Host "    -vmprefix:          (Optional) Prefix of virtual machine name"
	Write-Host "    -vmname             (Optional) Virtual machine name"
    Write-Host "    -vmfolder:          (Optional) The target virtual machine folder"
    Write-Host "    -dm:                (Optional) Disk format:thin, lazyzeroedthick, zeroedthick (default)"
	Write-Host "    -cpucount:          (Optional) Number of virtual CPUs for each VM (default is 2)"
	Write-Host "    -memory:            (Optional) Amount of memory for each VM (default is 8192)"
    Write-Host "    -poweron:           (Optional) Auto power on the VM after deploy, (no power on by default)"
	Write-Host "    -username           (Optional) Username of vSphere client"
	Write-Host "    -password           (Optional) Password of vSphere client"
	Write-Host "    -interactive        (Optional) Interactive way to deploy"
    Write-Host "    -ipsec_key          (Optional) IPSec pre-shared key"
	Write-Host ""
	Write-Host "    example: .\$scriptName -mode redeploy -file your_setting_file_path -nodeid 1 -targeturi vi://username:password@vsphere_host_url -ds datastore_name -net network_name -vmprefix vmprefix- -vmfolder vm_folder -dm zeroedthick -cpucount 2 -memory 8192 -poweron"
	Write-Host ""
	Write-Host "Install-vmx mode options: "
    Write-Host "    -nodecount          Node counts of the cluster (valid value is 1 or 3 or 5), please note 1+0 cluster is a evaluation variant with no production support"
    Write-Host "    -nodeid:            Specific node to be deployed, for VMX, nodeid is only support 1 node"
	Write-Host "    -vmfolder:          Virtual Machine location"
    Write-Host "    -net:               Network mode, bridged | nat"
    Write-Host "    -vmprefix:          (Optional) Prefix of virtual machine name"
	Write-Host "    -vmname             (Optional) Virtual machine name"
	Write-Host "    -interactive        (Optional) Interactive way to deploy"
	Write-Host ""
	Write-Host "    example: .\$scriptName -mode install-vmx vip 1.2.3.0 -ipaddr_1 1.2.3.1 -gateway 1.1.1.1 -netmask 255.255.255.0 -vmprefix vmprefix- -vmfolder vm_location -net network_mode -nodecount 1 -nodeid 1"
	Write-Host ""
}

function CleanUp() {
	Get-ChildItem -path $scriptPath | Where{$_.Name.Contains("disk4")} | Remove-Item
	if ($Script:isVMX -eq $true) {
		Remove-Item $scriptPath"\vipr-$releaseVersion-$currentNodeId\$vmname.vmx" -recurse
		Remove-Item $scriptPath"\vipr-$releaseVersion-$currentNodeId\$vmname\*" -recurse
	}
}

function CheckPrerequisetes() {
    try {
        $ovfCheckResult=([string](ovftool --version)).Split(' ')
    } catch {
        throw "Could not find ovftool, please go to https://my.vmware.com/web/vmware/details?productId=352&downloadGroup=OVFTOOL350 to download ovftool at first, then follow ovftool user guide to config environment variable"
    }
	$psSupportMajorVersion=4
	if ($host.Version.Major -lt $psSupportMajorVersion) {
		throw "Please use powershell 4.0 or later."
	}
}

${include="common-functions.ps1"}

function CheckUsernameAndPasswordExistence() {
	$uid=""
	$pwd=""
	$startIdx=$Script:targetUri.IndexOf("://")
	$endIdx=$Script:targetUri.IndexOf("@")
	if ($endIdx -ne -1) {
		[String]$restContent=$Script:targetUri.SubString($startIdx+3, $endIdx-$startIdx-3)
		if ($restContent.length -gt 0) {
			$pwdStartIdx=$restContent.IndexOf(":")
			if ($pwdStartIdx -ne -1) {
                $uid=$restContent.Substring(0,$pwdStartIdx)
                $pwd=$restContent.Substring($pwdStartIdx+1)
            }
            else {
                $uid=$restContent
            }
		}
	} 

    $Script:isUsernameGiven=(-not ([String]::IsNullOrEmpty($Script:username) -and [String]::IsNullOrEmpty($uid)))

    $Script:isPasswordGiven=(-not ([String]::IsNullOrEmpty($Script:password) -and [String]::IsNullOrEmpty($pwd)))   
}

function CheckRequiredParams($isInteractive) {		
	$Script:nodeId=CheckParam $Script:nodeId "Node ID" $isInteractive $false IsValidNodeId
	if ($Script:isVMX -eq $false) {
		$Script:ds=CheckParam $Script:ds "Datastore" $isInteractive $false IsValidNotNullParam
		$Script:net=CheckParam $Script:net "Network name" $isInteractive $false IsValidNotNullParam
	
		$Script:targetUri=CheckParam $Script:targetUri "Target URI" $isInteractive $false IsValidNotNullParam
		CheckUsernameAndPasswordExistence
	    if ($Script:isUsernameGiven -eq $false) {
	        $Script:username=CheckParam $Script:username "User name" $isInteractive $false IsValidNotNullParam	
	    }
		if ($Script:isPasswordGiven -eq $false) {
			$Script:password=CheckParam $Script:password "Password" $isInteractive $true IsValidNotNullParam
		}
	} else {
		$Script:net=CheckParam $Script:net "Network mode [bridged | nat]" $isInteractive $false IsValidNet
	}
}

function CheckOtherParams($isInteractive) {
	if ($Script:isVMX -eq $false) {
		$Script:dm=CheckParam $Script:dm "Disk provisioning" $isInteractive $false IsValidDm
	} 
	$Script:vmFolder=CheckParam $Script:vmFolder "Folder" $isInteractive $false IsValidNotNullParam
	$Script:cpuCount=CheckParam $Script:cpuCount "CPU count" $isInteractive $false IsValidCPUCount
	$Script:memory=CheckParam $Script:memory "Memory" $isInteractive $false IsValidMemory
	
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
	if ($Script:isVMX -eq $false) {
		$Script:ds=CheckParam $Script:ds "Datastore cluster" $isInteractive $false IsValidNotNullParam
		$Script:net=CheckParam $Script:net "Network name" $isInteractive $false IsValidNotNullParam
		$Script:dm=CheckParam $Script:dm "Disk provisioning" $isInteractive $false IsValidDm
	} else {
		$Script:net=CheckParam $Script:net "Network mode [bridged | nat]" $isInteractive $false IsValidNet
	}
	$Script:vmFolder=CheckParam $Script:vmFolder "Folder" $isInteractive $false IsValidNotNullParam
	$Script:vmName=CheckParam $Script:vmName "VM name" $isInteractive $false IsValidNotNullParam	
	$Script:cpuCount=CheckParam $Script:cpuCount "CPU" $isInteractive $false IsValidCPUCount
	$Script:memory=CheckParam $Script:memory "Memory" $isInteractive $false IsValidMemory
	if ($Script:isVMX -eq $false) {
		[string]$poweronValue="no" 
		if ($Script:powerOn -eq $true) {
			$poweronValue="yes"
		}
		$poweronValue=CheckParam $poweronValue "Power on" $isInteractive $false IsValidBooleanParam
		$Script:powerOn=$false
		if ($poweronValue -eq "yes") {
			$Script:powerOn=$true
		}	
		$Script:targetUri=CheckParam $Script:targetUri "Target URI" $isInteractive $false IsValidNotNullParam
		$Script:username=CheckParam $Script:username "User name" $isInteractive $false IsValidNullParam
		$Script:password=CheckParam $Script:password "Password" $isInteractive $true IsValidNullParam
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
								$Script:isNetmaskGiven=$true
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
						"-targeturi" {
							if ([String]::IsNullOrEmpty($Script:targetUri)) {
								$Script:targetUri=$value
							}
						}
						"-ds" {
							if ([String]::IsNullOrEmpty($Script:ds)) {
								$Script:ds=$value
							}
						}
						"-net" {
							if ([String]::IsNullOrEmpty($Script:net)) {
								$Script:net=$value
							}
						}
						"-dm" {
							if ([String]::IsNullOrEmpty($Script:dm)) {
								$Script:dm=$value
							}
						}
						"-vmfolder" {
							if ([String]::IsNullOrEmpty($Script:vmFolder)) {
								$Script:vmFolder=$value
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
						"-username" {
							if ([String]::IsNullOrEmpty($Script:username)) {
								$Script:username=$value
							}
						}
						"-password" {
							if ([String]::IsNullOrEmpty($Script:password)) {
								$Script:password=$value
							}
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
	if ($Script:isVMX -eq $false) {	
		if (([String]::IsNullOrEmpty($Script:net)) -or ([String]::IsNullOrEmpty($Script:nodeId)) -or 
			([String]::IsNullOrEmpty($Script:ds)) -or ([String]::IsNullOrEmpty($Script:targetUri))){
			$Script:interactive=$true
		}
	} else {
		if (([String]::IsNullOrEmpty($Script:net)) -or ([String]::IsNullOrEmpty($Script:vmFolder))){
			$Script:interactive=$true
		}
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
	if ([String]::IsNullOrEmpty($Script:dm)) {
		$Script:dm="zeroedthick"
	}
	if ([String]::IsNullOrEmpty($Script:vmFolder)) {
		$Script:vmFolder="/"
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
    $nodeCountLabel = "	Node count [ 1 (evaluation only) | 3 | 5 ]"
    if ($Script:isVMX -eq $true) {
        $nodeCountLabel = "	Node count [ 1 (Node count can only be 1 in install-vmx mode) ]"
    }
    Write-Host "	${nodeCountLabel}: $Script:nodeCount"
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
	Write-Host "	Mode [ install | redeploy | install-vmx ]: $Script:mode"
	Write-Host "	Node ID: $Script:nodeId"
	if ($Script:isVMX -eq $false) {
		Write-Host "	Datastore: $Script:ds"
		Write-Host "	Network name: $Script:net"
		Write-Host "	Disk provisioning [ thin | lazyzeroedthick | zeroedthick]: $Script:dm"
	} else {
		Write-Host "	Network mode [bridged | nat]: $Script:net"
	}
	Write-Host "	Folder: $Script:vmFolder"
	Write-Host "	Virtual machine name: $Script:vmName"

	Write-Host "	CPUs: $Script:cpuCount"
	Write-Host "	Memory: $Script:memory"
	if ($Script:isVMX -eq $false) {	
		$displayValueOfPowerOn="no"
		if ($Script:powerOn -eq $true) {
			$displayValueOfPowerOn="yes"
		}
		Write-Host "	Power on: $displayValueOfPowerOn"
		Write-Host "	Target URI: $Script:targetUri"
		Write-Host "	Username: $Script:username"
	}
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

function GenerateVMXDescriptor($vmdkDescriptorName, $currentNodeId) {
    $vmxFileContent='${vmxtemplate}'
    $vmxFileContent='${include="vmx-template"}'
    $vmxFileContent=$vmxFileContent.Replace('${1}', $vmdkDescriptorName)
    $outputFilePath=$scriptPath+"\"+$disk4NamePrefix+$currentNodeId+".vmx"
    [io.file]::WriteAllText($outputFilePath, $vmxFileContent)
}

function GenerateVMDKDescriptor($vmdkFlatFileName, $currentNodeId) {
    $vmdkDescriptorFileContent='${include="vmdk-descriptor-template"}'
    $vmdkDescriptorFileContent=$vmdkDescriptorFileContent.Replace('${1}', $vmdkFlatFileName)
    $outputFilePath=$scriptPath+"\"+$disk4NamePrefix+"descriptor-"+$currentNodeId+".vmdk"
    [io.file]::WriteAllText($outputFilePath, $vmdkDescriptorFileContent)
}

function GenerateVMDKFlat($ovfPropsContent, $currentNodeId) {  
    $fixedContentLength='${config_file_size}'
    $headerContent='${include="iso-header"}'
    $trailerContent='${include="iso-trailer"}'
    $headerFileBytes=[System.Convert]::FromBase64String($headerContent)
    $trailerFileBytes=[System.Convert]::FromBase64String($trailerContent)

    CheckPropertiesContentLength $ovfPropsContent $fixedContentLength

    $outputFilePath=$scriptPath+"\"+$disk4NamePrefix+"descriptor-"+$currentNodeId+"-flat.vmdk"      
    $enc = [system.Text.Encoding]::ASCII   
    $currentProperitiesContent=$enc.GetBytes($ovfPropsContent) 
    $paddingContentLength=$fixedContentLength-$ovfPropsContent.Length
    $paddingContent=New-Object byte[] $paddingContentLength       
    $vmdkFlatDiskFile=$headerFileBytes+$currentProperitiesContent+$paddingContent+$trailerFileBytes
    [io.file]::WriteAllBytes($outputFilePath, $vmdkFlatDiskFile)
}

function GetChecksumForFile($fileName) {
    [String]$checksumOutput=Get-FileHash -Path $fileName -Algorithm SHA1
    return $checksumOutput.substring($checksumOutput.IndexOf("Hash=")+5, 40)
}

function GenerateChecksumString($fileName, $hashValue) {
    return "SHA1($fileName)= $hashValue"
}

function CalculateChecksumForOVFTemplateAndDisk4($ovfFileName, $disk4FileName, $currentNodeId) {
    $mfTemplate='${data_disk_mf}'
    
    $mfTemplate=$mfTemplate.Replace('${vmdk_dir}', $scriptPath)
    $ovfCheckSumValue = GetChecksumForFile $ovfFileName
    $disk4ChecksumValue = GetChecksumForFile $disk4FileName
    $mfContent=$mfTemplate+(GenerateChecksumString $ovfFileName $ovfCheckSumValue)+"`n"+(GenerateChecksumString $disk4FileName $disk4ChecksumValue)+"`n"
    $outputFilePath=$scriptPath+"\${product_name}-$releaseVersion-controller-$currentNodeId.mf"
    [io.file]::WriteAllText($outputFilePath, $mfContent)
}

function GenerateOVFFileTemplateForEachNode($disk4FileName, $currentNodeId) {
    $ovfTemplate='${include="storageos-vsphere-template.xml"}'
    $vSphereOnly="<Item>
        <rasd:Address>0</rasd:Address>
        <rasd:Description>SCSI Controller</rasd:Description>
        <rasd:ElementName>SCSI Controller 0</rasd:ElementName>
        <rasd:InstanceID>3</rasd:InstanceID>
        <rasd:ResourceSubType>VirtualSCSI</rasd:ResourceSubType>
        <rasd:ResourceType>6</rasd:ResourceType>
      </Item>"
    if ($Script:isVMX -eq $true) {
        $vSphereOnly=""
	}

    $ovfTemplate=$ovfTemplate.Replace('${vmdk_dir}', $scriptPath)
	$ovfTemplate=$ovfTemplate.Replace('${cpu_count}', $Script:cpuCount)
	$ovfTemplate=$ovfTemplate.Replace('${memory}', $Script:memory)
    $ovfTemplate=$ovfTemplate.Replace('${2}', $disk4FileName)
	$ovfContent=$ovfTemplate.Replace('${vsphere_only}', $vSphereOnly)
    $outputFilePath=$scriptPath+"\${product_name}-$releaseVersion-controller-$currentNodeId.ovf"
    [io.file]::WriteAllText($outputFilePath, $ovfContent)
}

function GenerateDisk4($currentNodeId, $ovfProperties) {
	# 2.1 Generate vmx file for each node
	$vmdkDescriptorName=$disk4NamePrefix+"descriptor-"+$currentNodeId+".vmdk"
	GenerateVMXDescriptor $vmdkDescriptorName $currentNodeId

	# 2.2 Generate disk4 descriptor file for each node
	$vmdkFlatFileName=$disk4NamePrefix+"descriptor-"+$currentNodeId+"-flat.vmdk"
	GenerateVMDKDescriptor $vmdkFlatFileName $currentNodeId

	# 2.3 Generate disk4 flat disk file for each node
	GenerateVMDKFlat $ovfProperties $currentNodeId

	# 2.4 Generate disk4 vmdk file for deployment for each node
	$vmxFileName=$disk4NamePrefix+$currentNodeId+".vmx"
	$vmdkFileName=$disk4NamePrefix+$currentNodeId+".vmdk"
	ovftool -o $vmxFileName $vmdkFileName
	if ($LASTEXITCODE -ne 0) {
        throw "Create disk4 failed for $currentNodeId"
    }

	# 2.5 Rename disk4
	$disk4Path=$scriptPath+"\"+$disk4NamePrefix+$currentNodeId+"-disk1.vmdk"
	$disk4FileName=$scriptPath+"\"+$disk4NamePrefix+$currentNodeId+".vmdk"
	Move-Item $disk4Path $disk4FileName -Force
}

function GenerateOVFAndCalChecksumForSingleVM($currentNodeId) {
	# 3.1 Generate OVF template
	$disk4FileName=$disk4NamePrefix+$currentNodeId+".vmdk"
	GenerateOVFFileTemplateForEachNode $disk4FileName $currentNodeId

	# 3.2 Calculate checksum for each disk4 file
	$ovfFileName="${product_name}-$releaseVersion-controller-$currentNodeId.ovf"
	$disk4FileName=$disk4NamePrefix+$currentNodeId+".vmdk"
	CalculateChecksumForOVFTemplateAndDisk4 $ovfFileName $disk4FileName $currentNodeId
}

function CreateSubfolderAndMoveFiles($currentNodeId) {
	$subfolderPath=$scriptPath+"\${product_name}-$releaseVersion-$currentNodeId"
	$returnValue=(New-Item $subfolderPath -type directory -Force)
	# move disk4 file, ovf file and mf file for that node to sub-folder
	Move-Item $scriptPath"\"$disk4NamePrefix$currentNodeId".vmdk" $scriptPath"\${product_name}-$releaseVersion-$currentNodeId" -Force
	Move-Item $scriptPath"\${product_name}-$releaseVersion-controller-$currentNodeId.ovf" $scriptPath"\${product_name}-$releaseVersion-$currentNodeId" -Force
	Move-Item $scriptPath"\${product_name}-$releaseVersion-controller-$currentNodeId.mf" $scriptPath"\${product_name}-$releaseVersion-$currentNodeId" -Force
}

function DisplayLicense() {
	if ($Script:acceptAllEulas -eq $false) {
		try {
			[string]$license="${include="storageos-license-ps.txt"}" | out-host -paging
		} 
		catch {
			# Do nothing
		}

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
-targetUri=$Script:savedUri
-ds=$Script:ds
-net=$Script:net
-dm=$Script:dm
-vmFolder=$Script:vmFolder
"	
	$enc = [system.Text.Encoding]::ASCII   
	$userSettingsContent=$enc.GetBytes($userSettings)
	$outputFilePath=$scriptPath+"\.settings"	
	[io.file]::WriteAllBytes($outputFilePath, $userSettingsContent)
}

function RegenerateTargetUri() {
	$hostUri=""
	$uid=""
	$pwd=""
	$protocal="vi"
	[String]$rawUri=$Script:targetUri
	
	$protocalIdx=$rawUri.IndexOf("://")
	if ($protocalIdx -eq -1) {
		$hostUri=$rawUri
	}
	else {
		$protocal=$rawUri.SubString(0, $protocalIdx)
		$restPart=$rawUri.SubString($protocalIdx+3)
		
		$hostIdx=$restPart.IndexOf("@")
		if ($hostIdx -eq -1) {
			$hostUri=$restPart
		}
		else {
			$hostUri=$restPart.SubString($hostIdx+1)
			$uidAndPwd=$restPart.SubString(0, $hostIdx)
			
			$pwdIdx=$uidAndPwd.IndexOf(":")
			if ($pwdIdx -eq -1) {
				$uid=$uidAndPwd
			}
			else {
				$uid=$uidAndPwd.SubString(0, $pwdIdx)
				$pwd=$uidAndPwd.SubString($pwdIdx+1)
			}
		}
	}
	
	if ( -not [String]::IsNullOrEmpty($Script:username)) {
		$uid=EncodingUrl $Script:username
	}
	
	if ( -not [String]::IsNullOrEmpty($Script:password)) {
		$pwd=EncodingUrl $Script:password
	}
	
	$newUri=$protocal+"://"
	if ([String]::IsNullOrEmpty($uid)) {
		$newUri+=$hostUri
		$Script:savedUri=$newUri
	}
	else {
		$newUri+=$uid
		$Script:savedUri=$newUri
		if (-not [String]::IsNullOrEmpty($pwd)) {
			$newUri+=":"+$pwd
		}
		$newUri+="@"+$hostUri
		$Script:savedUri+="@"+$hostUri
	}
	
	$Script:targetUri=$newUri
}

function DeploySingleNode($currentNodeId) {
	$vmname=$Script:vmName
	
	$vmnameOption="--name=$vmname"
    $ovfFileName=$scriptPath+"\${product_name}-$releaseVersion-$currentNodeId\${product_name}-$releaseVersion-controller-$currentNodeId.ovf"
    $dsOption=""
    if (-not [string]::IsNullOrEmpty($ds)) {
        $dsOption="-ds="+$ds
    }
	$netOption=""
    if (-not [string]::IsNullOrEmpty($net)) {
        $netOption="--net:ViPR Network=$net"
    }
    $vmFolderOption=""
    if (-not [string]::IsNullOrEmpty($vmFolder)) {
        $vmFolderOption="--vmFolder="+$vmFolder
    }
	$dmOption="-dm=eagerZeroedThick"
	if ($dm -eq "thin") {
		$dmOption="-dm=thin"
	}
	if ($dm -eq "lazyzeroedthick") {
		$dmOption="-dm=thick"
	}

	$acceptAllEulasOption="--acceptAllEulas"
    $powerOnOption=""
    if ($powerOn) {
        $powerOnOption="--powerOn"
    }
    ovftool $acceptAllEulasOption $dmOption $dsOption $powerOnOption $vmnameOption $vmFolderOption $netOption $ovfFileName $Script:targetUri 
    if ($LASTEXITCODE -ne 0) {
        throw "Deploy $currentNodeId failed."
    }
}

function DeployVMX($currentNodeId) {
	$vmname=$Script:vmName
	$acceptAllEulasOption="--acceptAllEulas"
		
    $ovfFileName=$scriptPath+"\${product_name}-$releaseVersion-$currentNodeId\${product_name}-$releaseVersion-controller-$currentNodeId.ovf"
	$vmxFileName=$scriptPath+"\${product_name}-$releaseVersion-$currentNodeId\$vmname.vmx"
	
	# Generate vmx file
	ovftool $acceptAllEulasOption $ovfFileName $vmxFileName
	
	# Generate disk section for VMX file 
	$diskInfos='scsi0:0.present = "TRUE"
scsi0:0.deviceType = "disk"
scsi0:0.fileName = "{1}"
scsi0:0.mode = "persistent"
scsi0:1.present = "TRUE"
scsi0:1.deviceType = "disk"
scsi0:1.fileName = "{2}"
scsi0:2.present = "TRUE"
scsi0:2.deviceType = "disk"
scsi0:2.fileName = "{3}"
scsi0:3.present = "TRUE"
scsi0:3.deviceType = "disk"
scsi0:3.fileName = "{4}"
scsi0.virtualDev = "lsilogic"
scsi0.present = "TRUE"
vmci0.unrestricted = "false"'
	for ($i = 1; $i -le 3; $i++) {
		$diskInfos=$diskInfos.Replace("{$i}",$scriptPath+"\vipr-$releaseVersion-disk$i.vmdk")
	}
	$diskInfos=$diskInfos.Replace("{4}",$scriptPath+"\vipr-$releaseVersion-$currentNodeId\vipr-$releaseVersion-disk4-$currentNodeId.vmdk")
	
	# Read VMX file content and append disk infos
	if ($Script:net.ToLower() -eq "nat") {
		(Get-Content $vmxFileName) | foreach-object {$_ -replace 'ethernet0.connectionType = "bridged"', 'ethernet0.connectionType = "nat"'} | Set-Content $vmxFileName
	}
	Add-Content -path $vmxFileName -value $diskInfos
	
	# Deploy VMX 
	$tmpDeployPath=$scriptPath+"\${product_name}-$releaseVersion-$currentNodeId"
	ovftool $acceptAllEulasOption $vmxFileName $tmpDeployPath
	
	# Deploy modified OVF to VM location
	$modifiedOVF=$tmpDeployPath+"\$vmname\$vmname.ovf"
	ovftool $acceptAllEulasOption $modifiedOVF $Script:vmFolder
}

if($help) {
    Usage
    return
}

try {
    # Check if the ovftool has been installed and configured.
    CheckPrerequisetes
	
    switch ($mode.ToLower()) {
        "install" {
			# 0. Validate necessary parameters integrity and correctness
			Write-Host ""
			Write-Host "****** Checking parameters of vSphere install ******"
			LoadParamsFromFile
			
			# 0. Display license to let user confirm	
			DisplayLicense
			
			if ($Script:acceptAllEulas -eq $false) {
				return
			}
			
			CheckFreshInstallParams
        }
        "redeploy" {
			# 0. Validate necessary parameters integrity	
			Write-Host ""
			Write-Host "****** Checking parameters of vSphere redeploy ******"	
			$Script:file=CheckParam $Script:file "Config file" $false $false IsValidNotNullParam
			LoadParamsFromFile
			
			# 0. Display license to let user confirm	
			DisplayLicense
			
			if ($Script:acceptAllEulas -eq $false) {
				return
			}

			CheckRedeploymentParams
        }
        "install-vmx" {
			$Script:isVMX=$true
			# 0. Validate necessary parameters integrity	
			Write-Host ""
			Write-Host "****** Checking parameters of vSphere redeploy ******"	
			LoadParamsFromFile
			
			# 0. Display license to let user confirm	
			DisplayLicense
			
			if ($Script:acceptAllEulas -eq $false) {
				return
			}
			
			CheckFreshInstallParams
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
	
	if ($Script:isVMX -eq $false) {
		# 2.0 Regenerate targetUri
		RegenerateTargetUri
	}
	
	# 2.1 Generate ovf-env.properties content for current node
	$currentNodeId="${product_name}$nodeId"
	[array]$outputProperties=GenerateOvfenvProperties                 
	
	# 3. Generate disk4(vmdk)     
	Write-Host ""			
	Write-Host "****** Generating disk4 vmdk files for current node ******"
	$idx=[Convert]::ToInt32($Script:nodeId, 10)-1
	GenerateDisk4 $currentNodeId $outputProperties[$idx]

	# 4. Generate OVF template and calculate checksum of disk4 for current node
	GenerateOVFAndCalChecksumForSingleVM $currentNodeId
				
	# 5. Create sub-folder for current node and move files only used for that node to corresponding sub-folder 
	CreateSubfolderAndMoveFiles $currentNodeId
		
	# 6. Save user settings
	SaveUserSettings
		
	# 7. Deploy vipr    
	Write-Host ""
	Write-Host "****** Deploying $Script:vmName ******"
	Write-Host ""
	if ($Script:isVMX -eq $false) {
		DeploySingleNode $currentNodeId
	} else {
		DeployVMX $currentNodeId
	}
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
