package com.forexalert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ForexAlertService : Service() {

    companion object {

        const val ACTION_SYMBOL_CHANGED =
            "com.forexalert.app.ACTION_SYMBOL_CHANGED"

        const val EXTRA_SYMBOL =
            "symbol"

        private const val DERIV_WS =
            "wss://ws.binaryws.com/websockets/v3"

        private const val PREFS =
            "forex_alert_preferences"

        private const val ALERTS_KEY =
            "alerts"

        private const val HISTORY_KEY =
            "history"

        private const val CHANNEL_ID =
            "forex_alert_service"

        private const val ALERT_CHANNEL_ID =
            "forex_alerts"

        private const val SERVICE_NOTIFICATION_ID =
            100

        private const val ALERT_NOTIFICATION_ID =
            200
    }

    private val preferences by lazy {
        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )
            .writeTimeout(
                15,
                TimeUnit.SECONDS
            )
            .pingInterval(
                30,
                TimeUnit.SECONDS
            )
            .build()

    private var webSocket: WebSocket? = null

    private var currentSymbol =
        "frxEURUSD"

    private var reconnecting = false

    private var serviceRunning = false

    override fun onCreate() {
        super.onCreate()

        serviceRunning = true

        createNotificationChannels()

        startForeground(
            SERVICE_NOTIFICATION_ID,
            createServiceNotification()
        )

        connect()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action ==
            ACTION_SYMBOL_CHANGED
        ) {

            val symbol =
                intent.getStringExtra(
                    EXTRA_SYMBOL
                )

            if (
                !symbol.isNullOrEmpty()
            ) {
                currentSymbol = symbol
            }

            reconnect()
        }

        return START_STICKY
    }

    private fun connect() {

        if (!serviceRunning) {
            return
        }

        if (reconnecting) {
            return
        }

        reconnecting = true

        webSocket?.close(
            1000,
            "Reconnecting"
        )

        webSocket = null

        val request =
            Request.Builder()
                .url(DERIV_WS)
                .build()

        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        reconnecting = false

                        subscribeToTicks(
                            webSocket
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        processMessage(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        reconnecting = false

                        scheduleReconnect()
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        reconnecting = false

                        scheduleReconnect()
                    }
                }
            )
    }

    private fun subscribeToTicks(
        socket: WebSocket
    ) {

        val request =
            JSONObject()

        request.put(
            "ticks",
            currentSymbol
        )

        request.put(
            "subscribe",
            1
        )

        socket.send(
            request.toString()
        )
    }

    private fun processMessage(
        message: String
    ) {

        try {

            val json =
                JSONObject(message)

            if (
                json.has("error")
            ) {
                return
            }

            if (
                json.optString(
                    "msg_type"
                ) != "tick"
            ) {
                return
            }

            val tick =
                json.optJSONObject(
                    "tick"
                ) ?: return

            val symbol =
                tick.optString(
                    "symbol"
                )

            if (
                symbol != currentSymbol
            ) {
                return
            }

            val price =
                tick.optDouble(
                    "quote",
                    Double.NaN
                )

            if (
                !price.isFinite()
            ) {
                return
            }

            checkAlerts(
                symbol,
                price
            )

        } catch (
            _: Exception
        ) {
        }
    }

    private fun checkAlerts(
        symbol: String,
        price: Double
    ) {

        val alerts =
            getAlerts()

        var changed = false

        for (
            i in 0 until alerts.length()
        ) {

            val alert =
                alerts.optJSONObject(i)
                    ?: continue

            val alertSymbol =
                alert.optString(
                    "symbol"
                )

            if (
                alertSymbol != symbol
            ) {
                continue
            }

            val enabled =
                alert.optBoolean(
                    "enabled",
                    false
                )

            if (!enabled) {
                continue
            }

            val triggered =
                alert.optBoolean(
                    "triggered",
                    false
                )

            if (triggered) {
                continue
            }

            val target =
                alert.optDouble(
                    "target",
                    Double.NaN
                )

            if (
                !target.isFinite()
            ) {
                continue
            }

            val condition =
                alert.optString(
                    "condition",
                    "Above"
                )

            val shouldTrigger =
                when (condition) {

                    "Above" ->
                        price >= target

                    "Below" ->
                        price <= target

                    "Touch" ->
                        isTouching(
                            price,
                            target
                        )

                    else ->
                        false
                }

            if (!shouldTrigger) {
                continue
            }

            alert.put(
                "triggered",
                true
            )

            alert.put(
                "enabled",
                false
            )

            alert.put(
                "triggeredPrice",
                price
            )

            changed = true

            addHistory(
                alert,
                price
            )

            sendAlertNotification(
                alert,
                price
            )
        }

        if (changed) {
            saveAlerts(alerts)
        }
    }

    private fun isTouching(
        price: Double,
        target: Double
    ): Boolean {

        val difference =
            kotlin.math.abs(
                price - target
            )

        return difference <=
            getTouchTolerance(target)
    }

    private fun getTouchTolerance(
        price: Double
    ): Double {

        return when {

            price >= 1000 ->
                0.01

            price >= 100 ->
                0.001

            price >= 10 ->
                0.001

            else ->
                0.00001
        }
    }

    private fun getAlerts(): JSONArray {

        val saved =
            preferences.getString(
                ALERTS_KEY,
                null
            )

        if (
            saved.isNullOrEmpty()
        ) {
            return JSONArray()
        }

        return try {

            JSONArray(saved)

        } catch (
            _: Exception
        ) {

            JSONArray()
        }
    }

    private fun saveAlerts(
        alerts: JSONArray
    ) {

        preferences.edit()
            .putString(
                ALERTS_KEY,
                alerts.toString()
            )
            .apply()
    }

    private fun addHistory(
        alert: JSONObject,
        price: Double
    ) {

        val saved =
            preferences.getString(
                HISTORY_KEY,
                null
            )

        val history =
            if (
                saved.isNullOrEmpty()
            ) {
                JSONArray()
            } else {
                try {
                    JSONArray(saved)
                } catch (
                    _: Exception
                ) {
                    JSONArray()
                }
            }

        val item =
            JSONObject()

        item.put(
            "id",
            alert.optLong("id")
        )

        item.put(
            "symbol",
            alert.optString("symbol")
        )

        item.put(
            "displaySymbol",
            alert.optString(
                "displaySymbol"
            )
        )

        item.put(
            "condition",
            alert.optString(
                "condition"
            )
        )

        item.put(
            "target",
            alert.optDouble(
                "target"
            )
        )

        item.put(
            "triggeredPrice",
            price
        )

        item.put(
            "timestamp",
            System.currentTimeMillis()
        )

        history.put(item)

        while (
            history.length() > 100
        ) {

            history.remove(0)
        }

        preferences.edit()
            .putString(
                HISTORY_KEY,
                history.toString()
            )
            .apply()
    }

    private fun sendAlertNotification(
        alert: JSONObject,
        price: Double
    ) {

        val notificationManager =
            getSystemService(
                NotificationManager::class.java
            )

        val displaySymbol =
            alert.optString(
                "displaySymbol",
                alert.optString(
                    "symbol"
                )
            )

        val condition =
            alert.optString(
                "condition"
            )

        val target =
            alert.optDouble(
                "target",
                price
            )

        val notification =
            NotificationCompat.Builder(
                this,
                ALERT_CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    "Forex Alert"
                )
                .setContentText(
                    "$displaySymbol $condition $target — Price: $price"
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "$displaySymbol reached your $condition alert.\n\n" +
                                "Target: $target\n" +
                                "Current price: $price"
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .build()

        notificationManager.notify(
            ALERT_NOTIFICATION_ID +
                (alert.optLong("id") % 100000).toInt(),
            notification
        )
    }

    private fun createNotificationChannels() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val serviceChannel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Forex Alert Service",
                    NotificationManager.IMPORTANCE_LOW
                )

            serviceChannel.description =
                "Keeps Forex price alerts active"

            val alertChannel =
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Forex Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )

            alertChannel.description =
                "Price alert notifications"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                serviceChannel
            )

            manager.createNotificationChannel(
                alertChannel
            )
        }
    }

    private fun createServiceNotification():
        Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_dialog_info
            )
            .setContentTitle(
                "Forex Alert"
            )
            .setContentText(
                "Monitoring Forex price alerts"
            )
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .setOngoing(true)
            .build()
    }

    private fun reconnect() {

        webSocket?.close(
            1000,
            "Symbol changed"
        )

        webSocket = null

        reconnecting = false

        connect()
    }

    private fun scheduleReconnect() {

        if (!serviceRunning) {
            return
        }

        android.os.Handler(
            mainLooper
        ).postDelayed(
            {

                if (serviceRunning) {
                    connect()
                }

            },
            5000
        )
    }

    override fun onDestroy() {

        serviceRunning = false

        webSocket?.close(
            1000,
            "Service stopped"
        )

        webSocket = null

        client.dispatcher.executorService.shutdown()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}