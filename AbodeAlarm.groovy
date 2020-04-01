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
    importUrl: 'https://raw.githubusercontent.com/jorhett/hubitat-abode/v0/AbodeAlarm.groovy',
  ) {
    capability 'Actuator'
    capability 'Refresh'
    command 'armAway'
    command 'armHome'
    command 'disarm'
    command 'logout'
    attribute 'isLoggedIn', 'String'
    attribute 'gatewayMode', 'String'
  }

  preferences {
    if(showLogin != false) {
      section('Abode API') {
        input name: 'username', type: 'text',     title: 'Abode username',   required: true,  displayDuringSetup: true, description: '<em>Abode username</em>'
        input name: 'password', type: 'password', title: 'Abode password',   required: true,  displayDuringSetup: true, description: '<em>Abode password</em>'
        input name: 'mfa_code', type: 'text',     title: 'Current MFA Code', required: false, displayDuringSetup: true, description: '<em>Not stored -- used one time</em>'
      }
    }
    section('Behavior') {
      input name: 'targetModeAway', type: 'enum',   title: 'Hubitat Mode when Abode Away',     options: location.getModes().collect { it.toString() }
      input name: 'targetModeHome', type: 'enum',   title: 'Hubitat Mode when Abode Home',     options: location.getModes().collect { it.toString() }
      input name: 'syncArming',     type: 'bool',   title: 'Sync Exit Delay start',            defaultValue: false, description: '<em>Enable concurrent exit delays</em>'

      input name: 'saveContacts',   type: 'bool',   title: 'Save Abode contact events',        defaultValue: false, description: '<em>...to Hubitat Events</em>'
      input name: 'saveGeofence',   type: 'bool',   title: 'Save Abode geofence events',       defaultValue: false, description: '<em>...to Hubitat Events</em>'
      input name: 'saveAutomation', type: 'bool',   title: 'Save CUE Automation actions',      defaultValue: false, description: '<em>...to Hubitat Events</em>'

      input name: 'showLogin',      type: 'bool',   title: 'Show login fields',                defaultValue: true,  description: '<em>Show login fields</em>', submitOnChange: true
      input name: 'logDebug',       type: 'bool',   title: 'Enable debug logging',             defaultValue: true,  description: '<em>for 2 hours</em>'
      input name: 'logTrace',       type: 'bool',   title: 'Enable trace logging',             defaultValue: false, description: '<em>for 30 minutes</em>'
      input name: 'timeoutSlack',   type: 'number', title: 'Timeout slack in seconds',         defaultValue: '30',  description: '<em><b>+</b> for resilience, <b>-</b> reconnect faster</em>'
    }
  }
}

// Hubitat standard methods
def installed() {
  log.debug 'installed'
  device.updateSetting('showLogin', [value: true, type: 'bool'])
  initialize()
  if (!childDevices)
    createIsArmedSwitch()
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
  if (!childDevices)
    createIsArmedSwitch()

  // Disable high levels of logging after time
  if (logTrace) runIn(1800,disableTrace)
  if (logDebug) runIn(7200,disableDebug)

  // Reasons we should attempt login again
  if (
    // If they supplied mfa code they want to login again
    (!username.isEmpty() && !password.isEmpty() && mfa_code) ||
    // If we aren't logged in, attempt login
    (!username.isEmpty() && !password.isEmpty() && (state.token == null)) ||
    // If they changed the username, attempt login
    (!username.isEmpty() && !password.isEmpty() && (username != getDataValue('abodeID')))
  )
    login()
  else
    validateSession()

  // Clear the MFA token entry -- will be useless anyway
  device.updateSetting('mfa_code', [value: '', type: 'text'])
}

def refresh() {
  if (validateSession()) {
    parsePanel(getPanel())
    if (state.webSocketConnected != true)
      connectEventSocket()
  }
}

def uninstalled() {
  clearLoginState()
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

// isArmed Child Switch
def createIsArmedSwitch() {
  addChildDevice('hubitat', 'Virtual Switch', device.id + '-isArmed', [name: device.name + '-isArmed', isComponent: true])
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
    updateDataValue('mfa_enabled', '1')
    sendEvent(name: 'isLoggedIn', value: "false - requires ${reply.mfa_type}", descriptionText: "Multi-Factor Authentication required: ${reply.mfa_type}", displayed: true)
  }
  else if(reply.containsKey('token')) {
    sendEvent(name: 'isLoggedIn', value: true, displayed: true)
    device.updateSetting('showLogin', [value: false, type: 'bool'])
    parseLogin(reply)
    state.access_token = getAccessToken()
    parsePanel(getPanel())
    connectEventSocket()
  }
}

// Make sure we're still authenticated
private validateSession() {
  user = getUser()
  logged_in = user?.id ? true : false
  if(! logged_in) {
    if (state.token) {
      sendEvent(name: 'lastResult', value: 'Not logged in', descriptionText: 'Attempted transaction when not logged in', displayed: true)
      clearLoginState()
    }
  }
  else {
    parseUser(user)
    state.access_token = getAccessToken()
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
  clearLoginState()
}

private clearLoginState() {
  state.clear()
  unschedule()
  device.updateSetting('showLogin', [value: true, type: 'bool'])
  sendEvent(name: 'isLoggedIn', value: false, displayed: true)
}

// Send a request to change mode to Abode
private changeMode(String new_mode) {
  if(new_mode != device.currentValue('gatewayMode')) {
    // Only update area 1 since area is not returned in event messages
    reply = doHttpRequest('PUT','/api/v1/panel/mode/1/' + new_mode)
    if (reply['area'] == '1') {
      log.info "Sent request to change Abode gateway mode to ${new_mode}"
      state.localModeChange = new_mode
    }
  } else {
    if (logDebug) log.debug "Gateway is already in mode ${new_mode}"
  }
}

// Process an update from Abode that the mode has changed
private updateMode(String new_mode) {
  log.info 'Abode gateway mode has changed to ' + new_mode
  sendEvent(name: "gatewayMode", value: new_mode, descriptionText: 'Gateway mode has changed to ' + new_mode, displayed: true)

  // Set isArmed?
  isArmed = getChildDevice(device.id + '-isArmed')
  if (new_mode == 'standby')
    isArmed.off()
  else {
    isArmed.on()

    // Avoid changing the mode if it's a rebound from a local action
    if (new_mode == state.localModeChange) {
      state.remove('localModeChange')
    } else {
      if (targetModeAway && new_mode == 'away') {
        log.info 'Changing Hubitat mode to ' + new_mode
        location.setMode(targetModeAway)
      }
      else if (targetModeHome) {
        log.info 'Changing Hubitat mode to ' + new_mode
        location.setMode(targetModeHome)
      }
    }
  }
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

private parseLogin(Map data) {
  state.token = data.token

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
  if (device.currentValue('gatewayMode') != modeMap['1'])
    sendEvent(name: "gatewayMode", value: modeMap['1'], descriptionText: "Gateway mode is ${modeMap['1']}", displayed: true)

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
  sendEvent(name: 'lastResult', value: "${status} ${message}", descriptionText: message, type: 'API call', displayed: true)
  return result
}

// Abode event websocket handling
def connectEventSocket() {
  if (!state.webSocketConnectAttempt) state.webSocketConnectAttempt = 0
  if (logDebug) log.debug "Attempting WebSocket connection for Abode events (attempt ${state.webSocketConnectAttempt})"
  try {
    interfaces.webSocket.connect('wss://my.goabode.com/socket.io/?EIO=3&transport=websocket', headers: [
      'Origin': baseURL() + '/',
      'Cookie': "SESSION=${state.cookies['SESSION']}",
    ])
    if (logDebug) log.debug 'EventSocket connection initiated'
    runEvery5Minutes(checkSocketTimeout)
  }
  catch(error) {
    log.error 'WebSocket connection to Abode event socket failed: ' + error.toString()
  }
}

private terminateEventSocket() {
  if (logDebug) log.debug 'Disconnecting Abode event socket'
  try {
    interfaces.webSocket.close()
    state.webSocketConnected = false
    state.webSocketConnectAttempt = 0
    if (logDebug) log.debug 'EventSocket connection terminated'
  }
  catch(error) {
    log.error 'Disconnect of WebSocket from Abode portal failed: ' + error.toString()
  }
}

// failure handler: validate state and reconnect in 5 seconds
private restartEventSocket() {
  terminateEventSocket()
  runInMillis(5000, refresh)
}

def sendPing() {
  if (logTrace) log.trace 'Sending webSocket ping'
  interfaces.webSocket.sendMessage('2')
}

def sendPong() {
  if (logTrace) log.trace 'Sending webSocket pong'
  interfaces.webSocket.sendMessage('3')
}

def receivePong() {
  runInMillis(state.webSocketPingInterval, sendPing)
}

// This is called every 5 minutes whether we are connected or not
def checkSocketTimeout() {
  if (state.webSocketConnected) {
    responseTimeout = state.lastMsgReceived + state.webSocketPingTimeout + (timeoutSlack*1000)
    if (now() > responseTimeout) {
      log.warn 'Socket ping timeout - Disconnecting Abode event socket'
      restartEventSocket()
    }
  } else {
    connectEventSocket()
  }
}

// Websocket message parsing
private devicesToIgnore() {
  return [
    // Don't need to log what the camera captured
    'Iota Cam'
  ]
}

// These events have corresponding timeline and don't appear actionable
private eventsToIgnore() {
  return [
    // Internal alarm tracking events used by Abode responders
    'alarm.add',
    'alarm.del',
    // Nest integration events
    'nest.refresh.true',
  ]
}

String formatEventUser(HashMap jsondata) {
  userdata = ''
  if (jsondata.user_name) {
    userdata += ' by ' + jsondata.user_name
  }
  if (jsondata.mobile_name) {
    userdata += ' using ' + jsondata.mobile_name
  }
  return userdata
}

def syncArmingEvents(String event_type) {
  switch(event_type) {
    case ~/Arming .* Away.*/:
      if (targetModeAway) location.setMode(targetModeAway)
      break
    case ~/Arming .* Home.*/:
      if (targetModeHome) location.setMode(targetModeHome)
      break
    default:
      // ignore it
      break
  }
}

def sendEnabledEvents(
  String alert_name,
  String alert_value,
  String message,
  String alert_type
) {
  switch(alert_type) {
    // Ignore camera events
    case ~/.* Cam/:
      break

    // User choice to log
    case ~/.* Contact/:     // or event code 5100 open, 5101 closed, 5110 unlocked, 5111 locked
      if (saveContacts)
        sendEvent(name: alert_name, value: alert_value, descriptionText: message, type: alert_type, displayed: true)
      break

    case ~/CUE Automation/:    // or event code 520x
      if (saveAutomation)
        sendEvent(name: alert_name, value: alert_value, descriptionText: message, type: alert_type, displayed: true)
      break

    default:
      sendEvent(name: alert_name, value: alert_value, descriptionText: message, type: alert_type, displayed: true)
      break
  }
}

def parseEvent(String event_text) {
  twovalue = event_text =~ /^\["com\.goabode\.([\w+\.]+)",(.*)\]$/
  if (twovalue.find()) {
    event_type = twovalue[0][1]
    event_data = twovalue[0][2]
    switch(event_data) {
      // Quoted text
      case ~/^".*"$/:
        message = event_data[1..-2]
        break

      // Unquoted text
      case ~/^\w+$/:
        message = event_data
        break

      // JSON format
      case ~/^\{.*\}$/:
        details = parseJson(event_data)
        message = details.event_name
        user_info = formatEventUser(details)
        device_type = details.device_type ?: ''
        alert_name = details.device_name ?: 'unknown'
        alert_value = details.event_type

        if (details.event_type == 'Automation') {
          alert_type = 'CUE Automation'
          // Automation puts the rule name in device_name, which is backwards for our purposes
          alert_name = 'Automation'
          alert_value = details.device_name
        }
        else if (user_info)
          alert_type = user_info
        else if (device_type != '')
          alert_type = device_type
        else
          alert_type = ''
        break

      default:
        log.warn "Event ${event_type} has unknown data format: ${event_data}"
        message = event_data
        break
    }
    switch(event_type) {
      case eventsToIgnore:
        break

      case 'gateway.mode':
        updateMode(message)
        break

      case ~/^gateway\.timeline.*/:
        if (logDebug) log.debug "${event_type} -${device_type} ${message}"

        // Devices we ignore events for
        if (! devicesToIgnore().contains(details.device_name)) {
          if (syncArming) syncArmingEvents(details.event_type)
          sendEnabledEvents(alert_name, alert_value, message, alert_type)
        }
         break

      // Presence/Geofence updates
      case ~/fence.update.*/:
        if (saveGeofence)
          sendEvent(name: details.name, value: details.location, descriptionText: details.message, type: 'Geofence', displayed: true)
        break

      default:
        if (logDebug) log.debug "Ignoring Event ${event_type} ${message}"
        break
    }
  } else {
    log.warn "Unparseable Event message: ${event_text}"
  }
}

// Hubitat required method: This method is called with any incoming messages from the web socket server
def parse(String message) {
  state.lastMsgReceived = now()
  if (logTrace) log.trace 'webSocket event raw: ' + message

  // First character is the event type
  event_type = message.substring(0,1)
  // remainder is the data (optional)
  event_data = message.substring(1)

  switch(event_type) {
    case '0':
      log.debug 'webSocket session open received'
      jsondata = parseJson(event_data)
      if (jsondata.containsKey('pingInterval')) state.webSocketPingInterval = jsondata['pingInterval']
      if (jsondata.containsKey('pingTimeout'))  state.webSocketPingTimeout  = jsondata['pingTimeout']
      if (jsondata.containsKey('sid'))          state.webSocketSid          = jsondata['sid']
      break

    case '1':
      log.debug 'webSocket session close received'
      restartEventSocket()
      break

    case '2':
      if (logTrace) log.trace 'webSocket Ping received, sending reply'
      sendPong()
      break

    case '3':
      if (logTrace) log.trace 'webSocket Pong received'
      receivePong()
      break

    case '4':
      // First character of the message indicates purpose
      message_type = event_data.substring(0,1)
      message_data = event_data.substring(1)
      switch(message_type) {
        case '0':
          log.info 'webSocket message = Event socket connected'
          runInMillis(state.webSocketPingInterval, sendPing)
          break

        case '1':
          log.info 'webSocket message = Event socket disconnected'
          break

        case '2':
          parseEvent(message_data)
          break

        case '4':
          log.warn 'webSocket message = Error: ' + message_data
          sendEvent(name: 'webSocket Message', value: message_data, descriptionText: message_data, type: 'Error', displayed: true)

          // Authorization failure message is enclosed in double quotes ;p
          if (message_data == '"Not Authorized"') {
            terminateEventSocket()
            // validate the session and get a new access token
            refresh()
          }
          break

        default:
          log.warn "webSocket message = (unknown:${message_type}): ${message_data}"
          sendEvent(name: 'webSocket Message', value: message_data, descriptionText: message_data, type: 'Unknown type', displayed: true)
          break
      }
      break

    default:
      log.warn "Unknown webSocket event (${event_type}) received: " + event_data
      break
  }
}

// Hubitat required method: This method is called with any status messages from the web socket client connection
def webSocketStatus(String message) {
  if (logTrace) log.trace 'webSocketStatus ' + message
  switch(message) {
    case ~/^status: open.*$/:
      log.debug 'Connected to Abode event socket'
		  sendEvent([name: 'eventSocket', value: 'connected'])
      state.webSocketConnected = true
      state.webSocketConnectAttempt = 0
		  break

    case ~/^status: closing.*$/:
      log.debug 'Closing connection to Abode event socket'
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

  if ((device.currentValue('isLoggedIn') == true) && !state.webSocketConnected && state.webSocketConnectAttempt < 10)
    runIn(120, 'connectEventSocket')
}
