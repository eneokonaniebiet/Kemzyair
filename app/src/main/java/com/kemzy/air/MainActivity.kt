package com.kemzy.air
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
class MainActivity:ComponentActivity(){
 private lateinit var preview:PreviewView;private lateinit var status:TextView;private val executor=Executors.newSingleThreadExecutor()
 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){startCamera()}
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);preview=findViewById(R.id.preview);status=findViewById(R.id.status)
  findViewById<Button>(R.id.gallery).setOnClickListener{startActivity(Intent(Intent.ACTION_PICK).apply{type="image/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)})}
  val p=mutableListOf(Manifest.permission.CAMERA);if(android.os.Build.VERSION.SDK_INT>=31){p+=Manifest.permission.BLUETOOTH_SCAN;p+=Manifest.permission.BLUETOOTH_CONNECT;p+=Manifest.permission.BLUETOOTH_ADVERTISE};if(android.os.Build.VERSION.SDK_INT>=33){p+=Manifest.permission.NEARBY_WIFI_DEVICES;p+=Manifest.permission.POST_NOTIFICATIONS};permissions.launch(p.toTypedArray())
 }
 private fun startCamera(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){status.text="Camera permission required";return};val f=ProcessCameraProvider.getInstance(this);f.addListener({val c=f.get();val p=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider};val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();a.setAnalyzer(executor,HandAnalyzer(this){s->runOnUiThread{status.text=s}});c.unbindAll();c.bindToLifecycle(this,CameraSelector.DEFAULT_FRONT_CAMERA,p,a);status.text="Kémzy Air • Point ☝️ • Grab ✊ • Release 🖐️";ContextCompat.startForegroundService(this,Intent(this,AirService::class.java))},ContextCompat.getMainExecutor(this))}
 override fun onDestroy(){executor.shutdown();super.onDestroy()}
}