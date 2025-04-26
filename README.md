# U-tec ULTRALOQ Latch 5 NFC Integration for Hubitat

This project provides a custom Hubitat app and driver to integrate the U-tec ULTRALOQ Latch 5 NFC lock with your Hubitat Elevation hub.

---

## 🔧 Installation

### Option 1: Manual Installation

1. **Install the Library:**
   - Navigate to **Libraries Code** > **New Library**.
   - Copy the contents from [`lib/UTecLockHelper.groovy`](https://github.com/gilderman/utec-lock/blob/main/libraries/UTecLockHelper.groovy) 
   - Paste into the code editor and click **Save**.

2. **Install the Driver:**
   - Go to your Hubitat web interface.
   - Navigate to **Drivers Code** > **New Driver**.
   - Copy the contents of [`drivers/UTecLockDeviceHandler.groovy`](https://github.com/gilderman/utec-lock/blob/main/drivers/UTecLockDeviceHandler.groovy).
   - Paste into the code editor and click **Save**.

3. **Install the App:**
   - Go to **Apps Code** > **New App**.
   - Copy the contents of [`apps/UTecLockManager.groovy`](https://github.com/gilderman/utec-lock/blob/main/apps/UTecLockManager.groovy).
   - Paste into the code editor and click **Save**.

4. **Add the App:**
   - Go to **Apps** > **Add User App**.
   - Select **ULoc App** from the list to create an instance.

### Option 2: Hubitat Package Manager (HPM)

*Currently not available via HPM.* You must install manually using the steps above.

---

## 🔄 Pairing Your Lock with Hubitat

1. **Obtain Developer Access**
2. **Prepare the Lock:**
   - Open the **U-tec** mobile app.
   - Go to **Settings** > **Developer Console**.

3. **Set Redirecrt URL**
   - [https://clolud.hubitat.com/oauth/stateredirect]
---

## ⚙️ Configuring the ULoc App

1. Navigate to **Apps** and open **ULoc App**.
2. Enter you developer API API and Secret .
3. Click login and follow authorization workflow.
4. When login is successful, close the popup window.
5. Click Discover Locks.
6. The App will add lock devices. 
7. Click **Done**.

---

## ✅ Features

- Lock/unlock via Hubitat
- Status reporting
- Change lock mode
- Integration with automations and dashboards

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

_Contributions and suggestions are welcome!_
