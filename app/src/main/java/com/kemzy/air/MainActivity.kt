package com.kemzy.air

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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

class MainActivity:ComponentActivity(),AirTransfer.Callbacks{
 private lateinit var preview:PreviewView
 private lateinit var status:TextView
 private val executor=Executors.newSingleThreadExecutor()
 private var selected:Uri?=null
 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){startCamera();startAir()}
 private val picker=registerForActivityResult(ActivityResultContracts.GetContent()){u->if(u!=null){selected=u;status.text="Gallery item selected • PINCH/GRAB to send"}}
 override fun onCreate(b:Bundle?){
  super.onCreate(b);setContentView(R.layout.activity_main)
  preview=findViewById(R.id.preview);status=findViewById(R.id.status)
  findViewById<Button>(R.id.gallery).setOnClickListener{picker.launch("image/* video/*")}
  findViewById<Button>(R.id.pair).setOnClickListener{startAir()}
  findViewById<Button>(R.id.send).setOnClickListener{selected?.let{AirTransfer.sendUri(it)}?:run{status.text="Choose a Gallery photo or video first"}}
  val p=mutableListOf(Manifest.permission.CAMERA)
  if(Build.VERSION.SDK_INT>=31){p+=Manifest.permission.BLUETOOTH_SCAN;p+=Manifest.permission.BLUETOOTH_CONNECT;p+=Manifest.permission.BLUETOOTH_ADVERTISE}
  if(Build.VERSION.SDK_INT>=33){p+=Manifest.permission.NEARBY_WIFI_DEVICES;p+=Manifest.permission.POST_NOTIFICATIONS}
  permissions.launch(p.toTypedArray())
 }
 private fun startAir(){
  AirTransfer.start(this,this)
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)try{ContextCompat.startForegroundService(this,Intent(this,AirService::class.java))}catch(_:Exception){}
 }
 private fun startCamera(){
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){status.text="Camera permission required";return}
  val f=ProcessCameraProvider.getInstance(this)
  f.addListener({
   val cp=f.get()
   val pv=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider}
   val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
   a.setAnalyzer(executor,HandAnalyzer(this){g->runOnUiThread{status.text=g;if((g.startsWith("GRAB")||g.startsWith("PINCH"))&&selected!=null){AirTransfer.sendUri(selected!!);selected=null}}})
   cp.unbindAll();cp.bindToLifecycle(this,CameraSelector.DEFAULT_FRONT_CAMERA,pv,a)
   status.text="Kémzy Air • POINT ☝️ • GRAB/PINCH ✊ • OPEN/RELEASE 🖐️"
  },ContextCompat.getMainExecutor(this))
 }
 override fun status(s:String){runOnUiThread{status.text=s}}
 override fun pairingRequest(id:String,name:String,code:String){runOnUiThread{AlertDialog.Builder(this).setTitle("Pair Kémzy Air").setMessage("Connect to $name?\n\nConfirm this code matches on both phones:\n$code").setPositiveButton("Pair"){_,_->AirTransfer.acceptPair(id)}.setNegativeButton("Cancel"){_,_->AirTransfer.rejectPair(id)}.setCancelable(false).show()}}
 override fun deviceFound(name:String){runOnUiThread{status.text="Found nearby: $name"}}
 override fun transferProgress(p:Int){runOnUiThread{status.text="Air Transfer • $p%"}}
 override fun received(name:String){runOnUiThread{status.text="✓ $name saved to Gallery"}}
 override fun onDestroy(){executor.shutdown();super.onDestroy()}
}