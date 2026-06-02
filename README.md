# 🎣 FishingMacro Pro — Android

Fishing automation app pentru Android cu detectie de pixel prin MediaProjection API.

![Android](https://img.shields.io/badge/Android-API%2026%2B-green?style=flat-square&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?style=flat-square&logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)

## ⬇️ Download APK

👉 **[Descarca FishingMacroPro.apk](../../releases/latest)**

## ✨ Features

- 👁 **Detectie pixel** prin MediaProjection API (fara root)
- 🎨 **Preluare automata culoare** de la bobber
- ⏱ **Delay de reactie** configurabil (ms)
- 🔄 **Cooldown** configurabil dupa fiecare actiune
- 🎲 **Mod Natural** — 8% sansa sa sara o detectie (arata mai uman)
- 🔁 **State machine RESET/WATCH** — nu triggereaza de doua ori pe acelasi eveniment
- 📱 UI dark, curat, optimizat pentru mobil

## 📲 Instalare

1. Descarca APK din **Releases**
2. Pe telefon: **Setari → Securitate → Instalare aplicatii necunoscute → Activeaza**
3. Deschide APK → Instaleaza
4. Deschide aplicatia si urmeaza pasii

## 📖 Cum se foloseste

### Pas 1 — Activeaza Accessibility Service
- Apasa butonul galben din aplicatie
- Gaseste **FishingMacro Pro** in lista → activeaza-l
- Revino in aplicatie

### Pas 2 — Seteaza pixelul
- Deschide jocul si arunca undita
- Revino in FishingMacro → atinge ecranul **exact pe bobber**
- Apasa **"Seteaza Pixel"**

### Pas 3 — Preia culoarea
- Apasa **"Preia Culoarea Pixelului"**
- Accepta permisiunea de screen capture
- Culoarea bobber-ului e salvata automat

### Pas 4 — Configureaza
| Setare | Recomandat | Descriere |
|--------|-----------|-----------|
| Toleranta | 15 | Cat de similar trebuie sa fie pixelul |
| Delay reactie | 100ms | Cat asteapta inainte sa actioneze |
| Cooldown | 3.0s | Pauza dupa fiecare aruncare |
| Mod Natural | Optional | 8% skip pentru a parea mai uman |

### Pas 5 — START
- Revino in joc si arunca undita
- Apasa **▶ START** in aplicatie
- Accepta permisiunea de screen capture

## ⚙️ Cum functioneaza

```
RESET → asteapta ca pixelul sa NU fie culoarea bobber-ului
  ↓
WATCH → detecteaza cand bobber-ul se scufunda (culoarea se schimba)
  ↓
TRIGGER → asteapta [delay]ms → tap → 0.5s → tap → cooldown
  ↓
RESET → ciclu nou
```

## ⚠️ Permisiuni

| Permisiune | Scop |
|-----------|------|
| Accessibility Service | Simuleaza tap-uri pe ecran |
| Screen Capture (MediaProjection) | Citeste culoarea pixelilor |
| Foreground Service | Ruleaza in background |
