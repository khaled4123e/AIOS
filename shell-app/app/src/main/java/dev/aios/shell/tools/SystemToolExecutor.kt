// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.tools

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Fuehrt echte Android-Systembefehle aus.
 *
 * Jede Methode gibt eine Map zurueck mit mindestens:
 *   "success" (Boolean), "message" (String) und ggf. weitere Daten.
 */
class SystemToolExecutor(private val context: Context) : dev.aios.shell.broker.SystemToolExecutor {

    // -----------------------------------------------------------------------
    //  Oeffentliche Dispatch-Methode
    // -----------------------------------------------------------------------

    override fun execute(toolId: String, input: Map<String, Any>): Map<String, Any> {
        return try {
            when (toolId) {
                // -- Einstellungen --
                "system.settings.set_focus_mode"    -> setFocusMode(input)
                "system.settings.control_brightness" -> controlBrightness(input)
                "system.settings.control_volume"     -> controlVolume(input)
                "system.settings.set_wifi"           -> setWifi(input)
                "system.settings.set_bluetooth"      -> setBluetooth(input)
                "system.settings.set_airplane_mode"  -> setAirplaneMode(input)
                "system.settings.set_rotation"       -> setRotation(input)
                "system.settings.set_location"       -> setLocation(input)
                "system.settings.get_battery"        -> getBattery()

                // -- Apps --
                "system.apps.open"              -> openApp(input)
                "system.apps.list_installed"    -> listInstalledApps()
                "system.apps.get_info"          -> getAppInfo(input)
                "system.apps.force_stop"        -> forceStopApp(input)
                "system.apps.uninstall"         -> uninstallApp(input)

                // -- Kommunikation --
                "system.phone.call"             -> phoneCall(input)
                "system.phone.send_sms"         -> sendSms(input)
                "system.contacts.search"        -> searchContacts(input)
                "system.contacts.add"           -> addContact(input)

                // -- Medien --
                "system.media.play_pause"       -> mediaPlayPause()
                "system.media.next_track"       -> mediaNextTrack()
                "system.media.prev_track"       -> mediaPrevTrack()
                "system.media.take_photo"       -> takePhoto()
                "system.media.take_screenshot"  -> takeScreenshot()

                // -- Kalender & Wecker --
                "system.calendar.create_event"  -> createCalendarEvent(input)
                "system.calendar.list_events"   -> listCalendarEvents(input)
                "system.alarm.set"              -> setAlarm(input)
                "system.alarm.set_timer"        -> setTimer(input)

                // -- Geraeteinformationen --
                "system.device.get_info"             -> getDeviceInfo()
                "system.device.get_network_info"     -> getNetworkInfo()
                "system.device.get_storage"          -> getStorageInfo()
                "system.device.get_running_processes" -> getRunningProcesses()

                // -- Zwischenablage & Eingabe --
                "system.clipboard.copy"   -> clipboardCopy(input)
                "system.clipboard.paste"  -> clipboardPaste()
                "system.input.type_text"  -> typeText(input)

                // -- Benachrichtigungen --
                "system.notifications.list"        -> listNotifications()
                "system.notifications.dismiss"     -> dismissNotification(input)
                "system.notifications.dismiss_all" -> dismissAllNotifications()
                "system.notifications.send"        -> sendNotification(input)

                // -- Dateisystem --
                "system.files.read"        -> readFile(input)
                "system.files.write"       -> writeFile(input)
                "system.files.list"        -> listDirectory(input)
                "system.files.search"      -> searchFiles(input)
                "system.files.delete"      -> deleteFile(input)
                "system.files.create_dir"  -> createDirectory(input)

                // -- Shell --
                "system.shell.execute"   -> shellExecute(input)
                "system.shell.get_prop"  -> shellGetProp(input)

                else -> mapOf(
                    "success" to false,
                    "message" to "Unbekanntes Tool: $toolId"
                )
            }
        } catch (e: SecurityException) {
            mapOf(
                "success" to false,
                "message" to "Berechtigung verweigert: ${e.message}"
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Fehler bei Ausfuehrung von $toolId: ${e.message}"
            )
        }
    }

    // ===================================================================
    //  1. Einstellungen
    // ===================================================================

    private fun setFocusMode(input: Map<String, Any>): Map<String, Any> {
        val enabled = input["enabled"] as? Boolean ?: true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) {
            // Einstellungsseite oeffnen, damit der Nutzer die Berechtigung erteilt
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return mapOf(
                "success" to false,
                "message" to "Berechtigung fuer Nicht-Stoeren wird benoetigt. Einstellungen wurden geoeffnet."
            )
        }

        val filter = if (enabled) {
            NotificationManager.INTERRUPTION_FILTER_NONE
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        nm.setInterruptionFilter(filter)

        return mapOf(
            "success" to true,
            "message" to if (enabled) "Nicht-Stoeren aktiviert" else "Nicht-Stoeren deaktiviert",
            "enabled" to enabled
        )
    }

    private fun controlBrightness(input: Map<String, Any>): Map<String, Any> {
        val level = (input["level"] as? Number)?.toInt() ?: 50

        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return mapOf(
                "success" to false,
                "message" to "Schreibberechtigung fuer Systemeinstellungen wird benoetigt. Einstellungen wurden geoeffnet."
            )
        }

        // Automatische Helligkeit deaktivieren
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )

        val brightnessValue = (level.coerceIn(0, 100) * 255) / 100
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightnessValue
        )

        return mapOf(
            "success" to true,
            "message" to "Helligkeit auf ${level}% gesetzt",
            "level" to level,
            "raw_value" to brightnessValue
        )
    }

    private fun controlVolume(input: Map<String, Any>): Map<String, Any> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val level = (input["level"] as? Number)?.toInt()
        val streamName = (input["stream"] as? String) ?: "media"

        val stream = when (streamName.lowercase()) {
            "ring", "klingelton"        -> AudioManager.STREAM_RING
            "media", "medien"           -> AudioManager.STREAM_MUSIC
            "alarm", "wecker"           -> AudioManager.STREAM_ALARM
            "notification", "benachrichtigung" -> AudioManager.STREAM_NOTIFICATION
            "system"                    -> AudioManager.STREAM_SYSTEM
            else                        -> AudioManager.STREAM_MUSIC
        }

        val maxVolume = audioManager.getStreamMaxVolume(stream)

        if (level != null) {
            val volumeValue = (level.coerceIn(0, 100) * maxVolume) / 100
            audioManager.setStreamVolume(stream, volumeValue, 0)
            return mapOf(
                "success" to true,
                "message" to "Lautstaerke ($streamName) auf ${level}% gesetzt",
                "stream" to streamName,
                "level" to level,
                "raw_value" to volumeValue,
                "max_value" to maxVolume
            )
        }

        // Kein Level angegeben: aktuelle Lautstaerke zurueckgeben
        val currentVolume = audioManager.getStreamVolume(stream)
        val currentPercent = if (maxVolume > 0) (currentVolume * 100) / maxVolume else 0
        return mapOf(
            "success" to true,
            "message" to "Aktuelle Lautstaerke ($streamName): ${currentPercent}%",
            "stream" to streamName,
            "level" to currentPercent,
            "raw_value" to currentVolume,
            "max_value" to maxVolume
        )
    }

    private fun setWifi(input: Map<String, Any>): Map<String, Any> {
        val enabled = input["enabled"] as? Boolean ?: true

        // Ab Android Q kann WLAN nicht mehr direkt geschaltet werden
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return mapOf(
                "success" to true,
                "message" to "WLAN-Einstellungen wurden geoeffnet (ab Android 10 ist direktes Schalten nicht erlaubt)"
            )
        }

        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiManager.isWifiEnabled = enabled

        return mapOf(
            "success" to true,
            "message" to if (enabled) "WLAN aktiviert" else "WLAN deaktiviert",
            "enabled" to enabled
        )
    }

    private fun setBluetooth(input: Map<String, Any>): Map<String, Any> {
        val enabled = input["enabled"] as? Boolean ?: true
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter

        if (adapter == null) {
            return mapOf(
                "success" to false,
                "message" to "Bluetooth wird auf diesem Geraet nicht unterstuetzt"
            )
        }

        // Ab Android 13 (TIRAMISU) muss BLUETOOTH_CONNECT erteilt sein
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return mapOf(
                    "success" to false,
                    "message" to "Bluetooth-Berechtigung (BLUETOOTH_CONNECT) wird benoetigt"
                )
            }
        }

        // Ab Android 13 kann Bluetooth nicht mehr direkt geschaltet werden
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return mapOf(
                "success" to true,
                "message" to "Bluetooth-Einstellungen wurden geoeffnet (ab Android 13 ist direktes Schalten nicht erlaubt)"
            )
        }

        @Suppress("DEPRECATION")
        if (enabled) adapter.enable() else adapter.disable()

        return mapOf(
            "success" to true,
            "message" to if (enabled) "Bluetooth aktiviert" else "Bluetooth deaktiviert",
            "enabled" to enabled
        )
    }

    private fun setAirplaneMode(input: Map<String, Any>): Map<String, Any> {
        // Flugmodus kann ohne Systemrechte nicht direkt gesetzt werden
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return mapOf(
            "success" to true,
            "message" to "Flugmodus-Einstellungen wurden geoeffnet"
        )
    }

    private fun setRotation(input: Map<String, Any>): Map<String, Any> {
        val enabled = input["enabled"] as? Boolean ?: true

        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return mapOf(
                "success" to false,
                "message" to "Schreibberechtigung fuer Systemeinstellungen wird benoetigt"
            )
        }

        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (enabled) 1 else 0
        )

        return mapOf(
            "success" to true,
            "message" to if (enabled) "Automatische Bildschirmdrehung aktiviert" else "Automatische Bildschirmdrehung deaktiviert",
            "enabled" to enabled
        )
    }

    private fun setLocation(input: Map<String, Any>): Map<String, Any> {
        // Standortdienste koennen nur ueber die Einstellungen geschaltet werden
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return mapOf(
            "success" to true,
            "message" to "Standort-Einstellungen wurden geoeffnet"
        )
    }

    private fun getBattery(): Map<String, Any> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperature = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val voltage = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0

        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC      -> "Netzteil"
            BatteryManager.BATTERY_PLUGGED_USB     -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Kabellos"
            else                                   -> "Nicht angeschlossen"
        }

        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD         -> "Gut"
            BatteryManager.BATTERY_HEALTH_OVERHEAT     -> "Ueberhitzt"
            BatteryManager.BATTERY_HEALTH_DEAD         -> "Defekt"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Ueberspannung"
            BatteryManager.BATTERY_HEALTH_COLD         -> "Zu kalt"
            else                                       -> "Unbekannt"
        }

        return mapOf(
            "success" to true,
            "message" to "Akkustand: ${level}%, Laden: ${if (isCharging) "Ja" else "Nein"}",
            "level" to level,
            "is_charging" to isCharging,
            "charging_source" to chargingSource,
            "temperature_celsius" to temperature,
            "voltage" to voltage,
            "health" to healthText
        )
    }

    // ===================================================================
    //  2. Apps
    // ===================================================================

    private fun openApp(input: Map<String, Any>): Map<String, Any> {
        val packageName = input["package_name"] as? String
        val appName = input["name"] as? String

        if (packageName != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return mapOf(
                    "success" to true,
                    "message" to "App '$packageName' gestartet"
                )
            }
            return mapOf(
                "success" to false,
                "message" to "App '$packageName' nicht gefunden oder hat keine startbare Activity"
            )
        }

        if (appName != null) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val searchLower = appName.lowercase()
            val match = apps.firstOrNull {
                pm.getApplicationLabel(it).toString().lowercase().contains(searchLower)
            }
            if (match != null) {
                val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return mapOf(
                        "success" to true,
                        "message" to "App '${pm.getApplicationLabel(match)}' gestartet",
                        "package_name" to match.packageName
                    )
                }
            }
            return mapOf(
                "success" to false,
                "message" to "Keine installierte App mit dem Namen '$appName' gefunden"
            )
        }

        return mapOf(
            "success" to false,
            "message" to "Kein Paketname oder App-Name angegeben"
        )
    }

    private fun listInstalledApps(): Map<String, Any> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = apps
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { appInfo ->
                mapOf(
                    "name" to pm.getApplicationLabel(appInfo).toString(),
                    "package_name" to appInfo.packageName,
                    "system_app" to ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                )
            }
            .sortedBy { (it["name"] as String).lowercase() }

        return mapOf(
            "success" to true,
            "message" to "${appList.size} startbare Apps gefunden",
            "count" to appList.size,
            "apps" to appList
        )
    }

    private fun getAppInfo(input: Map<String, Any>): Map<String, Any> {
        val packageName = input["package_name"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Paketname angegeben")

        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

            val versionName = pkgInfo.versionName ?: "Unbekannt"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            val permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()
            val sourceDir = appInfo.sourceDir
            val appSize = File(sourceDir).length()

            mapOf(
                "success" to true,
                "message" to "App-Informationen fuer '$packageName' abgerufen",
                "name" to pm.getApplicationLabel(appInfo).toString(),
                "package_name" to packageName,
                "version_name" to versionName,
                "version_code" to versionCode,
                "target_sdk" to appInfo.targetSdkVersion,
                "min_sdk" to (appInfo.minSdkVersion),
                "apk_size_bytes" to appSize,
                "system_app" to ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0),
                "installed_path" to sourceDir,
                "permissions" to permissions
            )
        } catch (e: PackageManager.NameNotFoundException) {
            mapOf(
                "success" to false,
                "message" to "App '$packageName' nicht gefunden"
            )
        }
    }

    private fun forceStopApp(input: Map<String, Any>): Map<String, Any> {
        val packageName = input["package_name"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Paketname angegeben")

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("am", "force-stop", packageName))
            process.waitFor()
            if (process.exitValue() == 0) {
                mapOf(
                    "success" to true,
                    "message" to "App '$packageName' wurde beendet"
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "App '$packageName' konnte nicht beendet werden (Root-Rechte erforderlich)"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Fehler beim Beenden von '$packageName': ${e.message}"
            )
        }
    }

    private fun uninstallApp(input: Map<String, Any>): Map<String, Any> {
        val packageName = input["package_name"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Paketname angegeben")

        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return mapOf(
            "success" to true,
            "message" to "Deinstallations-Dialog fuer '$packageName' wurde geoeffnet"
        )
    }

    // ===================================================================
    //  3. Kommunikation
    // ===================================================================

    private fun phoneCall(input: Map<String, Any>): Map<String, Any> {
        val number = input["number"] as? String
            ?: return mapOf("success" to false, "message" to "Keine Telefonnummer angegeben")

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return mapOf(
                "success" to true,
                "message" to "Anruf an $number wird gestartet"
            )
        }

        // Ohne CALL_PHONE-Berechtigung: Waehler oeffnen
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return mapOf(
            "success" to true,
            "message" to "Waehler mit Nummer $number geoeffnet (Anruf-Berechtigung nicht erteilt)"
        )
    }

    private fun sendSms(input: Map<String, Any>): Map<String, Any> {
        val number = input["number"] as? String
            ?: return mapOf("success" to false, "message" to "Keine Telefonnummer angegeben")
        val body = input["body"] as? String ?: input["message"] as? String ?: ""

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }
                smsManager.sendTextMessage(number, null, body, null, null)
                return mapOf(
                    "success" to true,
                    "message" to "SMS an $number gesendet"
                )
            } catch (e: Exception) {
                return mapOf(
                    "success" to false,
                    "message" to "Fehler beim Senden der SMS: ${e.message}"
                )
            }
        }

        // Ohne Berechtigung: SMS-App oeffnen
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return mapOf(
            "success" to true,
            "message" to "SMS-App mit Nummer $number geoeffnet (SMS-Berechtigung nicht erteilt)"
        )
    }

    private fun searchContacts(input: Map<String, Any>): Map<String, Any> {
        val query = input["query"] as? String ?: input["name"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Suchbegriff angegeben")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return mapOf(
                "success" to false,
                "message" to "Kontakte-Berechtigung (READ_CONTACTS) wird benoetigt"
            )
        }

        val contacts = mutableListOf<Map<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: ""
                val number = cursor.getString(numberIdx) ?: ""
                val type = cursor.getInt(typeIdx)
                val typeLabel = when (type) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobil"
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME   -> "Privat"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK   -> "Arbeit"
                    else                                                -> "Sonstige"
                }
                contacts.add(mapOf("name" to name, "number" to number, "type" to typeLabel))
            }
        }

        return mapOf(
            "success" to true,
            "message" to "${contacts.size} Kontakt(e) fuer '$query' gefunden",
            "count" to contacts.size,
            "contacts" to contacts
        )
    }

    private fun addContact(input: Map<String, Any>): Map<String, Any> {
        val name = input["name"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Name angegeben")
        val phone = input["phone"] as? String
        val email = input["email"] as? String

        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, name)
            if (phone != null) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            if (email != null) putExtra(ContactsContract.Intents.Insert.EMAIL, email)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return mapOf(
            "success" to true,
            "message" to "Kontakt-Erstellung fuer '$name' geoeffnet"
        )
    }

    // ===================================================================
    //  4. Medien
    // ===================================================================

    private fun mediaPlayPause(): Map<String, Any> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)

        return mapOf(
            "success" to true,
            "message" to "Wiedergabe/Pause umgeschaltet"
        )
    }

    private fun mediaNextTrack(): Map<String, Any> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)

        return mapOf(
            "success" to true,
            "message" to "Naechster Titel"
        )
    }

    private fun mediaPrevTrack(): Map<String, Any> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)

        return mapOf(
            "success" to true,
            "message" to "Vorheriger Titel"
        )
    }

    private fun takePhoto(): Map<String, Any> {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return mapOf(
                "success" to true,
                "message" to "Kamera-App geoeffnet"
            )
        }
        return mapOf(
            "success" to false,
            "message" to "Keine Kamera-App verfuegbar"
        )
    }

    private fun takeScreenshot(): Map<String, Any> {
        // Screenshot erfordert Shell-Zugriff oder Accessibility-Service
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "input keyevent KEYCODE_SYSRQ"))
            process.waitFor()
            if (process.exitValue() == 0) {
                mapOf(
                    "success" to true,
                    "message" to "Screenshot wurde aufgenommen"
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "Screenshot konnte nicht aufgenommen werden (erweiterte Berechtigungen erforderlich)"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Fehler beim Screenshot: ${e.message}"
            )
        }
    }

    // ===================================================================
    //  5. Kalender & Wecker
    // ===================================================================

    private fun createCalendarEvent(input: Map<String, Any>): Map<String, Any> {
        val title = input["title"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Titel angegeben")
        val description = input["description"] as? String ?: ""
        val location = input["location"] as? String ?: ""
        val startTime = input["start_time"] as? Long
        val endTime = input["end_time"] as? Long
        val allDay = input["all_day"] as? Boolean ?: false

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Fallback: Kalender-Intent
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                putExtra(CalendarContract.Events.ALL_DAY, allDay)
                if (startTime != null) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                if (endTime != null) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return mapOf(
                "success" to true,
                "message" to "Kalender-App mit Termin '$title' geoeffnet (Kalender-Berechtigung nicht erteilt)"
            )
        }

        // Direkt in den Kalender eintragen
        val calendarId = getDefaultCalendarId()
        if (calendarId < 0) {
            return mapOf(
                "success" to false,
                "message" to "Kein Kalender auf dem Geraet gefunden"
            )
        }

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DTSTART, startTime ?: now)
            put(CalendarContract.Events.DTEND, endTime ?: (startTime ?: now) + 3600000)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

        return if (uri != null) {
            mapOf(
                "success" to true,
                "message" to "Termin '$title' erstellt",
                "event_uri" to uri.toString(),
                "event_id" to ContentUris.parseId(uri)
            )
        } else {
            mapOf(
                "success" to false,
                "message" to "Fehler beim Erstellen des Termins"
            )
        }
    }

    private fun getDefaultCalendarId(): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, selection, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return -1L
    }

    private fun listCalendarEvents(input: Map<String, Any>): Map<String, Any> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return mapOf(
                "success" to false,
                "message" to "Kalender-Berechtigung (READ_CALENDAR) wird benoetigt"
            )
        }

        val days = (input["days"] as? Number)?.toInt() ?: 7
        val now = System.currentTimeMillis()
        val end = now + days.toLong() * 24 * 60 * 60 * 1000

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(now.toString(), end.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val events = mutableListOf<Map<String, Any>>()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val locationIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)

            while (cursor.moveToNext()) {
                val eventStart = cursor.getLong(startIdx)
                val eventEnd = cursor.getLong(endIdx)
                events.add(mapOf(
                    "id" to cursor.getLong(idIdx),
                    "title" to (cursor.getString(titleIdx) ?: "Ohne Titel"),
                    "start" to dateFormat.format(Date(eventStart)),
                    "end" to dateFormat.format(Date(eventEnd)),
                    "location" to (cursor.getString(locationIdx) ?: ""),
                    "all_day" to (cursor.getInt(allDayIdx) == 1)
                ))
            }
        }

        return mapOf(
            "success" to true,
            "message" to "${events.size} Termin(e) in den naechsten $days Tagen",
            "count" to events.size,
            "events" to events
        )
    }

    private fun setAlarm(input: Map<String, Any>): Map<String, Any> {
        val hour = (input["hour"] as? Number)?.toInt()
            ?: return mapOf("success" to false, "message" to "Keine Stunde angegeben")
        val minute = (input["minute"] as? Number)?.toInt() ?: 0
        val label = input["label"] as? String ?: input["message"] as? String ?: "AIOS Wecker"

        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return mapOf(
                "success" to true,
                "message" to "Wecker auf ${String.format("%02d:%02d", hour, minute)} gestellt",
                "hour" to hour,
                "minute" to minute,
                "label" to label
            )
        }

        return mapOf(
            "success" to false,
            "message" to "Keine Wecker-App verfuegbar"
        )
    }

    private fun setTimer(input: Map<String, Any>): Map<String, Any> {
        val seconds = (input["seconds"] as? Number)?.toInt()
        val minutes = (input["minutes"] as? Number)?.toInt()
        val label = input["label"] as? String ?: input["message"] as? String ?: "AIOS Timer"

        val totalSeconds = (seconds ?: 0) + (minutes ?: 0) * 60
        if (totalSeconds <= 0) {
            return mapOf("success" to false, "message" to "Keine gueltige Dauer angegeben")
        }

        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, totalSeconds)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            val displayMinutes = totalSeconds / 60
            val displaySeconds = totalSeconds % 60
            return mapOf(
                "success" to true,
                "message" to "Timer auf ${displayMinutes}m ${displaySeconds}s gestellt",
                "total_seconds" to totalSeconds,
                "label" to label
            )
        }

        return mapOf(
            "success" to false,
            "message" to "Keine Timer-App verfuegbar"
        )
    }

    // ===================================================================
    //  6. Geraeteinformationen
    // ===================================================================

    private fun getDeviceInfo(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val totalRam = runtime.maxMemory()
        val freeRam = runtime.freeMemory()
        val usedRam = runtime.totalMemory() - freeRam

        return mapOf(
            "success" to true,
            "message" to "Geraeteinformationen abgerufen",
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT,
            "security_patch" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "Nicht verfuegbar"),
            "board" to Build.BOARD,
            "hardware" to Build.HARDWARE,
            "supported_abis" to Build.SUPPORTED_ABIS.toList(),
            "ram_total_mb" to (totalRam / (1024 * 1024)),
            "ram_used_mb" to (usedRam / (1024 * 1024)),
            "ram_free_mb" to (freeRam / (1024 * 1024)),
            "display_id" to Build.DISPLAY,
            "build_id" to Build.ID,
            "build_type" to Build.TYPE,
            "fingerprint" to Build.FINGERPRINT
        )
    }

    private fun getNetworkInfo(): Map<String, Any> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = if (network != null) cm.getNetworkCapabilities(network) else null

        val result = mutableMapOf<String, Any>(
            "success" to true,
            "message" to "Netzwerkinformationen abgerufen",
            "connected" to (network != null)
        )

        if (capabilities != null) {
            result["has_wifi"] = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            result["has_cellular"] = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            result["has_ethernet"] = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            result["has_vpn"] = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                result["download_bandwidth_kbps"] = capabilities.linkDownstreamBandwidthKbps
                result["upload_bandwidth_kbps"] = capabilities.linkUpstreamBandwidthKbps
            }
        }

        // WLAN-Details
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wm.connectionInfo
            if (wifiInfo != null) {
                @Suppress("DEPRECATION")
                result["wifi_ssid"] = wifiInfo.ssid ?: "Unbekannt"
                result["wifi_rssi"] = wifiInfo.rssi
                result["wifi_link_speed_mbps"] = wifiInfo.linkSpeed
                result["wifi_frequency_mhz"] = wifiInfo.frequency
            }
        }

        return result
    }

    private fun getStorageInfo(): Map<String, Any> {
        val internal = Environment.getDataDirectory()
        val statInternal = StatFs(internal.path)
        val totalInternal = statInternal.totalBytes
        val freeInternal = statInternal.availableBytes
        val usedInternal = totalInternal - freeInternal

        val result = mutableMapOf<String, Any>(
            "success" to true,
            "message" to "Speicherinformationen abgerufen",
            "internal_total_gb" to String.format("%.2f", totalInternal / 1_073_741_824.0),
            "internal_used_gb" to String.format("%.2f", usedInternal / 1_073_741_824.0),
            "internal_free_gb" to String.format("%.2f", freeInternal / 1_073_741_824.0),
            "internal_used_percent" to if (totalInternal > 0) ((usedInternal * 100) / totalInternal).toInt() else 0
        )

        // Externer Speicher (falls vorhanden)
        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            val statExternal = StatFs(externalDirs[1]!!.path)
            val totalExternal = statExternal.totalBytes
            val freeExternal = statExternal.availableBytes
            result["external_total_gb"] = String.format("%.2f", totalExternal / 1_073_741_824.0)
            result["external_free_gb"] = String.format("%.2f", freeExternal / 1_073_741_824.0)
        }

        return result
    }

    private fun getRunningProcesses(): Map<String, Any> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("ps", "-A", "-o", "PID,NAME"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            process.waitFor()

            val processes = lines.drop(1).mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) {
                    mapOf("pid" to parts[0], "name" to parts[1])
                } else null
            }

            mapOf(
                "success" to true,
                "message" to "${processes.size} laufende Prozesse gefunden",
                "count" to processes.size,
                "processes" to processes
            )
        } catch (e: Exception) {
            // Fallback: nur Paketnamen der laufenden Tasks
            mapOf(
                "success" to false,
                "message" to "Prozessliste konnte nicht abgerufen werden: ${e.message}"
            )
        }
    }

    // ===================================================================
    //  7. Zwischenablage & Eingabe
    // ===================================================================

    private fun clipboardCopy(input: Map<String, Any>): Map<String, Any> {
        val text = input["text"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Text angegeben")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AIOS", text)
        clipboard.setPrimaryClip(clip)

        return mapOf(
            "success" to true,
            "message" to "Text in die Zwischenablage kopiert",
            "length" to text.length
        )
    }

    private fun clipboardPaste(): Map<String, Any> {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if (!clipboard.hasPrimaryClip()) {
            return mapOf(
                "success" to true,
                "message" to "Zwischenablage ist leer",
                "text" to ""
            )
        }

        val item = clipboard.primaryClip?.getItemAt(0)
        val text = item?.text?.toString() ?: item?.coerceToText(context)?.toString() ?: ""

        return mapOf(
            "success" to true,
            "message" to "Zwischenablage gelesen",
            "text" to text,
            "length" to text.length
        )
    }

    private fun typeText(input: Map<String, Any>): Map<String, Any> {
        val text = input["text"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Text angegeben")

        // Texteingabe ueber Shell-Befehl simulieren
        return try {
            // Sonderzeichen escapen
            val escaped = text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace(" ", "%s")
            val process = Runtime.getRuntime().exec(arrayOf("input", "text", escaped))
            process.waitFor()
            if (process.exitValue() == 0) {
                mapOf(
                    "success" to true,
                    "message" to "Text eingegeben (${text.length} Zeichen)"
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "Texteingabe fehlgeschlagen (erweiterte Berechtigungen erforderlich)"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Fehler bei der Texteingabe: ${e.message}"
            )
        }
    }

    // ===================================================================
    //  8. Benachrichtigungen
    // ===================================================================

    private fun listNotifications(): Map<String, Any> {
        // Zugriff auf aktive Benachrichtigungen erfordert NotificationListenerService
        return mapOf(
            "success" to false,
            "message" to "Zum Lesen von Benachrichtigungen wird ein NotificationListenerService benoetigt. " +
                    "Bitte aktivieren Sie den AIOS-Benachrichtigungszugriff in den Einstellungen.",
            "settings_action" to "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        )
    }

    private fun dismissNotification(input: Map<String, Any>): Map<String, Any> {
        return mapOf(
            "success" to false,
            "message" to "Zum Verwalten von Benachrichtigungen wird ein NotificationListenerService benoetigt"
        )
    }

    private fun dismissAllNotifications(): Map<String, Any> {
        return mapOf(
            "success" to false,
            "message" to "Zum Verwalten von Benachrichtigungen wird ein NotificationListenerService benoetigt"
        )
    }

    private fun sendNotification(input: Map<String, Any>): Map<String, Any> {
        val title = input["title"] as? String ?: "AIOS"
        val body = input["body"] as? String ?: input["message"] as? String ?: ""
        val channelId = "aios_tool_notifications"
        val notificationId = (input["id"] as? Number)?.toInt() ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Kanal erstellen (ab Android O erforderlich)
        val channel = NotificationChannel(
            channelId,
            "AIOS Benachrichtigungen",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigungen vom AIOS-System"
        }
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(notificationId, notification)

        return mapOf(
            "success" to true,
            "message" to "Benachrichtigung gesendet: '$title'",
            "notification_id" to notificationId
        )
    }

    // ===================================================================
    //  9. Dateisystem
    // ===================================================================

    private fun readFile(input: Map<String, Any>): Map<String, Any> {
        val path = input["path"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Dateipfad angegeben")
        val file = File(path)

        if (!file.exists()) {
            return mapOf("success" to false, "message" to "Datei '$path' nicht gefunden")
        }
        if (!file.canRead()) {
            return mapOf("success" to false, "message" to "Keine Leseberechtigung fuer '$path'")
        }
        if (file.isDirectory) {
            return mapOf("success" to false, "message" to "'$path' ist ein Verzeichnis, keine Datei")
        }

        // Groessenbegrenzung: 1 MB
        val maxSize = 1_048_576L
        if (file.length() > maxSize) {
            return mapOf(
                "success" to false,
                "message" to "Datei ist zu gross (${file.length()} Bytes, Maximum: $maxSize Bytes)"
            )
        }

        val content = file.readText(Charsets.UTF_8)
        return mapOf(
            "success" to true,
            "message" to "Datei '$path' gelesen (${file.length()} Bytes)",
            "content" to content,
            "size" to file.length(),
            "last_modified" to SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN).format(Date(file.lastModified()))
        )
    }

    private fun writeFile(input: Map<String, Any>): Map<String, Any> {
        val path = input["path"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Dateipfad angegeben")
        val content = input["content"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Inhalt angegeben")
        val append = input["append"] as? Boolean ?: false

        val file = File(path)

        // Sicherstellen, dass das Elternverzeichnis existiert
        file.parentFile?.mkdirs()

        return try {
            if (append) {
                file.appendText(content, Charsets.UTF_8)
            } else {
                file.writeText(content, Charsets.UTF_8)
            }
            mapOf(
                "success" to true,
                "message" to "Datei '$path' geschrieben (${content.length} Zeichen)",
                "path" to path,
                "size" to file.length()
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Fehler beim Schreiben von '$path': ${e.message}"
            )
        }
    }

    private fun listDirectory(input: Map<String, Any>): Map<String, Any> {
        val path = input["path"] as? String
            ?: Environment.getExternalStorageDirectory().absolutePath
        val file = File(path)

        if (!file.exists()) {
            return mapOf("success" to false, "message" to "Verzeichnis '$path' nicht gefunden")
        }
        if (!file.isDirectory) {
            return mapOf("success" to false, "message" to "'$path' ist kein Verzeichnis")
        }

        val entries = file.listFiles()?.map { f ->
            mapOf(
                "name" to f.name,
                "path" to f.absolutePath,
                "is_directory" to f.isDirectory,
                "size" to f.length(),
                "last_modified" to SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
                    .format(Date(f.lastModified())),
                "readable" to f.canRead(),
                "writable" to f.canWrite()
            )
        }?.sortedWith(compareByDescending<Map<String, Any>> { it["is_directory"] as Boolean }
            .thenBy { (it["name"] as String).lowercase() })
            ?: emptyList()

        return mapOf(
            "success" to true,
            "message" to "${entries.size} Eintraege in '$path'",
            "path" to path,
            "count" to entries.size,
            "entries" to entries
        )
    }

    private fun searchFiles(input: Map<String, Any>): Map<String, Any> {
        val query = input["query"] as? String ?: input["name"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Suchbegriff angegeben")
        val startPath = input["path"] as? String
            ?: Environment.getExternalStorageDirectory().absolutePath
        val maxResults = (input["max_results"] as? Number)?.toInt() ?: 50

        val results = mutableListOf<Map<String, Any>>()
        val startDir = File(startPath)

        if (!startDir.exists() || !startDir.isDirectory) {
            return mapOf("success" to false, "message" to "Startverzeichnis '$startPath' nicht gefunden")
        }

        fun searchRecursive(dir: File) {
            if (results.size >= maxResults) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (results.size >= maxResults) return
                if (f.name.lowercase().contains(query.lowercase())) {
                    results.add(mapOf(
                        "name" to f.name,
                        "path" to f.absolutePath,
                        "is_directory" to f.isDirectory,
                        "size" to f.length()
                    ))
                }
                if (f.isDirectory && f.canRead()) {
                    searchRecursive(f)
                }
            }
        }

        searchRecursive(startDir)

        return mapOf(
            "success" to true,
            "message" to "${results.size} Ergebnis(se) fuer '$query' gefunden",
            "query" to query,
            "count" to results.size,
            "results" to results
        )
    }

    private fun deleteFile(input: Map<String, Any>): Map<String, Any> {
        val path = input["path"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Dateipfad angegeben")
        val file = File(path)

        if (!file.exists()) {
            return mapOf("success" to false, "message" to "Datei '$path' nicht gefunden")
        }

        val deleted = if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }

        return if (deleted) {
            mapOf(
                "success" to true,
                "message" to "'$path' wurde geloescht"
            )
        } else {
            mapOf(
                "success" to false,
                "message" to "'$path' konnte nicht geloescht werden"
            )
        }
    }

    private fun createDirectory(input: Map<String, Any>): Map<String, Any> {
        val path = input["path"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Verzeichnispfad angegeben")
        val dir = File(path)

        if (dir.exists()) {
            return if (dir.isDirectory) {
                mapOf("success" to true, "message" to "Verzeichnis '$path' existiert bereits")
            } else {
                mapOf("success" to false, "message" to "'$path' existiert bereits als Datei")
            }
        }

        val created = dir.mkdirs()
        return if (created) {
            mapOf(
                "success" to true,
                "message" to "Verzeichnis '$path' erstellt"
            )
        } else {
            mapOf(
                "success" to false,
                "message" to "Verzeichnis '$path' konnte nicht erstellt werden"
            )
        }
    }

    // ===================================================================
    //  10. Shell / Befehlsausfuehrung
    // ===================================================================

    private fun shellExecute(input: Map<String, Any>): Map<String, Any> {
        val command = input["command"] as? String
            ?: return mapOf("success" to false, "message" to "Kein Befehl angegeben")
        val timeoutMs = (input["timeout_ms"] as? Number)?.toLong() ?: 10_000L

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()

            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

            if (!finished) {
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "message" to "Befehl nach ${timeoutMs}ms abgebrochen (Zeitlimit ueberschritten)",
                    "command" to command
                )
            }

            val exitCode = process.exitValue()
            mapOf(
                "success" to (exitCode == 0),
                "message" to if (exitCode == 0) "Befehl erfolgreich ausgefuehrt" else "Befehl mit Fehlercode $exitCode beendet",
                "exit_code" to exitCode,
                "stdout" to stdout.trim(),
                "stderr" to stderr.trim(),
                "command" to command
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Fehler bei Ausfuehrung: ${e.message}",
                "command" to command
            )
        }
    }

    private fun shellGetProp(input: Map<String, Any>): Map<String, Any> {
        val property = input["property"] as? String ?: input["key"] as? String
            ?: return mapOf("success" to false, "message" to "Keine Eigenschaft angegeben")

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", property))
            val value = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            process.waitFor()

            mapOf(
                "success" to true,
                "message" to "Systemeigenschaft '$property' abgerufen",
                "property" to property,
                "value" to value
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Fehler beim Lesen von '$property': ${e.message}"
            )
        }
    }
}
