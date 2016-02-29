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

////////////////////////////////////////////////////////////////////

function validateLogin()
{
	var userName = document.getElementById("inputId").value;
	var passwd = document.getElementById("inputPassword").value;
	
	if (userName == "admin")
		{
			console.log("username is correct");
			if(passwd == "password")
				{
					console.log("authenticated");
				}
			else
				alert("wrong password");
		}
	else
		{
			alert("Not valid user");
		}
	
}