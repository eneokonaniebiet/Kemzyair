# Kémzy Air

Local-first Android air interaction foundation for Gallery transfer.

Current implementation:
- Front-camera MediaPipe hand landmarks.
- Point, pinch/grab and open-hand recognition.
- Gallery picker foundation.
- Persistent foreground service.
- Nearby-device permissions.
- No cloud backend.

The hand model is downloaded once from Google's MediaPipe model hosting and then stored in app-private storage. After that, hand recognition is local/offline.

Build with Android Studio or ./gradlew :app:assembleDebug.

Next layer: Nearby Connections discovery/pairing, trusted-phone storage, and actual photo/video byte transfer.