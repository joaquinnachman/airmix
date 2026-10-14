# AirMix — control liviano para Behringer XR12 (y X Air en general)

App Android nativa en Kotlin, con la distribución de pantallas de MX-MIX, hecha desde cero.
No usa código ni gráficos de Behringer. Habla directo con la consola por OSC, puerto UDP 10024.

## Por qué no se debería poner lenta

- **Sin librerías externas.** Solo usa el SDK de Android, sin Compose ni AndroidX. El APK pesa unos cientos de KB.
- **Medidores sin asignar memoria.** Se leen en un buffer fijo y se dibujan desde un único "tick" a 30 fps, que solo redibuja los que están en pantalla.
- **Envíos coalescidos.** Si movés un fader rápido, a la consola sale solo el último valor y no se acumula una cola.
- **Suscripciones atadas a la pantalla.** Cada control se suscribe al aparecer y se desuscribe al cambiar de pantalla. Este tipo de fuga es la sospechosa de lo que le pasa a MX-MIX.

## Cómo compilarla e instalarla

1. Instalá **Android Studio** (gratis) en la PC.
2. *File → Open* → elegí esta carpeta (`AirMix`). La primera vez descarga Gradle y el SDK, así que tarda unos minutos.
3. Opción A, directo a la tablet:
   - Activá *Opciones de desarrollador → Depuración USB* en la A8 y conectala por USB.
   - Apretá ▶ **Run**.
4. Opción B, un APK para pasar por WhatsApp o Drive:
   - *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
   - El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.
   - Instalalo en la tablet. Tenés que permitir "instalar apps de origen desconocido".

Si Android Studio dice que falta algo al sincronizar, aceptá lo que te ofrece instalar (SDK 35, build tools).

## Primer uso

1. Conectá la tablet al WiFi del router de la consola.
2. Abrí AirMix. Aparece "Conectar a la consola", con la lista de las que encontró en la red, o podés escribir la IP a mano.
3. La IP queda guardada y la próxima vez se conecta sola.
4. El ícono de WiFi arriba a la derecha está verde si hay conexión y rojo si no. Tocalo para cambiar de consola.

## Qué tiene esta versión (0.1)

- **HOME.**
  - Capas CH 1-8, CH 9-12, AUX, FX RETURN, BUS y DCA.
  - Por tira: nombre y color del scribble, paneo, M/S, fader, medidor, MUTE y dB.
  - Main Out a la derecha.
  - **SENDS TO**: modo "sends on fader" para Bus 1-2 y FX 1-4. Los faders se ponen cian y la tira de la derecha pasa a ser el master de ese bus.
- **Detalle de canal**, tocando el nombre de la tira:
  - **CONFIG:** nombre, color, mute, solo, gain, trim, low cut, 48V, phase, asignación a LR, DCA y mute groups.
  - **GATE:** tipo, threshold, range, attack, hold, release, key filter y curva.
  - **DYN:** comp/exp, threshold, ratio, knee, gain, attack, hold, release, mix, envelope, detection, filtro y curva.
  - **EQ:** 4 bandas (6 en buses y master) con gráfico arrastrable, tipo, freq, gain, Q, low cut y RST ALL.
  - **SENDS:** faders de envío a cada bus y FX.
  - Las flechas ‹ › pasan de canal y DONE vuelve.
- **EFFECTS:** tipo cargado en cada FX, nivel de retorno, send master y mute del retorno.
- **METERS:** los 40 medidores de la consola en una pantalla.
- **Gestos:**
  - Faders y perillas se mueven arrastrando, no saltan al tocarlos.
  - **Doble tap** vuelve al valor por defecto: 0 dB en faders, centro en paneos.

Todavía no tiene: RECORDER, ROUTING, edición de parámetros de efectos, RTA ni gráficos de GR.

## Para verificar en el primer ensayo (con MX-MIX a mano de respaldo)

Escribí esto según el protocolo público de X Air, pero no lo pude probar contra una XR12 real. Revisá:

1. **Cantidad de canales:** ¿la capa CH 9-12 muestra lo que esperás (click y pista en 11/12)?
2. **Medidores:** ¿cada tira muestra el nivel de su canal? Si están corridos, avisame y ajusto los índices.
3. **SOLO:** ¿el botón S solea el canal correcto?
4. **TRIM** en canales de línea: ¿se mueve el trim real?
5. **Nombres de efectos:** ¿el tipo que muestra EFFECTS coincide con el de la consola?
6. **Prueba de aguante:** dejala conectada una hora o más moviendo cosas, y mirá si sigue fluida.

Cualquier cosa que no ande, pasame captura y qué hiciste, y lo corrijo.
