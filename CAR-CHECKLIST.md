# Sesiunea la mașină: listă de verificare

## Acasă, înainte

- [ ] **Cont Google pentru navigație.** Dacă ai YouTube Premium **Family**: fă un cont nou „mașină” și
      adaugă-l în familie. Dacă ai Premium individual: decide dacă pui contul principal (cu 2FA activ).
- [ ] Ai la îndemână login-ul **Spotify** (sau login cu telefonul).
- [ ] Mac încărcat + încărcător, iPhone încărcat (hotspot-ul consumă).
- [ ] iPhone → Personal Hotspot: **Maximize Compatibility ON**. **Low Power Mode OFF**.
- [ ] Date mobile: Waze (~200 MB) + actualizări Google se descarcă prin hotspot.

## La mașină

1. **Motorul pornit** (sesiune de 30–45 min, bateria). Frână de mână, **nu** în marșarier.
2. Navigație: Wi-Fi → hotspot-ul iPhone. **AutoLink oprit.** ADB pornit (Developer options).
3. Mac pe hotspot → `./connect.sh` → scrie-mi „conectat”.
4. Eu rulez `./car-test.sh "$(cut -d: -f1 .unit)"`:
   - **testul decisiv:** noua fereastră (TextureView) umple cardul? Comparație automată cu varianta veche;
   - dacă nu: încerc pe loc `--ef vdscale 0.6667` și alte valori, fără recompilare.
5. Cont Google pe navigație → **Play Store: Settings → Auto-update apps → Don't auto-update** →
   instalează **Waze** → login **YouTube** (Premium).
6. Pornește ceva în **Spotify** și în **YouTube** → verific ce primește cardul Now Playing.
7. Apasă Home → **DriveDeck** → întâi **„Just once”**. Dacă merge bine → **„Always”**.
8. Dacă se pierde conexiunea: reconectează Wi-Fi-ul navigației, rulează din nou `./connect.sh`
   (descarcă jurnalul cu motivul deconectării).

## Anulare

| Ce | Comandă / unde |
|---|---|
| Scoți DriveDeck (Home revine la launcher-ul original) | `adb uninstall dev.drivedeck` |
| Scoți Aurora | `adb uninstall com.aurora.store` |
| Animații / aplicații dezactivate pe 16 oct | `~/Documents/bmw-e90-navigatie/ANULARE.md` |
| **La final** | Developer options → **USB debugging OFF** |
