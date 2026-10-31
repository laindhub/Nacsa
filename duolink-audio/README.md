# DuoLink Direct · 0.5.0 beta

Aplicación Android 10+ para usar un segundo celular como parlante de audio interno capturable mediante **Wi-Fi Direct (P2P)**, sin conectarse a un router y sin solicitar una IP manual.

## Usar en un auto o transporte

1. Instalá el APK en los dos teléfonos. Activá **Wi-Fi** en ambos. No hace falta tener una red Wi-Fi disponible ni Internet para la transmisión.
2. En el **anfitrión** tocá **Crear conexión cercana** y aceptá los permisos de dispositivos cercanos. Se crea un grupo P2P con el anfitrión como dueño. Luego **Iniciar captura de audio**, confirmá el permiso del sistema y copiá el PIN.
3. En el teléfono **parlante**, habilitá Ubicación en Android (para búsqueda P2P) y tocá **Buscar celulares cercanos**. Al ver el nombre del anfitrión, ingresá su PIN de seis dígitos y tocá su nombre. Aceptá los avisos de conexión del sistema que aparezcan.
4. Reproducí el **tono de prueba** en el anfitrión para confirmar que ambos dispositivos transmiten. Abrí Spotify para verificar si esa versión permite capturar el audio.
5. Si Spotify impide la captura, el tono se transmitirá pero la música no. Esto es una limitación del reproductor de origen, no de Wi-Fi Direct.

**La app no usa Spotify Jam ni requiere una segunda cuenta Premium.** Para viajes, es mejor probar canciones de Spotify descargadas en el anfitrión. El uso simultáneo de datos móviles para música en streaming y Wi-Fi Direct depende del modelo y la implementación del fabricante. El audio de Spotify puede estar protegido y dar silencio aun con una conexión estable.

## Implementación

- `WifiP2pManager.createGroup`: el anfitrión crea la red directa.
- `WifiP2pManager.discoverPeers/connect`: el parlante encuentra y se conecta a un Android cercano.
- `requestConnectionInfo`: dirección IP del dueño del grupo asignada automáticamente por Wi-Fi Direct.
- `AudioPlaybackCapture/MediaProjection`: captura **solo** audio interno de apps que autorizan la función oficial de Android.
- TCP local, PCM 48 kHz estéreo, PIN de sesión de 6 dígitos y búfer seleccionable.

### Advertencias

- Sin garantía de funcionamiento en todos los fabricantes/ROM. Wi-Fi Direct puede estar limitado o deshabilitado.
- El búfer introduce latencia en el parlante y puede producir eco frente al sonido local. No se promete sincronización perfecta de ambos altavoces.
- La negociación P2P depende del sistema: algunos Android requieren aceptar una invitación o habilitar ubicación.
- La captura Spotify puede estar bloqueada por la política DRM/captura de su aplicación.
- Los datos PCM no llevan cifrado propio, pero Wi-Fi Direct negocia protección WPA2. El PIN autentica al cliente.
- No probado físicamente en dos teléfonos. Compilación disponible mediante GitHub Actions.

