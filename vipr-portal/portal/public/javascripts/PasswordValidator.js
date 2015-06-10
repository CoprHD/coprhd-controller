function getValidatePasswordRes(data,controlGroup,url){
		$.post(url, data)
		.done(function(result) {
		showPasswordValidityStatus(result,controlGroup);
	});
}
function validatePasswordStrength(passwdField,url,authToken){
	   $("#" + passwdField).on('keyup',function(){
		   var data = {
		            authenticityToken:authToken ,
		            password: $('#' + passwdField).val(),
		            fieldName: passwdField,
		            oldPassword: $("input[name*='oldPassword']").val()
		    }
		   var controlGroup = $('input[name="'+ $("#" + passwdField).attr('name')+'"]').parents('.form-group');
		    removeValidationMessage(controlGroup);
			 if($('#validationRes')){
					$('#validationRes').remove();	
				}
			 if($("#" + passwdField).val()=="" || $("#" + passwdField).val()== null){
				 addValidationMessage(controlGroup,'Required') ;
			 }else{
				 setTimeout(getValidatePasswordRes(data,controlGroup,url),500000);
			 }
	 	 });
}

function showPasswordValidityStatus(results,controlGroup){
	var finalResult = null;
	if ($.isArray(results)) {
		finalResult = results[0];
    }
    else {
    	finalResult = results ;
    }
	 if (!finalResult.success) {
		 addValidationMessage(controlGroup,finalResult.message);
     }else{
    	 removeValidationMessage(controlGroup);
    	 $('.help-inline', controlGroup).html('<span id="validationRes" class="text-success"> <span class="glyphicon glyphicon-ok"></span></span>');
     }
}

function validateConfPwField(pwField,confPwField){
	$("#" + confPwField).on('keyup',function(){
	 	var controlGroup = $('input[name="'+ $("#" + confPwField).attr('name')+'"]').parents('.form-group');
	 	removeValidationMessage(controlGroup);
		var pwValue = $("#" + pwField).val();
		var confPwValue = $("#" + confPwField).val();
		if(pwValue != confPwValue){
			addValidationMessage(controlGroup,'Passwords do not match');
		}
	 });
}

function addValidationMessage(controlGrp,msg){
	controlGrp.addClass('has-error');
     $('.help-inline', controlGrp).text(msg);
}

function removeValidationMessage(controlGrp){
	controlGrp.removeClass('has-error');
	 $('.help-inline', controlGrp).text("");
}

