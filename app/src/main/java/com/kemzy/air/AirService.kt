package com.kemzy.air
import android.app.*
import android.content.Intent
import android.os.IBinder
class AirService:Service(){override fun onCreate(){super.onCreate();val ch=NotificationChannel("kemzy_air","Kémzy Air",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager::class.java).createNotificationChannel(ch);startForeground(7,Notification.Builder(this,"kemzy_air").setContentTitle("Kémzy Air active").setContentText("Air gestures are enabled").setSmallIcon(android.R.drawable.ic_menu_compass).build())};override fun onStartCommand(i:Intent?,f:Int,id:Int)=START_STICKY;override fun onBind(i:Intent?):IBinder?=null}