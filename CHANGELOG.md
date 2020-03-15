# Changelog
All notable changes to this project will be documented in this file. 
The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

This project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html)

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
