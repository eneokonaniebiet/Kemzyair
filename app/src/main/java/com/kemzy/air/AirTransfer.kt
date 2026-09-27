package com.kemzy.air

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object AirTransfer {
    private const val SERVICE_ID="com.kemzy.air.transfer.v1"
    private const val PREFS="kemzy_air_pairing"
    private const val DEVICE_ID="device_id"
    private const val TRUSTED_ID="trusted_id"
    private var ctx:Context?=null
    private var client:ConnectionsClient?=null
    private var endpoint:String?=null
    private var running=false
    private var cb:Callbacks?=null
    private var pendingName:String?=null
    private val incoming=ConcurrentHashMap<Long,Payload>()
    private val outgoing=ConcurrentHashMap<Long,String>()

    interface Callbacks {
        fun status(s:String)
        fun pairingRequest(id:String,name:String,code:String)
        fun deviceFound(name:String)
        fun transferProgress(p:Int)
        fun received(name:String)
    }
    private fun c()=requireNotNull(ctx)
    private fun prefs()=c().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    private fun deviceId():String=prefs().getString(DEVICE_ID,null) ?: UUID.randomUUID().toString().replace("-","").take(12).also{prefs().edit().putString(DEVICE_ID,it).apply()}
    private fun name()="Kémzy Air • "+android.os.Build.MODEL.take(18)+" • "+deviceId()
    private fun parseId(s:String)=s.substringAfterLast("•").trim().takeIf{it.length==12}

    fun start(context:Context,callbacks:Callbacks){
        ctx=context.applicationContext;cb=callbacks;client=Nearby.getConnectionsClient(c())
        if(running)return
        running=true;advertise();discover();cb?.status("Kémzy Air • looking for your paired phone")
    }
    fun stop(){client?.stopAdvertising();client?.stopDiscovery();client?.stopAllEndpoints();endpoint=null;running=false}
    fun acceptPair(id:String){client?.acceptConnection(id,payloadCallback)?.addOnFailureListener{cb?.status("Pairing failed: "+(it.message?:"unknown error"))}}
    fun rejectPair(id:String){client?.rejectConnection(id)}
    fun sendUri(uri:Uri):Boolean{
        val x=ctx ?: return false
        val ep=endpoint ?: run{cb?.status("Pair the other phone first");return false}
        val r=x.contentResolver
        val filename=queryName(r,uri) ?: "Kémzy-"+System.currentTimeMillis()
        val mime=r.getType(uri) ?: guessMime(filename)
        if(mime?.startsWith("image/")!=true && mime?.startsWith("video/")!=true){cb?.status("Photos and videos only");return false}
        Thread{
            try{
                val f=File(x.cacheDir,"air_send_"+System.currentTimeMillis()+"_"+sanitize(filename))
                r.openInputStream(uri).use{input->requireNotNull(input);FileOutputStream(f).use{output->input.copyTo(output,65536)}}
                val p=Payload.fromFile(f);p.setFileName(filename);p.setSensitive(true);outgoing[p.id]=f.absolutePath
                cb?.status("Sending $filename…")
                client?.sendPayload(ep,p)?.addOnFailureListener{outgoing.remove(p.id)?.let{File(it).delete()};cb?.status("Transfer could not start: "+(it.message?:"radio error"))}
            }catch(e:Exception){cb?.status("Could not prepare file: "+(e.message?:"unknown error"))}
        }.start();return true
    }
    private fun advertise(){
        val o=AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client?.startAdvertising(name(),SERVICE_ID,lifecycle, o)?.addOnFailureListener{cb?.status("Advertising unavailable: "+(it.message?:"check Nearby permissions"))}
    }
    private fun discover(){
        val o=DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client?.startDiscovery(SERVICE_ID,discovery,o)?.addOnFailureListener{cb?.status("Discovery unavailable: "+(it.message?:"check Nearby permissions"))}
    }
    private val discovery=object:EndpointDiscoveryCallback(){
        override fun onEndpointFound(id:String,info:DiscoveredEndpointInfo){
            val n=info.endpointName ?: return
            val rid=parseId(n) ?: return
            if(rid==deviceId()||endpoint!=null)return
            cb?.deviceFound(n)
            val trusted=prefs().getString(TRUSTED_ID,null)
            if(trusted==null||trusted==rid){
                endpoint=id;pendingName=n
                client?.requestConnection(name(),id,lifecycle)?.addOnFailureListener{endpoint=null}
            }
        }
        override fun onEndpointLost(id:String){if(id==endpoint){endpoint=null;cb?.status("Paired phone moved out of range")}}
    }
    private val lifecycle=object:ConnectionLifecycleCallback(){
        override fun onConnectionInitiated(id:String,info:ConnectionInfo){
            pendingName=info.endpointName
            cb?.pairingRequest(id,info.endpointName ?: "Kémzy phone",info.authenticationDigits ?: "----")
        }
        override fun onConnectionResult(id:String,result:ConnectionResolution){
            if(result.status.isSuccess){
                endpoint=id
                parseId(pendingName?:"")?.let{prefs().edit().putString(TRUSTED_ID,it).apply()}
                cb?.status("Connected • local Air Transfer ready")
            }else{endpoint=null;cb?.status("Pairing was not completed")}
        }
        override fun onDisconnected(id:String){
            if(id==endpoint)endpoint=null
            cb?.status("Phone disconnected • Kémzy keeps looking")
            if(running){advertise();discover()}
        }
    }
    private val payloadCallback=object:PayloadCallback(){
        override fun onPayloadReceived(id:String,payload:Payload){if(payload.type==Payload.Type.FILE)incoming[payload.id]=payload}
        override fun onPayloadTransferUpdate(id:String,u:PayloadTransferUpdate){
            val total=max(1L,u.totalBytes)
            cb?.transferProgress((u.bytesTransferred.coerceAtMost(total)*100L/total).toInt())
            if(u.status==PayloadTransferUpdate.Status.SUCCESS){
                incoming.remove(u.payloadId)?.let{saveIncoming(it)}
                outgoing.remove(u.payloadId)?.let{File(it).delete()}
            }else if(u.status==PayloadTransferUpdate.Status.FAILURE||u.status==PayloadTransferUpdate.Status.CANCELED){
                outgoing.remove(u.payloadId)?.let{File(it).delete()};cb?.status("Transfer failed")
            }
        }
    }
    private fun saveIncoming(p:Payload){
        val x=ctx ?: return
        Thread{
            try{
                val uri=p.asFile().asUri()
                val mime=x.contentResolver.getType(uri) ?: "application/octet-stream"
                val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let{"."+it} ?: ""
                val filename="Kémzy-"+System.currentTimeMillis()+ext
                val image=mime.startsWith("image/")
                val collection=if(android.os.Build.VERSION.SDK_INT>=29){
                    if(image)MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }else{
                    if(image)MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val v=android.content.ContentValues().apply{
                    put(MediaStore.MediaColumns.DISPLAY_NAME,filename);put(MediaStore.MediaColumns.MIME_TYPE,mime)
                    if(android.os.Build.VERSION.SDK_INT>=29)put(MediaStore.MediaColumns.IS_PENDING,1)
                }
                val out=x.contentResolver.insert(collection,v) ?: error("MediaStore insert failed")
                x.contentResolver.openInputStream(uri).use{input->requireNotNull(input);x.contentResolver.openOutputStream(out,"w").use{output->requireNotNull(output);input.copyTo(output,65536)}}
                if(android.os.Build.VERSION.SDK_INT>=29){v.clear();v.put(MediaStore.MediaColumns.IS_PENDING,0);x.contentResolver.update(out,v,null,null)}
                x.contentResolver.delete(uri,null,null);p.close();cb?.received(filename);cb?.status("Received $filename • saved to Gallery")
            }catch(e:Exception){p.close();cb?.status("Received file could not be saved: "+(e.message?:"unknown error"))}
        }.start()
    }
    private fun queryName(r:ContentResolver,u:Uri):String?{r.query(u,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())return it.getString(0)};return null}
    private fun guessMime(n:String)=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substringAfterLast('.', "").lowercase())
    private fun sanitize(s:String)=s.replace(Regex("[^A-Za-z0-9._-]"),"_")
}