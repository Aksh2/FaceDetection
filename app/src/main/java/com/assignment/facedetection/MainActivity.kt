package com.assignment.facedetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
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

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        private val REQUIRED_PERMISSION = arrayOf(Manifest.permission.CAMERA)
        private val REQUEST_CODE_PERMISSION = 10
        private val TAG = MainActivity::class.java.simpleName
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    val mainViewModel : MainViewModel by viewModels()


    private var imageCapture: ImageCapture? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var binding: ActivityMainBinding

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
    }

    fun initializeCamera(){
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindView(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        outputDirectory= getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }


    private fun startCamera(){

        initializeCamera()

    }

    private fun takePhoto(){
        imageCapture ?: return
        val photoFile = generatePhotoFile()
        val outputOptions = generateOuputOptions(photoFile)
        initializeImageCapture(outputOptions, photoFile)
    }

    private fun generatePhotoFile(): File{
        return File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis())+".jpg")

    }

    private fun initializeImageCapture(outputOptions: ImageCapture.OutputFileOptions, photoFile: File){
        imageCapture?.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback{

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg ="Photo Capture Success"
                Log.d(TAG,"$msg: $savedUri")
                showToast(msg)
            }

            override fun onError(exception: ImageCaptureException) {
                val errorMsg = "Failed to capture image"
                Log.e(TAG,"$errorMsg with exception: $exception")
                showToast(errorMsg)
            }
        })
    }

    private fun generateOuputOptions(photoFile: File): ImageCapture.OutputFileOptions{
        return ImageCapture.OutputFileOptions
            .Builder(photoFile).build()
    }

    private fun initializeObserver(){
        mainViewModel.captureLiveData.observe(this,{
            when(it){
                true-> takePhoto()
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

        val imageAnalyzer = ImageAnalysis.Builder().build().also {
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


        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val imageInput = InputImage.fromMediaImage(
                    mediaImage, imageProxy.imageInfo
                        .rotationDegrees
                )
                val detector = FaceDetection.getClient(intializeFaceDetectorOptions())
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



