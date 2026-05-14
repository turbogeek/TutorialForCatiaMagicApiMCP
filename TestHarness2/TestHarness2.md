# TestHarness2 for CATIA Magic API

This test harness is a Groovy script for CATIA Magic API. It allows for the loading and testing of Groovy scripts to written by the LLM to create a positive feedback loop to allow the LLM to vialidate scripts that create and export diagrams for use as assets in the development of prompt chains.

## Primary goal

To have feedback on scripts as soon as possible after creation. In particular, the need to test views in SysMLv2 projects that are meant to show the contents of packages and elements.

## Requirements

- Does what the original demo harness can do:
  - See test harness\README.md
- open a project
- Save diagram (or in SysMLv2 projects, views) of  as SVG or PNG files
- close a project
- show validation errors and warnings
- undo changes if possible so that if we are running a series of tests, we do not need to manually open a new project for each test
- have a modeless dialog for monitoring the harness' health, port,  usage statistics, and other status and to shut it dowm.

## Setup

## Usage
