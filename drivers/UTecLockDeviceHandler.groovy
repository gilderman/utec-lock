metadata {
    definition(
		name: "U-tec Lock", 
		namespace: "gilderman", 
		author: "Ilia Gilderman",
		importUrl: "https://raw.githubusercontent.com/gilderman/utec-lock/main/drivers/UTecLockDeviceHandler.groovy"
	) 
	{
        capability "Lock"
        capability "Refresh"
    }
}

def lock() {
    sendEvent(name: "lock", value: "locked")
    log.debug "Locking the door..."
}

def unlock() {
    sendEvent(name: "lock", value: "unlocked")
    log.debug "Unlocking the door..."
}

def refresh() {
    log.debug "Refreshing lock status..."
    sendEvent(name: "lock", value: device.currentValue("lock") ?: "unknown")
}