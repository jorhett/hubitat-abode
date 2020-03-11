/*
 * Abode Alarm
 *
 * Copyright 2020 Jo Rhett.  All Rights Reserved
 * Started from Hubitat example driver code https://github.com/hubitat/HubitatPublic/tree/master/examples/drivers
 * Implementation inspired by https://github.com/MisterWil/abodepy
 *
 *  Licensed under the Apache License, Version 2.0 -- details in the LICENSE file in this repo
 *
 */

 metadata {
  definition (
    name: 'Abode Alarm',
    namespace: 'jorhett',
    author: 'Jo Rhett',
    importUrl: 'https://raw.githubusercontent.com/jorhett/hubitat-abode/master/AbodeAlarm.groovy',
  ) {
    // capability 'Alarm'
    // capability 'Chime'
    capability 'Refresh'
    command 'armAway', [[name: 'areaNumber', type: 'NUMBER', description: 'Area to arm Away (empty = all areas)', constraints:['NUMBER']]]
    command 'armHome', [[name: 'areaNumber', type: 'NUMBER', description: 'Area to arm Home (empty = all areas)', constraints:['NUMBER']]]
    command 'disarm',  [[name: 'areaNumber', type: 'NUMBER', description: 'Area to disarm (empty = all areas)',   constraints:['NUMBER']]]
    command 'logout'
    attribute 'isLoggedIn', 'String'
  }

  preferences {
    if(showLogin != false) {
      section('Abode API') {
        input name: 'username', type: 'text', title: 'Abode username',   required: true
        input name: 'password', type: 'text', title: 'Abode password',   required: true
        input name: 'mfa_code', type: 'text', title: 'Current MFA Code', required: false, description: '<em>Not stored -- used one time</em>'
      }
    }
    section('Behavior') {
      input name: 'showLogin', type: 'bool', title: 'Show Login',           defaultValue: true,  description: '<em>Show login fields</em>', submitOnChange: true
      input name: 'logDebug',  type: 'bool', title: 'Enable debug logging', defaultValue: true,  description: '<em>for 2 hours</em>'
      input name: 'logTrace',  type: 'bool', title: 'Enable trace logging', defaultValue: false, description: '<em>for 30 minutes</em>'
    }
  }
}

// Hubitat standard methods
def installed() {
  log.debug 'installed'
  device.updateSetting('logInfo', [value: true, type: 'bool'])
  initialize()
}

private initialize() {
  state.uuid = UUID.randomUUID()
}

def updated() {
  log.info 'Preferences saved.'
  log.info 'debug logging is: ' + logDebug
  log.info 'description logging is: ' + logDetails
  log.info 'Abode username: ' + username

  // Disable high levels of logging after time
  if (logTrace) runIn(1800,disableTrace)
  if (logDebug) runIn(7200,disableDebug)

  // Validate the session
  if (state.token == null) {
    log.debug 'Not currently logged in.'
    if (!username.isEmpty() && !password.isEmpty())
      login()

    // Clear the MFA token entry -- will be useless anyway
    device.updateSetting('mfa_code', [value: '', type: 'text'])
  }
  else {
    validateSession()
  }
}

def refresh() {
  if (validateSession())
    parsePanel(getPanel())
}

def uninstalled() {
  clearState()
  log.debug 'uninstalled'
}

def disarm(area_input = null) {
  changeMode('standby', area_input)
}

def armHome(area_input = null) {
  changeMode('home', area_input)
}

def armAway(area_input = null) {
  changeMode('away', area_input)
}

def disableDebug(String level) {
  log.info "Timed elapsed, disabling debug logging"
  device.updateSetting("logDebug", [value: 'false', type: 'bool'])
}
def disableTrace(String level) {
  log.info "Timed elapsed, disabling trace logging"
  device.updateSetting("logTrace", [value: 'false', type: 'bool'])
}

// Abode actions
private baseURL() {
  return 'https://my.goabode.com'
}

private driverUserAgent() {
  return 'AbodeAlarm/0.2.0 Hubitat Evolution driver'
}

private login() {
  if(state.uuid == null) initialize()
  def params = [
    path: '/api/auth2/login',
    body: [id: username, password: password, mfa_code: mfa_code, uuid: state.uuid, remember_me: 1]
  ]
  reply = postJson(params)
  if(reply.containsKey('mfa_type')) {
    sendEvent(name: 'requiresMFA', value: reply.mfa_type, descriptionText: "Multi-Factor Authentication required: ${reply.mfa_type}", displayed: true)
  }
  if(reply.containsKey('token')) {
    sendEvent(name: 'isLoggedIn', value: true, displayed: true)
    device.updateSetting('showLogin', [value: false, type: 'bool'])
    parseLogin(reply)
    parsePanel(getPanel())
  }
}

// Make sure we're still authenticated
private validateSession() {
  user = getUser()
  logged_in = user?.id ? true : false
  if(! logged_in) {
    if (auth.token) clearState()
  }
  else {
    parseUser(user)
  }
  return logged_in
}

def logout() {
  if(state.token && validateSession()) {
    def params = [
      path: '/api/v1/logout',
      body: [:],
    ]
    reply = postJson(params)
  }
  else {
    sendEvent(name: 'lastResult', value: 'Not logged in', descriptionText: 'Attempted logout when not logged in', displayed: true)
  }
  clearState()
  device.updateSetting('showLogin', [value: true, type: 'bool'])
}

private clearState() {
  state.clear()
  sendEvent(name: 'isLoggedIn', value: false, displayed: true)
}

private changeMode(String new_mode, area_input = null) {
  modeMap = state.mode
  areas = (area_input == null) ? modeMap.keySet() : [area_input]
  areas.each() { area_number ->
    current_mode = modeMap[area_number]
    if(current_mode == new_mode) {
      if (logDebug) log.debug "Area ${area_number} is already in mode ${new_mode}"
    }
    else {
      reply = putHttp('/api/v1/panel/mode/' + area_number + '/' + new_mode)
      if (reply['area'] == area_number.toString()) {
        if (logDebug) log.debug "Area ${reply['area']} has been set to mode ${reply['mode']}"
        modeMap[reply['area']] = reply['mode']
      }
    }
  }
  state.mode = modeMap
  sendEvent(name: 'mode', value: modeMap, displayed: true)
}

// Abode types
private getAccessToken() {
  reply = getHttp('/api/auth2/claims')
  return reply?.access_token
}

private getPanel() {
  getHttp('/api/v1/panel')
}

private getUser() {
  getHttp('/api/v1/user')
}
/* until I get to these
private getSounds() {
  getHttp('/api/v1/sounds')
}

private getSiren() {
  getHttp('/api/v1/siren')
}

private getDevices() {
  getHttp('/api/v1/devices')
}
*/

private parseLogin(Map data) {
  state.token = data.token
  state.access_token = getAccessToken()
  updateDataValue('loginExpires', data.expired_at)

  // Login contains a panel hash which is different enough we can't reuse parsePanel()
  ['ip','mac','model','online'].each() { field ->
    updateDataValue(field, data.panel[field])
  }
}

private parseUser(Map user) {
  // Store these for use by Apps
  updateDataValue('abodeID', user.id)
  ['plan','mfa_enabled'].each() { field ->
    updateDataValue(field, user[field])
  }
  // ignore everything else for now
  return user
}

private parsePanel(Map panel) {
  // Update these for use by Apps
  ['ip','online'].each() { field ->
    updateDataValue(field, panel[field])
  }
  areas = parseAreas(panel['areas']) ?: []
  mode = parseMode(panel['mode'], areas) ?: {}

  return panel
}

private parseAreas(Map areas) {
  // Haven't found anything useful other than list of area keys
  areas.keySet()
}

private parseMode(Map mode, Set areas) {
  modeMap = [:]
  // Collect mode for each area
  areas.each() { number ->
    modeMap[number] = mode["area_${number}"]
  }
  state.mode = modeMap

  return modeMap
}

// HTTP methods tuned for Abode
private postJson(params) {
  result = [:]
  status = ''
  message = ''
  params.uri = baseURL()
  params.headers = ['User-Agent': driverUserAgent()]
  if (state.token) params.headers['ABODE-API-KEY'] = state.token
  try {
    httpPostJson(params) { response ->
      if (logTrace) log.trace response
      if (logDebug) log.debug "postJson ${params.uri} results: ${response.status}"
      status = response.status.toString()
      result = response.data
      message = result?.message ?: ''
    }
  } catch(error) {
    log.error "postJson ${params.uri} result: ${error.response.status} ${error.response.data?.message}"
    error.response.data?.errors?.each() { errormsg ->
      log.warn errormsg.toString()
    }
    if (logTrace) log.trace error.response?.data

    status = error.response.status?.toString()
    message = error.response.data?.message ?: params['path']
  }
  sendEvent(name: 'lastResult', value: "${status} ${message}", descriptionText: message, displayed: true)

  return result
}

private getHttp(String path) {
  result = null
  message = ''
  params = [
    uri: baseURL(),
    path: path,
    headers: [
      'Authorization': "Bearer ${state.access_token}",
      'ABODE-API-KEY': state.token,
      'User-Agent': driverUserAgent(),
    ],
  ]
  try {
    httpGet(params) { response ->
      status = response?.status.toString()
      if (logDebug) log.debug "getHttp(${path}) results: ${response.status}"
      if (logTrace) log.trace response.data
      result = response.data
      message = result?.message ?: ''
    }
  } catch(error) {
    log.error "getHttp(${path}) result: ${error.response.status} ${error.response.data?.message}"
    error.response.data?.errors?.each() { errormsg ->
      log.warn errormsg.toString()
    }
    if (logTrace) log.trace error.response.data
    status = error.response.status?.toString()
    message = error.response.data?.message ?: path
  }
  sendEvent(name: 'lastResult', value: "${status} ${message}", descriptionText: message, displayed: true)
  return result
}

private putHttp(String path) {
  result = null
  message = ''
  params = [
    uri: baseURL(),
    path: path,
    headers: [
      'Authorization': "Bearer ${state.access_token}",
      'ABODE-API-KEY': state.token,
      'User-Agent': driverUserAgent(),
    ],
  ]
  try {
    httpPut(params) { response ->
      status = response?.status.toString()
      if (logDebug) log.debug "putHttp(${path}) results: ${response.status}"
      if (logTrace) log.trace response.data
      result = response.data
      message = result?.message ?: ''
    }
  } catch(error) {
    log.error "putHttp(${path}) result: ${error.response.status} ${error.response.data?.message}"
    error.response.data?.errors?.each() { errormsg ->
      log.warn errormsg.toString()
    }
    if (logTrace) log.trace error.response.data
    status = error.response.status?.toString()
    message = error.response.data?.message ?: path
  }
  sendEvent(name: 'lastResult', value: "${status} ${message}", descriptionText: message, displayed: true)
  return result
}
