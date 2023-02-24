package com.portfolio.wvr

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.updateLayoutParams
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {
    private var currentRecording: Recording? = null
    private val FILENAMEFORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private var audioEnabled = false
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var context: Context;

    //private lateinit var durationTextView: TextView;
    private var targetQuality = Quality.HIGHEST;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        context = this;
        //durationTextView = findViewById<TextView>(R.id.durationTextView)

        runBlocking {
            bindCaptureUseCase()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private suspend fun bindCaptureUseCase() {
        val cameraProvider = ProcessCameraProvider.getInstance(this).await()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        var previewView = findViewById<PreviewView>(R.id.previewView)
        previewView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val orientation = baseContext.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                dimensionRatio = "V,9:16"
            } else {
                dimensionRatio = "H,16:9"
            }
        }

        val previewBuilder = Preview.Builder()
        val preview = previewBuilder.build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        val quality = targetQuality
        val qualitySelector = QualitySelector.from(quality)

        val recorderBuilder = Recorder.Builder()
        recorderBuilder.setQualitySelector(qualitySelector)
        val recorder = recorderBuilder.build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                videoCapture,
                preview
            )
            startRecording();
        } catch (exc: Exception) {
            exc.printStackTrace()
            Toast.makeText(context, exc.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    @Throws(IOException::class)
    private fun createFile(): File? {
        var dir: File = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/result1"), "")
        var testFile: File = File(dir.path + File.separator + "test_img1.mp4")
        return testFile
    }

    /*
    val CAMERA_IMAGE_RESULT: Int = 1001
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CAMERA_IMAGE_RESULT -> {
                if (resultCode == RESULT_OK) {
                    if (mUri != null) {
                        Log.d("uriPath", mUri.getPath().replace("//", "/"))
                        val profileImageFilepath: String = mUri.getPath().replace("//", "/")
                        Log.d("path", profileImageFilepath)
                        profileIV.setImageURI(mUri)
                        /*Your Asynctask to upload Image using profileImageFilepath*/PostDataAsyncTask().execute(
                            profileImageFilepath
                        )
                    }
                }
            }
        }
    }
    */

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val name = SimpleDateFormat(FILENAMEFORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(this.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        currentRecording = videoCapture.output
            .prepareRecording(this.context, mediaStoreOutputOptions)
            .apply {
                // Enable Audio for recording
                if (
                    PermissionChecker.checkSelfPermission(this@CameraActivity, RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this@CameraActivity)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d("Cam Activity", "--Started--")
                    }
                    is VideoRecordEvent.Status -> {
                        val durationInNanos: Long =
                            recordEvent.recordingStats.recordedDurationNanos;
                        val durationInSeconds: Double = durationInNanos / 1000 / 1000 / 1000.0
                        Log.d("DBR", "duration: " + durationInNanos)
                        //durationTextView.setText("Duration: "+durationInSeconds)
                        if (durationInSeconds >= 5.0) {
                            if (currentRecording != null) {
                                currentRecording!!.stop();
                                Toast.makeText(context, "Saved", Toast.LENGTH_LONG).show()
                                this.onBackPressed()
                            }
                        }
                        if (recordEvent is VideoRecordEvent.Finalize) {
                            // display the captured video
                        }
                        Log.d("Camera Activity", "---record progressing---")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Log.d("Camera Activity", "---record complete---")
                        } else {
                            currentRecording?.close()
                            currentRecording = null
                            Log.e(
                                "Cam Activity",
                                "Video capture ends with error: ${recordEvent.error}"
                            )
                        }

                    }
                }
            }
    }
}