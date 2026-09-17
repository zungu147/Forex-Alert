package com.forexalert.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var priceText: TextView
    private lateinit var statusText: TextView
    private lateinit var timeText: TextView

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        connectToDeriv()
    }

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(32, 32, 32, 32)
        root.setBackgroundColor(Color.WHITE)

        val title = TextView(this)

        title.text = "FOREX ALERT"
        title.textSize = 28f
        title.setTextColor(Color.BLACK)
        title.gravity = Gravity.CENTER

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val symbolText = TextView(this)

        symbolText.text = "EUR/USD"
        symbolText.textSize = 22f
        symbolText.setTextColor(Color.DKGRAY)
        symbolText.gravity = Gravity.CENTER

        root.addView(
            symbolText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 30
            }
        )

        priceText = TextView(this)

        priceText.text = "--"
        priceText.textSize = 42f
        priceText.setTextColor(Color.BLACK)
        priceText.gravity = Gravity.CENTER

        root.addView(
            priceText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
            }
        )

        statusText = TextView(this)

        statusText.text = "Connecting..."
        statusText.textSize = 18f
        statusText.setTextColor(Color.GRAY)
        statusText.gravity = Gravity.CENTER

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 25
            }
        )

        timeText = TextView(this)

        timeText.text = ""
        timeText.textSize = 14f
        timeText.setTextColor(Color.GRAY)
        timeText.gravity = Gravity.CENTER

        root.addView(
            timeText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
            }
        )

        setContentView(root)
    }

    private fun connectToDeriv() {

        runOnUiThread {
            statusText.text = "Connecting to market data..."
            statusText.setTextColor(Color.GRAY)
        }

        val request = Request.Builder()
            .url("wss://ws.binaryws.com/websockets/v3")
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {

                    runOnUiThread {
                        statusText.text = "Connected"
                        statusText.setTextColor(Color.rgb(0, 150, 0))
                    }

                    val subscribeRequest = JSONObject()

                    subscribeRequest.put(
                        "ticks",
                        "frxEURUSD"
                    )

                    subscribeRequest.put(
                        "subscribe",
                        1
                    )

                    webSocket.send(
                        subscribeRequest.toString()
                    )
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {

                    processMessage(text)
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {

                    runOnUiThread {
                        statusText.text = "Disconnecting..."
                        statusText.setTextColor(Color.GRAY)
                    }
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {

                    runOnUiThread {
                        statusText.text = "Disconnected"
                        statusText.setTextColor(Color.RED)
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

                        statusText.setTextColor(Color.RED)
                    }
                }
            }
        )
    }

    private fun processMessage(message: String) {

        try {

            val json = JSONObject(message)

            if (json.optString("msg_type") != "tick") {
                return
            }

            val tick = json.optJSONObject("tick")
                ?: return

            val quote = tick.optDouble(
                "quote",
                Double.NaN
            )

            if (quote.isNaN()) {
                return
            }

            val epoch = tick.optLong(
                "epoch",
                0
            )

            val price = String.format(
                Locale.US,
                "%.5f",
                quote
            )

            runOnUiThread {

                priceText.text = price

                if (epoch > 0) {
                    timeText.text =
                        "Last update: $epoch"
                }
            }

        } catch (e: Exception) {

            runOnUiThread {
                statusText.text =
                    "Data error"
                statusText.setTextColor(Color.RED)
            }
        }
    }

    override fun onDestroy() {

        webSocket?.close(
            1000,
            "App closed"
        )

        client.dispatcher.executorService.shutdown()

        super.onDestroy()
    }
}