# Logo AirMix (8C: curva EQ con banda llena)

`gen.py` genera todo desde una sola definición, con el wordmark "airmix" en Unbounded 500 convertido a trazos:

    python3 gen.py Unbounded[wght].ttf salida/
    NODE_PATH=$(npm root -g) node render.js '[["salida/icon-1024.svg","salida/icon-1024.png",1024,1024,"#12161B"]]'

Salidas y destino:
- `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `logo_lockup.xml` → `android/app/src/main/res/drawable/`
- `icon-1024.png` (cuadrado opaco, iOS redondea solo) → `ios/AirMix/Assets.xcassets/AppIcon.appiconset/`
- `logo.svg` → `ios/AirMix/Assets.xcassets/Logo.imageset/`

Colores: fondo #12161B, naranja #FF7A45, blanco #E8EDF2, gris #8A96A3.
