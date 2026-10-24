# AirMix — control liviano para Behringer XR12 / XR16 / XR18 / X32

## Novedades 0.4.6
- **Colores por sector.** Cada sección tiene su color en el riel, el encabezado, el título de cada tira y el fader:
  - canales: coral
  - AUX: violeta
  - FX returns: turquesa
  - buses: celeste
  - matrices: lavanda
  - FX sends: ámbar
  - DCA: rosa
  - main: blanco
  - El color de scribble de cada canal y el color del bus en los envíos se mantienen.
- **Ícono** con "airmix" bastante más grande y más claro.
- **Versión** al lado del logo, arriba a la izquierda.

## Novedades 0.4.5
- **Escenas guardadas en la tablet, sin internet.** Al tocar "LEER CONSOLA Y GUARDAR", la escena queda en la app y además en **Descargas/AirMix** (se ve en "Mis archivos"). Ya no se abre Google Drive.
  - El botón EXPORTAR de cada escena ofrece "Guardar en Descargas/AirMix (tablet)" o "Elegir carpeta o app…".
  - En iPad/iPhone queda en Archivos → En mi iPad → AirMix → Escenas.

## Novedades 0.4.4
- **GAIN según la fuente del canal.** La perilla GAIN y el 48V ahora van al preamp de la entrada que tiene asignada el canal ("Fuente"), no al número de canal. Si la fuente no tiene preamp (USB, AUX), lo dice en lugar de mostrar una perilla muerta.
  - Debajo se ve qué preamp se está usando, y si la consola no contesta aparece "la consola no responde" en amarillo. Si te pasa, pasame lo que dice esa línea.
- **Reintento de pedidos.** Si la consola no contestó algo (por ejemplo, el efecto cargado en un FX), la app lo vuelve a pedir cada 1,5 s, hasta 6 veces. Los pedidos salen un poco más espaciados para no saturar la consola.

## Novedades 0.4.3
- **EFECTOS editables.** Elegís el FX a la izquierda y a la derecha cambiás el tipo de efecto y todos sus parámetros, con valores reales (ms, s, Hz, dB…). También el retorno, el send master y el mute.
  - Los rangos salen de las mismas tablas que usan X-Air Edit y las escenas .scn.
  - Verificar en la consola: en X32, los FX 5-8 usan la lista reducida de efectos (sin reverbs).
- **RTA en el EQ.** El analizador de la consola se dibuja detrás de la curva del canal o bus. Se prende y apaga con el botón RTA.
  - Al abrir el EQ, la app selecciona ese canal en la consola, porque el RTA muestra el canal seleccionado.
  - Verificar en la consola: que el RTA siga al canal abierto (si no, revisar la fuente del RTA en X-Air Edit).
- **RESET del EQ** con confirmación. Ahora deja el EQ plano de verdad: 0 dB y tipo, frecuencia y Q de fábrica en todas las bandas. Antes solo ponía las ganancias en 0, así que si una banda era un corte o se había movido la frecuencia, parecía que no hacía nada.
- **Nombre con OK.** El nombre del canal se manda al tocar OK (o "Listo" en el teclado). Después la app se lo vuelve a pedir a la consola para confirmar: muestra ✓ si quedó, reintenta hasta 3 veces y avisa si no pudo.
- **Colores.** Paleta de 16 colores en CONFIG para canales y buses. El color del bus se ve en toda la app:
  - en los envíos de cada canal;
  - en el selector de "sends on fader";
  - en el encabezado y el fader cuando estás mandando a ese bus.
- **ENVÍOS con nombres.** Cada columna muestra el nombre del bus ("1-JUANCITO") con su color, y en FX el efecto cargado ("FX1 · Hall Reverb").
- **TEMAS.** Nueva pestaña en el panel de notas con la lista de temas de la banda.
  - Se carga desde un archivo .txt, .docx, .doc o .rtf, con un tema por línea. La numeración ("1.", "02 -") se ignora sola.
  - Lo que va después de " // " o de una tabulación queda como nota del tema. Ejemplo: `Bésame Mucho // Am, arranca Garko`.
  - Se edita a mano: agregar, ordenar, nota por tema y borrar.
  - Tocando un tema queda marcado como el que suena. ANTERIOR / SIGUIENTE para moverte durante el show.
- **Ícono** con el "airmix" más grande.

## Novedades 0.3
- **ESCENAS (.scn).** Nueva pestaña para guardar y cargar escenas.
  - Guardar lee la consola completa y crea un .scn compatible con X-Air Edit / X32-Edit. Queda en la tablet y te ofrece exportarlo a Drive, Descargas, etc.
  - Cargar abre un .scn (tuyo o de X-Air Edit) y lo manda a la consola. Antes de cargar, la app verifica con la consola que entiende bien cada campo. Lo que no puede verificar queda como estaba y te lo informa.
  - Todavía no carga parámetros internos de los efectos ni el ruteo.
- **Cronómetro de show** arriba en el riel.
  - Tocá para arrancar o pausar. Mantené apretado para ponerlo en cero.
  - Sigue contando aunque cierres la app.
- **NOTAS.** Panel lateral que se abre desde el riel.
  - Se guarda solo.
  - "+ MARCA" inserta el tiempo del show, por ejemplo [42:10], para anotar en qué momento pasó algo.

## Novedades 0.2
- **Multi-consola.** XR12, XR16, XR18/X18/MR18 y X32/M32. La app detecta el modelo y el puerto sola (X Air usa 10024, X32 usa 10023) y arma las capas, buses, matrices, DCAs y envíos que correspondan.
- **MUTE FX** en el riel izquierdo, siempre a mano: mutea todos los efectos de un toque para hablar entre tema y tema, y al volver a tocarlo restaura solo los que estaban abiertos.
  - En X Air mutea los FX SEND masters, así las colas de reverb se apagan naturales.
  - En X32 mutea los FX returns.
- **Diseño propio.** Paleta grafito y coral, tiras más limpias, fader con escala en dB, perillas tipo anillo y detalle de canal con pestañas arriba.


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
