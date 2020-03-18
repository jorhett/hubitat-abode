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
    capability 'Actuator'
    capability 'Refresh'
    command 'armAway', [[name: 'areaNumber', type: 'NUMBER', description: 'Area to arm Away (empty = all areas)', constraints:['NUMBER']]]
    command 'armHome', [[name: 'areaNumber', type: 'NUMBER', description: 'Area to arm Home (empty = all areas)', constraints:['NUMBER']]]
    command 'disarm',  [[name: 'areaNumber', type: 'NUMBER', description: 'Area to disarm (empty = all areas)',   constraints:['NUMBER']]]
    command 'logout'
    attribute 'isLoggedIn', 'String'
    attribute 'gatewayMode', 'String'
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
  device.updateSetting('showLogin', [value: true, type: 'bool'])
  initialize()
}

private initialize() {
  state.uuid = UUID.randomUUID()
  state.cookies = [:]
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
    if (logDebug) log.debug 'Not currently logged in.'
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
  if (validateSession()) {
    parsePanel(getPanel())
    if (state.webSocketConnected != true)
      connectEventSocket()
  }
}

def uninstalled() {
  clearState()
  if (logDebug) log.debug 'uninstalled'
}

def disarm() {
  changeMode('standby')
}
def armHome() {
  changeMode('home')
}
def armAway() {
  changeMode('away')
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
  return 'AbodeAlarm/0.4.0 Hubitat Elevation driver'
}

private login() {
  if(state.uuid == null) initialize()
  input_values = [
    id: username,
    password: password,
    mfa_code: mfa_code,
    uuid: state.uuid,
    remember_me: 1,
  ]
  reply = doHttpRequest('POST', '/api/auth2/login', input_values)
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
  // may not want to force logout
  logged_in = user?.id ? true : false
  if(! logged_in) {
    if (state.token) clearState()
  }
  else {
    parseUser(user)
  }
  return logged_in
}

def logout() {
  if(state.token && validateSession()) {
    reply = doHttpRequest('POST', '/api/v1/logout')
    terminateEventSocket()
  }
  else {
    sendEvent(name: 'lastResult', value: 'Not logged in', descriptionText: 'Attempted logout when not logged in', displayed: true)
  }
  clearState()
  device.updateSetting('showLogin', [value: true, type: 'bool'])
}

private clearState() {
  state.clear()
  unschedule()
  sendEvent(name: 'isLoggedIn', value: false, displayed: true)
}

private changeMode(String new_mode) {
  current_mode = mode
  if(current_mode != new_mode) {
    reply = doHttpRequest('PUT','/api/v1/panel/mode/1/' + new_mode)
    if (reply['area'] == 1)
      log.info "Successfully sent request to change gateway mode to ${new_mode}"
  } else {
    if (logDebug) log.debug "Gateway is already in mode ${new_mode}"
  }
}

// Only update area 1 since area is not provided in event messages
private updateMode(String new_mode) {
  if (logDebug) log.info 'Gateway mode has changed to ' + new_mode
  sendEvent(name: "gatewayMode", value: new_mode, descriptionText: 'Gateway mode has changed to ' + new_mode, displayed: true)
}

// Abode types
private getAccessToken() {
  reply = doHttpRequest('GET','/api/auth2/claims')
  return reply?.access_token
}

private getPanel() {
  doHttpRequest('GET','/api/v1/panel')
}

private getUser() {
  doHttpRequest('GET','/api/v1/user')
}
/* until I get to these
private getSounds() {
  doHttpRequest('GET','/api/v1/sounds')
}

private getSiren() {
  doHttpRequest('GET','/api/v1/siren')
}

private getDevices() {
  doHttpRequestdoRequestJson('GET','/api/v1/devices')
}
*/

private parseLogin(Map data) {
  state.token = data.token
  state.access_token = getAccessToken()
  state.loginExpires = data.expired_at

  // Login contains a panel hash which is different enough we can't reuse parsePanel()
  device.data.remove('loginExpires')
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
  parseMode(panel['mode'], areas) ?: {}

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
  // Status is based on area 1 only
  if (gatewayMode != modeMap['area_1'])
    sendEvent(name: "gatewayMode", value: modeMap['area_1'], descriptionText: "Gateway mode is ${modeMap['area_1']}", displayed: true)

  state.modes = modeMap
}

// HTTP methods tuned for Abode
private storeCookies(String cookies) {
  // Cookies are comma separated, colon-delimited pairs
  cookies.split(',').each {
    namevalue = it.split(';')[0].split('=')
    state.cookies[namevalue[0]] = namevalue[1]
  }
}

private doHttpRequest(String method, String path, Map body = [:]) {
  result = [:]
  status = ''
  message = ''
  params = [
    uri: baseURL(),
    path: path,
    headers: ['User-Agent': driverUserAgent()],
  ]
  if (method == 'POST' && body.isEmpty() == false)
    params.body = body
  if (state.token) params.headers['ABODE-API-KEY'] = state.token
  if (state.access_token) params.headers['Authorization'] = "Bearer ${state.access_token}"
  if (state.cookies) params.headers['Cookie'] = state.cookies.collect { key, value -> "${key}=${value}" }.join('; ')

  Closure $parseResponse = { response ->
    if (logTrace) log.trace response.data
    if (logDebug) log.debug "HTTPS ${method} ${path} results: ${response.status}"
    status = response.status.toString()
    result = response.data
    message = result?.message ?: "${method} ${path} successful"
    if (response.headers.'Set-Cookie') storeCookies(response.headers.'Set-Cookie')
  }
  try {
    switch(method) {
      case 'PATCH':
        httpPatch(params, $parseResponse)
        break
      case 'POST':
        httpPostJson(params, $parseResponse)
        break
      case 'PUT':
        httpPut(params, $parseResponse)
        break
      default:
        httpGet(params, $parseResponse)
        break
    }
  } catch(error) {
    // Is this an HTTP error or a different exception?
    if (error.metaClass.respondsTo(error, 'response')) {
      if (logTrace) log.trace error.response.data
      status = error.response.status?.toString()
      result = error.response.data
      message = error.response.data?.message ?:  "${method} ${path} failed"
      log.error "HTTPS ${method} ${path} result: ${error.response.status} ${error.response.data?.message}"
      error.response.data?.errors?.each() { errormsg ->
        log.warn errormsg.toString()
      }
    } else {
      status = 'Exception'
      log.error error.toString()
    }
  }
  sendEvent(name: 'lastResult', value: "${status} ${message}", descriptionText: message, displayed: true)
  return result
}

// Abode websocket implementation
private connectEventSocket() {
  if (!state.webSocketConnectAttempt) state.webSocketConnectAttempt = 0
  if (logDebug) log.debug "Attempting WebSocket connection for Abode events (attempt ${state.webSocketConnectAttempt})"
  try {
    interfaces.webSocket.connect('wss://my.goabode.com/socket.io/?EIO=3&transport=websocket', headers: [
      'Origin': baseURL() + '/',
      'Cookie': "SESSION=${state.cookies['SESSION']}",
    ])
    if (logDebug) log.debug 'Connection initiated'
  }
  catch(error) {
    log.error 'WebSocket connection to Abode event socket failed: ' + error.message
  }
}

private terminateEventSocket() {
  if (logDebug) log.debug 'Disconnecting Abode event socket'
  try {
    interfaces.webSocket.close()
    state.webSocketConnected = false
    state.webSocketConnectAttempt = 0
    if (logDebug) log.debug 'Connection terminated'
  }
  catch(error) {
    log.error 'Disconnect of WebSocket from Abode portal failed: ' + error.message
  }
}

def sendPing() {
  if (logTrace) log.trace 'Sending webSocket ping'
  interfaces.webSocket.sendMessage('2')
}

def sendPong() {
  if (logTrace) log.trace 'Sending webSocket pong'
  interfaces.webSocket.sendMessage('3')
}

def parseEvent(String event_text) {
  twovalue = event_text =~ /^\["com\.goabode\.([\w+\.]+)",(.*)\]$/
  if (twovalue.find()) {
    event_type = twovalue[0][1]
    event_data = twovalue[0][2]
    switch(event_data) {
      // JSON format
      case ~/^\{.*\}$/:
        details = parseJson(event_data)
        message = details.event_name
        break

      // Quoted text
      case ~/^".*"$/:
        message = event_data[1..-2]
        break

      default:
        log.error "Event ${event_type} has unknown data format: ${event_data}"
        message = event_data
        break
    }
    switch(event_type) {
      case 'gateway.mode':
        updateMode(message)
        break

      case ~/^gateway\.timeline.*/:
        // device type is not included in all events
        device_type = details.device_type ? " (${details.device_type})" : ''
        if (logDebug) log.debug "${event_type} -${device_type} ${details.event_name}"

        // Automation puts the rule name in device_name, which is backwards for our purposes
        if (details.event_type == 'Automation')
          sendEvent(name: 'Automation', value: details.device_name, descriptionText: details.event_name, displayed: true)
        else
          sendEvent(name: details.device_name, value: details.event_type, descriptionText: details.event_name + device_type, displayed: true)
        break

      default:
        if (logDebug) log.debug "Ignoring Event ${event_type} ${message}"
        break
    }
  } else {
    log.error "Unparseable Event message: ${event_text}"
  }
}

// Hubitat required method: This method is called with any incoming messages from the web socket server
def parse(String message) {
  if (logTrace) log.trace 'webSocket event raw: ' + message

  // First character is the event type
  event_type = message.substring(0,1)
  // remainder is the data (optional)
  event_data = message.substring(1)

  switch(event_type) {
    case '0':
      log.info 'webSocket session open received'
      jsondata = parseJson(event_data)
      if (jsondata.containsKey('pingInterval')) state.webSocketPingInterval = jsondata['pingInterval']
      if (jsondata.containsKey('pingTimeout'))  state.webSocketPingTimeout  = jsondata['pingTimeout']
      if (jsondata.containsKey('sid'))          state.webSocketSid          = jsondata['sid']
      break

    case '1':
      log.info 'webSocket session close received' + event_data
      runIn(120, connectEventSocket)
      break

    case '2':
      if (logTrace) log.trace 'webSocket Ping received, sending reply'
      sendPong()
      break

    case '3':
      if (logTrace) log.trace 'webSocket Pong received'
      runInMillis(state.webSocketPingInterval, sendPing)
      break

    case '4':
      // First character of the message indicates purpose
      switch(event_data.substring(0,1)) {
        case '0':
          log.info 'webSocket message = Socket connected'
          runInMillis(state.webSocketPingInterval, sendPing)
          break

        case '1':
          log.info 'webSocket message = Socket disconnected'
          break

        case '2':
          if (logTrace) log.trace 'webSocket message = Event: ' + event_data.substring(1)
          parseEvent(event_data.substring(1))
          break

        case '4':
          log.info 'webSocket message = Error: ' + event_data.substring(1)
          break

        default:
          log.error "webSocket message = (unknown:${event_data.substring(0,1)}): " + event_data.substring(1)
          break
      }
      break

    default:
      log.error "Unknown webSocket event (${event_type}) received: " + event_data
      break
  }
}

// Hubitat required method: This method is called with any status messages from the web socket client connection
def webSocketStatus(String message) {
  if (logTrace) log.trace 'webSocketStatus ' + message
  switch(message) {
    case ~/^status: open.*$/:
      log.info 'Connected to Abode event socket'
		  sendEvent([name: 'eventSocket', value: 'connected'])
      state.webSocketConnected = true
      state.webSocketConnectAttempt = 0
		  break

    case ~/^status: closing.*$/:
      log.info 'Closing connection to Abode event socket'
		  sendEvent([name: 'eventSocket', value: 'disconnected'])
      state.webSocketConnected = false
      state.webSocketConnectAttempt = 0
      break

    case ~/^failure:(.*)$/:
      log.warn 'Event socket connection: ' + message
      state.webSocketConnected = false
      state.webSocketConnectAttempt += 1
      break

    default:
      log.warn 'Event socket sent unexpected message: ' + message
      state.webSocketConnected = false
      state.webSocketConnectAttempt += 1
  }

  if (isLoggedIn && !state.webSocketConnected && state.webSocketConnectAttempt < 10)
    runIn(120, 'connectEventSocket')
}
