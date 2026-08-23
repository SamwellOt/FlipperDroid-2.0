# FlipperDroid — Roadmap de Evolução

Comparação com o [firmware oficial do Flipper Zero](https://github.com/flipperdevices/flipperzero-firmware)
e propostas de novas funcionalidades, com avaliação **honesta** da viabilidade em Android.

## ✅ Implementado

- **QR / Barcode Scanner** real com câmera (dep. `com.journeyapps:zxing-android-embedded`).
- **BLE Scanner + GATT Explorer** (estilo nRF Connect): scan, conectar, descobrir serviços/características, ler valores.
- **BLE Spam** expandido: além de Apple/Samsung, agora **Microsoft SwiftPair** e **Google Fast Pair**.
- **NFC avançado:** ataque por dicionário de chaves Mifare Classic, leitura NDEF, clone de UID corrigido (carta "magic"), export de dump.
- **Infravermelho:** transmissão real via `ConsumerIrManager` (NEC) + teclado numérico.
- **Base de códigos IR (telecomando universal):** encoders NEC/NECext/Samsung32/Sony-SIRC/RC5, **parser do formato `.ir` do Flipper** (parsed + raw), remotes de exemplo empacotados, **import de `.ir` externos** (Flipper-IRDB via SAF) e **power sweep** (TV-B-Gone simplificado).
- **Wardriving** (inspirado no Evil-M5Project): scan Wi-Fi + GPS → **export CSV Wigle/WiGLE**.
- **Skimmer Detector** (Bluetooth Classic + BLE por assinatura de módulo série).
- **WiFi Analyzer** (gráfico de ocupação de canais 2.4/5 GHz) + **MAC spoofing (root)**.
- **BLE Beacon** emitter: iBeacon, Eddystone-URL, manufacturer data custom.
- **BLE GATT write + notify** (além de read) no BLE Scanner.
- **NFC NDEF write** (texto/URI, formata o tag se preciso).
- **Network extras** nativos: Wake-on-LAN, tabela ARP (`/proc/net/arp`), ping sweep /24.
- **BadUSB real via USB Gadget (root)** + **parser DuckyScript** (escreve em `/dev/hidgX`).
- **BLE HID device** (celular como teclado Bluetooth sem fio, via `BluetoothHidDevice`).
- **Evil Portal** (LocalOnlyHotspot + servidor HTTP embutido que captura credenciais).
- **2FA Vault (TOTP)** — RFC 6238, validado contra os vetores oficiais.
- **Decoders de formato Flipper** — `.nfc` (ler/gerar), `.sub` (ler/analisar) + salvar `.nfc` no NFC.
- **Tela de Logs** central (`AppLog`) com export.
- **SettingsScreen** — toggles conectados/persistidos (keep-screen-on funcional).
- **Testes unitários** (`src/test/LogicUnitTest.kt`) da lógica pura: TOTP, encoders IR, parser `.ir`, DuckyScript, Mifare, Wi-Fi channel, skimmer, `.nfc`/`.sub`.
- Permissões novas no manifesto: `TRANSMIT_IR`, `CAMERA`.

### Nota sobre build/SDK
Não há Android SDK utilizável neste ambiente (só um stub Debian), então **não foi possível compilar o app aqui**. O código foi validado por inspeção; os algoritmos críticos (encoder NEC, TOTP) foram cross-validados em Python. Rode na sua máquina (SDK em `/home/archiznoo/Android/Sdk`):
- `./gradlew testDebugUnitTest` — roda os testes unitários (não precisa de device).
- `./gradlew assembleDebug` — compila o APK.

### Do Evil-M5Project — o que é portável
- ✅ **Nativo:** Wardriving (feito), port scan (nmap, feito), skimmer detection (BLE/Wi-Fi), open-Wi-Fi checker.
- ⚠️ **Parcial nativo:** Evil Portal (`LocalOnlyHotspot` + servidor HTTP; sem roteamento de internet).
- ❌ **Só via M5:** deauth, beacon spam, karma, evil twin, sniffing/handshake (exigem injeção 802.11).
- 🔌 **Melhor caminho:** um **controlador do Evil-M5** no app (Web UI HTTP / UART-USB / BLE) para disparar os ataques do ESP32.

O restante abaixo permanece como backlog.

## Contexto: o que o Flipper Zero tem vs. o que um celular tem

| Interface Flipper Zero | Rádio/hardware dedicado | Celular Android tem? |
|---|---|---|
| **Sub-GHz** (300–928 MHz, CC1101) | Sim | ❌ Não (sem rádio Sub-GHz) |
| **RFID 125 kHz** (LF) | Sim | ❌ Não (NFC do celular é 13.56 MHz apenas) |
| **NFC 13.56 MHz** | Sim | ✅ Sim (limitado pelo controlador NFC) |
| **Infravermelho** | Sim (TX+RX) | ⚠️ Só alguns celulares (TX apenas, sem RX) |
| **iButton / 1-Wire** | Sim | ❌ Não |
| **GPIO** | Sim | ❌ Não (sem pinos expostos) |
| **BadUSB (HID gadget)** | Sim | ⚠️ Precisa de kernel com USB gadget + root |
| **Bluetooth LE** | Sim | ✅ Sim |
| **U2F** | Sim | ✅ Sim (via API) |

> **Conclusão-chave:** um celular **nunca** substitui o Sub-GHz, o RFID 125 kHz, o iButton nem o GPIO
> do Flipper — falta o hardware de rádio. Onde o FlipperDroid pode brilhar é em **NFC, IR, BLE, Wi-Fi e USB**,
> e em compatibilidade de formatos de arquivo (`.nfc`, `.ir`, `.sub`) para interoperar com um Flipper real.

---

## Prioridade ALTA — alto valor, viável em Android

### 1. Controle remoto IR universal (base de códigos)
- **O que faz:** telecomando universal de TV/AC/som, como o app *Infrared Remote* do Flipper.
- **Mapeia para:** Infrared.
- **Viabilidade:** ✅ Viável em celulares **com blaster IR** (Xiaomi, alguns Samsung/Huawei). Sem RX, não dá para *aprender* códigos capturando — só transmitir.
- **Como:** `ConsumerIrManager.transmit()` (já implementado com NEC nesta base). Falta uma **base de dados de códigos** por marca. Importar o [Flipper-IRDB](https://github.com/logickworkshop/Flipper-IRDB) e um parser do formato `.ir` (protocolos NEC, RC5, RC6, SIRC, Samsung32, RAW).
- **Esforço:** M

### 2. NFC avançado — salvar, reemitir e dicionário de chaves Mifare
- **O que faz:** salvar dumps de tags, reemular/reescrever, e **ataque de dicionário** de chaves Mifare Classic (como o app NFC do Flipper).
- **Mapeia para:** NFC.
- **Viabilidade:** ✅ Viável. (O Nested/Darkside do Flipper **não** é replicável — o controlador NFC do Android não expõe o rádio; só dá para testar dicionário de chaves conhecidas.)
- **Como:** `MifareClassic.authenticateSectorWithKeyA/B` iterando sobre um dicionário (`std.keys` do Flipper/mfoc). Persistir dumps em formato `.nfc` compatível com Flipper. Já existe `MifareClassicUtils.writeBlock`/`cloneUid` (cartões "magic").
- **Esforço:** M

### 3. QR / Barcode Scanner (com câmera)
- **O que faz:** ler QR/códigos de barras — a tela atual é só "Coming Soon".
- **Viabilidade:** ✅ Totalmente viável (ZXing já é dependência do projeto).
- **Como:** CameraX + `zxing` (ou ML Kit Barcode). Já geramos QR; falta a leitura.
- **Esforço:** S

### 4. Compatibilidade com formatos de arquivo do Flipper
- **O que faz:** importar/exportar `.nfc`, `.ir`, `.sub` para interoperar com um Flipper real.
- **Viabilidade:** ✅ Parsing é 100% viável (o uso do `.sub` depende de hardware externo).
- **Como:** parsers de texto simples desses formatos + Storage Access Framework.
- **Esforço:** M

### 5. BLE além do spam — Scanner + GATT Explorer
- **O que faz:** listar dispositivos BLE, serviços/características GATT, ler/escrever (base para pentest de IoT).
- **Mapeia para:** Bluetooth.
- **Viabilidade:** ✅ Viável. O `BluetoothViewModel` já existe mas está **incompleto** (sem `startScan()` público). Completá-lo.
- **Como:** `BluetoothLeScanner` + `BluetoothGatt`.
- **Esforço:** M

---

## Prioridade MÉDIA — viável com root ou ressalvas

### 6. Wi-Fi Evil Portal / Captive Portal
- **O que faz:** cria um AP com portal cativo para captura de credenciais (teste autorizado).
- **Viabilidade:** ⚠️ Parcial. `WifiManager.startLocalOnlyHotspot` + servidor HTTP local (NanoHTTPD). SSID e redirecionamento total são limitados vs. um AP real.
- **Esforço:** M

### 7. U2F / FIDO2 Authenticator
- **O que faz:** o celular como chave de segurança U2F.
- **Mapeia para:** U2F.
- **Viabilidade:** ✅ Via API (FIDO2 API / Credential Manager) — abordagem diferente do Flipper mas mesmo resultado.
- **Esforço:** M

### 8. BadUSB de verdade (USB HID Gadget) — **corrigir arquitetura**
- **O que faz:** o celular age como teclado ao ser plugado num PC (DuckyScript).
- **Viabilidade:** ⚠️ **A implementação atual está invertida.** Ela usa a API **USB Host** (celular como host) para mandar HID a um periférico — isso *não* é BadUSB. BadUSB real exige o celular em modo **USB Gadget/Peripheral** via `configfs` (`/dev/hidgX`), o que precisa de **kernel com HID gadget + root**.
- **Como:** script root que configura o gadget HID via `configfs` e escreve relatórios em `/dev/hidg0`. Adicionar um **parser DuckyScript** (DELAY, STRING, ENTER, GUI, CTRL, etc.).
- **Esforço:** L

### 9. Deauth Wi-Fi funcional (via companheiro ESP32)
- **O que faz:** deauth 802.11 de verdade.
- **Viabilidade:** ❌ no rádio interno da maioria dos celulares (sem monitor mode/injeção), mesmo com root. ✅ Com um **ESP32 externo** (ex.: ESP32 Marauder) via USB/BLE.
- **Como:** protocolo serial com firmware ESP32 Marauder. (Nesta base o deauth já reporta honestamente os pré-requisitos em vez de falhar em silêncio.)
- **Esforço:** L

---

## Prioridade BAIXA / Não viável em celular (documentar honestamente)

| Funcionalidade | Veredito | Motivo / alternativa |
|---|---|---|
| **Sub-GHz** (garagem 433 MHz, etc.) | ❌ Não viável | Sem rádio Sub-GHz. Alternativa: dongle CC1101 externo via USB. |
| **RFID 125 kHz** (crachás LF) | ❌ Não viável | NFC do celular é 13.56 MHz. Precisa de leitor LF externo. |
| **iButton / 1-Wire** | ❌ Não viável | Sem hardware 1-Wire. |
| **GPIO / debug hardware** | ❌ Não viável | Sem pinos. Alternativa: adaptador USB-serial/OTG. |
| **App Catalog / plugins** | ⚠️ Repensar | Módulos internos + deep links; não faz sentido copiar o `.fap` do Flipper. |

---

## Correções técnicas recomendadas (dívida técnica)

- **`BluetoothViewModel`**: incompleto (sem `startScan()` nem StateFlows públicos) — ou completar (item 5) ou remover.
- **BadUSB**: reescrever para USB Gadget (item 8).
- **`namespace`/`applicationId` = `com.example.flipperdroid`**: trocar por um domínio real antes de publicar.
- **Testes**: só há os `ExampleUnitTest`/`ExampleInstrumentedTest` gerados — adicionar cobertura para parsers (NEC/IR, `.nfc`, dicionário Mifare).

---

## Sugestão de sequência

1. QR Scanner (S) → vitória rápida, fecha um "Coming Soon".
2. IR universal + base de códigos + parser `.ir` (M).
3. NFC: salvar/reemitir dumps + dicionário Mifare + formato `.nfc` (M).
4. BLE Scanner/GATT (M) completando o `BluetoothViewModel`.
5. BadUSB via USB Gadget + DuckyScript (L).
6. Ponte ESP32 para Sub-GHz + deauth real (L) — a única via para “paridade de rádio” com o Flipper.
