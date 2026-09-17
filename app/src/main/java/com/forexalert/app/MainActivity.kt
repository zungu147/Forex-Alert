package com.forexalert.app

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var priceText: TextView
    private lateinit var statusText: TextView
    private lateinit var alertContainer: LinearLayout
    private lateinit var symbolSpinner: Spinner
    private lateinit var targetInput: EditText
    private lateinit var conditionSpinner: Spinner

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private var currentPrice = Double.NaN
    private var currentSymbol = "frxEURUSD"

    private val symbols = linkedMapOf(
        "EUR/USD" to "frxEURUSD",
        "GBP/USD" to "frxGBPUSD",
        "USD/JPY" to "frxUSDJPY",
        "USD/CHF" to "frxUSDCHF",
        "AUD/USD" to "frxAUDUSD",
        "USD/CAD" to "frxUSDCAD",
        "NZD/USD" to "frxNZDUSD"
    )

    private val preferences by lazy {
        getSharedPreferences(
            "forex_alert_preferences",
            Context.MODE_PRIVATE
        )
    }

    private val alerts = mutableListOf<PriceAlert>()

    private val notificationChannelId = "forex_alerts"

    data class PriceAlert(
        val id: Long,
        val symbol: String,
        val condition: String,
        val target: Double,
        var enabled: Boolean,
        var triggered: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        loadAlerts()
        createInterface()
        requestNotificationPermission()

        connectToDeriv()
    }

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 24, 24, 24)
        root.setBackgroundColor(Color.WHITE)

        val scrollView = ScrollView(this)

        val content = LinearLayout(this)

        content.orientation = LinearLayout.VERTICAL

        /*
         * Title
         */

        val title = TextView(this)

        title.text = "FOREX ALERT"
        title.textSize = 28f
        title.setTextColor(Color.BLACK)
        title.gravity = Gravity.CENTER

        content.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        /*
         * Symbol selector
         */

        val symbolLabel = TextView(this)

        symbolLabel.text = "Forex Pair"
        symbolLabel.textSize = 16f
        symbolLabel.setTextColor(Color.DKGRAY)

        content.addView(
            symbolLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 25
            }
        )

        symbolSpinner = Spinner(this)

        val symbolNames = symbols.keys.toList()

        symbolSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            symbolNames
        )

        symbolSpinner.setSelection(0)

        symbolSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    val selectedName =
                        symbolNames[position]

                    currentSymbol =
                        symbols[selectedName]
                            ?: "frxEURUSD"

                    currentPrice = Double.NaN

                    priceText.text = "--"

                    reconnectToDeriv()
                }
            }

        content.addView(
            symbolSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        /*
         * Current price
         */

        priceText = TextView(this)

        priceText.text = "--"
        priceText.textSize = 42f
        priceText.setTextColor(Color.BLACK)
        priceText.gravity = Gravity.CENTER

        content.addView(
            priceText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
            }
        )

        /*
         * Connection status
         */

        statusText = TextView(this)

        statusText.text = "Connecting..."
        statusText.textSize = 17f
        statusText.setTextColor(Color.GRAY)
        statusText.gravity = Gravity.CENTER

        content.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        /*
         * Alert section title
         */

        val alertTitle = TextView(this)

        alertTitle.text = "Create Price Alert"
        alertTitle.textSize = 21f
        alertTitle.setTextColor(Color.BLACK)

        content.addView(
            alertTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 30
            }
        )

        /*
         * Condition
         */

        val conditionLabel = TextView(this)

        conditionLabel.text = "Condition"
        conditionLabel.textSize = 15f
        conditionLabel.setTextColor(Color.DKGRAY)

        content.addView(
            conditionLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
            }
        )

        conditionSpinner = Spinner(this)

        val conditions = listOf(
            "Above",
            "Below",
            "Touch"
        )

        conditionSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            conditions
        )

        content.addView(
            conditionSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        /*
         * Target price
         */

        targetInput = EditText(this)

        targetInput.hint = "Target price"
        targetInput.textSize = 18f
        targetInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        content.addView(
            targetInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
            }
        )

        /*
         * Add alert button
         */

        val addButton = Button(this)

        addButton.text = "ADD ALERT"

        addButton.setOnClickListener {

            addAlert()
        }

        content.addView(
            addButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
            }
        )

        /*
         * Alert list title
         */

        val alertsTitle = TextView(this)

        alertsTitle.text = "My Alerts"
        alertsTitle.textSize = 21f
        alertsTitle.setTextColor(Color.BLACK)

        content.addView(
            alertsTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 30
            }
        )

        /*
         * Alert container
         */

        alertContainer = LinearLayout(this)

        alertContainer.orientation =
            LinearLayout.VERTICAL

        content.addView(
            alertContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        scrollView.addView(content)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        refreshAlertList()
    }

    private fun addAlert() {

        val targetText =
            targetInput.text.toString().trim()

        if (targetText.isEmpty()) {

            Toast.makeText(
                this,
                "Enter a target price",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val target =
            targetText.toDoubleOrNull()

        if (target == null || target <= 0) {

            Toast.makeText(
                this,
                "Enter a valid price",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val condition =
            conditionSpinner.selectedItem
                .toString()

        val displaySymbol =
            symbolSpinner.selectedItem
                .toString()

        val alert = PriceAlert(
            id = System.currentTimeMillis(),
            symbol = currentSymbol,
            condition = condition,
            target = target,
            enabled = true,
            triggered = false
        )

        alerts.add(alert)

        saveAlerts()
        refreshAlertList()

        targetInput.text.clear()

        Toast.makeText(
            this,
            "Alert added",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun refreshAlertList() {

        if (!::alertContainer.isInitialized) {
            return
        }

        alertContainer.removeAllViews()

        val currentAlerts =
            alerts.filter {
                it.symbol == currentSymbol
            }

        if (currentAlerts.isEmpty()) {

            val emptyText = TextView(this)

            emptyText.text =
                "No alerts for this pair"

            emptyText.textSize = 16f
            emptyText.setTextColor(Color.GRAY)

            alertContainer.addView(
                emptyText
            )

            return
        }

        for (alert in currentAlerts) {

            val row = LinearLayout(this)

            row.orientation =
                LinearLayout.VERTICAL

            row.setPadding(
                12,
                12,
                12,
                12
            )

            val description =
                TextView(this)

            description.text =
                "${alert.condition} ${formatPrice(alert.target)}"

            description.textSize = 18f
            description.setTextColor(Color.BLACK)

            row.addView(description)

            val status =
                TextView(this)

            status.text =
                when {
                    alert.triggered ->
                        "Triggered"

                    alert.enabled ->
                        "Enabled"

                    else ->
                        "Disabled"
                }

            status.textSize = 14f

            status.setTextColor(
                if (alert.enabled)
                    Color.rgb(0, 140, 0)
                else
                    Color.GRAY
            )

            row.addView(status)

            val buttons =
                LinearLayout(this)

            buttons.orientation =
                LinearLayout.HORIZONTAL

            val toggleButton =
                Button(this)

            toggleButton.text =
                if (alert.enabled)
                    "Disable"
                else
                    "Enable"

            toggleButton.setOnClickListener {

                alert.enabled =
                    !alert.enabled

                saveAlerts()
                refreshAlertList()
            }

            buttons.addView(
                toggleButton,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val deleteButton =
                Button(this)

            deleteButton.text = "Delete"

            deleteButton.setOnClickListener {

                alerts.removeAll {
                    it.id == alert.id
                }

                saveAlerts()
                refreshAlertList()
            }

            buttons.addView(
                deleteButton,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            row.addView(buttons)

            alertContainer.addView(row)
        }
    }

    private fun checkAlerts(price: Double) {

        var changed = false

        for (alert in alerts) {

            if (!alert.enabled) {
                continue
            }

            if (alert.triggered) {
                continue
            }

            if (alert.symbol != currentSymbol) {
                continue
            }

            val shouldTrigger =
                when (alert.condition) {

                    "Above" ->
                        price >= alert.target

                    "Below" ->
                        price <= alert.target

                    "Touch" ->
                        abs(price - alert.target) <=
                            touchTolerance()

                    else ->
                        false
                }

            if (shouldTrigger) {

                alert.triggered = true
                changed = true

                showAlertNotification(alert, price)

                runOnUiThread {

                    Toast.makeText(
                        this,
                        "${displaySymbol(alert.symbol)} ${alert.condition} ${formatPrice(alert.target)}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        if (changed) {

            saveAlerts()

            runOnUiThread {
                refreshAlertList()
            }
        }
    }

    private fun touchTolerance(): Double {

        return when {

            currentSymbol.contains("JPY") ->
                0.005

            else ->
                0.00005
        }
    }

    private fun showAlertNotification(
        alert: PriceAlert,
        price: Double
    ) {

        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notificationManager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val intent =
            Intent(this, MainActivity::class.java)

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            android.app.Notification.Builder(
                this,
                notificationChannelId
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    "Forex Alert"
                )
                .setContentText(
                    "${displaySymbol(alert.symbol)} ${alert.condition} ${formatPrice(alert.target)} — ${formatPrice(price)}"
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(true)
                .setPriority(
                    android.app.Notification.PRIORITY_HIGH
                )
                .build()

        notificationManager.notify(
            alert.id.toInt(),
            notification
        )
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    notificationChannelId,
                    "Forex Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )

            channel.description =
                "Price alert notifications"

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    100
                )
            }
        }
    }

    private fun connectToDeriv() {

        runOnUiThread {

            statusText.text =
                "Connecting..."

            statusText.setTextColor(
                Color.GRAY
            )
        }

        val request =
            Request.Builder()
                .url(
                    "wss://api.derivws.com/trading/v1/options/ws/public"
                )
                .build()

        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        runOnUiThread {

                            statusText.text =
                                "Live"

                            statusText.setTextColor(
                                Color.rgb(
                                    0,
                                    150,
                                    0
                                )
                            )
                        }

                        subscribeToSymbol(
                            webSocket
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        processMessage(text)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        runOnUiThread {

                            statusText.text =
                                "Disconnected"

                            statusText.setTextColor(
                                Color.RED
                            )
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        runOnUiThread {

                            statusText.text =
                                "Connection failed"

                            statusText.setTextColor(
                                Color.RED
                            )
                        }
                    }
                }
            )
    }

    private fun subscribeToSymbol(
        webSocket: WebSocket
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

        webSocket.send(
            request.toString()
        )
    }

    private fun reconnectToDeriv() {

        webSocket?.close(
            1000,
            "Changing symbol"
        )

        connectToDeriv()

        refreshAlertList()
    }

    private fun processMessage(
        message: String
    ) {

        try {

            val json =
                JSONObject(message)

            if (json.has("error")) {
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

            if (symbol != currentSymbol) {
                return
            }

            val quote =
                tick.optDouble(
                    "quote",
                    Double.NaN
                )

            if (quote.isNaN()) {
                return
            }

            currentPrice = quote

            runOnUiThread {

                priceText.text =
                    formatPrice(quote)

                statusText.text =
                    "Live"

                statusText.setTextColor(
                    Color.rgb(
                        0,
                        150,
                        0
                    )
                )
            }

            checkAlerts(quote)

        } catch (_: Exception) {
        }
    }

    private fun formatPrice(
        price: Double
    ): String {

        return if (
            currentSymbol.contains("JPY")
        ) {

            String.format(
                Locale.US,
                "%.3f",
                price
            )

        } else {

            String.format(
                Locale.US,
                "%.5f",
                price
            )
        }
    }

    private fun displaySymbol(
        symbol: String
    ): String {

        return symbols.entries
            .firstOrNull {
                it.value == symbol
            }
            ?.key
            ?: symbol
    }

    private fun saveAlerts() {

        val array =
            JSONArray()

        for (alert in alerts) {

            val objectItem =
                JSONObject()

            objectItem.put(
                "id",
                alert.id
            )

            objectItem.put(
                "symbol",
                alert.symbol
            )

            objectItem.put(
                "condition",
                alert.condition
            )

            objectItem.put(
                "target",
                alert.target
            )

            objectItem.put(
                "enabled",
                alert.enabled
            )

            objectItem.put(
                "triggered",
                alert.triggered
            )

            array.put(
                objectItem
            )
        }

        preferences.edit()
            .putString(
                "alerts",
                array.toString()
            )
            .apply()
    }

    private fun loadAlerts() {

        val saved =
            preferences.getString(
                "alerts",
                null
            ) ?: return

        try {

            val array =
                JSONArray(saved)

            for (i in 0 until array.length()) {

                val objectItem =
                    array.getJSONObject(i)

                alerts.add(
                    PriceAlert(
                        id =
                            objectItem.getLong("id"),

                        symbol =
                            objectItem.getString(
                                "symbol"
                            ),

                        condition =
                            objectItem.getString(
                                "condition"
                            ),

                        target =
                            objectItem.getDouble(
                                "target"
                            ),

                        enabled =
                            objectItem.getBoolean(
                                "enabled"
                            ),

                        triggered =
                            objectItem.getBoolean(
                                "triggered"
                            )
                    )
                )
            }

        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {

        webSocket?.close(
            1000,
            "App closed"
        )

        client.dispatcher
            .executorService
            .shutdown()

        super.onDestroy()
    }
}