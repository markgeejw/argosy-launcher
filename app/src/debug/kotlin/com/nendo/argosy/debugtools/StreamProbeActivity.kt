package com.nendo.argosy.debugtools

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.nendo.argosy.R
import com.nendo.argosy.data.model.stream.StreamRequest
import com.nendo.argosy.data.stream.toIntent

class StreamProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun field(hintRes: Int, initial: String = ""): EditText = EditText(this).also {
            it.setHint(hintRes)
            it.setText(initial)
            layout.addView(it)
        }
        val host = field(R.string.kaegr_probe_host)
        val client = field(R.string.kaegr_probe_client, "com.limelight")
        val appId = field(R.string.kaegr_probe_id)
        val title = field(R.string.kaegr_probe_app)
        layout.addView(Button(this).apply {
            setText(R.string.kaegr_probe_launch)
            setOnClickListener {
                val id = appId.text.toString().trim()
                if (!isValidStreamProbeInput(host.text, client.text, id, title.text)) {
                    Toast.makeText(this@StreamProbeActivity, R.string.kaegr_probe_invalid, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val intent = StreamRequest(host.text.toString().trim(), id, title.text.toString())
                    .toIntent(client.text.toString().trim())
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@StreamProbeActivity, R.string.kaegr_probe_failed, Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) {
                    Toast.makeText(this@StreamProbeActivity, R.string.kaegr_probe_failed, Toast.LENGTH_LONG).show()
                }
            }
        })
        setContentView(layout)
    }
}

internal fun isValidStreamProbeInput(
    host: CharSequence,
    client: CharSequence,
    appId: String,
    title: CharSequence
): Boolean = host.isNotBlank() && client.isNotBlank() && title.isNotBlank() &&
    appId.toIntOrNull()?.let { it > 0 } == true
