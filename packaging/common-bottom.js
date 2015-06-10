showHideIpForNode();
	function showHideIpForNode(){
		var ele = document.getElementById("nodeCount");
		var nodeCount = ele[ele.selectedIndex].value;
		if(nodeCount == 1){
			document.getElementById("nodeCount1").style.display = 'block';
			document.getElementById("server1_IPv4").style.display = 'block';
			document.getElementById("server2_IPv4").style.display = 'none';
			document.getElementById("server3_IPv4").style.display = 'none';
			document.getElementById("server4_IPv4").style.display = 'none';
			document.getElementById("server5_IPv4").style.display = 'none';
			
			document.getElementById("server1_IPv6").style.display = 'block';
			document.getElementById("server2_IPv6").style.display = 'none';
			document.getElementById("server3_IPv6").style.display = 'none';
			document.getElementById("server4_IPv6").style.display = 'none';
			document.getElementById("server5_IPv6").style.display = 'none';
		}
		if(nodeCount == 3){
			document.getElementById("cpuCount").value="2";
			document.getElementById("memory").value="8192";
			document.getElementById("nodeCount1").style.display = 'none';
			document.getElementById("server1_IPv4").style.display = 'block';
			document.getElementById("server2_IPv4").style.display = 'block';
			document.getElementById("server3_IPv4").style.display = 'block';
			document.getElementById("server4_IPv4").style.display = 'none';
			document.getElementById("server5_IPv4").style.display = 'none';
			
			document.getElementById("server1_IPv6").style.display = 'block';
			document.getElementById("server2_IPv6").style.display = 'block';
			document.getElementById("server3_IPv6").style.display = 'block';
			document.getElementById("server4_IPv6").style.display = 'none';
			document.getElementById("server5_IPv6").style.display = 'none';
		}
		if(nodeCount == 5){
		    document.getElementById("cpuCount").value="4";
			document.getElementById("memory").value="16384";
			document.getElementById("nodeCount1").style.display = 'none';
			document.getElementById("server1_IPv4").style.display = 'block';
			document.getElementById("server2_IPv4").style.display = 'block';
			document.getElementById("server3_IPv4").style.display = 'block';
			document.getElementById("server4_IPv4").style.display = 'block';
			document.getElementById("server5_IPv4").style.display = 'block';
			
			document.getElementById("server1_IPv6").style.display = 'block';
			document.getElementById("server2_IPv6").style.display = 'block';
			document.getElementById("server3_IPv6").style.display = 'block';
			document.getElementById("server4_IPv6").style.display = 'block';
			document.getElementById("server5_IPv6").style.display = 'block';
		}
	}
	