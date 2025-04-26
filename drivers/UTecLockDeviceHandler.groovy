metadata {
    definition(name: "U-tech ULTRALOQ Latch 5 NFC", namespace: "gilderman", author: "Ilia Gilderman", 
    	importUrl: "https://raw.githubusercontent.com/gilderman/utec-lock/main/drivers/UTecLockDeviceHandler.groovy")
    {
        capability "Lock"
        capability "Refresh"
        capability "Battery"
        capability "HealthCheck"
    }
}

preferences {
	input name: "lockMode", type: "enum", title: "Select Lock Mode", options: [
        "0": "Normal",
        "1": "Passage",
        "2": "Lockout"
    ], required: true, defaultValue: "0"
 
    input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: true
}

def updated() {
    log.debug "Device updated"
    
    setMode(settings.lockMode.toInteger())
    
    if (logEnable) {
        log.debug "Debug logging is enabled."
        parent.setDebugOn(true)
        runIn(1800, "logsOff")  // Turn off debug logs after 30 mins
    }
    else {
        parent.setDebugOn(false)
    }
}

def logsOff() {
    updateSetting("logEnable", [value: "false", type: "bool"])
    parent.setDebugOn(flase)
    log.warn "Debug logging disabled automatically."
}

def setMode(Integer mode) {
    logDebug("Setting mode to: ${mode}")
    
    def resp = parent.lockCommand("setMode", device.deviceNetworkId, [mode: mode])
    if (resp) {
        sendEvent(name: "lockMode", value: mode)
    }
}
 
def lock() {
    logDebug("Locking the door...")

    def resp = parent.lockCommand("lock", device.deviceNetworkId)
    if (resp) {
        sendEvent(name: "lock", value: "locked")
    }
}

def unlock() {
    logDebug("Unlocking the door...")

    def resp = parent.lockCommand("unlock", device.deviceNetworkId)
    if (resp) {
        sendEvent(name: "lock", value: "unlocked")
    }
}

def refresh() {
    logDebug("Refreshing lock status...")

    parent.refreshDeviceStatus(device)
}

def ping() {
    logDebug("Ping received")
    
    parent.refreshDeviceStatus(device)
    sendEvent(name: "lastCheckin", value: new Date().toString())
}

def logDebug(msg) {
    if (settings?.logEnable) log.debug msg
}

