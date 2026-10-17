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
4. Eu rulez `./car-verdict.sh` (reluabil: dacă pică Wi-Fi-ul → `./connect.sh` → din nou `./car-verdict.sh`):
   - `activityview`: varianta veche, **trebuie să pice** (FAIL ~0,5). Dovedește că judecătorul vede problema;
   - `hwc_off`: aceeași, cu compunerea hardware oprită. **PASS = cauza e compozitorul hardware**;
   - `texture`: noua fereastră **fără** corecția vdfix, se așteaptă FAIL (~lățime/1920);
   - `texture_fix`: **testul decisiv**, cu vdfix: trebuie PASS și imaginea nedeformată;
   - `vdscale`: doar dacă ambele pică, încearcă alte dimensiuni ale ecranului virtual;
   - `gps`: permisiunea de locație a Waze și ultimul fix GPS;
   - verdictul complet e în `shots/<sesiune>/verdict.md`, cu capturi și dump-uri SurfaceFlinger.
5. Cont Google pe navigație → **Play Store: Settings → Auto-update apps → Don't auto-update** →
   instalează **Waze** → login **YouTube** (Premium).
6. Pornește ceva în **Spotify** și în **YouTube** → verific ce primește cardul Now Playing.
   Apoi atinge **coperta Spotify** în Now Playing → biblioteca se deschide peste hartă → prima dată
   Spotify cere **autorizare** pentru DriveDeck (o singură dată) → alege un playlist.
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

## Deschis după sesiunea din 17 oct, 16:26

- **Sesiunea `s-1017-162610` NU e valabilă.** `activityview`, `hwc_off` și `texture` au dat FAIL, dar
  capturile arată ecranul principal al navigației, nu DriveDeck: judecătorul a măsurat ecranul greșit.
  `texture_fix` (testul decisiv) n-a apucat să ruleze. vdfix e încă nedovedit pe navigație.
- **Cauza, din log:** la revenirea în DriveDeck, slotul repornește aplicația care avea deja task
  (Settings în test) → `am_focused_stack ... bringingFoundTaskToFront returnToHome` → launcher-ul
  navigației (stack-ul home) ajunge peste DriveDeck. Pe emulator nu s-a văzut pentru că acolo DriveDeck
  era Home. Probabil la fel cu Waze cât timp DriveDeck nu e Home implicit.
- **De făcut acasă, înainte de mașină:**
  1. slotul să nu mai trimită `startActivity` pentru un task existent, doar `moveToDisplay`; reprodus pe
     emulator cu launcher-ul original ca Home (FAIL înainte, PASS după);
  2. `car-verdict.sh`: dacă DriveDeck nu e în față la captură, pasul e INVALID, nu FAIL;
  3. o singură instanță DriveDeck (launcher + Home creează două).
