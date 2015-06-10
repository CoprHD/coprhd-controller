 function showConfirm() {
		if (confirm("Do you want to generate the setting file?") == true) {
			generateSettingsFile();
			document.getElementById('generateFileForm').setAttribute("onSubmit", "return true;");
		}else{
			document.getElementById('generateFileForm').setAttribute("onSubmit", "return false;");
		}
	}
  
	function destroyClickedElement(event)
	{
		document.body.removeChild(event.target);
	}
	
	function validateForRequired(obj){
		var controlGroup = obj.parentNode.parentNode;
		if(obj.value == "" || obj.value == null){
			addValidationMessage(controlGroup, 'Required');
		}else{
			removeValidationMessage(controlGroup);
		}
	}
	function addValidationMessage(controlGrp,msg){
		controlGrp.className = "form-group required has-error";
		controlGrp.children[2].children[0].innerHTML=msg;
		document.getElementById("generateBtn").disabled = true;
	}
	function removeValidationMessage(controlGrp){
		controlGrp.className = "form-group required";
		controlGrp.children[2].children[0].innerHTML='';
		document.getElementById("generateBtn").disabled = false;
	}
	
	function validateIPAddress(ipaddrEle) {
		var ipaddr = ipaddrEle.value;
		   ipaddr = ipaddr.replace( /\s/g, ""); //remove spaces for checking
			var re = /^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/; //regex. check for digits and in
            //all 4 quadrants of the IP
			if (re.test(ipaddr)) {
				var parts = ipaddr.split(".");
				//if any part is greater than 255
				for (var i=0; i<parts.length; i++) {
					if (parseInt(parseFloat(parts[i])) > 255){
						return false;
					}
				}
				return true;
			} else {
				return false;
			}
	}
	
	function validateIpAdrrAndShowMsg(ipaddrEle){
		var controlGroup = ipaddrEle.parentNode.parentNode;
		if(!validateIPAddress(ipaddrEle)){
			addValidationMessage(controlGroup, 'Invalid IP Adress.Valid IP Example 10.100.97.121');
		}else{
			removeValidationMessage(controlGroup);
		}
	}

	function validateIPv6Address(ipaddrEle) {
		var ipaddr = ipaddrEle.value;
		if(ipaddr != "::0"){
			ipaddr = ipaddr.replace( /\s/g, "");
			var re = /^(?:[A-F0-9]{1,4}:){7}[A-F0-9]{1,4}$/; //regex to check for hexadecimal
			if (re.test(ipaddr)) {
				return true;
			} else {
				return false;
			}
		}else{
			return true;
		}
	}
	
	function validateIpv6AdrrAndShowMsg(ipaddrEle){
		var controlGroup = ipaddrEle.parentNode.parentNode;
		if(!validateIPv6Address(ipaddrEle)){
			addValidationMessage(controlGroup,'Invalid IPv6 address. Valid Format is xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx(x is a hexadecimal value).');
		}else{
			removeValidationMessage(controlGroup);
		}
	}
	
	function enableDisableIpfieldsBasedOnVIP(ipaddrEle){
		var controlGroup = ipaddrEle.parentNode.parentNode;
		if((controlGroup.className).indexOf('has-error') > 0){
			if(ipaddrEle.id == 'virtualIPv4'){
				disableIPv4Fields();
			}
			if(ipaddrEle.id == 'virtualIPv6'){
				disableIPv6Fields();
			}
		
		}else{
			if(ipaddrEle.id == 'virtualIPv4'){
				enableIPv4Fields();
			}
			if(ipaddrEle.id == 'virtualIPv6'){
				enableIPv6Fields();
			}
		}
	}
	function enableIPv4Fields(){
		document.getElementById('server1IPv4').removeAttribute("disabled");
		document.getElementById('server2IPv4').removeAttribute("disabled");
		document.getElementById('server3IPv4').removeAttribute("disabled");
		document.getElementById('server4IPv4').removeAttribute("disabled");
		document.getElementById('server5IPv4').removeAttribute("disabled");
		document.getElementById('defaultGatewayIPv4').removeAttribute("disabled");
		document.getElementById('networkNetmask').removeAttribute("disabled");
	}
	
	function disableIPv4Fields(){
		document.getElementById('server1IPv4').disabled = "true";
		document.getElementById('server2IPv4').disabled = "true";
		document.getElementById('server3IPv4').disabled = "true";
		document.getElementById('server4IPv4').disabled = "true";
		document.getElementById('server5IPv4').disabled = "true";
		document.getElementById('defaultGatewayIPv4').disabled = "true";
		document.getElementById('networkNetmask').disabled = "true";
	}
	function enableIPv6Fields(){
		document.getElementById('server1IPv6').removeAttribute("disabled");
		document.getElementById('server2IPv6').removeAttribute("disabled");
		document.getElementById('server3IPv6').removeAttribute("disabled");
		document.getElementById('server4IPv6').removeAttribute("disabled");
		document.getElementById('server5IPv6').removeAttribute("disabled");
		document.getElementById('defaultGatewayIPv6').removeAttribute("disabled");
		document.getElementById('ipv6PrefixLength').removeAttribute("disabled");
	}
	
	function disableIPv6Fields(){
		document.getElementById('server1IPv6').disabled = "true";
		document.getElementById('server2IPv6').disabled = "true";
		document.getElementById('server3IPv6').disabled = "true";
		document.getElementById('server4IPv6').disabled = "true";
		document.getElementById('server5IPv6').disabled = "true";
		document.getElementById('defaultGatewayIPv6').disabled = "true";
		document.getElementById('ipv6PrefixLength').disabled = "true";
	}