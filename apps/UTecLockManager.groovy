#include gilderman.UTecLockHelper

definition(
    name: "UTecLockManager",
    namespace: "gilderman",
    author: "Ilia Gilderman",
    description: "Integration with U-tec ULTRALOQ Latch 5 NFC lock",
    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/gilderman/utec-lock/master/resources/icons/u-tec_2024_new_logo.svg",
    iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/gilderman/utec-lock/main/apps/UTecLockManager.groovy",
    oauth: true,
    singleInstance: false,
    installOnOpen: true
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "U-tec Lock Setup", install: true, uninstall: true) {
        
		section("OAuth2 Settings") {
			input "clientId", "password", title: "Client ID", required: true, submitOnChange: true
			input "clientSecret", "password", title: "Client Secret", required: true, submitOnChange: true
		}

        section("Login to U-tec") {
        	href(name: "login", title: "Login with U-tec", required: false, 
                 description: "Click to authenticate", 
                 url: getLoginUrl(state.clientId)
                 , style: "external"
                )
        }
        section("Login Status") {
            paragraph state.loginSuccess ? "✅ Login Successful!" : "❌ Not Logged In"
        }
                
		section("Lock Discovery") {
			input "discoverLocks", "button", title: "Discover Locks", submitOnChange: true
		}
	}
}

mappings {
	path("/callback") {
		action: [
			GET: "oauthCallback"
		]
	}
    
    path("/notifications") {
        action: [
            POST: "notificationsCallback"
        ]
    }
}

def installed() { initialize() }

def updated() { 
    if (settings.clientId) {
        state.clientId = settings.clientId  
    }
    
    if (settings.clientSecret) {
        state.clientSecret = settings.clientSecret  
    }  
}

def initialize() {
}

def appButtonHandler(btn) {
    if (btn == "discoverLocks") {
        discoverLocks()
    }
}

def discoverLocks() {
	def response = sendPostRequest("Discovery", null)
	if (response) {
        registerNotificationsCallback()
        
        devices = response.payload.devices

        devices.each { device ->
    		def deviceId = device.id       
	  		def deviceName = device.name   
 		   
            createChildDevice(deviceId, deviceName)
        }
    } else {
        log.warn "Failed to receive response from API"
    }
}

def createChildDevice(deviceId, deviceName) {
	log.info "Creating child device: ${deviceName}"

	if (!getChildDevice(deviceId)) {
        addChildDevice(
            "gilderman",  
            "U-tech ULTRALOQ Latch 5 NFC", 
            deviceId,
            [label: deviceName, isComponent: false]
        )
        log.info "Child device '${deviceName}' created."
    } else {
        log.info "Child device '${deviceName}' already exists."
    }
}

def updateDeviceStatus(device, Map deviceStatus) {
	def scaledBattery = deviceStatus.batteryLevel * 20  // 1 → 20%, 5 → 100%

    device.sendEvent(name: "lock", value: deviceStatus.lockStatus)
    device.sendEvent(name: "lockMode", value: deviceStatus.lockMode)
    device.sendEvent(name: "battery", value: scaledBattery)
    device.sendEvent(name: "status", value: deviceStatus.onlineStatus)
}

def refreshDeviceStatus(device, Map deviceStatus = null) {
    if (!deviceStatus) {
    	deviceStatus = getStatusCommand(device.deviceNetworkId)
    }
    if (deviceStatus) {
        updateDeviceStatus(device, deviceStatus)
    }
    else {    
        log.error "Failed to query device with network id ${device.deviceNetworkId}"
    }
}

def notificationsCallback() {
	def payload = request.JSON
    //log.debug "Received LAN callback: ${payload}"  
    
    def deviceStatus = parseStatusResponse(payload)
    
    def device = getChildDevice(deviceStatus.id) 

    if (device) {
    	refreshDeviceStatus(device, deviceStatus)
    }
  	else {
        log.error "Failed to find device with id ${deviceStatus.id}"
    }
}