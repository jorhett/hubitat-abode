# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

This project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html)

## 2020-04-25 Release 1.0.1

### Added

- Hubitat Package Manager manifest

### Changed

- Move logging of mode change request prior to sending to make Abode websocket delays visible
- Reformat log messages for easier understanding when debug is disabled.
   _Author had never run it with debug logging disabled_. :stuck_out_tongue_winking_eye:

## 2020-04-25 Release 1.0

No changes

## 2020-04-04 Beta Release 0.7.0

### Added

- New attribute gatewayTimeline allows event subscription to Abode gateway timeline events
- New attribute lastResult allows event subscription to Abode portal interactions

### Changed

- Moved the driver into drivers/ directory, to allow both drivers and apps in this repo

### Fixed

- Inconsistent fields in websocket messages sometimes caused attempts to dereference null fields

## 2020-03-31 Beta Release 0.6.3

### Fixes

- Mask the saved password in the UI

## 2020-03-28 Beta Release 0.6.2

### Changed

- Recognize authorization failure during websocket setup and act appropriately
- Get a new access token during refresh()
- Demote some log messages to Debug as they've turned out to be uninformative for normal use
- Promote error responses up to Warn log level

### Removed

- The datetime returned by the API which was previously stored in `loginExpires` (but never used) has been found to be irrelevant

## 2020-03-24 Beta Release 0.6.1

### Changed

- Suppress Hubitat mode change when Abode mode was set from Hubitat
- Avoid processing Abode mode update to same mode
- Improve log output in different socket disconnection scenarios

## 2020-03-21 Beta Release 0.6.0

### Added

- Options to synchronize Abode mode changes to Hubitat Mode
- Option to sync mode when exit delay starts
- Virtual switch isArmed which be used by Mode Manager for Return from Away
- Ignore list for events we can't use

## 2020-03-19 Release 0.5.0

### Added

- Preference for how much slack to allow in socket timeout
- Ignore lists for events types and device names

### Changed

- Utilize type field in alerts to communicate data source
- Refactored session management of event socket to improve error handling

## 2020-03-18 Release 0.4.0

### Added

- Maintain websocket connection for Abode events
- Log timeline events to Hubitat events
- Update status when received back from Abode

### Changed

- Track gateway mode from area_1 exclusively, since areas aren't specified in Abode events

## 2020-03-15 Release 0.3.0

### Changed

- Retain SESSION cookie for persistent session
- Handle errors that are not from net.http implementation properly

## 2020-03-12 Release 0.2.1

### Added

- Actuator capability so that Rule Machine can target commands

### Changed

- Area status is now stored in Attributes instead of State so that Rule Machine can access it

## 2020-03-10 Release 0.2.0

### Added

- Change the arm mode for all or a selected area

## 2020-03-06 Release 0.1.0

### Added

- Login to Abode portal with or without multi-factor authentication
- View current mode in each area
- Refresh panel and user data
- Retrieve access_token for WebSocket use
- Preferences to control log verbosity
- Logout
