plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace="com.kemzy.air"; compileSdk=35
 defaultConfig { applicationId="com.kemzy.air"; minSdk=26; targetSdk=35; versionCode=1; versionName="0.1.0" }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("androidx.activity:activity-ktx:1.10.1")
 implementation("androidx.lifecycle:lifecycle-service:2.8.7")
 implementation("androidx.camera:camera-core:1.4.2")
 implementation("androidx.camera:camera-camera2:1.4.2")
 implementation("androidx.camera:camera-lifecycle:1.4.2")
 implementation("androidx.camera:camera-view:1.4.2")
 implementation("com.google.mediapipe:tasks-vision:0.10.26")
 implementation("com.google.android.gms:play-services-nearby:19.3.0")
}
