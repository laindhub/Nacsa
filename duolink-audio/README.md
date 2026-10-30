# DuoLink Audio · Android Wi-Fi speaker

Este proyecto compila la app Android en GitHub Actions. Un Android anfitrión captura audio interno cuya aplicación de origen permite la captura oficial de Android (MediaProjection + AudioPlaybackCapture, Android 10+), y lo transmite por Wi-Fi LAN al Android parlante usando PCM 48 kHz estéreo.

## Instalación

Descargá el artifact `DuoLink-Audio-0.4.1-beta-apk` del workflow `DuoLink Audio · APK` en Actions, extraé el archivo `app-debug.apk` e instalalo en ambos teléfonos.

Los dos deben estar conectados a la misma Wi-Fi. En el primero elegí Anfitrión, autorizá la captura y copiá la IP/PIN. En el segundo elegí Parlante, ingresá IP/PIN y conectá. Probá primero el tono de prueba, después Spotify en el anfitrión.

## Límite de compatibilidad

**Spotify puede bloquear la captura del audio interno** por su política de protección de contenidos. El APK no modifica ni elude Spotify, ni garantiza captura del audio de Spotify. Si el indicador muestra 0 mientras Spotify reproduce, Android probablemente no entrega su audio a la app. El tono sirve para diagnosticar el transporte. No usa una segunda cuenta Premium ni Jam. La captura original local y el parlante pueden no estar perfectamente sincronizados, y existir eco entre ambos teléfonos.

**Seguridad**: PIN de sesión de seis dígitos; transporte PCM no cifrado. Utilizar solamente Wi-Fi de confianza. Wi-Fi Direct y sincronización absoluta no están implementados en esta versión.
