function navigate(navigationInput) {
    qlarrVariables = {};
    var valuesKeys = Object.keys(navigationInput.values)
    var codes = navigationInput.codes;

    codes.forEach(function(componentCode) {
        eval(componentCode + " = {};");
        qlarrVariables[componentCode] = eval(componentCode);
    })
    // first we will set all the variables from DB
    valuesKeys.forEach(function(key){
        var value = navigationInput.values[key]
        var names = key.split('.')
        qlarrVariables[names[0]][names[1]] = verifyValue(names[1], value.returnType.toLowerCase(), value.value)
    });

    navigationInput.sequence.forEach(function(systemInstruction, index) {
        var instruction = systemInstruction.instruction
        // Then we run active instructions
        // Or Defaults if they don't have value already
        if (instruction.isActive) {
            qlarrVariables[systemInstruction.componentCode][instruction.code] = runInstruction(instruction.code, instruction.text, instruction.returnType.toLowerCase())
        } else if (instruction.text != null && typeof qlarrVariables[systemInstruction.componentCode][instruction.code] === 'undefined') {
            if (instruction.returnType.toLowerCase() == "string") {
                var text = "\"" + instruction.text + "\""
            } else {
                var text = instruction.text
            }
            try {
                qlarrVariables[systemInstruction.componentCode][instruction.code] = JSON.parse(text)
            } catch (e) {
                print(text)
                print(e)
                qlarrVariables[systemInstruction.componentCode][instruction.code] = defaultValue(instruction.code, instruction.returnType.toLowerCase());
            }
        }
    })
    navigationInput.formatInstructions.forEach(function(formatInstruction, index) {
        var instruction = formatInstruction.instruction
        qlarrVariables[formatInstruction.componentCode][instruction.code] = runInstruction(instruction.code, instruction.text, "Map")
    })
    return JSON.stringify(qlarrVariables);
}

function verifyValue(code, returnTypeName, value) {
    if (isCorrectReturnType(returnTypeName, value)) {
        return value;
    } else {
        return defaultValue(code, returnTypeName);
    }
}

function runInstruction(code, instructionText, returnTypeName) {
    try {
        if (returnTypeName != "map" && returnTypeName != "file") {
            var value = eval(instructionText);
        } else {
            eval("var value = " + instructionText + ";");
        }
        if (isCorrectReturnType(returnTypeName, value)) {
            return value;
        } else {
            return defaultValue(code, returnTypeName);
        }
    } catch (e) {
        //print(e)
        return defaultValue(code, returnTypeName);
    }
}

function isCorrectReturnType(returnTypeName, value) {
    switch (returnTypeName) {
        case "boolean":
            return typeof value === "boolean";
            break;
        case "date":
            return QlarrScripts.isValidSqlDateTime(value);
            break;
        case "int":
            return typeof value === "number" && value % 1 == 0;
            break;
        case "double":
            return typeof value === "number";
            break;
        case "list":
            return Array.isArray(value);
            break;
        case "string":
            return typeof value === "string";
            break;
        case "map":
            return typeof value === "object";
            break;
        case "file":
            if (typeof value !== "object") {
                return false;
            } else {
                var keys = Object.keys(value);
                return keys.indexOf("filename") >= 0 && keys.indexOf("stored_filename") >= 0 && keys.indexOf("size") >= 0 && keys.indexOf("type") >= 0
            }
            break;
        default:
            return false;
    }
    return false;
}

function defaultValue(code, returnTypeName) {
    if (code == "value") {
        return undefined;
    } else if (code == "relevance" || code == "conditional_relevance" || code == "validity") {
        return true
    }
    switch (returnTypeName) {
        case "boolean":
            return false;
            break;
        case "date":
            return "1970-01-01 00:00:00";
            break;
        case "string":
            return "";
            break;
        case "int":
        case "double":
            return 0;
            break;
        case "list":
            return [];
            break;
        case "map":
            return {};
            break;
        case "file":
            return {
                filename: "", stored_filename: "", size: 0, type: ""
            };
            break;
        default:
            return "";
    }
    return "";
}