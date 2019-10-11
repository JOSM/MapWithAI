# JOSM MapWithAI Plugin (formerly RapiD Plugin)

[![pipeline status](https://gitlab.com/smocktaylor/rapid/badges/master/pipeline.svg)](https://gitlab.com/smocktaylor/rapid/commits/master)
[![code coverage](https://gitlab.com/smocktaylor/rapid/badges/master/coverage.svg)](https://codecov.io/github/smocktaylor/rapid?branch=master)
[![license](https://img.shields.io/badge/license-GPLv2-blue.svg?style=flat-square)](./LICENSE)

This plugin brings MapWithAI information into JOSM.


## Installation

To use this plugin, [install JOSM](https://josm.openstreetmap.de/wiki/Download) and then [in the preferences menu install the **MapWithAI** plugin](https://josm.openstreetmap.de/wiki/Help/Preferences/Plugins#AutomaticinstallationviaPreferencesmenu)

## Usage
1. Open JOSM
2. Install the plugin ("JOSM Preferences" -> "Plugins" -> "MapWithAI")
3. Download data ("File" -> "Download Data") or just use the "Download data" button on the toolbar
4. Download MapWithAI data ("Data" -> "MapWithAI")
5. Switch to the "MapWithAI" layer (if you don't have one, file a bug report with the location)
6. If you are in a location where Facebook has run its AI, *or* Microsoft has provided building footprints, you should see data. If you do not, check your paintstyles (some paintstyles make buildings *really* hard to see).

### Optional JOSM Setup
1. Open JOSM
2. Add the "MapWithAI" paintstyle by going to "Map Settings" -> "Map Paint Styles" -> the plus sign next to "Active styles" -> enter `https://gitlab.com/smocktaylor/rapid/raw/master/src/resources/styles/standard/rapid.mapcss` in the "URL / File" field.

## Information
* [RapiD](https://mapwith.ai/rapid)
* [HOT Tasking Manager + RapiD] (https://tasks-assisted.hotosm.org/)
* [RapiD source code and country requests](https://github.com/facebookincubator/RapiD)
* [mapwith.ai](https://mapwith.ai/)

## Contributing

- The **source code** is hosted on [GitLab](https://gitlab.com/smocktaylor/rapid).
- **Issues** are managed in [GitLab](https://gitlab.com/smocktaylor/rapid/issues)
- **Translations** are not currently done.

## Authors

- Taylor Smock (taylor.smock)

## License

GPLv2 or later (same as JOSM)
