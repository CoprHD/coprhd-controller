function CheckPropertiesContentLength($outputProperties, $fixedContentLength) {
    for ($i=0; $i -lt $outputProperties.Length; $i++) {
        if ($fixedContentLength -lt $outputProperties[$i].Length) {
            throw "Properties content is larger than $fixedContentLength."
        }
    }
}

function IsValidIPv4Netmask($mask, $label) {
    $netmaskPattern = '^(254|252|248|240|224|192|128)\.0\.0\.0|255\.(254|252|248|240|224|192|128|0)\.0\.0|255\.255\.(254|252|248|240|224|192|128|0)\.0|255\.255\.255\.(254|252|248|240|224|192|128|0)$'
    $isValid = $true
    if (-not ($mask -match $netmaskPattern)) {
        Write-Host "Invalid $label"
        $isValid = $false
    }
    return $isValid
}

function IsValidIPv4($ipv4, $label) {
	if ([string]::IsNullOrEmpty($ipv4)) {
		return $false
	}
	
	$isValid=$true
	$defaultIPv4Value="0.0.0.0"
	$ipv4Pattern='^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$'
	if ($ipv4 -ne $defaultIPv4Value) {
        if (-not ($ipv4 -match $ipv4Pattern)) {
            Write-Host "Invalid  $label"  
			$isValid=$false
        }    
    }
	else {
		if ($label -ne "Public virtual IPv4 address") {
			$isValid=$false
		}
	}
	return $isValid
}

function IsValidIPv6($ipv6, $label) {
	if ([string]::IsNullOrEmpty($ipv6)) {
		return $false
	}

	$isValid=$true
	$defaultIPv6Value="::0"
    $ipv6Pattern='^([0-9A-Fa-f]{0,4}:){2,7}([0-9A-Fa-f]{1,4}$|((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\.|$)){4})$'
    if ($ipv6 -ne $defaultIPv6Value) {
        if (-not ($ipv6 -match $ipv6Pattern)) {
            Write-Host "Invalid  $label"  
			$isValid=$false
        }    
    }
	else {
		if ($label -ne "Public virtual IPv6 address") {
			$isValid=$false
		}
	}
	return $isValid
}

function IsValidNodeCount($paramValue, $label) {
	if ([string]::IsNullOrEmpty($paramValue)) {
		return $false
	}

	$isValid=$true
	if ((($paramValue -ne "3") -and ($paramValue -ne "5") -and ($paramValue -ne "1")) -or (($Script:isVMX -eq $true) -and ($paramValue -ne "1"))) {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	if ($paramValue -eq "1") {
		$Script:ipaddr_2="0.0.0.0"
		$Script:ipaddr_3="0.0.0.0"
		$Script:ipaddr_4="0.0.0.0"
		$Script:ipaddr_5="0.0.0.0"
		$Script:ipaddr6_2="::0"
		$Script:ipaddr6_3="::0"
		$Script:ipaddr6_4="::0"
		$Script:ipaddr6_5="::0"
	}
	if ($paramValue -eq "3") {
		$Script:ipaddr_4="0.0.0.0"
		$Script:ipaddr_5="0.0.0.0"
		$Script:ipaddr6_4="::0"
		$Script:ipaddr6_5="::0"
	}	
	return $isValid
}

function IsValidIPv6PrefixLength ($prefixLength, $label) {
	if ([string]::IsNullOrEmpty($prefixLength)) {
		return $false
	}

	$isValid=$true
    [int]$lengthValue = [convert]::ToInt32($prefixLength, 10)
    if (($lengthValue -lt 0) -or ($lengthValue -gt 128)) {
        Write-Host "Invalid  $label"  
		$isValid=$false
    }
	return $isValid
}

function IsValidNotNullParam($paramValue, $label) {
	$isValid=$true
	if ([String]::IsNullOrEmpty($paramValue)) {
		$isValid=$false
	}
	return $isValid
}

function IsValidNullParam($paramValue, $label) {
	return $true
}

function IsValidBooleanParam($paramValue, $label) {
	$isValid=$true
	if (($paramValue.ToLower() -ne "yes") -and ($paramValue.ToLower() -ne "no")) {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

# novApp only
function IsValidDm($paramValue, $label) {
	if ([string]::IsNullOrEmpty($paramValue)) {
		return $false
	}

	$isValid=$true
	if (($paramValue.ToLower() -ne "thin") -and ($paramValue.ToLower() -ne "zeroedthick") -and ($paramValue.ToLower() -ne "lazyzeroedthick")) {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

# Hyper-V only
function IsValidDiskType($paramValue, $label) {
	if ([string]::IsNullOrEmpty($paramValue)) {
		return $false
	}

	$isValid=$true
	if (($paramValue.ToLower() -ne "dynamic") -and ($paramValue.ToLower() -ne "fixed")) {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

# Hyper-V only
function IsValidLibraryPath($paramValue, $label) {

	$isValid=$true
	if ([String]::IsNullOrEmpty($paramValue)) {
		$isValid=$false
	}
	if ((-not [String]::IsNullOrEmpty($paramValue)) -and (-not (Test-Path $paramValue))) {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

# Hyper-V only
function IsValidVlan($paramValue, $label) {
	if ([string]::IsNullOrEmpty($paramValue)) {
		return $false
	}

	$isValid=$true
	try {
		$count=[Convert]::ToInt32($paramValue)
	}
	catch {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

# VMX only
function IsValidNet($paramValue, $label) {
	if ([string]::IsNullOrEmpty($paramValue)) {
		return $false
	}

	$isValid=$true
	if (($paramValue.ToLower() -ne "bridged") -and ($paramValue.ToLower() -ne "nat")) {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

function IsValidNodeId($paramValue, $label) {
	if ([string]::IsNullOrEmpty($paramValue)) {
		return $false
	}

	$isValid=$true
	try {
		$nodeIdPattern='^[1-5]$'
		if (-not ($paramValue -match $nodeIdPattern) -or ([Convert]::ToInt32($paramValue) -gt [Convert]::ToInt32($Script:nodeCount))) {
			Write-Host "Invalid  $label"
			$isValid=$false
		}
	}
	catch {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

function IsValidCPUCount($paramValue, $label) {
	if ([string]::IsNullOrEmpty($paramValue)) {
		return $false
	}

	$isValid=$true
	try {
		$count=[Convert]::ToInt32($paramValue)
		if (($count -lt 1) -or ($count -gt 16)) {
			Write-Host "Invalid  $label"
			$isValid=$false
		}
	}
	catch {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

function IsValidMemory($paramValue, $label) {
	if ([string]::IsNullOrEmpty($paramValue)) {
		return $false
	}

	$isValid=$true
	try {
		$count=[Convert]::ToInt32($paramValue)
		if (($count -lt 4096) -or ($count -gt 16384)) {
			Write-Host "Invalid  $label"
			$isValid=$false
		}
	}
	catch {
		Write-Host "Invalid  $label"
		$isValid=$false
	}
	return $isValid
}

function CheckParam ($paramValue, $label, $isInteractive, $isSecurity, $validFunc) {
	$result=& $validFunc $paramValue $label	

	if (($result -eq $false) -or ($isInteractive -eq $true)) { 
		while ($true) {
			$Script:interactive=$true
			$newValue=""
			
			if ($isSecurity -eq $true) {
				Write-Host "$label (): " -NoNewline
				$securityContent=Read-Host -AsSecureString
				$newValue=[Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($securityContent))
			}
			else {
				Write-Host "$label ($paramValue): " -NoNewline
				$newValue=Read-Host
			}
			if (-not [String]::IsNullOrEmpty($newValue)) {
				if (($newValue -eq "''") -or ($newValue -eq '""')) {
					$newValue=""
				}
				$newResult=& $validFunc $newValue $label
				if ($newResult -eq $true) {
					return $newValue
				} 			
			} else {
				if ($result -eq $true) {
					return $paramValue
				}
			}
		} 
	}
	else {
		return $paramValue
	}
}

function SetIpv4DefaultValue() {
	$defaultIPv4Value="0.0.0.0"
	$Script:ipaddr_1=$defaultIPv4Value
	$Script:ipaddr_2=$defaultIPv4Value
	$Script:ipaddr_3=$defaultIPv4Value
	$Script:ipaddr_4=$defaultIPv4Value
	$Script:ipaddr_5=$defaultIPv4Value
	$Script:gateway=$defaultIPv4Value
	$Script:netmask="255.255.255.0"
}

function IsIPv4Setted() {
	$isIPv4Setted=$false
	if (($Script:vip -ne "0.0.0.0") -or ($Script:ipaddr_1 -ne "0.0.0.0") -or ($Script:ipaddr_2 -ne "0.0.0.0") -or ($Script:ipaddr_3 -ne "0.0.0.0") -or ($Script:ipaddr_4 -ne "0.0.0.0") -or ($Script:ipaddr_5 -ne "0.0.0.0") -or ($Script:isNetmaskGiven -eq $true -and $Script:netmask -ne "255.255.255.0") -or ($Script:gateway -ne "0.0.0.0")) {
		$isIPv4Setted=$true
	}
	
	return $isIPv4Setted
}

function IsIPv6Setted() {
	$isIPv6Setted=$false
	if (($Script:vip6 -ne "::0") -or ($Script:ipaddr6_1 -ne "::0") -or ($Script:ipaddr6_2 -ne "::0") -or ($Script:ipaddr6_3 -ne "::0") -or ($Script:ipaddr6_4 -ne "::0") -or ($Script:ipaddr6_5 -ne "::0") -or ($Script:gateway6 -ne "::0")) {
		$isIPv6Setted=$true
	}
	
	return $isIPv6Setted
}

function CheckIpv4Params ($isInteractive) {
	$isIpv4Set=IsIPv4Setted
	if (($isInteractive -eq $false) -and ($isIpv4Set -eq $true) -and ($Script:vip -eq "0.0.0.0")) {
		$Script:vip=CheckParam $Script:vip "Public virtual IPv4 address" $true $false IsValidIPv4
	}
	else {
		$Script:vip=CheckParam $Script:vip "Public virtual IPv4 address" $isInteractive $false IsValidIPv4
	}
	
    if ($Script:vip -ne "0.0.0.0") {
        $Script:ipaddr_1=CheckParam $Script:ipaddr_1 "Server 1 IPv4 address" $isInteractive $false IsValidIPv4
		if ($Script:nodeCount -ne "1") {
			$Script:ipaddr_2=CheckParam $Script:ipaddr_2 "Server 2 IPv4 address" $isInteractive $false IsValidIPv4
			$Script:ipaddr_3=CheckParam $Script:ipaddr_3 "Server 3 IPv4 address" $isInteractive $false IsValidIPv4
		}
		if ($Script:nodeCount -eq "5") { 
			$Script:ipaddr_4=CheckParam $Script:ipaddr_4 "Server 4 IPv4 address" $isInteractive $false IsValidIPv4
			$Script:ipaddr_5=CheckParam $Script:ipaddr_5 "Server 5 IPv4 address" $isInteractive $false IsValidIPv4
		}

		if ($Script:nodeCount -eq "1") {
			$Script:ipaddr_2="0.0.0.0"
			$Script:ipaddr_3="0.0.0.0"
			$Script:ipaddr_4="0.0.0.0"
			$Script:ipaddr_5="0.0.0.0"
		}
		if ($Script:nodeCount -eq "3") {
			$Script:ipaddr_4="0.0.0.0"
			$Script:ipaddr_5="0.0.0.0"
		}
		
        $Script:gateway=CheckParam $Script:gateway "IPv4 default gateway" $isInteractive $false IsValidIPv4
		if ($Script:isNetmaskGiven -eq $false) {
			$Script:netmask=CheckParam $Script:netmask "Network netmask" $true $false IsValidIPv4Netmask
			$Script:isNetmaskGiven=$true
		}
		else {
			$Script:netmask=CheckParam $Script:netmask "Network netmask" $isInteractive $false IsValidIPv4Netmask
		}
    }
	else {
		SetIpv4DefaultValue
	}
}

function SetIpv6DefaultValue() {
	$defaultIPv6Value="::0"
	$Script:ipaddr6_1=$defaultIPv6Value
	$Script:ipaddr6_2=$defaultIPv6Value
	$Script:ipaddr6_3=$defaultIPv6Value
	$Script:ipaddr6_4=$defaultIPv6Value
	$Script:ipaddr6_5=$defaultIPv6Value
	$Script:gateway6=$defaultIPv6Value
	$Script:ipv6prefixlength="64"
}

function CheckIpv6Params ($isInteractive) {
	$isIpv6Set=IsIPv6Setted
	if (($isInteractive -eq $false) -and ($isIpv6Set -eq $true) -and ($Script:vip6 -eq "::0")) {
		$Script:vip6=CheckParam $Script:vip6 "Public virtual IPv6 address" $true $false IsValidIPv6
	}
	else {
		$Script:vip6=CheckParam $Script:vip6 "Public virtual IPv6 address" $isInteractive $false IsValidIPv6
	}
	
    if ($Script:vip6 -ne "::0") {
        $Script:ipaddr6_1=CheckParam $Script:ipaddr6_1 "Server 1 IPv6 address" $isInteractive $false IsValidIPv6
		if ($Script:nodeCount -ne "1") {
			$Script:ipaddr6_2=CheckParam $Script:ipaddr6_2 "Server 2 IPv6 address" $isInteractive $false IsValidIPv6
			$Script:ipaddr6_3=CheckParam $Script:ipaddr6_3 "Server 3 IPv6 address" $isInteractive $false IsValidIPv6
		}
		if ($Script:nodeCount -eq "5") {
			$Script:ipaddr6_4=CheckParam $Script:ipaddr6_4 "Server 4 IPv6 address" $isInteractive $false IsValidIPv6
			$Script:ipaddr6_5=CheckParam $Script:ipaddr6_5 "Server 5 IPv6 address" $isInteractive $false IsValidIPv6
		} 
		if ($Script:nodeCount -eq "1") {
			$Script:ipaddr6_2="::0"
			$Script:ipaddr6_3="::0"
			$Script:ipaddr6_4="::0"
			$Script:ipaddr6_5="::0"
		}
		if ($Script:nodeCount -eq "3") {
			$Script:ipaddr6_4="::0"
			$Script:ipaddr6_5="::0"
		}
        $Script:gateway6=CheckParam $Script:gateway6 "IPv6 default gateway" $isInteractive $false IsValidIPv6
		$Script:ipv6prefixlength=CheckParam $Script:ipv6prefixlength "IPv6 prefix length" $isInteractive $false IsValidIPv6PrefixLength
    }
	else {
		SetIpv6DefaultValue
	}
}

function hasDupIPv4Props() {
	$hasDuplicate = $false
	if (IsIPv4Setted) {
		$IPs = @()
		$dupIPs = @()
		$IPs += $Script:vip
		if(-not ($Script:ipaddr_1 -in $IPs)) {
			$IPs += $Script:ipaddr_1
		} elseif (-not ($Script:ipaddr_1 -in $dupIPs)) {
			$dupIPs += $Script:ipaddr_1
		}
		if ($Script:nodeCount -ne "1") {
			if(-not ($Script:ipaddr_2 -in $IPs)) {
				$IPs += $Script:ipaddr_2
			} elseif (-not ($Script:ipaddr_2 -in $dupIPs)) {
				$dupIPs += $Script:ipaddr_2
			}
			if(-not ($Script:ipaddr_3 -in $IPs)) {
				$IPs += $Script:ipaddr_3
			} elseif (-not ($Script:ipaddr_3 -in $dupIPs)) {
				$dupIPs += $Script:ipaddr_3
			}
		}
		if ($Script:nodeCount -eq "5") {
			if(-not ($Script:ipaddr_4 -in $IPs)) {
				$IPs += $Script:ipaddr_4
			} elseif (-not ($Script:ipaddr_4 -in $dupIPs)) {
				$dupIPs += $Script:ipaddr_4
			}
			if(-not ($Script:ipaddr_5 -in $IPs)) {
				$IPs += $Script:ipaddr_5
			} elseif (-not ($Script:ipaddr_5 -in $dupIPs)) {
				$dupIPs += $Script:ipaddr_5
			}
		}
		if(-not ($Script:gateway -in $IPs)) {
			$IPs += $Script:gateway
		} elseif (-not ($Script:gateway -in $dupIPs)) {
			$dupIPs += $Script:gateway
		}
		if(-not ($Script:netmask -in $IPs)) {
			$IPs += $Script:netmask
		} elseif (-not ($Script:netmask -in $dupIPs)) {
			$dupIPs += $Script:netmask
		}
		if("$($IPs.Length - 3)" -ne $Script:nodeCount) {
			Write-Host "There are duplicate IPs in your input IPv4 properties: $dupIPs"
			$hasDuplicate = $true
		}
	}
	return $hasDuplicate
}

function hasDupIPv6Props() {
	$hasDuplicate = $false
	if (IsIPv6Setted) {
		$IPs = @()
		$dupIPs = @()
		$IPs += $Script:vip6
		if(-not ($Script:ipaddr6_1 -in $IPs)) {
			$IPs += $Script:ipaddr6_1
		} elseif(-not ($Script:ipaddr6_1 -in $dupIPs)) {
			$dupIPs += $Script:ipaddr6_1
		}
		if ($Script:nodeCount -ne "1") {
			if(-not ($Script:ipaddr6_2 -in $IPs)) {
				$IPs += $Script:ipaddr6_2
			} elseif(-not ($Script:ipaddr6_2 -in $dupIPs)) {
				$dupIPs += $Script:ipaddr6_2
			}
			if(-not ($Script:ipaddr6_3 -in $IPs)) {
				$IPs += $Script:ipaddr6_3
			} elseif(-not ($Script:ipaddr6_3 -in $dupIPs)) {
				$dupIPs += $Script:ipaddr6_3
			}
		}
		if ($Script:nodeCount -eq "5") {
			if(-not ($Script:ipaddr6_4 -in $IPs)) {
				$IPs += $Script:ipaddr6_4
			} elseif(-not ($Script:ipaddr6_4 -in $dupIPs)) {
				$dupIPs += $Script:ipaddr6_4
			}
			if(-not ($Script:ipaddr6_5 -in $IPs)) {
				$IPs += $Script:ipaddr6_5
			} elseif(-not ($Script:ipaddr6_5 -in $dupIPs)) {
				$dupIPs += $Script:ipaddr6_5
			}
		}
		if(-not ($Script:gateway6 -in $IPs)) {
			$IPs += $Script:gateway6
		} elseif(-not ($Script:gateway6 -in $dupIPs)) {
			$dupIPs += $Script:gateway6
		}
		if("$($IPs.Length - 2)" -ne $Script:nodeCount) {
			Write-Host "There are duplicate IPs in your input IPv6 properties: $dupIPs"
			$hasDuplicate = $true
		}
	}
	return $hasDuplicate
}

function CheckNetWorkProperties($isInteractive) {
    $nodeCountLabel = "Node count [ 1 (evaluation only) | 3 | 5 ]"
    if ($Script:isVMX -eq $true) {
        $nodeCountLabel = "Node count [ 1 (Node count can only be 1 in install-vmx mode) ]"
    }
	$newNodeCount=CheckParam $Script:nodeCount $nodeCountLabel $isInteractive $false IsValidNodeCount
	if ($Script:nodeCount -ne $newNodeCount) {
		if ($newNodeCount -ne "5") {
			if (-not $Script:isCPUCountGiven) {
				$Script:cpuCount="2"
			}
			if (-not $Script:isMemoryGiven) {
				$Script:memory="8192"
			}
		} 
		else {
			if (-not $Script:isCPUCountGiven) {
				$Script:cpuCount="4"
			}
			if (-not $Script:isMemoryGiven) {
				$Script:memory="16384"
			}
		}
	}
	$Script:nodeCount=$newNodeCount
	
	while ($true) {
		CheckIpv4Params $isInteractive
		while (hasDupIPv4Props) {
			CheckIpv4Params $true
		}
		CheckIpv6Params $isInteractive
		while (hasDupIPv6Props) {
			CheckIpv6Params $true
		}
		if (($Script:vip -ne "0.0.0.0") -or ($Script:vip6 -ne "::0")) {
			break
		}
		else {
		   $isInteractive=$true
		}
	}
}

function AskUserDecisionRecurr($label) {
	while ($true) {
		Write-Host "Would you like to keep $label (Y/y/N/n): " -NoNewline
		$userDecision=Read-Host
		if ($userDecision.ToLower() -eq "y") {
			return $true
		}
		if ($userDecision.ToLower() -eq "n"){
			return $false
		}
	}
}

function GenerateOvfenvProperties() {
    $nodesIpProperties=[String]::Format("network_1_ipaddr6={0}`nnetwork_1_ipaddr={1}`n", 
	$Script:ipaddr6_1, $Script:ipaddr_1)
	if ($Script:nodeCount -ne "1") {
		$nodesIpProperties+=[String]::Format("network_2_ipaddr6={0}`nnetwork_2_ipaddr={1}`nnetwork_3_ipaddr6={2}`nnetwork_3_ipaddr={3}`n", 
		$Script:ipaddr6_2, $Script:ipaddr_2, $Script:ipaddr6_3, $Script:ipaddr_3)
	}
    if ($Script:nodeCount -eq "5") {
		$nodesIpProperties+=[String]::Format("network_4_ipaddr6={0}`nnetwork_4_ipaddr={1}`nnetwork_5_ipaddr6={2}`nnetwork_5_ipaddr={3}`n", 
		$Script:ipaddr6_4, $Script:ipaddr_4, $Script:ipaddr6_5, $Script:ipaddr_5)
	}
	$nodesIpProperties+=[String]::Format("ipsec_key_ovfenv={0}`n",$Script:ipsec_key)
    $outputProperties=New-Object string[] $nodeCount  
    for ($i=0; $i -lt $outputProperties.Length; $i++) {
        $currentNodeId='${product_name}'+($i+1)       
        $ovfProperties=[String]::Format("{0}network_gateway6={1}`nnetwork_gateway={2}`nnetwork_netmask={3}`nnetwork_prefix_length={4}`nnetwork_vip6={5}`nnetwork_vip={6}`nnode_count={7}`nnode_id={8}`nmode={9}`n", $nodesIpProperties, $gateway6, $gateway, $netmask, $ipv6prefixlength, $vip6, $vip, $nodeCount, $currentNodeId, $mode.ToLower())              
        $outputProperties[$i]=$ovfProperties
    }      
    return $outputProperties
}

function CheckVersion() {
	if ($Script:clusterVersion -ne "${product_name}-$Script:releaseversion") {
		throw "The versions are different: cluster:$Script:clusterVersion, the node to be redeploy: ${product_name}-$Script:releaseversion"
	}
}

function EncodingUrl($url) {
	$nullOutput=[System.Reflection.Assembly]::LoadWithPartialName("System.web")
	$encodedUrl=[System.Web.HttpUtility]::UrlEncode($url) 
	return $encodedUrl
}

function SetDefaultValueOfCPUAndMemory() {
	if ([String]::IsNullOrEmpty($Script:cpuCount)) {
		if ($Script:nodeCount -ne "5") {
			$Script:cpuCount="2"
		}
		else {
			$Script:cpuCount="4"
		}
	}
	if ([String]::IsNullOrEmpty($Script:memory)) {
		if ($Script:nodeCount -ne "5") {
			$Script:memory="8192"
		}
		else {
			$Script:memory="16384"
		}
	}
}