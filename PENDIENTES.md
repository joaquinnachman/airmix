# AirMix — pendientes

Todo va en Android e iOS a la vez. Lo de "Próxima versión" se implementa en la compilación de los lunes (o cuando Joaquín diga "compilá"); "Futuro" y "No hacer", nunca solos.

> Esta lista vive en dos lugares que tienen que quedar iguales: el documento `claude/AirMix-pendientes.md` del Proyecto "Duo GYP" y `PENDIENTES.md` en el repo. Si uno no se puede leer, vale el otro; al cambiar uno, se actualiza el otro.

## Regla fija (desde 23/10/2026)
- **En cada update, revisar la versión de celular (pantalla chica)**, no solo la de tablet.
  - Antes de entregar: renderizar o capturar la pantalla principal y las que se tocaron en tamaño celular.
  - Chequear que no haya textos encimados ni cortados y que los faders se puedan usar.
- Desde 0.4.7 el CI saca capturas solo (celular y tablet, Android e iOS) y las adjunta al Release.
- **Compilación semanal automática (desde 11/11/2026):**
  - Todos los lunes 11:32 (hora de Buenos Aires) una tarea programada implementa lo de "Próxima versión", compila, revisa las capturas y publica el Release.
  - La app avisa sola del update.
  - "Futuro", "No hacer" y la clave de firma no se tocan solos.

## Decidido: NO hacer
- Deshacer (descartado el 31/10/2026).
- Ideas del 31/10/2026 no elegidas: fader fino, ayuda con la ganancia, reloj por tema, notas por canal, botones de volumen de la tablet, ficha técnica de la escena, aviso de batería.
- Del 12/11/2026, no elegidas: escena por tema, memorias A/B/C/D, aviso grande de envíos, ring out guiado, registro del show, plantilla GyP, modo escenario oscuro, segunda pantalla, prueba de línea paso a paso.
- Del 01/11/2026, no elegidas: automix, PRE/POST de envíos, safes al cargar escenas, GEQ en buses/main, auriculares/solo.
- Respaldo antes de cargar escena, mute groups, vista "mi monitor" para el dúo, medidor grande del MAIN: propuestos el 31/10/2026, Joaquín no los eligió.
- **Compatibilidad con otras marcas** (Soundcraft, Yamaha, Allen & Heath, etc.) ni un clon "Bis". Joaquín lo descartó el 22/10/2026: es demasiado trabajo. No volver a proponerlo.

## Próxima versión

1. **Proteger el fader del MAIN LR** (elegida el 12/11/2026, idea 8 de la tanda de mejoras).
   - Que un roce no suba ni baje todo el PA. Igual que el MUTE del main: hay que mantener apretado para moverlo.
   - Al tocar el fader del MAIN LR, durante ~0,6 s no se mueve; mientras tanto se ve una barrita que se llena.
   - Una vez "agarrado", se mueve normal mientras no se suelte el dedo. Al soltar, queda protegido de nuevo.
   - Vale en MEZCLA y en el detalle del MAIN.
   - También con SENDS ON FADER, si la tira MAIN muestra el fader del main.
   - Los demás faders (canales, buses) siguen como siempre.
   - Doble toque en el fader del main: no hace nada (que no lo lleve a 0 dB de golpe).
   - Android (FaderView/stripView) e iOS (FaderView/StripView). Revisar en celular.
2. **Clave de firma fuera del repo público** (quedó del pedido del aviso de actualización).
   - `android/airmix-debug.keystore` está en el historial público.
   - Moverla a un secreto de GitHub no alcanza: hace falta una clave nueva, lo que obliga a desinstalar una última vez.
   - Joaquín decide cuándo. **La tarea de los lunes no la hace sola.**

## Futuro (Joaquín dijo "me interesa en el futuro", 01/11/2026; no hacer hasta que lo pida)
1. **Medidor de reducción del compresor y del gate** en cada canal: cuánto está comprimiendo o cerrando.
2. **Salidas físicas:** qué sale por AUX OUT 1/2 de la XR12 y desde qué punto.
3. **Presets de canal:** guardar la configuración de un canal (EQ, compresor, gate…) y cargarla en otro.
4. **Presets de efectos:** guardar un efecto ajustado y recuperarlo sin cargar la escena.

## Para probar en la XR12 (0.5.3)
- **RTA del canal:** al abrir el EQ de un canal, el espectro tiene que ser el de ese canal ("RTA: CH 01" arriba). Al salir, la consola vuelve a mostrar el RTA de antes.
  - Dirección usada: `/-stat/rta/source` (X Air, 0-32 = Ch01..16, Aux, Fx1..4, Bus1..6, Send1..4, LR, MON).
  - En X32 no se toca.
- **Acople:** que ahora marque en el canal que acopla, y en el EQ del MAIN el acople general.
- **COPIAR / PEGAR:**
  - copiar un EQ y pegarlo en otro canal;
  - copiar un canal entero;
  - "ENVÍOS AL BUS" de un bus a otro (monitor de Garko → Palmito).
  - Confirmar que no toca nombre, entrada, GAIN ni 48V.

## Para probar en la XR12 (0.5.0)
- **Cargar escena:** que los links y el 48V queden como la escena (y apagados si no los trae). Si el 48V sigue sin cambiar, mandar las dos .scn.
- **TAP:** que el delay siga el tempo. Va en negras; si el efecto tiene FACTOR, se respeta.
- **Detector de acople:** ajustar la sensibilidad con uso real (hoy: 12 dB sobre las bandas vecinas, sostenido ~0,7 s, arriba de -45 dB).
- **LINE CHECK:** "señal" = más de -50 dB en el último 1,5 s.
- **Candado:** se mantiene apretado ~0,5 s para bloquear y para desbloquear.

## Para confirmar con Joaquín / probar en la XR12 (0.4.7)
- **Desmutear el MAIN LR con un toque:** lo asumimos así, falta que lo confirme.
- **LINK STEREO:** probar que la consola copia fader, mute y EQ al par, y que el link hecho desde la consola o desde otra app aparece solo en AirMix.
- **Escenas .scn:** el cargador no aplica el bloque `config`, así que el link de canales puede no restaurarse al cargar una escena.
- **Chip SOLO:** usa `/-stat/solo`. Si la XR12 no lo manda, el chip no aparece nunca (CLR SOLO sigue en MEDIDORES igual).

## Hecho en 0.5.3 (12/11/2026)
- **COPIAR / PEGAR seteos** (Clipboard.kt / Clipboard.swift):
  - botón 📋 en el detalle: copiar la sección visible o la tira entera;
  - en buses, también "ENVÍOS AL BUS";
  - pegar acá o en varios, con confirmación;
  - chip 📋 arriba.
  - Usa las tablas de las escenas (nodeList + fieldsFor).
  - Nunca: nombre, insrc/rtnsrc/source, trim/rtntrim/rtnsw, 48V.
- **RTA con la fuente de la tira abierta** (X Air `/-stat/rta/source`), con el título "RTA: …" y la fuente de antes restaurada al salir. El detector de acople queda por canal y también en el MAIN.

## Hecho en 0.5.2 (09/11/2026)
- **Aviso de actualización:**
  - Consulta el último Release de GitHub al volver a la app, cada 6 h como mucho.
  - No avisa si está conectada a la consola o con el candado puesto.
- **Android:** baja AirMix.apk (solo de los Releases de joaquinnachman/airmix) y abre el instalador (UpdateProvider propio, sin dependencias).
  - La primera vez pide "Permitir de esta fuente".
- **iOS:** avisa y abre la página del Release.
- ACERCA DE → "Buscar actualización".
- `versionCode` / `CFBundleVersion` = número de build del CI.

## Hecho en 0.5.1 (01/11/2026)
- **EFECTOS como rack** (estilo Mixing Station):
  - un frente de equipo por FX, con orejas y tornillos;
  - display ámbar con el nombre (tocar = cambiar efecto);
  - todas las perillas editables ahí;
  - RETORNO y MUTE.
- **ENTRADA física del canal** en CONFIG → PREAMP (X Air insrc, X32 source) + aviso de entrada repetida.
  - A verificar en la XR12: que la lista IN 01–12 coincida con las entradas reales.
- DCA fuera del riel (se puede sumar a FAVS).
- FAVORITOS → **FAVS ⭐️**.

## Hecho en 0.5.0 (31/10/2026)
1. **Cargar escena limpia:**
   - Links de canales y buses y 48V según la escena; lo que no trae, OFF.
   - Aviso de 48V antes de cargar.
   - Las escenas nuevas guardan `/config/chlink` y `/config/buslink`.
2. **Respaldo de escenas (Android):**
   - Las importadas se copian a Descargas/AirMix.
   - Sin escenas en la app: botón RECUPERAR (selector con selección múltiple).
3. **Reloj SHOW gigante:**
   - Se abre manteniendo apretado el reloj.
   - Toque: cierra.
   - Mantener apretado: reset (antes estaba en el reloj chico).
4. **Corte de conexión:** franja roja + vibración (en iOS vibra solo el iPhone); reconecta solo.
5. **Candado** en la barra de arriba.
6. **TAP** en la pantalla principal: va a todos los efectos con TIME (DLY, 3TAP, 4TAP, D/RV, D/CR, D/FL, MODD).
7. **Clip:** marca roja de 3 s en todos los medidores.
8. **Detector de acople** en el EQ (todos los canales):
   - Línea roja en el RTA.
   - "✂ CORTAR" crea un PEQ de -6 dB, Q 8, en una banda libre.
   - Si ya hay un corte cerca, lo baja 3 dB más.
9. **LINE CHECK** en MEDIDORES.
10. **★ FAVORITOS:**
    - Última capa del riel, hasta 8 tiras.
    - ELEGIR TIRAS.
    - Un par linkeado aparece como una sola tira.

## Hecho en 0.4.8 (25/10/2026)
- Arreglo, a partir de un video de Joaquín: el MUTE del MAIN LR se desmuteaba al soltar el dedo después de mantenerlo 2 s, porque el soltar contaba como toque. Ahora queda muteado.

## Hecho en 0.4.7 (25/10/2026)
1. **Vista de celular:**
   - Tiras compactas.
   - La escala de dB muestra solo las marcas que entran.
   - Barra superior más chica, con chip SOLO.
   - Detalle, EQ, FX y CONFIG con scroll horizontal.
   - Textos de tiras angostas en una línea que se achica.
2. **CLR SOLO** salió del riel. Ahora está en MEDIDORES, arriba a la derecha. Cuando hay algún solo prendido aparece un chip SOLO en la barra superior, que también lo limpia.
3. **NOTAS / TEMAS** en tablet corre las tiras en vez de taparlas. En celular el panel sigue tapando, porque no entra de otra forma.
4. **MUTE del MAIN LR:** hay que mantenerlo 2 s; una barra roja se va llenando. Desmutear es un toque.
5. **LINK STEREO** en CONFIG del canal, con el par en una sola tira "CH 07-08":
   - medidor doble;
   - capas dinámicas;
   - el estado se escucha en vivo.
6. **ACERCA DE**, abajo de todo en ESCENAS:
   - "Creada por Joaquín Nachman · 2026", con su Instagram (@joaquinnachman);
   - errores a joaquinnachman@gmail.com, con botones para escribir el mail y abrir Instagram;
   - nota de independencia y créditos técnicos.
   - Sin mencionar Claraboya.

## Hecho en 0.4.6 (23/10/2026)
- Colores por sector. Paleta elegida por Claude porque Joaquín pidió compilar directo:
  - CH coral, AUX violeta, FX RTN turquesa, BUS celeste, MTX lavanda, FX SEND ámbar, DCA rosa, MAIN blanco.
  - Se usan en el riel, el encabezado, el título de cada tira y el fader.
  - Si no le gusta, se cambia en `Sector` (Ui.kt / Theme.swift).
- Ícono: "airmix" de 19 a 30 (escala 240) y más claro (#B9C2CC). El dibujo sube 18. Dentro de la zona segura de Android (el script lo verifica).
- Versión al lado del logo, leída de versionName / CFBundleShortVersionString.

## Hecho en 0.4.5 (21/10/2026)
- Escenas guardadas local, sin internet: Descargas/AirMix en Android, Archivos → AirMix → Escenas en iOS.
- EXPORTAR con opción local o de selector.

## Hecho en 0.4.4 (21/10/2026), confirmado en la XR12 el 24/10/2026
- GAIN y 48V según la fuente del canal, con diagnóstico visible. Joaquín confirmó que se solucionó.
- Reintento automático de pedidos sin respuesta. Se solucionó lo de FX 1 y FX 4 vacíos.
- Pedidos un poco más espaciados (2 ms).

## Hecho en 0.4.3 (21/10/2026)
1. Panel de FX editable.
2. RTA en el EQ.
3. Nombre con OK verificado.
4. Ícono con texto un poco más grande (13 -> 19).
5. Nombres de bus y de efecto en ENVÍOS.
6. RESET del EQ completo.
7. Colores de bus en toda la app.
8. Lista de TEMAS (.txt / .docx / .doc / .rtf).

### Para verificar con la consola real
- Que el RTA siga al canal abierto.
- Los nombres y rangos de los parámetros de cada efecto.
- En X32, la lista reducida de los FX 5-8.
