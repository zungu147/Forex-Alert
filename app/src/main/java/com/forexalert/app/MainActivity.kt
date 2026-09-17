package com.forexalert.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var priceText: TextView

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        getPrice()
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

        val symbol = TextView(this)

        symbol.text = "EUR/USD"
        symbol.textSize = 22f
        symbol.setTextColor(Color.DKGRAY)
        symbol.gravity = Gravity.CENTER

        root.addView(
            symbol,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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
                topMargin = 30
                bottomMargin = 20
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
            )
        )

        setContentView(root)
    }

    private fun getPrice() {

        statusText.text = "Getting market price..."

        thread {

            try {

                val url = URL(
                    "https://api.deriv.com"
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                connection.connect()

                val responseCode = connection.responseCode

                connection.disconnect()

                handler.post {

                    if (responseCode in 200..499) {

                        statusText.text =
                            "Internet connection available"

                    } else {

                        statusText.text =
                            "Connection error"
                    }
                }

            } catch (e: Exception) {

                handler.post {

                    statusText.text =
                        "Unable to connect"
                }
            }
        }
    }
}