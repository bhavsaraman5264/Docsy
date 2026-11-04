# M1 spike ke nateeje (4 Nov 2026)

`androidx.pdf` 1.0.0-beta01. Test: `PdfSpikeActivity` ke synthetic touch gestures, emulators par.

| Device | Android | SDK extension (S) | Engine |
|---|---|---|---|
| kholo_api30 | 11 (API 30) | n/a | Fallback |
| kholo_api34 | 14 (API 34) | 7 | Fallback |
| kholo_api36 | 16 (API 36) | 17 | Poora |

Asli phone par abhi test nahi hua. Sare numbers emulator ke hain.

## Faisla

**Viewer: `SandboxedPdfLoader` + public `PdfView`, Compose me `AndroidView` se.** Fragment nahi. Password flow hamare haath me rehta hai, aur zoom ke zaroori niyam `PdfView` par pass hain. Apna zoom layer abhi nahi chahiye, sirf "har page apni width" wale niyam ka faisla baaki hai (neeche dekho).

## Zoom niyam

| Niyam | Nateeja | Naap |
|---|---|---|
| File fit-to-width khule | Pass | 1080px par zoom 1.815 = expected 1.815 |
| Page top se khule | Pass | scroll 0,0 (tall document). Chhote document ke liye `verticalAlignment` public hai |
| Zoom ungliyon ke beech ho | Pass | Drift 5-8 px, teeno devices (pinch 1.25x). Library `toInt()` karti hai |
| Fit se chhota na ho | Pass | `minZoom` public hai. Set karne ke baad pinch out par zoom fit par ruka, scrollX 0 |
| Kinara screen se door na jaye | Pass | Left gap 0, right gap 0.5 px se kam |
| Seedhe scroll me side na hile | Pass | 40px jitter ke saath scrollX delta 0 |
| Zoom ke baad sharp render | Library me hai, aankh se test baaki | Gesture ke baad bitmap fetch hota hai. Asli phone par dekhna hai |
| Rotate par wahi jagah | Aadha pass | Page yaad raha (first=2). Zoom nahi badla, isliye landscape me page screen ki poori width nahi leta. `ZoomRules.zoomAfterResize` theek karega |
| Har page apni width par fit | **Fail** | `PdfView` me poore document ka ek hi zoom hai. Dekho "Khuli baat" |
| Rotate button | Pass, View ghuma kar | Public API nahi. `pdfView.rotation = 90f` + width/height swap chala. Yeh poori file ghumata hai, jo plan ka niyam hai |
| Double-tap | Pass | Fit se 2x, tap point par (drift 2-5 px), dobara tap par fit |
| Fit button | Pass | Public `zoom` aur `scrollTo` se bana |

## Khuli baat: mixed-size PDF

Library sabse chaude page par fit karti hai. Fixture me 1190pt ka ek page tha, isliye pehla A4 page screen ki aadhi width (540 of 1080 px) par aaya.

- **Theek ho sakta hai:** khulte waqt zoom pehle page par fit karo, aur `minZoom` sabse chaude page par rakho. Fit button current page ko fit karega.
- **Theek nahi ho sakta:** scroll karte waqt har page ka apna alag zoom. Iske liye apna zoom layer likhna padega.

## Baaki sawaal

| Sawaal | Jawab |
|---|---|
| API 30 par password PDF | `PdfPasswordException: Incorrect password`, **sahi password par bhi**. Fallback par galat aur sahi password me farak nahi pata chalta, isliye `PdfCapabilities` pehle dekhna zaroori hai |
| Search aur text copy | Fallback par `UnsupportedOperationException`. Android 16 par chalte hain (3 pages me hits, 1929 chars) |
| Password path | Android 16 par `openDocument(uri, password)` se 3 fake PDFs (AES-128) khule. Galat ya khaali password par `PdfPasswordException` |
| Kharab aur khaali file | `IllegalStateException: Unknown loading status`, koi crash nahi. `AppError.Corrupt` me map karna hai. Khaali file `DocumentType.Empty` pehle pakdega |
| Night mode | Pass. `LAYER_TYPE_HARDWARE` + invert filter. Document set karne se **pehle** lagana hai, baad me lagane par ek test me page 1 kaala aaya |
| Release APK | **2.48 MB** PDF viewer ke saath (R8 on). Target 15 MB |
| Permissions | INTERNET nahi, storage nahi |
| Pehla page | 1.0-2.5 s emulator par (software GPU). Asli budget phone par M6 me naapna hai |
| WhatsApp tree picker | **Baaki.** Asli phone aur WhatsApp chahiye |

## Plan me badlaav

1. Poore features ka niyam: Android 15+, ya Android 11-14 par extension 13+. Plan me "Android 12+" likha tha. Yeh library ke source se liya hai.
2. Android 14 emulator par extension 7 mila. Yani naya Android hone se poore features ki guarantee nahi. Asli phones me kitna hai, yeh Play data se pata chalega.
3. `SandboxedPdfLoader` ka `IllegalStateException` bhi error mapping me jayega.
