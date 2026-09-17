package com.forexalert.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {

        private const val DERIV_WS =
            "wss://ws.binaryws.com/websockets/v3"

        private const val PREFS =
            "forex_alert_preferences"

        private const val ALERTS_KEY =
            "alerts"

        private const val HISTORY_KEY =
            "history"

        private const val SELECTED_SYMBOL_KEY =
            "selected_symbol"

        private const val NOTIFICATION_PERMISSION_CODE =
            1001
    }

    private lateinit var symbolSpinner: Spinner
    private lateinit var priceText: TextView
    private lateinit var statusText: TextView
    private lateinit var lastUpdateText: TextView
    private lateinit var conditionSpinner: Spinner
    private lateinit var targetInput: EditText
    private lateinit var alertsContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout

    private val preferences by lazy {
        getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                20,
                TimeUnit.SECONDS
            )
            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )
            .writeTimeout(
                20,
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

    private var currentPrice =
        Double.NaN

    private var isConnecting =
        false

    private var spinnerInitialized =
        false

    private val symbols =
        listOf(
            "frxEURUSD",
            "frxGBPUSD",
            "frxUSDJPY",
            "frxUSDCHF",
            "frxAUDUSD",
            "frxUSDCAD",
            "frxNZDUSD",
            "frxEURGBP",
            "frxEURJPY",
            "frxGBPJPY"
        )

    private val displaySymbols =
        listOf(
            "EUR/USD",
            "GBP/USD",
            "USD/JPY",
            "USD/CHF",
            "AUD/USD",
            "USD/CAD",
            "NZD/USD",
            "EUR/GBP",
            "EUR/JPY",
            "GBP/JPY"
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        createInterface()

        createNotificationChannel()

        requestNotificationPermission()

        currentSymbol =
            preferences.getString(
                SELECTED_SYMBOL_KEY,
                "frxEURUSD"
            ) ?: "frxEURUSD"

        setupSymbolSpinner()

        setupConditionSpinner()

        loadAlerts()

        loadHistory()

        /*
         * IMPORTANT:
         *
         * ForexAlertService is temporarily NOT
         * started here.
         *
         * We are testing the live price connection
         * independently from the background service.
         */

        connectToDeriv()
    }

    private fun createInterface() {

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            24,
            24,
            24,
            24
        )

        root.setBackgroundColor(
            android.graphics.Color.WHITE
        )

        val scrollView =
            ScrollView(this)

        val content =
            LinearLayout(this)

        content.orientation =
            LinearLayout.VERTICAL

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        scrollView.addView(
            content
        )

        val title =
            TextView(this)

        title.text =
            "Forex Alert"

        title.textSize =
            28f

        title.setTextColor(
            android.graphics.Color.BLACK
        )

        title.setPadding(
            0,
            0,
            0,
            24
        )

        content.addView(
            title
        )

        val pairLabel =
            TextView(this)

        pairLabel.text =
            "Forex Pair"

        pairLabel.textSize =
            16f

        content.addView(
            pairLabel
        )

        symbolSpinner =
            Spinner(this)

        content.addView(
            symbolSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        priceText =
            TextView(this)

        priceText.text =
            "--"

        priceText.textSize =
            34f

        priceText.setTextColor(
            android.graphics.Color.BLACK
        )

        priceText.setPadding(
            0,
            28,
            0,
            4
        )

        content.addView(
            priceText
        )

        statusText =
            TextView(this)

        statusText.text =
            "Connecting..."

        statusText.textSize =
            16f

        content.addView(
            statusText
        )

        lastUpdateText =
            TextView(this)

        lastUpdateText.text =
            "Last update: --"

        lastUpdateText.textSize =
            14f

        lastUpdateText.setPadding(
            0,
            4,
            0,
            24
        )

        content.addView(
            lastUpdateText
        )

        val alertTitle =
            TextView(this)

        alertTitle.text =
            "Create Price Alert"

        alertTitle.textSize =
            20f

        alertTitle.setTextColor(
            android.graphics.Color.BLACK
        )

        alertTitle.setPadding(
            0,
            8,
            0,
            12
        )

        content.addView(
            alertTitle
        )

        conditionSpinner =
            Spinner(this)

        content.addView(
            conditionSpinner
        )

        targetInput =
            EditText(this)

        targetInput.hint =
            "Target price"

        targetInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        targetInput.setSingleLine(
            true
        )

        content.addView(
            targetInput
        )

        val addButton =
            Button(this)

        addButton.text =
            "ADD ALERT"

        content.addView(
            addButton
        )

        addButton.setOnClickListener {
            addAlert()
        }

        val myAlertsTitle =
            TextView(this)

        myAlertsTitle.text =
            "My Alerts"

        myAlertsTitle.textSize =
            20f

        myAlertsTitle.setTextColor(
            android.graphics.Color.BLACK
        )

        myAlertsTitle.setPadding(
            0,
            28,
            0,
            12
        )

        content.addView(
            myAlertsTitle
        )

        alertsContainer =
            LinearLayout(this)

        alertsContainer.orientation =
            LinearLayout.VERTICAL

        content.addView(
            alertsContainer
        )

        val historyTitle =
            TextView(this)

        historyTitle.text =
            "Alert History"

        historyTitle.textSize =
            20f

        historyTitle.setTextColor(
            android.graphics.Color.BLACK
        )

        historyTitle.setPadding(
            0,
            28,
            0,
            12
        )

        content.addView(
            historyTitle
        )

        historyContainer =
            LinearLayout(this)

        historyContainer.orientation =
            LinearLayout.VERTICAL

        content.addView(
            historyContainer
        )

        setContentView(root)
    }

    private fun setupSymbolSpinner() {

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                displaySymbols
            )

        symbolSpinner.adapter =
            adapter

        val savedIndex =
            symbols.indexOf(
                currentSymbol
            )

        if (savedIndex >= 0) {

            symbolSpinner.setSelection(
                savedIndex,
                false
            )
        }

        symbolSpinner.onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (!spinnerInitialized) {

                        spinnerInitialized =
                            true

                        return
                    }

                    if (
                        position < 0 ||
                        position >= symbols.size
                    ) {
                        return
                    }

                    val newSymbol =
                        symbols[position]

                    if (
                        newSymbol ==
                        currentSymbol
                    ) {
                        return
                    }

                    currentSymbol =
                        newSymbol

                    preferences.edit()
                        .putString(
                            SELECTED_SYMBOL_KEY,
                            currentSymbol
                        )
                        .apply()

                    reconnectToDeriv()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
    }

    private fun setupConditionSpinner() {

        val conditions =
            listOf(
                "Above",
                "Below",
                "Touch"
            )

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                conditions
            )

        conditionSpinner.adapter =
            adapter
    }

    private fun connectToDeriv() {

        if (isFinishing) {
            return
        }

        if (isConnecting) {
            return
        }

        isConnecting =
            true

        runOnUiThread {

            statusText.text =
                "Connecting to Deriv..."

            statusText.setTextColor(
                android.graphics.Color.DKGRAY
            )
        }

        val request =
            Request.Builder()
                .url(DERIV_WS)
                .header(
                    "User-Agent",
                    "ForexAlert/1.0 Android"
                )
                .build()

        webSocket =
            client.newWebSocket(
                request,
                object :
                    WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        isConnecting =
                            false

                        runOnUiThread {

                            statusText.text =
                                "Connected - requesting price"

                            statusText.setTextColor(
                                android.graphics.Color.rgb(
                                    0,
                                    120,
                                    0
                                )
                            )
                        }

                        subscribeToTicks(
                            webSocket
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        processTickMessage(
                            text
                        )
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        isConnecting =
                            false

                        val errorMessage =
                            t.message
                                ?: t.javaClass.simpleName

                        val responseInfo =
                            if (
                                response != null
                            ) {
                                " HTTP ${response.code}"
                            } else {
                                ""
                            }

                        runOnUiThread {

                            statusText.text =
                                "WebSocket error: $errorMessage$responseInfo"

                            statusText.setTextColor(
                                android.graphics.Color.RED
                            )

                            lastUpdateText.text =
                                "Endpoint: $DERIV_WS"
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        isConnecting =
                            false

                        runOnUiThread {

                            statusText.text =
                                "Disconnected: $code $reason"

                            statusText.setTextColor(
                                android.graphics.Color.RED
                            )
                        }
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

        request.put(
            "req_id",
            1
        )

        val sent =
            socket.send(
                request.toString()
            )

        if (!sent) {

            runOnUiThread {

                statusText.text =
                    "Failed to send tick request"

                statusText.setTextColor(
                    android.graphics.Color.RED
                )
            }
        }
    }

    private fun processTickMessage(
        message: String
    ) {

        try {

            val json =
                JSONObject(message)

            if (
                json.has("error")
            ) {

                val error =
                    json.optJSONObject(
                        "error"
                    )

                val code =
                    error?.optString(
                        "code"
                    )

                val messageText =
                    error?.optString(
                        "message"
                    )

                runOnUiThread {

                    statusText.text =
                        "Deriv API error: $code $messageText"

                    statusText.setTextColor(
                        android.graphics.Color.RED
                    )
                }

                return
            }

            val messageType =
                json.optString(
                    "msg_type"
                )

            if (
                messageType != "tick"
            ) {

                runOnUiThread {

                    statusText.text =
                        "Connected: received $messageType"
                }

                return
            }

            val tick =
                json.optJSONObject(
                    "tick"
                )

            if (tick == null) {

                runOnUiThread {

                    statusText.text =
                        "Tick response has no data"

                    statusText.setTextColor(
                        android.graphics.Color.RED
                    )
                }

                return
            }

            val symbol =
                tick.optString(
                    "symbol"
                )

            val price =
                tick.optDouble(
                    "quote",
                    Double.NaN
                )

            if (
                symbol != currentSymbol
            ) {
                return
            }

            if (
                !price.isFinite()
            ) {

                runOnUiThread {

                    statusText.text =
                        "Invalid price received"

                    statusText.setTextColor(
                        android.graphics.Color.RED
                    )
                }

                return
            }

            currentPrice =
                price

            val epoch =
                tick.optLong(
                    "epoch",
                    System.currentTimeMillis() / 1000
                )

            runOnUiThread {

                displayPrice(
                    price,
                    epoch
                )
            }

        } catch (
            e: Exception
        ) {

            runOnUiThread {

                statusText.text =
                    "Data error: ${e.message}"

                statusText.setTextColor(
                    android.graphics.Color.RED
                )
            }
        }
    }

    private fun displayPrice(
        price: Double,
        epoch: Long
    ) {

        val decimals =
            getDecimalPlaces(
                price
            )

        priceText.text =
            String.format(
                Locale.US,
                "%.${decimals}f",
                price
            )

        statusText.text =
            "Live"

        statusText.setTextColor(
            android.graphics.Color.rgb(
                0,
                150,
                0
            )
        )

        lastUpdateText.text =
            "Last update: $epoch"
    }

    private fun getDecimalPlaces(
        price: Double
    ): Int {

        return when {

            price >= 1000 ->
                2

            price >= 100 ->
                3

            price >= 10 ->
                3

            else ->
                5
        }
    }

    private fun addAlert() {

        val targetText =
            targetInput.text
                .toString()
                .trim()

        if (
            targetText.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Enter a target price",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val target =
            targetText.toDoubleOrNull()

        if (
            target == null ||
            !target.isFinite()
        ) {

            Toast.makeText(
                this,
                "Enter a valid price",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val condition =
            conditionSpinner.selectedItem
                ?.toString()
                ?: "Above"

        val alert =
            JSONObject()

        alert.put(
            "id",
            System.currentTimeMillis()
        )

        alert.put(
            "symbol",
            currentSymbol
        )

        alert.put(
            "displaySymbol",
            getDisplaySymbol(
                currentSymbol
            )
        )

        alert.put(
            "condition",
            condition
        )

        alert.put(
            "target",
            target
        )

        alert.put(
            "enabled",
            true
        )

        alert.put(
            "triggered",
            false
        )

        alert.put(
            "triggeredPrice",
            JSONObject.NULL
        )

        val alerts =
            getAlerts()

        alerts.put(
            alert
        )

        saveAlerts(
            alerts
        )

        targetInput.text.clear()

        loadAlerts()

        Toast.makeText(
            this,
            "Alert added",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getAlerts():
        JSONArray {

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

            JSONArray(
                saved
            )

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

    private fun loadAlerts() {

        if (
            !::alertsContainer.isInitialized
        ) {
            return
        }

        alertsContainer.removeAllViews()

        val alerts =
            getAlerts()

        if (
            alerts.length() == 0
        ) {

            val empty =
                TextView(this)

            empty.text =
                "No alerts"

            empty.textSize =
                15f

            alertsContainer.addView(
                empty
            )

            return
        }

        for (
            i in 0 until alerts.length()
        ) {

            val alert =
                alerts.optJSONObject(
                    i
                )
                    ?: continue

            addAlertView(
                alert
            )
        }
    }

    private fun addAlertView(
        alert: JSONObject
    ) {

        val row =
            LinearLayout(this)

        row.orientation =
            LinearLayout.VERTICAL

        row.setPadding(
            12,
            12,
            12,
            12
        )

        val symbol =
            alert.optString(
                "displaySymbol",
                getDisplaySymbol(
                    alert.optString(
                        "symbol"
                    )
                )
            )

        val condition =
            alert.optString(
                "condition"
            )

        val target =
            alert.optDouble(
                "target",
                Double.NaN
            )

        val enabled =
            alert.optBoolean(
                "enabled",
                false
            )

        val triggered =
            alert.optBoolean(
                "triggered",
                false
            )

        val text =
            TextView(this)

        text.text =
            if (
                target.isFinite()
            ) {

                "$symbol  $condition  $target"

            } else {

                "$symbol  $condition"
            }

        text.textSize =
            16f

        row.addView(
            text
        )

        val buttonRow =
            LinearLayout(this)

        buttonRow.orientation =
            LinearLayout.HORIZONTAL

        val toggle =
            Button(this)

        toggle.text =
            if (enabled) {
                "Disable"
            } else {
                "Enable"
            }

        buttonRow.addView(
            toggle
        )

        val delete =
            Button(this)

        delete.text =
            "Delete"

        buttonRow.addView(
            delete
        )

        row.addView(
            buttonRow
        )

        if (triggered) {

            val triggeredText =
                TextView(this)

            val triggeredPrice =
                if (
                    alert.has(
                        "triggeredPrice"
                    ) &&
                    !alert.isNull(
                        "triggeredPrice"
                    )
                ) {

                    alert.optDouble(
                        "triggeredPrice",
                        Double.NaN
                    )

                } else {

                    Double.NaN
                }

            triggeredText.text =
                if (
                    triggeredPrice.isFinite()
                ) {

                    "Triggered at $triggeredPrice"

                } else {

                    "Triggered"
                }

            triggeredText.textSize =
                14f

            row.addView(
                triggeredText
            )
        }

        toggle.setOnClickListener {

            toggleAlert(
                alert.optLong(
                    "id"
                )
            )
        }

        delete.setOnClickListener {

            deleteAlert(
                alert.optLong(
                    "id"
                )
            )
        }

        alertsContainer.addView(
            row
        )
    }

    private fun toggleAlert(
        id: Long
    ) {

        val alerts =
            getAlerts()

        for (
            i in 0 until alerts.length()
        ) {

            val alert =
                alerts.optJSONObject(
                    i
                )
                    ?: continue

            if (
                alert.optLong(
                    "id"
                ) == id
            ) {

                val enabled =
                    alert.optBoolean(
                        "enabled",
                        false
                    )

                alert.put(
                    "enabled",
                    !enabled
                )

                break
            }
        }

        saveAlerts(
            alerts
        )

        loadAlerts()
    }

    private fun deleteAlert(
        id: Long
    ) {

        val oldAlerts =
            getAlerts()

        val newAlerts =
            JSONArray()

        for (
            i in 0 until oldAlerts.length()
        ) {

            val alert =
                oldAlerts.optJSONObject(
                    i
                )
                    ?: continue

            if (
                alert.optLong(
                    "id"
                ) != id
            ) {

                newAlerts.put(
                    alert
                )
            }
        }

        saveAlerts(
            newAlerts
        )

        loadAlerts()

        Toast.makeText(
            this,
            "Alert deleted",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun loadHistory() {

        if (
            !::historyContainer.isInitialized
        ) {
            return
        }

        historyContainer.removeAllViews()

        val saved =
            preferences.getString(
                HISTORY_KEY,
                null
            )

        if (
            saved.isNullOrEmpty()
        ) {

            val empty =
                TextView(this)

            empty.text =
                "No alert history"

            empty.textSize =
                15f

            historyContainer.addView(
                empty
            )

            return
        }

        val history =
            try {

                JSONArray(
                    saved
                )

            } catch (
                _: Exception
            ) {

                JSONArray()
            }

        if (
            history.length() == 0
        ) {

            val empty =
                TextView(this)

            empty.text =
                "No alert history"

            historyContainer.addView(
                empty
            )

            return
        }

        for (
            i in history.length() - 1 downTo 0
        ) {

            val item =
                history.optJSONObject(
                    i
                )
                    ?: continue

            val text =
                TextView(this)

            val symbol =
                item.optString(
                    "displaySymbol",
                    getDisplaySymbol(
                        item.optString(
                            "symbol"
                        )
                    )
                )

            val condition =
                item.optString(
                    "condition"
                )

            val target =
                item.optDouble(
                    "target",
                    Double.NaN
                )

            val triggeredPrice =
                item.optDouble(
                    "triggeredPrice",
                    Double.NaN
                )

            text.text =
                if (
                    target.isFinite() &&
                    triggeredPrice.isFinite()
                ) {

                    "$symbol  $condition  Target: $target  Triggered: $triggeredPrice"

                } else {

                    "$symbol  $condition"
                }

            text.textSize =
                14f

            text.setPadding(
                0,
                8,
                0,
                8
            )

            historyContainer.addView(
                text
            )
        }
    }

    private fun reconnectToDeriv() {

        webSocket?.close(
            1000,
            "Changing symbol"
        )

        webSocket = null

        isConnecting =
            false

        currentPrice =
            Double.NaN

        priceText.text =
            "--"

        statusText.text =
            "Connecting..."

        lastUpdateText.text =
            "Last update: --"

        connectToDeriv()
    }

    private fun getDisplaySymbol(
        symbol: String
    ): String {

        val index =
            symbols.indexOf(
                symbol
            )

        return if (
            index >= 0
        ) {

            displaySymbols[index]

        } else {

            symbol
        }
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    "forex_alerts",
                    "Forex Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )

            channel.description =
                "Price alert notifications"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

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
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    override fun onResume() {

        super.onResume()

        loadAlerts()

        loadHistory()

        if (
            webSocket == null &&
            !isConnecting
        ) {

            connectToDeriv()
        }
    }

    override fun onDestroy() {

        webSocket?.close(
            1000,
            "Activity destroyed"
        )

        webSocket = null

        super.onDestroy()
    }
}