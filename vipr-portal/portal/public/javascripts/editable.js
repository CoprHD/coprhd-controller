$(document).ready(function() {
    $(document).on('keyup', '.form-control-editable', function(e) {
        if (e.keyCode == 27) {
            var input = $(this);
            var value = input.data('value');
            input.val(value);
            input.trigger('blur');
        }
    });
    $(document).on('focus', '.form-control-editable', function(e) {
        var input = $(this);
        var value = input.val();
        input.data('value', value);
    });
});