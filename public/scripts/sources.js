function addCommaSpace(text) {
    var spacedText = "";
    for (i of text) {
        spacedText += i;
        if (i == ",") {
            spacedText += " ";
        }
    }
    return spacedText;
}
function createTable(sources) {
    var div = document.getElementsByClassName("mapwithaisourcetable")[0];
    var table = document.createElement("table");
    var tablebody = document.createElement("tbody");
    var tablehead = document.createElement("thead");
    var tableheadrow = document.createElement("tr");
    for (header of ["Source", "URL", "Parameters", "Countries", "License", "OSM Usable"]) {
        var th = document.createElement("th");
        th.textContent = header;
        tableheadrow.appendChild(th);
    }
    tablehead.appendChild(tableheadrow);

    for (source in sources) {
        var row = document.createElement("tr");
        var sourcetd = document.createElement("td");
        sourcetd.textContent = source;
        row.appendChild(sourcetd);
        for (columnName of ["url", "parameters", "countries", "license", "osm_compatible"]) {
            var column = document.createElement("td");
            column.textContent = sources[source][columnName];
            if (column.textContent == "[object Object]") {
                column.textContent = JSON.stringify(sources[source][columnName]);
            }
            column.textContent = addCommaSpace(column.textContent);
            row.appendChild(column);
        }
        tablebody.appendChild(row);
    }

    table.appendChild(tablehead);
    table.appendChild(tablebody);
    div.appendChild(table);
    console.log(sources);
}

function init() {
    fetch("json/sources.json")
        .then(response => response.json())
        .then(json => createTable(json));
}

init();
