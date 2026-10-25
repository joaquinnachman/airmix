# Paridad de funciones

| Función | Android | iOS |
|---|---|---|
| Conexión OSC (X Air 10024 / X32 10023, detección de modelo y puerto) | ✅ | ✅ |
| Búsqueda de consolas en la red | ✅ broadcast | ✅ barrido de la subred (iOS no permite broadcast sin permiso especial de Apple) |
| Capas: canales, aux, FX returns, buses, matrices (X32), FX sends (XR), DCA | ✅ | ✅ |
| Tiras: nombre/color, paneo, solo, fader con escala, medidor, mute | ✅ | ✅ |
| Sends on fader (buses y FX) | ✅ | ✅ |
| MUTE FX global con restauración | ✅ | ✅ |
| Detalle: CONFIG / GATE / DYN / EQ (gráfico arrastrable) / ENVÍOS | ✅ | ✅ |
| CONFIG: GAIN / 48V según la fuente del canal (insrc / source), con aviso si la consola no responde | ✅ | ✅ |
| Reintento automático de pedidos sin respuesta (cada 1,5 s, hasta 6 veces) | ✅ | ✅ |
| CONFIG: nombre con botón OK y verificación contra la consola (3 intentos) | ✅ | ✅ |
| CONFIG: paleta de 16 colores (canales y buses); el color del bus se ve en envíos, selector, encabezado y fader | ✅ | ✅ |
| EQ: RTA de la consola de fondo (X Air /meters/4, X32 /meters/15) con botón RTA | ✅ | ✅ |
| EQ: RESET con confirmación (0 dB + tipo/frecuencia/Q de fábrica) | ✅ | ✅ |
| ENVÍOS: nombre de cada bus ("1-JUANCITO") y nombre del efecto ("FX1 · Hall Reverb") | ✅ | ✅ |
| EFECTOS: cambiar tipo, todos los parámetros con unidades reales (tabla FX_TABLE idéntica), retorno, send master, mute | ✅ | ✅ |
| MEDIDORES (todos) | ✅ | ✅ |
| ESCENAS .scn: guardar (/node), cargar con verificación, lista local, exportar/importar | ✅ | ✅ |
| ESCENAS: guardado local sin internet (Android: Descargas/AirMix; iOS: Archivos → AirMix → Escenas) | ✅ | ✅ |
| Cronómetro de show persistente | ✅ | ✅ |
| Notas con marcas de tiempo | ✅ | ✅ |
| TEMAS: lista editable (orden, notas, tema que suena), carga desde .txt / .docx / .doc / .rtf | ✅ | ✅ |
| Layout iPhone (tiras con scroll horizontal) | — | ✅ |
| Ícono 8C (curva EQ) + logo en la barra superior, mismo vector | ✅ | ✅ |
| Versión instalada al lado del logo | ✅ | ✅ |
| Interfaz compacta para celular (tiras, escala sin encimar, barra y riel chicos, paneles con scroll horizontal) | ✅ | ✅ |
| CLR SOLO en MEDIDORES + aviso "● SOLO" en la barra (/-stat/solo) | ✅ | ✅ |
| NOTAS/TEMAS corre las tiras en tablet (encima en celular) | ✅ | ✅ |
| MUTE del MAIN LR manteniendo 2 s (desmutear: un toque) | ✅ | ✅ |
| LINK STEREO de canales (/config/chlink/N-M), par en una sola tira, capas dinámicas | ✅ | ✅ |
| ACERCA DE (ESCENAS) | ✅ | ✅ |
| Capturas automáticas en CI (Robolectric / simulador) adjuntas al Release | ✅ | ✅ |
| Acentos de color por sector (Sector en Ui.kt / Theme.swift) | ✅ | ✅ |
