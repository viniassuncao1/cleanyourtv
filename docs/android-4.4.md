# Android 4.4 (API 19) and old sets that are not Android TV

*Português: [mais abaixo](#portugues-pt-br).*

This fork's launcher builds with `minSdk 19`, so it can run on very old Android sets that are not
Android TV (no Play Store, no leanback). It was tried on **one** TV, for **one afternoon**. Everything
below says what was observed and what was not.

| | |
|---|---|
| TV | Philco smart TV (Hikeen / Changhong software, Realtek `rtd298x_tv001`), Brazil |
| OS | Android 4.4.2, API 19, build `eng.wubf.20170729.023516 test-keys`, 456 MB RAM, 1280x720 |

## What changes compared with the upstream launcher

- `minSdk` 26 to 19. Everything that needs Android 5.0+ lives in small `*Api21` classes that older
  versions never load, and is called only after a version check. Lint reports no `NewApi` errors and
  the unit tests pass. Behaviour on Android 5.0+ is meant to be unchanged.
- Apps are listed by `LEANBACK_LAUNCHER` from 5.0 and by `LAUNCHER` before, because TV apps on 4.x have
  no leanback entry.
- Before 5.0: no app banners (tiles show the icon), no HDMI/AV list from the TV Input Framework, no VPN
  badge, square banner corners, no letter spacing. The network pill reads the active network and
  refreshes on `CONNECTIVITY_ACTION`.
- Three opt-in things for sets with a firmware source panel (all in `config.json`; with none of them set, the launcher behaves as before):
  - `tvhome:hdmi` in `dock_apps`: a tile that opens the TV's own source panel (needs `source_panel_*`); without that entry no tile is added.
  - `ambient_glow: false`: no glow behind the focused tile, a plain background colour.
  - `live_source_activity`: see "Video source playing under the menu" below.

## Observed on the tested TV

- The launcher starts, lists the TV's apps, shows the clock and the Wi-Fi name, and survives a reboot.
- It became the default Home through Android's own "choose a home app" dialog (Home key, then
  "Always"). Nothing was disabled to make that work.
- The firmware's source panel (a service of the TV maker's "source" app) opens from the HDMI tile and
  lists antenna, cable, AV, HDMI 1 and HDMI 2. Choosing an input from it was not tested through the tile.
- Netflix does not sign in on Android 4.4 (the app opens, login fails). YouTube TV opens; playback was
  not checked.
- Screen mirroring tools such as `scrcpy` were not tried. `adb shell screencap -p` works for the Android UI, but the
  video picture (HDMI, tuner) is a hardware layer and shows as black in it.

## Steps

You need a computer on the same network, `adb`, and JDK 17 to 21 with Android SDK platform 34 and
build-tools 34.0.0 to build. `<TV_IP>` below is your TV's address.

1. **Enable ADB.** Developer options (tap the build number seven times in the About screen), then USB
   debugging. On the tested TV the port was already open over Wi-Fi: `adb connect <TV_IP>:5555`. Before the first reboot
   we also ran `adb shell setprop persist.adb.tcp.port 5555`; whether the port would have survived without it is unknown.
   > **Security.** That TV is a developer ("eng") build: `adb` gave a **root** shell over the network with
   > no authorisation prompt. Anyone on your Wi-Fi can do the same. Turn debugging off when you are done
   > if the firmware lets you, and never expose that port to the internet.
2. **Back up first**, on your computer (these are read-only):
   ```sh
   adb -s <TV_IP>:5555 shell pm list packages -f > packages.txt
   adb -s <TV_IP>:5555 shell getprop > getprop.txt
   for ns in system secure global; do adb -s <TV_IP>:5555 shell content query --uri content://settings/$ns > settings_$ns.txt; done
   ```
   These files can contain device identifiers and your network name. Keep them private; do not commit them.
3. **Disable** what you do not need, with [`examples/philco-android44/debloat.txt`](../examples/philco-android44/debloat.txt).
   It uses `pm disable-user`, so nothing is uninstalled. Keep a log of every command and its undo.
4. **Build** the launcher:
   ```sh
   cd launcher
   echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # your SDK path
   ./gradlew assembleRelease
   ```
   The APK is `launcher/app/build/outputs/apk/release/app-release.apk`.
5. **Install it as a normal app** and open it by component name (on 4.x it has no `LAUNCHER` entry, so it
   is not in any menu):
   ```sh
   adb -s <TV_IP>:5555 install -r launcher/app/build/outputs/apk/release/app-release.apk
   adb -s <TV_IP>:5555 shell am start -n com.gathin.tvhome/.MainActivity
   ```
   After a *re*install (`install -r`) the stock launcher came to the front once and Home only re-fronted it; starting
   the new one with the `am start` line above fixed it (a reboot also does). The stock launcher itself was intact.
   Do **not** use `build-and-install.sh` here as it is: it expects a stock launcher it recognises and uses
   `cmd package set-home-activity`, which does not exist on Android 4.4.
6. **Make it Home** (optional): press Home on the remote, pick the launcher in the dialog, choose
   "Always". While it is not the default, the Home key shows that dialog each time.
7. **Configure** with a theme. Copy [`themes/philco-android44/config.json`](../themes/philco-android44/config.json) to the
   launcher's folder and restart it:
   ```sh
   adb -s <TV_IP>:5555 push themes/philco-android44/config.json /sdcard/Android/data/com.gathin.tvhome/files/config.json
   adb -s <TV_IP>:5555 shell am force-stop com.gathin.tvhome
   adb -s <TV_IP>:5555 shell am start -n com.gathin.tvhome/.MainActivity
   ```
   `source_panel_service` and `live_source_activity` are specific to this firmware. For another TV, pull
   the maker's source/menu app with `adb pull` and read its manifest
   (`aapt2 dump xmltree --file AndroidManifest.xml app.apk`) to find a service and its action.
   For a wallpaper, put your own 1280x720 `wallpaper.jpg` in the same folder and set `"wallpaper": true`.
   No wallpaper ships here.

## Video source playing under the menu

On the tested firmware, at power-on the TV resumes its last video source (HDMI 2 here) natively, before
Android is up. A launcher with an opaque background then covers the picture while the sound keeps
playing. Observed in the system log: the firmware selects source `17` (HDMI 2) at boot, nothing selects
the Android source, and pressing Home from the TV app is what makes the system switch back to source `24`
(Android) and stop the video.

- Calling the firmware's `android.app.TvManager.setSource(int)` from the launcher returned `false` and
  changed nothing. What worked was opening the TV app (`com.tsb.tv/.Tv_strategy`), which makes the system play the
  source.
- With `live_source_activity` set, the launcher reads `getCurSourceType()` through reflection and, if
  `isLiveSource()` says a video source is selected, opens that activity. It does this **only in the first two
  minutes after boot**, so recreating the menu while you watch can never trap you in the source. On any
  device without that firmware class it does nothing.
- Observed: after a reboot with a 1080p signal present the launcher opened the TV app. Not verified by me:
  that the picture and sound are right, and that a console waking the TV over CEC behaves the same.
  The picture cannot be screenshotted, so check it on the screen. If the TV is switched on without
  a device, you get the source's "no signal" screen and Home takes you to the menu.

## Wrong time (an hour ahead)

On this firmware the clock was one hour ahead: `date` showed `BRST` (UTC-2) while Brazil has had no daylight
saving time since 2019. Its time-zone database is `tzdata2015a`, which still has the old rules. Check with
`adb shell date` and `adb shell date -u`. A fixed UTC-3 zone fixed it (Brazil: `America/Fortaleza`);
`persist.sys.timezone` is read at boot:

```sh
adb -s <TV_IP>:5555 shell setprop persist.sys.timezone America/Fortaleza
adb -s <TV_IP>:5555 shell settings put global auto_time_zone 0
adb -s <TV_IP>:5555 reboot
```

## Undo

- Launcher: `adb uninstall com.gathin.tvhome`. Uninstalling should bring back the stock launcher; this was
  **not tested** on the TV.
- Settings and theme: `adb shell rm /sdcard/Android/data/com.gathin.tvhome/files/config.json`.
- Packages: the same loop as the debloat list with `pm enable`.
- Time zone: `setprop persist.sys.timezone America/Sao_Paulo` and `settings put global auto_time_zone 1`.

---

## Português (pt-BR)

Este fork do launcher compila com `minSdk 19` e roda em TVs muito antigas que **não** são Android TV (sem
Play Store e sem leanback). Foi testado em **uma** TV, durante **uma tarde**. Abaixo está dito o que foi
observado e o que não foi.

**TV testada:** Philco smart (software Hikeen/Changhong, Realtek `rtd298x_tv001`), Android 4.4.2, build
`eng.wubf.20170729.023516 test-keys`, 456 MB de RAM, 1280x720.

### O que muda em relação ao launcher original

- `minSdk` de 26 para 19. O que exige Android 5.0+ fica em classes `*Api21`, que versões antigas nunca carregam,
  e só é chamado depois de checar a versão. O lint não aponta erros de API e os testes unitários passam.
  No Android 5.0+ o comportamento deve continuar igual.
- A grade lista apps por `LEANBACK_LAUNCHER` a partir do 5.0 e por `LAUNCHER` antes disso.
- Antes do 5.0: sem banners (o bloco mostra o ícone), sem a lista de HDMI/AV do framework de TV, sem selo de
  VPN, cantos retos no banner e sem espaçamento de letras.
- Três recursos opcionais para TVs com painel de fonte no firmware (no `config.json`, desligados por padrão):
  `tvhome:hdmi` em `dock_apps` (um bloco que abre o painel de fonte da própria TV, exige `source_panel_*`),
  `ambient_glow: false` (sem brilho atrás do bloco focado, só cor de fundo) e `live_source_activity`.

### Observado na TV testada

- O launcher abre, lista os apps da TV, mostra relógio e nome do Wi-Fi, e sobrevive a um reinício.
- Virou a tela inicial padrão pelo diálogo do próprio Android (Home, "Sempre"). Nada foi desativado para isso.
- O painel de fonte do firmware abre pelo bloco HDMI e lista antena, cabo, AV, HDMI 1 e HDMI 2. Escolher uma
  entrada por ele, a partir do bloco, não foi testado.
- A Netflix não faz login no Android 4.4 (o app abre, o login falha). O YouTube TV abre; a reprodução não foi
  conferida.

### Passos

1. **Ative o ADB** (Opções do desenvolvedor: toque sete vezes no número da versão). Na TV testada a porta já
   estava aberta pelo Wi-Fi: `adb connect <IP_DA_TV>:5555`.
   > **Segurança.** Essa TV é uma build de desenvolvimento ("eng"): o `adb` deu um shell de **root** pela
   > rede, sem pedir autorização. Qualquer pessoa no seu Wi-Fi pode fazer o mesmo. Desligue a depuração quando
   > terminar, se o firmware permitir, e nunca exponha essa porta na internet.
2. **Faça backup antes** (só leitura): lista de pacotes, `getprop` e as configurações `system`, `secure` e
   `global` (comandos na seção em inglês). Esses arquivos podem ter identificadores do aparelho e o nome da sua
   rede: mantenha privados e não envie ao git.
3. **Desative** o que não precisa com [`examples/philco-android44/debloat.txt`](../examples/philco-android44/debloat.txt),
   usando `pm disable-user` (nada é desinstalado). Anote cada comando e o comando que desfaz.
4. **Compile** (`./gradlew assembleRelease` em `launcher/`, com JDK 17 a 21 e Android SDK 34) e **instale como app
   comum** com `adb install -r`. Abra pelo nome do componente
   (`am start -n com.gathin.tvhome/.MainActivity`), porque no 4.x ele não aparece em nenhum menu.
   Depois de *reinstalar* (`install -r`), o launcher de fábrica apareceu uma vez e o Home só o trouxe de volta; abrir o novo
   com o `am start` acima resolveu (reiniciar também). O launcher de fábrica estava intacto.
   Não use o `build-and-install.sh` como está: ele espera um launcher de fábrica que reconheça e usa um
   comando que não existe no Android 4.4.
5. **Tela inicial padrão** (opcional): aperte Home, escolha o launcher e "Sempre".
6. **Configure** copiando [`themes/philco-android44/config.json`](../themes/philco-android44/config.json) para
   `/sdcard/Android/data/com.gathin.tvhome/files/config.json` e reiniciando o launcher. As chaves
   `source_panel_*` e `live_source_activity` são desse firmware. Para fundo de imagem, coloque o seu próprio
   `wallpaper.jpg` (1280x720) na mesma pasta e use `"wallpaper": true`. Nenhuma imagem acompanha o projeto.

### Fonte de vídeo tocando por baixo do menu

No firmware testado, ao ligar, a TV retoma a última fonte de vídeo (HDMI 2) pelo sistema nativo, antes de o
Android subir. Um launcher de fundo opaco cobre a imagem, mas o som continua. No log: o firmware escolhe a fonte
`17` (HDMI 2) no boot, ninguém escolhe a fonte Android, e é o Home, a partir do app de TV, que faz o sistema voltar
à fonte `24` (Android) e parar o vídeo.

- Chamar `android.app.TvManager.setSource(int)` do launcher devolveu `false` e não mudou nada. O que funcionou
  foi abrir o app de TV (`com.tsb.tv/.Tv_strategy`).
- Com `live_source_activity`, o launcher lê `getCurSourceType()` por reflexão e, se `isLiveSource()` disser que há
  fonte de vídeo, abre essa tela. Só nos **dois primeiros minutos depois do boot**, para nunca prender você no
  HDMI. Em qualquer aparelho sem essa classe do firmware, não faz nada.
- Observado: depois de um reinício com sinal de 1080p presente, o launcher abriu o app de TV. **Não verifiquei**
  que a imagem e o som estão corretos, nem que um console acordando a TV por CEC se comporta igual. A imagem não
  aparece em captura de tela: confira olhando a TV.

### Hora errada (uma hora adiantada)

Nesse firmware o relógio ficava uma hora adiantado: `date` mostrava `BRST` (UTC-2), mas o Brasil não tem horário de
verão desde 2019. O banco de fusos dele é o `tzdata2015a`, com a regra antiga. Um fuso fixo UTC-3 resolveu
(`America/Fortaleza`; comandos na seção em inglês).

### Desfazer

`adb uninstall com.gathin.tvhome` (deve voltar ao launcher de fábrica; **não testado** nesta TV), apagar o
`config.json`, `pm enable` nos pacotes e, para a hora, `persist.sys.timezone America/Sao_Paulo` e
`auto_time_zone 1`.
