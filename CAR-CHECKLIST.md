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
7. Apasă Home → **DriveDeck** → **„Always”**. Nu e opțional: dacă DriveDeck nu e aplicația Home, harta Waze
   stă pe pauză ori de câte ori atingi dashboard-ul (vezi punctul 6 mai jos, 17 oct seara).
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

### Rezolvat acasă, 17 oct seara (verificat pe emulator, NU încă pe navigație)

1. **Launcher-ul peste DriveDeck: reparat.** Mecanismul e confirmat: un task pornit cu
   `NEW_TASK|TASK_ON_HOME` (cum pornește un launcher) aduce Home în față la orice `startActivity` sau
   mutare a lui (`reparent ... returnToHome`). Slotul mută acum task-ul existent fără să-l repornească,
   apoi pune DriveDeck înapoi deasupra dacă a apărut ceva peste el. `./repro-return-home.sh`, câte 6
   runde: APK vechi + `FLAGS=0x10004000` **0/6**, APK nou **6/6**, iar controlul cu task obișnuit e 6/6 la ambele.
   Dacă launcher-ul vendorului pune într-adevăr flag-ul ăsta se vede în `<pas>.activities.txt` (`flg=`).
2. **`car-verdict.sh`: un pas cu DriveDeck acoperit iese INVALID și se reia la rularea următoare.**
   Pe emulator, cu APK-ul vechi: 4/4 INVALID; cu cel nou: judecat normal. Tot acolo, după `force-stop` se
   așteaptă dispariția display-ului vechi. Altfel aplicația din slot cade peste noul DriveDeck (o cursă
   găsită în harness, care putea să strice și rundele din mașină).
3. **O singură instanță:** pornire din iconiță + Home → APK vechi: 3/3 cu două dashboard-uri; nou: 3/3 cu unul.

4. **Waze pornit la rece ieșea din card.** Ecranul lui principal îl pornește Waze singur, fără display, și ajungea
   pe tot ecranul peste DriveDeck: APK vechi 3/3. Acum slotul îl readuce timp de 20 s după pornire: 3/3 în card.
   Dacă în timpul ăsta apeși Waze în dock, rămâne pe tot ecranul (2/2).
5. **Cardul Viteză e acum „Afară”**: temperatura de la senzorul mașinii, citită din `forfan.user.info`, pe care o
   scrie aplicația vendorului. La mașină, pasul `temp` o notează: trebuie să fie aceeași cifră ca în bara de sus a
   navigației.

6. **Harta Waze îngheța după orice atingere pe dashboard.** În Android 9, o singură activitate e activă: atingerea
   dădea focusul la DriveDeck, Waze intra pe pauză și harta nu mai desena. APK vechi: 0 cadre noi pe hartă cu mașina
   în mișcare, 3/3 runde. Acum DriveDeck dă focusul înapoi după 300 ms: 7/7 runde cu hartă vie (44–97 de cadre),
   `./repro-map-pause.sh`. **Merge doar cu DriveDeck ca Home:** altfel, la un Waze pornit din launcher-ul vendorului,
   focusul dat lui Waze aduce launcher-ul peste dashboard. DriveDeck detectează asta și păstrează focusul, deci harta
   stă pe pauză. **De urmărit la mașină:** pe emulator Waze crapă mai des acum, pentru că desenează mai mult
   (crash-ul e în GL-ul emulatorului). Dacă și pe navigație crapă des, suspectul e pauza/reluarea la fiecare atingere.

Limită cunoscută: judecătorul măsoară doar acoperirea. Pe emulator, `texture_fix` dă PASS deși imaginea
e întinsă, deci la mașină trebuie să ne uităm și la captură.

**Următoarea sesiune: `./car-verdict.sh --fresh`** (sesiunea veche are pasul `install` marcat cu APK-ul vechi).

## DriveDeck vs CarPlay: care e mai rapid (măsurat, nu estimat)

Același instrument pentru amândouă: `fps-probe.sh` glisează harta identic și citește din SurfaceFlinger
momentul afișării fiecărui cadru. DriveDeck se măsoară singur, în `car-verdict.sh` (pasul `fps`). CarPlay:

1. Pornește CarPlay (**cu cablu, dacă se poate**: wireless poate muta Wi-Fi-ul navigației și se pierde ADB-ul;
   sonda merge mai departe oricum, doar rezultatele le iau mai târziu). Deschide **Waze în CarPlay**, pe hartă,
   fără traseu (la fel ca în DriveDeck).
2. Eu rulez `./fps-probe.sh start carplay 60 20`: ai 20 s să ajungi pe hartă, apoi 60 s de glisări.
   Nu atinge ecranul în timpul ăsta. Sonda glisează doar când focusul e pe CarPlay, altfel sare și notează.
3. Când ADB-ul e din nou disponibil: `./fps-probe.sh pull carplay`. Compar stratul video CarPlay cu stratul
   DriveDeck din pasul `fps`.

**Atingere → reacție (filmuleț slow-motion).** iPhone: Camera → **Slo-mo 240 fps**, telefonul fix, cu ecranul
navigației și degetul în cadru. Cu harta Waze deschisă: **5 atingeri** pe butonul de căutare („Where to?”),
cu o pauză între ele. O dată în DriveDeck, o dată în CarPlay. Trimite-mi clipurile pe Mac (AirDrop). Eu număr
cadrele dintre atingere și prima schimbare pe ecran (1 cadru = 4,2 ms).

**Contact → hartă utilizabilă.** Filmuleț normal, din momentul în care pui contactul până când harta Waze
răspunde la atingere. O dată cu DriveDeck ca Home, o dată cu CarPlay.
