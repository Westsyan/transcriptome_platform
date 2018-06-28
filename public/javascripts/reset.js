

function restart(obj) {
    var id = obj.value;
    $.ajax({
        url: "/transcriptome/sample/deployGet?id=" + id,
        type: "get",
        dataType: "json",
        success: function (data) {
            $("#set-1").hide();
            $("#down-1").show();
            $("#up-1").hide()
            $("#Id").val(data.id);
            $("#proname").val(data.proname);
            $("#sample").val(data.sample);
            $("#encondingType").val(data.encondingType);
            if(data.stepMethod == "yes"){
                $("#stepValue").show()
            } else {
                $("#stepValue").hide()
            }
            $("#stepMethod").val(data.stepMethod);
            $("#seed_mismatches").val(data.seed_mismatches);
            $("#palindrome_clip_threshold").val(data.palindrome_clip_threshold);
            $("#simple_clip_threshold").val(data.simple_clip_threshold);
            $("#trimMethod").val(data.trimMethod);
            if(data.trimMethod == "yes"){
                $("#trimValue").show()
            } else {
                $("#trimValue").hide()
            }
            $("#window_size").val(data.window_size);
            $("#required_quality").val(data.required_quality);
            $("#minlenMethod").val(data.minlenMethod);
            if(data.minlenMethod == "yes"){
                $("#minlenValue").show()
            } else {
                $("#minlenValue").hide()
            }
            $("#minlen").val(data.minlen);
            $("#leadingMethod").val(data.leadingMethod);
            if(data.leadingMethod == "yea"){
                $("#leadingValue").show()
            } else {
                $("#leadingValue").hide()
            }
            $("#leading").val(data.leading);
            $("#trailingMethod").val(data.trailingMethod);
            if(data.trailingMethod == "yes"){
                $("#trailingValue").show()
            } else {
                $("#trailingValue").hide()
            }
            $("#trailing").val(data.trailing);
            $("#cropMethod").val(data.cropMethod);
            if(data.cropMethod == "yes"){
                $("#cropValue").show()
            } else {
                $("#cropValue").hide()
            }
            $("#crop").val(data.crop);
            $("#headcropMethod").val(data.headcropMethod);
            if(data.headcropMethod == "yes"){
                $("#headcropValue").show()
            } else {
                $("#headcropValue").hide()
            }
            $("#headcrop").val(data.headcrop);
            if(data.inputType == "PE"){
                $("#pe").show();
                $("#se").hide();
                $("#adapter_pe").val(data.adapter);
            }else{
                $("#pe").hide();
                $("#se").show();
                $("#adapter_se").val(data.adapter);
            }
            $("#rest").modal("show")

        }
    })
}



$("#down-1").click(function () {
    $("#set-1").show();
    $("#down-1").hide();
    $("#up-1").show()
});

$("#up-1").click(function () {
    $("#set-1").hide();
    $("#down-1").show();
    $("#up-1").hide()
});



function stepChange(element) {
    var value = $(element).find(">option:selected").val();
    if (value == "yes") {
        $("#stepValue").show()
    } else {
        $("#stepValue").hide()
    }
}

function trimChange(element) {
    var value = $(element).find(">option:selected").val();
    if (value == "yes") {
        $("#trimValue").show()
    } else {
        $("#trimValue").hide()
    }
}

function minlenChange(element) {
    var value = $(element).find(">option:selected").val()
    if (value == "yes") {
        $("#minlenValue").show()
    } else {
        $("#minlenValue").hide()
    }
}

function leadingChange(element) {
    var value = $(element).find(">option:selected").val()
    if (value == "yes") {
        $("#leadingValue").show()
    } else {
        $("#leadingValue").hide()
    }
}

function trailingChange(element) {
    var value = $(element).find(">option:selected").val()
    if (value == "yes") {
        $("#trailingValue").show()
    } else {
        $("#trailingValue").hide()
    }
}

function cropChange(element) {
    var value = $(element).find(">option:selected").val()
    if (value == "yes") {
        $("#cropValue").show()
    } else {
        $("#cropValue").hide()
    }
}

function headcropChange(element) {
    var value = $(element).find(">option:selected").val();
    if (value == "yes") {
        $("#headcropValue").show()
    } else {
        $("#headcropValue").hide()
    }
}





function formValidation() {
    $('#resetPEForm').formValidation({
        framework: 'bootstrap',
        icon: {
            valid: 'glyphicon glyphicon-ok',
            invalid: 'glyphicon glyphicon-remove',
            validating: 'glyphicon glyphicon-refresh'
        },
        fields: {
            seed_mismatches:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            palindrome_clip_threshold:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            simple_clip_threshold:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            window_size:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            required_quality:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            minlen:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            leading:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            trailing:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            crop:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            },
            headcrop:{
                validators: {
                    notEmpty: {
                        message: '不能为空！'
                    },
                    integer: {
                        message: '必须为整数！'
                    }
                }
            }
        }
    })}





