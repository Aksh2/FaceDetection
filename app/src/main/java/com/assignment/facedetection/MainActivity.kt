package com.assignment.facedetection

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
 import com.assignment.facedetection.databinding.ActivityMainBinding
import com.assignment.facedetection.viewmodel.MainViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.concurrent.schedule
import kotlin.coroutines.coroutineContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        private val REQUIRED_PERMISSION = arrayOf(Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE)
        private val REQUEST_CODE_PERMISSION = 10
        private val TAG = MainActivity::class.java.simpleName
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        val NOTIFICATION_CHANNEL_ID = "101"

    }

    val mainViewModel : MainViewModel by viewModels()


    private var imageCapture: ImageCapture? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var binding: ActivityMainBinding
    private lateinit var notificationManager : NotificationManager
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var  outputDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if(allPermissionsGranted())
            initializeCamera()
        else
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSION, REQUEST_CODE_PERMISSION)

       initializeObserver()
        setUpNotification()
    }

    private fun initializeCamera(){
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindView(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        outputDirectory= getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun notifyToOpenImage(imageFile: File){
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val intent = Intent().also {
            it.action = ACTION_VIEW
            Log.d("PATH","absolute : ${imageFile.absolutePath} path ${imageFile.path}")
            it.data = Uri.fromFile(imageFile)
            it.type = "image/*"
        }
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, 0)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Face Detection")
            .setAutoCancel(true)
            .setSound(defaultSound)
            .setContentText("Face Detection Complete ! Click here to open the image.")
            .setContentIntent(pendingIntent)
            .setWhen(System.currentTimeMillis())
            .setPriority(Notification.PRIORITY_MAX)

        notificationManager.notify(1, notificationBuilder.build())
    }


    private fun startCamera(){

        initializeCamera()

    }

    private fun takePhoto(){
        imageCapture ?: return
        val photoFile = generatePhotoFile()
        val outputOptions = generateOuputOptions(photoFile)
        initializeImageCapture(outputOptions, photoFile)
        notifyToOpenImage(photoFile)

    }

    private fun generatePhotoFile(): File{
        return File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis())+".jpg")

    }

    private fun setUpNotification() {


        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Notification",
                NotificationManager.IMPORTANCE_HIGH
            )

            notificationChannel.description = "Face Detection"
            notificationChannel.enableLights(true)
            notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            notificationChannel.enableVibration(true)
            notificationManager.createNotificationChannel(notificationChannel)



        }


    }



    private fun initializeImageCapture(outputOptions: ImageCapture.OutputFileOptions, photoFile: File){
        imageCapture?.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback{

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg ="Photo Capture Success"
                Log.d(TAG,"$msg: $savedUri")
                showToast(msg)

                writeToGallery(photoFile)

            }

            override fun onError(exception: ImageCaptureException) {
                val errorMsg = "Failed to capture image"
                Log.e(TAG,"$errorMsg with exception: $exception")
                showToast(errorMsg)
            }
        })
    }

    private fun writeToGallery(photoFile: File){
        val values = ContentValues();

        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.DATA, photoFile.absolutePath);

        applicationContext.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun generateOuputOptions(photoFile: File): ImageCapture.OutputFileOptions{
        return ImageCapture.OutputFileOptions
            .Builder(photoFile).build()
    }

    private fun initializeObserver(){
        mainViewModel.captureLiveData.observe(this,{
            when(it){
                true-> {
                    takePhoto()

                }
            }
        })
    }


    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }}
            return if(mediaDir != null && mediaDir.exists())
                mediaDir else filesDir
        }
    

    private fun bindView(cameraProvider: ProcessCameraProvider){
       val preview: Preview = Preview.Builder().build()
           .also {
               it.setSurfaceProvider(binding.viewFinderPv.surfaceProvider)

           }

        val cameraSelectorType: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        imageCapture = ImageCapture.Builder().build()

        val imageAnalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
            it.setAnalyzer(cameraExecutor, ImageAnalyzer(mainViewModel))
        }
        try{
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this,
                cameraSelectorType,preview,imageCapture, imageAnalyzer)

        }catch (e: Exception){
            Log.e(TAG, "Use case binding failed",e)
        }

    }

   private fun allPermissionsGranted() = REQUIRED_PERMISSION.all{
        ContextCompat.checkSelfPermission(baseContext,it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_CODE_PERMISSION){
            if(allPermissionsGranted()){
                startCamera()
            }else{
                Toast.makeText(this,"Permissions not granted by the user.",Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

class ImageAnalyzer(private val mainViewModel: MainViewModel): ImageAnalysis.Analyzer {
    val detector = FaceDetection.getClient(intializeFaceDetectorOptions())


        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val imageInput = InputImage.fromMediaImage(
                    mediaImage, imageProxy.imageInfo
                        .rotationDegrees
                )

                detector.process(imageInput).addOnSuccessListener { face ->
                    Log.d("Face Detection", "Face detection success ${face}")
                        mainViewModel.captureImage()

                }
                    .addOnFailureListener { e ->
                        Log.d(
                            "Face Detection", "Face detection failed" +
                                    "with error $e"
                        )
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }

            }

        }

        private fun intializeFaceDetectorOptions(): FaceDetectorOptions {
            return FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        }
    }



}



