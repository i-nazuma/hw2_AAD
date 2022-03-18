package at.technikum_wien.polzert.webdemo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        val LOG_TAG = MainActivity::class.simpleName
        const val RESULTS_KEY = "results"
    }

    private var resultTextView : TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        resultTextView = findViewById(R.id.tv_output)
        val loadButton : Button = findViewById(R.id.btn_load)
        loadButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                loadWebResult()
            }
        }
    }

    private fun getResponseFromHttpUrl(urlString : String) : String?{
        var con : HttpURLConnection? = null
        try{
            val url = URL(urlString)
            //return url.readText()
            con = url.openConnection() as HttpURLConnection
            con.requestMethod = "GET"
            return Scanner(con.inputStream).use { scanner ->
                scanner.useDelimiter("\\A")
                if(scanner.hasNext()) scanner.next() else null
            }
        }catch (ex : MalformedURLException) {
            Log.e(LOG_TAG, "Malformed URL.", ex)
            return null
        }catch (ex : IOException) {
            Log.e(LOG_TAG, "I/O exception", ex)
            return null
        } finally {
            con?.disconnect()
        }
    }

    private suspend fun loadWebResult() {
        val result = withContext(Dispatchers.IO) {
            getResponseFromHttpUrl(
                "https://data.wien.gv.at/daten/geo?service=WFS&request=GetFeature&version=1.1.0&typeName=ogdwien:OEFFHALTESTOGD&srsName=EPSG:4326&outputFormat=json"
            )
        }
        if(result == null) {
            Toast.makeText(this, getString(R.string.general_error),
            Toast.LENGTH_LONG).show()
            resultTextView?.text = ""
        } else {
            resultTextView?.text = withContext(Dispatchers.Default) {
                parseResponse(result).joinToString("\n")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val resultsString = resultTextView?.text?.toString() ?: ""
        outState.putString(RESULTS_KEY, resultsString)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val resultsString = savedInstanceState.getString(RESULTS_KEY)
        resultTextView?.text = resultsString
    }

    private fun parseResponse(response : String) : List<String> {
        val jsonRoot = JSONObject(response)
        val features = jsonRoot.optJSONArray("features")
        val stationSet = mutableSetOf<String>() //to prevent multiple entries
        if(features == null)
            Log.w(LOG_TAG, "No features found.")
        else {
                for (i in 0 until features.length()) {
                    val entry = features.optJSONObject(i)
                    val properties = entry?.optJSONObject("properties")
                    val name = properties?.optString("HTXT")
                    if(name == null)
                        Log.w(LOG_TAG, "No name for feature $i")
                    else
                        stationSet.add(name)
                }
            }

        return stationSet.toList().sorted()
    }
}