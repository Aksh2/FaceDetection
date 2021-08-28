package com.assignment.facedetection

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.assignment.facedetection.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private val REQUIRED_PERMISSION = arrayOf(Manifest.permission.CAMERA)
        private val REQUEST_CODE_PERMISSION = 10
        private val TAG = MainActivity::class.java.simpleName
    }

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


    fun startCamera(){
        initializeCamera()
    }

    fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }}
            return if(mediaDir != null && mediaDir.exists())
                mediaDir else filesDir
        }
    

    fun bindView(cameraProvider: ProcessCameraProvider){
       val preview: Preview = Preview.Builder().build()
           .also {
               it.setSurfaceProvider(binding.viewFinderPv.surfaceProvider)

           }

        val cameraSelectorType: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        try{
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this,cameraSelectorType,preview)

        }catch (e: Exception){
            Log.e(TAG, "Use case binding failed",e)
        }

    }

    fun allPermissionsGranted() = REQUIRED_PERMISSION.all{
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
}