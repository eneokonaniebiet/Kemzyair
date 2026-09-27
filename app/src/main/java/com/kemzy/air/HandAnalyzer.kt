package com.kemzy.air
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class HandAnalyzer(private val context:Context,private val report:(String)->Unit):ImageAnalysis.Analyzer{
 private var landmarker:HandLandmarker?=null;private val loading=AtomicBoolean(false);private var last=0L
 init{ensureModel()}
 private fun ensureModel(){val f=File(context.filesDir,"hand_landmarker.task");if(f.exists()){create(f);return};if(!loading.compareAndSet(false,true))return
  Thread{try{URL("https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task").openStream().use{input->f.outputStream().use{input.copyTo(it)}};create(f);report("Hand model ready • on-device")}catch(err:Exception){report("Hand model download failed; connect once")}finally{loading.set(false)}}.start()}
 private fun create(f:File){val base=BaseOptions.builder().setModelAssetPath(f.absolutePath).build();val o=HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(base).setRunningMode(RunningMode.IMAGE).setNumHands(1).build();landmarker=HandLandmarker.createFromOptions(context,o)}
 override fun analyze(image:ImageProxy){val bmp=Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888);image.close();val lm=landmarker?:return;val r=try{lm.detect(BitmapImageBuilder(bmp).build())}catch(_:Exception){return};val h=r.landmarks().firstOrNull()?:run{if(System.currentTimeMillis()-last>1000){report("Show one hand to camera");last=System.currentTimeMillis()};return};val i=h[8];val pinch=dist(h[4].x(),h[4].y(),h[8].x(),h[8].y())<.07f;val point=h[8].y()<h[6].y()&&h[12].y()>h[10].y();val open=h[8].y()<h[6].y()&&h[12].y()<h[10].y()&&h[16].y()<h[14].y()&&h[20].y()<h[18].y();val s=when{open->"OPEN • release";pinch->"GRAB/PINCH • selected";point->"POINT • x="+i.x()+" y="+i.y();else->"HAND • ready"};if(System.currentTimeMillis()-last>150){report(s);last=System.currentTimeMillis()}}
 private fun dist(a:Float,b:Float,c:Float,d:Float)=sqrt((a-c)*(a-c)+(b-d)*(b-d))
}