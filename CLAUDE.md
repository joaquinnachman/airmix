# AirMix — instrucciones para trabajar en este repo

App de control para consolas Behringer X Air (XR12/XR16/XR18) y X32/M32 por OSC/UDP.
Dueño: Joaquín Nachman. Uso principal: shows del dúo Garko y Palmito con una XR12 y una Samsung Tab A8.

## Regla principal: paridad Android ↔ iOS
- Hay **dos apps nativas**: `android/` (Kotlin, sin dependencias, vistas propias) e `ios/` (Swift/SwiftUI).
- **Todo cambio de funcionalidad, protocolo o diseño se hace en las dos** en el mismo commit/PR.
- Mantener `FEATURES.md` al día: cada función con su estado en Android e iOS.
- Las tablas de protocolo (direcciones OSC, campos .scn, índices de medidores y solos) tienen que ser idénticas en
  `android/.../Model.kt` + `Scenes.kt` e `ios/AirMix/Model.swift` + `Scenes.swift`.

## Compilación
- No hay SDK de Android ni Xcode local: compila GitHub Actions (`.github/workflows/build.yml`).
  - Job `android` en Ubuntu genera `AirMix.apk`.
  - Job `ios` en macOS genera el proyecto con XcodeGen (`ios/project.yml`) y un `AirMix-unsigned.ipa`.
  - Las dos salen en el mismo Release.
- Antes de cerrar un cambio hay que esperar a que los dos jobs pasen.

## Rendimiento (el motivo de la app)
- Nada de asignaciones en los caminos calientes (medidores, onDraw/Canvas).
- Medidores: un único timer a ~30 fps redibuja solo los visibles.
- Envíos coalescidos por dirección. Suscripciones atadas al ciclo de vida de cada vista.

## Idioma
- La interfaz y los textos al usuario van en castellano rioplatense.
