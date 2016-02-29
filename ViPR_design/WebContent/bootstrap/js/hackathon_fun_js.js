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
		
		alert("vipr ip : "+ViPRIP);
		alert("vipr username :"+ViPR_username);
		alert("vipr password :"+ViPR_password);

		return ViPRIP;
		
}

function Migrate()
{
	/*var HostMigrate = "http://"+ViPRIP+":9090/myApp/cometmigrate/migrate";
	alert ("Host Migrate="+HostMigrate);*/
	var hostIP = "10.247.98.230";
	var HostMigrate1 = "http://"+hostIP+":9090/myApp/cometmigrate/migrate";
	alert(HostMigrate1);
	var url = HostMigrate1;
	
	if(XMLHttpRequestObject)
		{
		
			XMLHttpRequestObject.onreadystatechange= function()
			{
				if(XMLHttpRequestObject.readyState== 4 && XMLHttpRequestObject.status == 200)
					{
						/*if((XMLHttpRequestObject.responseText).substring(0, 4)=="http")
							{
								alert("http"+XMLHttpRequestObject.responseText);
							}*/
						alert("inside xmlhttp")
					}
			}
			XMLHttpRequestObject.open("GET", url,true);
			XMLHttpRequestObject.send();
				
				//XMLHttpRequestObject.setRequestHeader("Content-Type", "text/xml");
				
			
				
		}


}




