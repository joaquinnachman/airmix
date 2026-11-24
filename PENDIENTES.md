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
- Del 22/11/2026, no elegidas: conexión sola a la última consola, aviso de canal con señal muteado, chequeo antes del show, comparar escena contra la consola, escenas en Google Drive, botón MUTE FX (en realidad ya existía en el riel; se queda como está), compatibilidad con Behringer Wing.
- **Compatibilidad con otras marcas** (Soundcraft, Yamaha, Allen & Heath, etc.) ni un clon "Bis". Joaquín lo descartó el 22/10/2026: es demasiado trabajo. No volver a proponerlo.

## Próxima versión

1. **Celular: MEZCLA más acorde a la pantalla chica** (Joaquín, 24/11/2026, con captura de su celular en horizontal, 2340×1080).
   - Joaquín: "Para tablet están perfectos los tamaños, pero para celu creo que podría ser mejor… capaz achicando botones". **La tablet no se toca.**
   - Lo que se ve mal (su captura y también la captura del CI `android-celular-1-mezcla`):
     - el recorrido del fader es corto: la parte útil ocupa menos de un tercio del alto; botones, títulos y barras se comen el resto;
     - el riel izquierdo no entra: "BUSES" queda cortado detrás de NOTAS y no se ven FX RTN/BUSES/FAVS sin scroll; NOTAS y MUTE FX son enormes;
     - en cada tira el nombre aparece dos veces (rótulo "CH 01" arriba + botón de nombre "CH 01");
     - el cap del fader tapa las marcas de la escala ("-60" se lee "-6(", el "0" del MAIN tapado);
     - la fila "CH 1-8 · TAP · SENDS ON FADER" ocupa una fila entera;
     - pestañas y el chip "● SIN CONEXIÓN" muy anchos.
   - Objetivo en celular: **el recorrido del fader ≥ 50 % del alto de la pantalla**, sin textos cortados ni encimados.
   - Ideas para lograrlo (todo con `sz(…)` / `Layout.compact`, Android e iOS):
     - sacar la fila de página: TAP y SENDS ON FADER pasan a la barra de arriba (más chicos) o al pie del riel; el nombre de la capa ya está marcado en el riel;
     - tira: un solo nombre (el botón de color; el número "CH 01" chiquito adentro o al costado), paneo + S + valor en una sola fila, MUTE más bajo, cap del fader más chico, la escala sin quedar debajo del cap;
     - riel: más angosto, letra más chica, todas las capas visibles; reloj SHOW, NOTAS (✎) y MUTE FX más chicos (una línea o ícono);
     - barra de arriba: pestañas con letra más chica, chip de conexión corto (punto + "SIN CONEX." / nombre corto), candado y wifi más chicos.
   - **Ojo:** en el celular de Joaquín se ve distinto que en el CI (por ejemplo, ahí sí aparecen los números de la escala); probablemente por el tamaño de letra/pantalla del sistema. Que el diseño compacto aguante letra del sistema grande (probar en el CI con `RuntimeEnvironment.setFontScale(1.3f)` además de 1.0, y una captura extra a 2340×1080).
   - Revisar también en celular el detalle de canal, EFECTOS, MEDIDORES, SALA, ESCENAS y el panel de envíos, que usan la misma barra de arriba.
2. **SALA: el ruido rosa tiene que salir de la consola al MAIN LR, no del parlante de la tablet** (Joaquín, 17/11/2026).
   - Joaquín: "la generación de ruido rosa sea desde la app hacia el main LR, es una función que tienen otras apps"; el parlante de la tablet "filtra y es malísimo".
   - Después él mide con alguno de los micrófonos.
   - **Límite técnico:** por wifi (OSC) la app solo manda órdenes, no audio. "Mandarlo desde la app" = prender el generador interno de la consola (oscilador / ruido rosa) por OSC y rutearlo al MAIN LR.
     - Así lo hacen las otras apps; en X32/M32 existe (`/-stat/osc/on` + `/config/osc/...`).
   - **Bloqueado hasta confirmar** que la XR12 tiene generador y cuál es su dirección OSC:
     - Joaquín va a fijarse en X-Air Edit / Mixing Station / MX Mix si tiene oscilador o ruido rosa, y en qué app lo vio.
     - **No mandar direcciones sin verificar.**
   - **Si existe:**
     - en SALA, botón RUIDO ROSA → MAIN LR, con nivel (arranca bajo, por ejemplo -30 dB) y apagado automático al salir de SALA, al perder conexión o a los 60 s;
     - la medición usa ese ruido en vez del parlante, y el parlante de la tablet se saca.
   - **Si no existe:** avisarle a Joaquín con las alternativas, sin implementar nada.
3. **Clave de firma fuera del repo público** (quedó del pedido del aviso de actualización).
   - `android/airmix-debug.keystore` está en el historial público.
   - Moverla a un secreto de GitHub no alcanza: hace falta una clave nueva, lo que obliga a desinstalar una última vez.
   - Joaquín decide cuándo. **La tarea de los lunes no la hace sola.**

## Futuro (Joaquín dijo "me interesa en el futuro", 01/11/2026; no hacer hasta que lo pida)
1. **Medidor de reducción del compresor y del gate** en cada canal: cuánto está comprimiendo o cerrando.
2. **Salidas físicas:** qué sale por AUX OUT 1/2 de la XR12 y desde qué punto.
3. **Presets de canal:** guardar la configuración de un canal (EQ, compresor, gate…) y cargarla en otro.
4. **Presets de efectos:** guardar un efecto ajustado y recuperarlo sin cargar la escena.
## Para probar en la XR12 (0.5.7)
- **Calidad del wifi:** con la consola conectada, la luz al lado del loguito de wifi tiene que estar verde (tocala: muestra los ms). Alejarse con la tablet o cargar la red y ver que vaya hacia amarillo/rojo. Sin consola: gris.
- **Envíos manteniendo apretado:** mantener apretado el nombre de un canal → panel con MON 1/2 y FX 1-4; mover un envío y ver que cambie en la consola (y en X-Air Edit). Tocar afuera cierra. Confirmar que mover el fader del canal no abre el panel.
- **MODO DEMO:** ESCENAS → ▶ MODO DEMO con la consola conectada: no tiene que cambiar nada en la consola mientras movés cosas. Al salir (MODO DEMO ✕ arriba), vuelve a la XR12 real con sus valores.
- **SALA guardar por lugar:** medir, GUARDAR SALA con un nombre, salir de la app, volver y abrirla en SALAS GUARDADAS.

## Para probar en la XR12 (0.5.5)
- **Fader del MAIN:** que un roce no lo mueva, que al mantener ~0,6 s se agarre y se mueva sin saltos, y que el doble toque no haga nada.
- **SALA** (a volumen moderado, micrófonos en su lugar, tablet apoyada cerca):
  - que el ruido rosa de la tablet se escuche en el RTA de cada micrófono (si no, subir el volumen de la tablet);
  - que durante la medición el MAIN (o el monitor) suba 3 dB y al terminar vuelva exacto a donde estaba;
  - que después la consola muestre el RTA como antes;
  - comparar las alertas con lo que acopla de verdad y ajustar los umbrales (hoy: medio 2 dB, ALTO 4 dB, corte de seguridad 12 dB);
  - verificar si la XR12 tiene generador de ruido propio (si lo tiene, se puede usar en vez del parlante).

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

## Hecho en 0.5.7 (23/11/2026)
- **MODO DEMO sin consola** (Demo.kt / Demo.swift): botón ▶ MODO DEMO en ESCENAS, al lado de ACERCA DE.
  - XR12 simulada: nombres, colores, faders, mutes, paneos, envíos a MON 1/2 y FX, EQ, efectos; lo que no está en la tabla toma un valor neutro.
  - Medidores (y RTA del EQ) con señal simulada desde el tick de 30 fps, sin asignaciones.
  - MixerClient.demo descarta todo envío y lo que llega de la red; arriba dice "▶ MODO DEMO ✕" (tocarlo = salir).
  - SALA, escenas a la consola y el aviso de actualización: como sin conexión. Sin franja roja de corte al entrar.
- **Calidad del wifi en la barra de arriba** (WifiView / WifiQualityView): loguito de wifi + luz en gradiente verde→amarillo→rojo.
  - /xinfo cada 1 s mientras hay conexión; respuesta suavizada (70/30); sin respuesta en 1 s = 400 ms.
  - Verde ≤ 20 ms, rojo ≥ 300 ms; gris sin conexión o en demo. Tocarla muestra los ms.
- **Mantener apretado el nombre (o el título) de una tira → envíos** (StripSends): canales, AUX y FX RTN.
  - Panel con un fader por bus (nombre y color del bus) y por FX (tipo de efecto); se cierra tocando afuera (o VOLVER en Android).
  - El gesto va sobre el nombre/título, no sobre el fader: no choca con el fader protegido del MAIN.
- **SALA: guardar por lugar:** GUARDAR SALA (nombre; si existe, se reemplaza) y SALAS GUARDADAS (VER / BORRAR), hasta 30, en el dispositivo.
- Capturas nuevas en CI: demo y envios.
- **A confirmar:** el ping de calidad usa /xinfo (la consola siempre lo contesta); si Joaquín nota algo raro en la conexión, se puede espaciar.

## Hecho en 0.5.6 (20/11/2026) — revisión X32 antes del show del sábado
- ENTRADA X32 = lista real de /ch/NN/config/source (0-64).
- Preamp X32 según /config/routing/IN/1-8…25-32 (AN → 000-031, A → 032-079, B → 080-127, CARD → sin preamp); se escucha el ruteo al conectar.
- Escenas X32: el 48V no se fuerza a OFF si la escena no trae la línea de headamp.
- Direcciones X32 verificadas contra X32Channel/Bus/Auxin/Fxrtn/Mtx/Dca/CfgMain/PrefStat.h (P.-G. Maillot).
- **Sigue sin tocarse en X32:** fuente del RTA (EQ, detector de acople y SALA usan lo que tenga la consola; SALA avisa que es solo X Air).

## Para probar en la X32 (sábado 21/11)
- Conectar (puerto 10023) y ver que detecta X32.
- Faders, mutes, nombres y medidores de canales, AUX IN, FX RTN, buses, matrices y main.
- CONFIG de un canal: que ENTRADA muestre la fuente correcta y que GAIN/48V muevan el preamp correcto (sobre todo si hay stagebox).
- Envíos a buses (SENDS ON FADER), EQ, gate, compresor.
- Rack de efectos (FX 1-4 y 5-8).

## Hecho en 0.5.5 (17/11/2026)
- **Fader del MAIN LR protegido** (FaderView.guarded en Android e iOS):
  - al tocarlo no se mueve durante 0,6 s (barrita blanca arriba del cap); después se mueve normal hasta soltar;
  - doble toque: no hace nada;
  - MEZCLA, detalle del MAIN y SENDS ON FADER (la tira MAIN siempre muestra el fader del main). Los demás faders, igual.
- **SALA: mapa de riesgo de acople, sin conectar nada** (Room.kt + RoomScreen.kt / Room.swift):
  - pestaña SALA entre MEDIDORES y ESCENAS (en celular las pestañas se angostaron para que entre la barra);
  - pasos: micrófonos (tildados solos los que tienen señal o 48V) → salida (MAIN o bus) → ruido rosa → MEDIR;
  - ruido rosa por el parlante de la tablet (filtro de Paul Kellet, fundido de 0,3 s), con botón PROBAR RUIDO;
  - por cada micrófono: fuente del RTA en ese canal, 0,8 s de espera + 2,2 s de promedio; dos pasadas (nivel actual y salida +3 dB);
  - riesgo = cuánto crece cada banda por encima de la mediana (medio ≥ 2 dB, ALTO ≥ 4 dB); también picos ≥ 10 dB sobre las vecinas; 60 Hz–12 kHz, hasta 5 alertas;
  - seguridad: si en la pasada +3 dB una banda sube 12 dB y pasa -30 dB, corta la prueba al instante y lo marca como ALTO;
  - ✂ CORTAR con OK, en el EQ de la salida medida (mismo corte que el detector: PEQ -6 dB, Q 8);
  - al terminar, cancelar, salir de SALA, perder la conexión o mandar la app a segundo plano: salida, fuente del RTA y ruido vuelven como estaban;
  - solo X Air (en X32 avisa).
  - **A confirmar:** se usa el parlante de la tablet porque no está verificado que la XR12 tenga generador de ruido propio; no se manda ninguna dirección OSC sin verificar.

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
