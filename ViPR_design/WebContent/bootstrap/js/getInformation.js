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

if(XMLHttpRequestObject)
{
	alert("true");
}
else
	{
	alert("false");
	}


function saveInformation()
{
	
	alert("i am in saveInformation");
	var username = document.getElementById("first_name").value;
	var title = document.getElementById("title").value;
	var emailID = document.getElementById("Emailid").value;
	var telephone = document.getElementById("Telephone").value;
	var mobile = document.getElementById("Mobile").value;
	
	
	
	var toServer = [username, title, emailID, telephone,  mobile];
	var myJsonString = JSON.stringify(toServer);
	
	for(var i=0; i<5;i++)
		{
			alert(toServer[i]);
		}
	
	if(XMLHttpRequestObject)
		{	
			alert("if XMLHttpRequestObject");
			var url = "./ContactFetch5";
			XMLHttpRequestObject.open("POST", url, true);
		
				XMLHttpRequestObject.setRequestHeader("Content-Type", "application/json");
				XMLHttpRequestObject.send(myJsonString);
				alert("its posted");
				
				XMLHttpRequestObject.onreadystatechange = function()
				{
					
						
					alert("its inside function");
					console.log("status=="+XMLHttpRequestObject.readyState);
					console.log("status--"+XMLHttpRequestObject.status);
					if(XMLHttpRequestObject.readyState ==4 && XMLHttpRequestObject.status == 200)
						{
							console.log("came in");
							
							if((XMLHttpRequestObject.responseText).substring(0, 4) == "http")
								{
									//obj.innerHTML = XMLHttpRequestObject.responseText;
									System.out.println("Its at 1st stage");
								}
							else
								{
									//var responsesStr = XMLHttpRequestObject.responseText;
									System.out.println("Its at 2nd stage");
																	
								}
						}
					else{
							System.out.println("Going out");
					}
					
				};
				
		}
}

function getInformation()
{
	/*//alert("its coming here");
	var FirstName = document.getElementById("first_name").value;
	var Title = document.getElementById("title").value;
	var MailId = document.getElementById("Emailid").value;
	var telephone = document.getElementById("Telephone").value;
	var mobile = document.getElementById("Mobile").value;
	
	var tableRow = "<tr><td>"+FirstName+"</td>" +
					"<td>"+Title+"</td>" +
					"<td>"+MailId+"</td>" +
					"<td>"+telephone+"</td>" +
					"<td>"+mobile+"</td></tr>";
	
	$("#contactTable > tbody").append(tableRow);
	alert("values received");
}
var responses = XMLHttpRequestObject.responseText;
var len = responses.length;
alert(len)*/
/*	$ajax({
		
		url:"ContactFetch6.java",
		type: "POST",
		dataType: "json",
		data: $("#submit").serialize(),
		success: function(data){
			if(data.isValid){
				alert("here");
			}
		}
	
});	*/
	alert("i am here");
	alert("XMLHttpRequestObject");
	if(XMLHttpRequestObject)
		{	
			alert("if XMLHttpRequestObject");
			var url = "./ContactFetch6";
			XMLHttpRequestObject.open("POST", url, true);
		
				XMLHttpRequestObject.setRequestHeader("Content-Type", "text/xml");
				XMLHttpRequestObject.send();
				alert("its posted");
				
				XMLHttpRequestObject.onreadystatechange = function()
				{
					
						
					alert("its inside function");
					console.log("status=="+XMLHttpRequestObject.readyState);
					console.log("status--"+XMLHttpRequestObject.status);
					if(XMLHttpRequestObject.readyState ==4 && XMLHttpRequestObject.status == 200)
						{
							console.log("came in");
							
							if((XMLHttpRequestObject.responseText).substring(0, 4) == "http")
								{
									//obj.innerHTML = XMLHttpRequestObject.responseText;
									System.out.println("Its at 1st stage");
								}
							else
								{
									//var responsesStr = XMLHttpRequestObject.responseText;
									System.out.println("Its at 2nd stage");
																	
								}
						}
					else{
							System.out.println("Going out");
					}
					
				}	;
				
		}
}

