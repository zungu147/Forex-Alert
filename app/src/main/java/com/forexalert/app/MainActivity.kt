package com.forexalert.app

import android.Manifest
import android.app.Activity
import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class MainActivity : Activity() {

    private lateinit var priceText: TextView
    private lateinit var statusText: TextView
    private lateinit var alertContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var symbolSpinner: Spinner
    private lateinit var targetInput: EditText
    private lateinit var conditionSpinner: Spinner

    private val preferences by lazy {
        getSharedPreferences(
            "forex_alert_preferences",
            Context.MODE_PRIVATE
        )
    }

    private val alerts =
        mutableListOf<PriceAlert>()

    private val history =
        mutableListOf<AlertHistory>()

    private var currentSymbol =
        "frxEURUSD"

    private var currentPrice =
        Double.NaN

    private var webSocket: WebSocket? = null

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

    private val symbols =
        linkedMapOf(

            "EUR/USD" to "frxEURUSD",

            "GBP/USD" to "frxGBPUSD",

            "USD/JPY" to "frxUSDJPY",

            "USD/CHF" to "frxUSDCHF",

            "AUD/USD" to "frxAUDUSD",

            "USD/CAD" to "frxUSDCAD",

            "NZD/USD" to "frxNZDUSD"
        )


    data class PriceAlert(

        val id: Long,

        val symbol: String,

        val condition: String,

        val target: Double,

        var enabled: Boolean,

        var triggered: Boolean,

        var triggeredPrice: Double =
            Double.NaN,

        var triggeredTime: Long =
            0L
    )


    data class AlertHistory(

        val id: Long,

        val symbol: String,

        val condition: String,

        val target: Double,

        val triggeredPrice: Double,

        val triggeredTime: Long
    )


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        loadAlerts()

        loadHistory()

        currentSymbol =
            preferences.getString(
                "selected_symbol",
                "frxEURUSD"
            ) ?: "frxEURUSD"

        createInterface()

        requestNotificationPermission()

        startMonitoringService()

        connectToDeriv()
    }


    override fun onResume() {

        super.onResume()

        if (
            webSocket == null
        ) {
            connectToDeriv()
        }

        loadAlerts()

        loadHistory()

        refreshAlertList()

        refreshHistoryList()
    }


    private fun startMonitoringService() {

        val intent =
            android.content.Intent(
                this,
                ForexAlertService::class.java
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            startForegroundService(
                intent
            )

        } else {

            startService(
                intent
            )
        }
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
            Color.WHITE
        )


        val scrollView =
            ScrollView(this)


        val content =
            LinearLayout(this)

        content.orientation =
            LinearLayout.VERTICAL


        val title =
            TextView(this)

        title.text =
            "FOREX ALERT"

        title.textSize =
            28f

        title.setTextColor(
            Color.BLACK
        )

        title.gravity =
            Gravity.CENTER


        content.addView(
            title
        )


        val symbolLabel =
            TextView(this)

        symbolLabel.text =
            "Forex Pair"

        symbolLabel.textSize =
            16f

        symbolLabel.setTextColor(
            Color.DKGRAY
        )


        content.addView(
            symbolLabel,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {

                topMargin = 25
            }
        )


        symbolSpinner =
            Spinner(this)


        val symbolNames =
            symbols.keys.toList()


        symbolSpinner.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                symbolNames
            )


        val savedIndex =
            symbolNames.indexOf(
                displaySymbol(
                    currentSymbol
                )
            )


        if (
            savedIndex >= 0
        ) {

            symbolSpinner
                .setSelection(
                    savedIndex
                )
        }


        symbolSpinner
            .onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {


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

                    val selected =
                        symbolNames[
                            position
                        ]


                    currentSymbol =
                        symbols[
                            selected
                        ] ?: "frxEURUSD"


                    currentPrice =
                        Double.NaN


                    priceText.text =
                        "--"


                    statusText.text =
                        "Connecting..."


                    preferences.edit()
                        .putString(
                            "selected_symbol",
                            currentSymbol
                        )
                        .apply()


                    reconnectToDeriv()


                    val intent =
                        android.content.Intent(
                            this@MainActivity,
                            ForexAlertService::class.java
                        )


                    intent.action =
                        ForexAlertService
                            .ACTION_SYMBOL_CHANGED


                    intent.putExtra(
                        ForexAlertService.EXTRA_SYMBOL,
                        currentSymbol
                    )


                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.O
                    ) {

                        startForegroundService(
                            intent
                        )

                    } else {

                        startService(
                            intent
                        )
                    }


                    loadAlerts()

                    loadHistory()

                    refreshAlertList()

                    refreshHistoryList()
                }
            }


        content.addView(
            symbolSpinner
        )


        priceText =
            TextView(this)

        priceText.text =
            "--"

        priceText.textSize =
            42f

        priceText.setTextColor(
            Color.BLACK
        )

        priceText.gravity =
            Gravity.CENTER


        content.addView(
            priceText,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {

                topMargin = 20
            }
        )


        statusText =
            TextView(this)

        statusText.text =
            "Connecting..."

        statusText.textSize =
            17f

        statusText.setTextColor(
            Color.GRAY
        )

        statusText.gravity =
            Gravity.CENTER


        content.addView(
            statusText
        )


        val alertTitle =
            TextView(this)

        alertTitle.text =
            "Create Price Alert"

        alertTitle.textSize =
            21f

        alertTitle.setTextColor(
            Color.BLACK
        )


        content.addView(
            alertTitle,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {

                topMargin = 30
            }
        )


        val conditionLabel =
            TextView(this)

        conditionLabel.text =
            "Condition"

        conditionLabel.textSize =
            15f

        conditionLabel.setTextColor(
            Color.DKGRAY
        )


        content.addView(
            conditionLabel
        )


        conditionSpinner =
            Spinner(this)


        conditionSpinner.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    "Above",
                    "Below",
                    "Touch"
                )
            )


        content.addView(
            conditionSpinner
        )


        targetInput =
            EditText(this)

        targetInput.hint =
            "Target price"

        targetInput.textSize =
            18f

        targetInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL


        content.addView(
            targetInput,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {

                topMargin = 10
            }
        )


        val addButton =
            Button(this)

        addButton.text =
            "ADD ALERT"

        addButton.setOnClickListener {

            addAlert()
        }


        content.addView(
            addButton
        )


        val alertsTitle =
            TextView(this)

        alertsTitle.text =
            "My Alerts"

        alertsTitle.textSize =
            21f

        alertsTitle.setTextColor(
            Color.BLACK
        )


        content.addView(
            alertsTitle,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {

                topMargin = 30
            }
        )


        alertContainer =
            LinearLayout(this)

        alertContainer.orientation =
            LinearLayout.VERTICAL


        content.addView(
            alertContainer
        )


        val historyTitle =
            TextView(this)

        historyTitle.text =
            "Alert History"

        historyTitle.textSize =
            21f

        historyTitle.setTextColor(
            Color.BLACK
        )


        content.addView(
            historyTitle,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {

                topMargin = 30
            }
        )


        historyContainer =
            LinearLayout(this)

        historyContainer.orientation =
            LinearLayout.VERTICAL


        content.addView(
            historyContainer
        )


        val clearButton =
            Button(this)

        clearButton.text =
            "CLEAR HISTORY"

        clearButton.setOnClickListener {

            history.clear()

            saveHistory()

            refreshHistoryList()
        }


        content.addView(
            clearButton
        )


        scrollView.addView(
            content
        )


        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )


        setContentView(
            root
        )


        refreshAlertList()

        refreshHistoryList()
    }


    private fun connectToDeriv() {

        runOnUiThread {

            statusText.text =
                "Connecting..."

            statusText.setTextColor(
                Color.GRAY
            )
        }


        webSocket?.close(
            1000,
            "Reconnect"
        )


        val request =
            Request.Builder()
                .url(
                    "wss://ws.binaryws.com/websockets/v3"
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

                        subscribeToSymbol(
                            webSocket
                        )
                    }


                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        processMessage(
                            text
                        )
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
                }
            )
    }


    private fun reconnectToDeriv() {

        webSocket?.close(
            1000,
            "Changing symbol"
        )

        webSocket = null

        connectToDeriv()
    }


    private fun subscribeToSymbol(
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
                JSONObject(
                    message
                )


            if (
                json.has("error")
            ) {

                runOnUiThread {

                    statusText.text =
                        "Data error"

                    statusText.setTextColor(
                        Color.RED
                    )
                }

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


            val quote =
                tick.optDouble(
                    "quote",
                    Double.NaN
                )


            if (
                !quote.isFinite()
            ) {
                return
            }


            currentPrice =
                quote


            runOnUiThread {

                priceText.text =
                    formatPrice(
                        quote
                    )


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

        } catch (
            _: Exception
        ) {
        }
    }


    private fun addAlert() {

        val text =
            targetInput.text
                .toString()
                .trim()


        if (
            text.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Enter a target price",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val target =
            text.toDoubleOrNull()


        if (
            target == null ||
            !target.isFinite() ||
            target <= 0
        ) {

            Toast.makeText(
                this,
                "Enter a valid price",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val condition =
            conditionSpinner
                .selectedItem
                .toString()


        val alert =
            PriceAlert(

                id =
                    System.currentTimeMillis(),

                symbol =
                    currentSymbol,

                condition =
                    condition,

                target =
                    target,

                enabled =
                    true,

                triggered =
                    false
            )


        alerts.add(
            alert
        )


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

        if (
            !::alertContainer
                .isInitialized
        ) {
            return
        }


        alertContainer
            .removeAllViews()


        val current =
            alerts.filter {
                it.symbol ==
                    currentSymbol
            }


        if (
            current.isEmpty()
        ) {

            val empty =
                TextView(this)

            empty.text =
                "No alerts for this pair"

            empty.textSize =
                16f

            empty.setTextColor(
                Color.GRAY
            )

            alertContainer.addView(
                empty
            )

            return
        }


        for (
            alert in current
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


            val description =
                TextView(this)

            description.text =
                "${alert.condition} ${formatPrice(alert.target)}"

            description.textSize =
                18f

            description.setTextColor(
                Color.BLACK
            )


            row.addView(
                description
            )


            val status =
                TextView(this)


            if (
                alert.triggered
            ) {

                status.text =
                    "Triggered at ${formatPrice(alert.triggeredPrice)}\n" +
                    formatTime(
                        alert.triggeredTime
                    )

                status.setTextColor(
                    Color.rgb(
                        0,
                        140,
                        0
                    )
                )

            } else {

                status.text =
                    if (
                        alert.enabled
                    )
                        "Enabled"
                    else
                        "Disabled"

                status.setTextColor(
                    if (
                        alert.enabled
                    )
                        Color.rgb(
                            0,
                            140,
                            0
                        )
                    else
                        Color.GRAY
                )
            }


            row.addView(
                status
            )


            val buttons =
                LinearLayout(this)

            buttons.orientation =
                LinearLayout.HORIZONTAL


            if (
                alert.triggered
            ) {

                val reset =
                    Button(this)

                reset.text =
                    "RESET"


                reset.setOnClickListener {

                    alert.triggered =
                        false

                    alert.triggeredPrice =
                        Double.NaN

                    alert.triggeredTime =
                        0L

                    alert.enabled =
                        true

                    saveAlerts()

                    refreshAlertList()
                }


                buttons.addView(
                    reset,
                    LinearLayout.LayoutParams(
                        0,
                        -2,
                        1f
                    )
                )

            } else {

                val toggle =
                    Button(this)

                toggle.text =
                    if (
                        alert.enabled
                    )
                        "DISABLE"
                    else
                        "ENABLE"


                toggle.setOnClickListener {

                    alert.enabled =
                        !alert.enabled

                    saveAlerts()

                    refreshAlertList()
                }


                buttons.addView(
                    toggle,
                    LinearLayout.LayoutParams(
                        0,
                        -2,
                        1f
                    )
                )
            }


            val delete =
                Button(this)

            delete.text =
                "DELETE"


            delete.setOnClickListener {

                alerts.removeAll {
                    it.id ==
                        alert.id
                }

                saveAlerts()

                refreshAlertList()
            }


            buttons.addView(
                delete,
                LinearLayout.LayoutParams(
                    0,
                    -2,
                    1f
                )
            )


            row.addView(
                buttons
            )


            alertContainer.addView(
                row
            )
        }
    }


    private fun refreshHistoryList() {

        if (
            !::historyContainer
                .isInitialized
        ) {
            return
        }


        historyContainer
            .removeAllViews()


        val current =
            history.filter {
                it.symbol ==
                    currentSymbol
            }.sortedByDescending {
                it.triggeredTime
            }


        if (
            current.isEmpty()
        ) {

            val empty =
                TextView(this)

            empty.text =
                "No triggered alerts"

            empty.textSize =
                16f

            empty.setTextColor(
                Color.GRAY
            )

            historyContainer.addView(
                empty
            )

            return
        }


        for (
            item in current
        ) {

            val text =
                TextView(this)

            text.text =
                "${displaySymbol(item.symbol)}\n" +
                "${item.condition} ${formatPrice(item.target)}\n" +
                "Triggered price: ${formatPrice(item.triggeredPrice)}\n" +
                formatTime(
                    item.triggeredTime
                )

            text.textSize =
                15f

            text.setTextColor(
                Color.DKGRAY
            )

            text.setPadding(
                12,
                12,
                12,
                18
            )


            historyContainer.addView(
                text
            )
        }
    }


    private fun saveAlerts() {

        try {

            val array =
                JSONArray()


            for (
                alert in alerts
            ) {

                val item =
                    JSONObject()


                item.put(
                    "id",
                    alert.id
                )

                item.put(
                    "symbol",
                    alert.symbol
                )

                item.put(
                    "condition",
                    alert.condition
                )

                item.put(
                    "target",
                    alert.target
                )

                item.put(
                    "enabled",
                    alert.enabled
                )

                item.put(
                    "triggered",
                    alert.triggered
                )


                if (
                    alert.triggeredPrice
                        .isFinite()
                ) {

                    item.put(
                        "triggeredPrice",
                        alert.triggeredPrice
                    )

                } else {

                    item.put(
                        "triggeredPrice",
                        JSONObject.NULL
                    )
                }


                item.put(
                    "triggeredTime",
                    alert.triggeredTime
                )


                array.put(
                    item
                )
            }


            preferences.edit()
                .putString(
                    "alerts",
                    array.toString()
                )
                .apply()

        } catch (
            _: Exception
        ) {
        }
    }


    private fun loadAlerts() {

        alerts.clear()


        val saved =
            preferences.getString(
                "alerts",
                null
            ) ?: return


        try {

            val array =
                JSONArray(saved)


            for (
                i in 0 until array.length()
            ) {

                val item =
                    array.getJSONObject(
                        i
                    )


                val triggeredPrice =
                    if (
                        item.has(
                            "triggeredPrice"
                        ) &&
                        !item.isNull(
                            "triggeredPrice"
                        )
                    ) {

                        item.optDouble(
                            "triggeredPrice",
                            Double.NaN
                        )

                    } else {

                        Double.NaN
                    }


                alerts.add(

                    PriceAlert(

                        id =
                            item.getLong(
                                "id"
                            ),

                        symbol =
                            item.getString(
                                "symbol"
                            ),

                        condition =
                            item.getString(
                                "condition"
                            ),

                        target =
                            item.getDouble(
                                "target"
                            ),

                        enabled =
                            item.getBoolean(
                                "enabled"
                            ),

                        triggered =
                            item.getBoolean(
                                "triggered"
                            ),

                        triggeredPrice =
                            triggeredPrice,

                        triggeredTime =
                            item.optLong(
                                "triggeredTime",
                                0L
                            )
                    )
                )
            }

        } catch (
            _: Exception
        ) {
        }
    }


    private fun saveHistory() {

        try {

            val array =
                JSONArray()


            for (
                item in history
            ) {

                if (
                    !item.triggeredPrice
                        .isFinite()
                ) {
                    continue
                }


                val objectItem =
                    JSONObject()


                objectItem.put(
                    "id",
                    item.id
                )

                objectItem.put(
                    "symbol",
                    item.symbol
                )

                objectItem.put(
                    "condition",
                    item.condition
                )

                objectItem.put(
                    "target",
                    item.target
                )

                objectItem.put(
                    "triggeredPrice",
                    item.triggeredPrice
                )

                objectItem.put(
                    "triggeredTime",
                    item.triggeredTime
                )


                array.put(
                    objectItem
                )
            }


            preferences.edit()
                .putString(
                    "history",
                    array.toString()
                )
                .apply()

        } catch (
            _: Exception
        ) {
        }
    }


    private fun loadHistory() {

        history.clear()


        val saved =
            preferences.getString(
                "history",
                null
            ) ?: return


        try {

            val array =
                JSONArray(saved)


            for (
                i in 0 until array.length()
            ) {

                val item =
                    array.getJSONObject(
                        i
                    )


                val price =
                    item.optDouble(
                        "triggeredPrice",
                        Double.NaN
                    )


                if (
                    !price.isFinite()
                ) {
                    continue
                }


                history.add(

                    AlertHistory(

                        id =
                            item.getLong(
                                "id"
                            ),

                        symbol =
                            item.getString(
                                "symbol"
                            ),

                        condition =
                            item.getString(
                                "condition"
                            ),

                        target =
                            item.getDouble(
                                "target"
                            ),

                        triggeredPrice =
                            price,

                        triggeredTime =
                            item.getLong(
                                "triggeredTime"
                            )
                    )
                )
            }

        } catch (
            _: Exception
        ) {
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
                ) !=
                PackageManager.PERMISSION_GRANTED
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


    private fun displaySymbol(
        symbol: String
    ): String {

        return symbols.entries
            .firstOrNull {
                it.value ==
                    symbol
            }
            ?.key
            ?: symbol
    }


    private fun formatPrice(
        price: Double
    ): String {

        return if (
            currentSymbol.contains(
                "JPY"
            )
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


    private fun formatTime(
        time: Long
    ): String {

        if (
            time <= 0
        ) {
            return ""
        }


        return SimpleDateFormat(
            "dd MMM yyyy, HH:mm:ss",
            Locale.getDefault()
        ).format(
            Date(time)
        )
    }


    override fun onDestroy() {

        webSocket?.close(
            1000,
            "App closed"
        )

        webSocket = null

        client.dispatcher
            .executorService
            .shutdown()

        super.onDestroy()
    }
}