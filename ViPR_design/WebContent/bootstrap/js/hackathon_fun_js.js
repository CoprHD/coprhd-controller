/**
 * 
 */

//////////////////////////////////////////////////////////////////////////////////////////////////////
// This gets run once as soon as the js file is included.  This is the initialization
// area.  Yep, guessing there is a better way of initializing these globals for the script.
//////////////////////////////////////////////////////////////////////////////////////////////////////
var XMLHttpRequestObject = false;

if (window.XMLHttpRequest)
{
    XMLHttpRequestObject = new XMLHttpRequest();
}
else if (window.ActiveXObject)
{
    XMLHttpRequestObject = new ActiveXObject("Microsoft.XMLHTTP");
}




function collectInformation() 
{
	
	    ViPRIP = document.getElementById("ViPR_IP").value;
		var ViPR_username = document.getElementById("ViPR_user").value;
		var ViPR_password = document.getElementById("ViPR_pass").value;
		
		/*alert("vipr ip : "+ViPRIP);
		alert("vipr username :"+ViPR_username);
		alert("vipr password :"+ViPR_password);*/

		//return ViPRIP;
		
}

function Migrate()
{
	/*var HostMigrate = "http://"+ViPRIP+":9090/myApp/cometmigrate/migrate";
	alert ("Host Migrate="+HostMigrate);*/
	var hostIP = "10.247.98.230";
	
	
	host=document.getElementById("HostsLists").value;
	volume=document.getElementById("source_vol").value;
	vpool=document.getElementById("targetarray").value;
	//var HostMigrate1 = "http://localhost:9090/myApp/powermigrate/migrateVolume?host="+host+"&sourceVolume="+volume+"&targetVolume="+vpool;
	var MigrateUrl = "http://localhost:9090/myapp/powermigrate/migrateTest?host="+host+"&sourceVolume="+volume+"&targetVolume=PPTargetVirtualPool";
	//alert(MigrateUrl);
		
		
		$.ajax({
		    url:MigrateUrl,
		    type:'GET',
		    success: function()
		    {
		    	alert("executed");
		    }
		});
	
/*	var url = HostMigrate1;
	
	if(XMLHttpRequestObject)
		{
		
			XMLHttpRequestObject.onreadystatechange= function()
			{
				if(XMLHttpRequestObject.readyState== 4 && XMLHttpRequestObject.status == 200)
					{
						if((XMLHttpRequestObject.responseText).substring(0, 4)=="http")
							{
								alert("http"+XMLHttpRequestObject.responseText);
							}
						alert("inside xmlhttp");
					}
			}
			XMLHttpRequestObject.open("GET", url,true);
			XMLHttpRequestObject.send();
				*/
				//XMLHttpRequestObject.setRequestHeader("Content-Type", "text/xml");
				
			
				
		}





/*function getHosts(){
	$.ajax({
	    url:'http://localhost:9090/myapp/powermigrate/hosts',
	    type:'GET',
	    dataType: ,
	    success: function() {
	        $.each(json, function(i, value) {
	           var vol = value.split("=",2);
	            $('#HostsLists').append($('<option>').text(vol[0]).attr('value', vol[1]));
	        });
	    }
	});	
}*/


function loadData()
{
	getHosts();
	getVolumes();
}
function getHosts()
{
	$.get( "http://localhost:9090/myapp/powermigrate/hosts", function( data ) {
		
		var hostsValue = new Array();
		hostsValue = data.split(",");
		var selectId = document.getElementById("HostsLists");
		for(var i=0;i<hostsValue.length;i++){
			var host=hostsValue[i].split("=",2);
			console.log("Host "+host[0]+ " Value:"+host[1]);
			 $('#HostsLists').append($('<option>').text(host[0]).attr('value', host[1]));
		}
		console.log("Called host value"+hostsValue);	
		});
}

function getVolumes(){
$.get( "http://localhost:9090/myapp/powermigrate/volumes", function( data ) {
		
		var volumesValue = new Array();
		volumesValue = data.split(",");
		var selectId = document.getElementById("source_vol");
		for(var i=0;i<volumesValue.length;i++){
			var volume=volumesValue[i];
			 $('#source_vol').append($('<option>').text(volume).attr('value', volume));
		}
		console.log("Called host value"+volume);	
		});}

var getJSON = function(url) {
	  return new Promise(function(resolve, reject) {
	    var xhr = new XMLHttpRequest();
	    xhr.open('get', url, true);
	    xhr.responseType = 'json';
	    xhr.onload = function() {
	      var status = xhr.status;
	      if (status == 200) {
	        resolve(xhr.response);
	      } else {
	        reject(status);
	      }
	    };
	    xhr.send();
	  });
	};







