package com.example.lango
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.graphics.drawable.LayerDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.example.lango.playback.AndroidAudioPlayer
import com.example.lango.record.AndroidAudioRecorder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException


@Suppress("DEPRECATION")



class MainActivity : ComponentActivity() {


    var content = "" //Translated text
    var response: String = ""
    var responseBody1 = ""

    private lateinit var volumeChangeReceiver: VolumeChangeReceiver
    lateinit var capReq: CaptureRequest.Builder
    lateinit var capReq1: CaptureRequest.Builder
    private lateinit var textureView: TextureView
    private lateinit var textureView2: TextureView
    lateinit var cameraManager: CameraManager
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraCaptureSession1: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var cameraDevice1: CameraDevice
    lateinit var captureRequest: CaptureRequest
    private lateinit var audioManager: AudioManager

    // creating variables on below line.
    lateinit var handler: Handler
    lateinit var handlerThread: HandlerThread
    lateinit var txtResponse: TextView
    lateinit var idTVQuestion: TextView
    lateinit var etQuestion: TextInputEditText
    val client = OkHttpClient()
    private var openAI = OpenAI(
        token = "",
        logging = LoggingConfig(LogLevel.All)
    )
    var correct: Boolean by mutableStateOf(false)
    var reset: Boolean by mutableStateOf(false)
    private val _apiResponse = MutableLiveData<String>()

    val apiResponse: LiveData<String>
        get() = _apiResponse

    fun setApiResponse(response: String) {
        _apiResponse.value = response
    }
    private val recorder by lazy {
        AndroidAudioRecorder(applicationContext)
    }

    private var audioFile: File? = null
    private val player by lazy {
        AndroidAudioPlayer(applicationContext)
    }
    var content1: Any? = ""
    var res: String = ""
    var engRes = "{\"text\":\"Where\"}"




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPermissions()
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            0
        )

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeChangeReceiver = VolumeChangeReceiver()

        // Register the receiver to listen for volume changes
        val filter = IntentFilter()
        filter.addAction("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeChangeReceiver, filter)



        try {
            textureView = findViewById(R.id.textureView) // Initialize textureView
            textureView2 = findViewById(R.id.textureView2)
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            handlerThread = HandlerThread("videoThread")
            handlerThread.start()
            handler = Handler(handlerThread.looper)
            Log.d("tag","&=>")
        } catch (e: Exception) {
            // Handle any exceptions that might occur during initialization
            Log.d("tag","(>)"+e.printStackTrace())
            // You can log the exception, display an error message, or take appropriate action here.
        }


        textureView?.surfaceTextureListener= object: TextureView.SurfaceTextureListener{
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    Log.d("tag","Visal")
                    open_camera()
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    TODO("Not yet implemented")
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    Log.d("Texture Updated", "Surface texture has been updated.")

                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    Log.d("Texture Updated", "Surface texture has been updated.")
                }

            }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }



    private inner class VolumeChangeReceiver : BroadcastReceiver() {
        private var lastVolume = 0

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                // Check if volume is increased or decreased
                if (currentVolume > lastVolume) {
                    // Volume increased, executeVolumeUpCommand()
                    executeVolumeUpCommand()
                } else if (currentVolume < lastVolume) {
                    // Volume decreased, executeVolumeDownCommand()
                    executeVolumeDownCommand()
                }
                lastVolume = currentVolume
            }
        }
    }


    private fun executeVolumeUpCommand() {
                findViewById<TextView?>(R.id.subtitles).setText(" ")
                val subtitlesTextView = findViewById<TextView>(R.id.subtitles)
                val curvedBackgroundDrawable = ContextCompat.getDrawable(this, R.drawable.start)
                val backgroundLayerDrawable = LayerDrawable(arrayOf(curvedBackgroundDrawable))
                subtitlesTextView.background = backgroundLayerDrawable
                if (isNetworkAvailable(this)) {
                    startRecording()
                }
                else{
                    Toast.makeText(applicationContext, "No internet", Toast.LENGTH_SHORT).show()
                }
    }

    private fun executeVolumeDownCommand() {
                val subtitlesTextView = findViewById<TextView>(R.id.subtitles)
                subtitlesTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
                val curvedBackgroundDrawable = ContextCompat.getDrawable(this, R.drawable.curved_background)
                val backgroundLayerDrawable = LayerDrawable(arrayOf(curvedBackgroundDrawable))
                subtitlesTextView.background = backgroundLayerDrawable
                if (isNetworkAvailable(this)) {
                    stopRecording()
                }
                else{
                    Toast.makeText(applicationContext, "No internet", Toast.LENGTH_SHORT).show()
                }
    }




    fun startRecording() {
        try {
            Toast.makeText(applicationContext, "Start Speaking", Toast.LENGTH_SHORT).show()
            File(cacheDir, "sample.mp3").also {
                recorder.start(it)
                audioFile = it
                response = ""   // Reset the response variable
                engRes = " "     // Reset the engRes variable
                content = ""
                correct = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
//            Log.d("tag","&$>+e.printStackTrace(")
        }
    }

    fun stopRecording() {
        try {

            recorder.stop()

            Log.d("tag","!!>>")
            res = ""
            res = audioFile?.let { transcribeAudio(it) }.toString()
            if(res.isNotEmpty()){
//                Toast.makeText(applicationContext, res, Toast.LENGTH_SHORT).show()
                val jsonObject = JSONObject(res)
                val extractedText = jsonObject.optString("text", "DefaultText")
                val sub: Unit = findViewById<TextView?>(R.id.subtitles).setText(extractedText)
                engRes = chatTranslateToEnglish(res)
                if(engRes.isNotEmpty()){
                    correct = true // Set 'correct' to true after displaying the text
//                    I have commented the below line because im using whisperAI
//                    val jsonObject = JSONObject(engRes)
//                    val extractedText = jsonObject.optString("text", "DefaultText")
//                    val sub: Unit = findViewById<TextView?>(R.id.subtitles).setText(extractedText)
                }
                else{
                    Toast.makeText(applicationContext, "No internet", Toast.LENGTH_SHORT).show()
                }
            }
            else{
                Toast.makeText(applicationContext, "No internet", Toast.LENGTH_SHORT).show()
            }

        }catch(e:Exception){
            Log.d("tag","()"+e.printStackTrace())
        }
    }


//    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
//        when (keyCode) {
//            KeyEvent.KEYCODE_VOLUME_UP -> {
//                findViewById<TextView?>(R.id.subtitles).setText(" ")
//                val subtitlesTextView = findViewById<TextView>(R.id.subtitles)
//                val curvedBackgroundDrawable = ContextCompat.getDrawable(this, R.drawable.start)
//                val backgroundLayerDrawable = LayerDrawable(arrayOf(curvedBackgroundDrawable))
//                subtitlesTextView.background = backgroundLayerDrawable
//                startRecording()
//            }
//            KeyEvent.KEYCODE_VOLUME_DOWN -> {
//                val subtitlesTextView = findViewById<TextView>(R.id.subtitles)
//                subtitlesTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
//                val curvedBackgroundDrawable = ContextCompat.getDrawable(this, R.drawable.curved_background)
//                val backgroundLayerDrawable = LayerDrawable(arrayOf(curvedBackgroundDrawable))
//                subtitlesTextView.background = backgroundLayerDrawable
//                stopRecording()
//            }
//        }
//        return super.onKeyDown(keyCode, event)
//    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList

        try {
            for (cameraId in cameraIds) {
                Log.d("Camera ID", cameraId)
            }
        } catch (e: CameraAccessException) {
            Log.e("Camera Access Exception", "Error accessing camera IDs: ${e.message}")
        }
        try{
            cameraManager.openCamera(
                cameraIds[0],
                object : CameraDevice.StateCallback() {
                    override fun onOpened(p0: CameraDevice) {
                        try {
                            cameraDevice = p0
                            cameraDevice1 = p0
                            capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            capReq1 = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            val surface = Surface(textureView.surfaceTexture)
                            val surface2 = Surface(textureView2.surfaceTexture)

                            capReq.addTarget(surface)
                            capReq.addTarget(surface2)
                            cameraDevice.createCaptureSession(
                                listOf(surface2,surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(p0: CameraCaptureSession) {
                                        Log.d("tag", "Vishaaaaal")
                                        cameraCaptureSession = p0
                                        cameraCaptureSession.setRepeatingRequest(
                                            capReq.build(),
                                            null,
                                            null
                                        )
                                        // Camera is open and ready for preview
                                    }
                                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                                        // Handle configuration failure
                                    }
                                },
                                null
                            ) // No need for handler when not capturing images
                        } catch (e: CameraAccessException) {
                            Log.d("tag", "Upooooot")
                            // Handle camera access exceptions
                        }
                    }

                    override fun onDisconnected(p0: CameraDevice) {
                        // Handle camera disconnection
                    }

                    override fun onError(p0: CameraDevice, p1: Int) {
                        // Handle camera device error
                    }
                },
                null
            ) // No need for handler when not capturing images
        }catch (e:Exception){

            Log.d("tag","&&"+e.printStackTrace())
        }

        }

    fun getPermissions() {
            val permissionsList = mutableListOf<String>()

            try {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                )
                    permissionsList.add(Manifest.permission.CAMERA)
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                )
                    permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                )
                    permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

                if (permissionsList.isNotEmpty()) {
                    ActivityCompat.requestPermissions(this, permissionsList.toTypedArray(), 101)
                }
            } catch (e: Exception) {
                // Handle any exceptions here
                print("#>" + e.printStackTrace()) // You can print the stack trace for debugging
                val stackTrace = Log.getStackTraceString(e)
                Log.d("Tag", "" + stackTrace)
            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEach {
            if (it != PackageManager.PERMISSION_GRANTED) {
                try {
                    getPermissions()
                } catch (e: Exception) {
                    Log.d("Tag",  "^>"+ e.printStackTrace())
                }
            }
        }
    }


    @SuppressLint("SuspiciousIndentation")
        fun chatTranslateToEnglish(inputText: String): String {
            Thread {
                val apiKey = ""
                val apiUrl = "https://api.openai.com/v1/chat/completions"
                val jsonMediaType = "application/json; charset=utf-8".toMediaType()
                val requestData = JSONObject()
                requestData.put("model", "gpt-3.5-turbo")
                val messages = JSONArray()
                val systemMessage = JSONObject()
                systemMessage.put("role", "system")
                systemMessage.put(
                    "content",
                    "Translate the text to english and do not reply anything else strictly"
                )
                val userMessage = JSONObject()
                userMessage.put("role", "user")
                userMessage.put("content", inputText)  // Include the inputText in the conversation

                messages.put(systemMessage)
                messages.put(userMessage)

                requestData.put("messages", messages)

                val requestBody = RequestBody.create(jsonMediaType, requestData.toString())

                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)

                try {
                    responseBody1 = client.newCall(request.build()).execute().use { response ->
                        response.body?.string() ?: ""
                    }
                    val jsonResponse = JsonParser.parseString(responseBody1).asJsonObject
                    content = jsonResponse.getAsJsonArray("choices")
                        .firstOrNull()
                        ?.asJsonObject
                        ?.getAsJsonObject("message")
                        ?.get("content")
                        ?.asString
                        ?: ""

                    println("#> Response Content: $content")
//                println("#> Response: $responseBody1")
                    correct = true
                } catch (e: IOException) {
                    e.printStackTrace()
                    println("!> Request failed with exception: ${e.message}")
                }
            }.start()

            while (content == "") {
            }
            return content;

    }

        fun transcribeAudio(audioFile: File): Any? {
                val audioRequestBody = audioFile.asRequestBody("audio/*".toMediaType())
                val formBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", audioFile.name, audioRequestBody)
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart("language", "en")
                    .build()

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .header(
                        "Authorization",
                        "Bearer //apikey"
                    )
                    .post(formBody)

                Thread {
                    try {
                        response = client.newCall(request.build()).execute().use { response ->
                            response.body?.string() ?: ""
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        correct = true
                    }
                }.start()
                while (response == "") {
                }
                correct = true
                println("&>" + response)
                return response;
        }
}


