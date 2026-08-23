# O que dá para fazer com Android — capacidades para pentest / ferramentas estilo Flipper

Levantamento do que um smartphone Android consegue fazer no contexto do FlipperDroid,
organizado por **nível de acesso necessário**. Marcado se o FlipperDroid já faz (✅),
poderia fazer nativamente (🟢), precisa root (🔴), precisa hardware externo (🔌) ou é inviável (⛔).

> **Status:** muitos itens 🟢/🔴 abaixo já foram implementados (BLE HID, Evil Portal, 2FA/TOTP,
> decoders `.nfc`/`.sub`, Wardriving, Skimmer, WiFi Analyzer + MAC spoof, BadUSB gadget, base IR).
> Veja a lista atual de "✅ Implementado" no [`ROADMAP.md`](ROADMAP.md). U2F/FIDO2 completo continua
> fora de escopo (exige backend de relying party + APIs FIDO2/Credential Provider e teste em device).

---

## A) Nativo — sem root, só APIs do Android

### NFC / RFID 13.56 MHz
- ✅ **Ler Mifare Classic** (UID, dump com chaves), **ataque por dicionário** de chaves, **NDEF** read.
- ✅ **Ler cartão EMV** (PAN mascarado, validade) via IsoDep.
- ✅ **HCE — emular cartão** (`HostApduService`) — já registrado no app.
- 🟢 **NDEF write / formatação** de tags.
- 🟢 **Clonar UID** para cartões "magic" (já implementado).
- 🟢 **Relay NFC/HCE** (encaminhar APDUs POS↔servidor): tecnicamente possível via HostApduService; é a base do "tap-and-steal". Limite: o AID precisa ser conhecido/registrado; roteamento total exige root. **Uso estritamente autorizado.**
- ⛔ Recuperação de chaves Nested/Darkside (o controlador NFC não expõe o rádio).
- ⛔ RFID 125 kHz (LF) — hardware diferente.

### Bluetooth / BLE
- ✅ **BLE Spam** (Apple, Samsung, Microsoft SwiftPair, Google Fast Pair).
- ✅ **BLE Scanner + GATT Explorer** (serviços/características, read).
- ✅ **Skimmer detector** (Classic + BLE por assinatura de módulo série).
- 🟢 GATT write/notify/subscribe, MTU, bonding.
- 🟢 **Advertiser/beacon** customizado (iBeacon, Eddystone) e simular periférico BLE.
- 🟢 Escanear Bluetooth Classic (descoberta, SDP, nomes).
- 🟢 BLE HID host (celular controla teclado/mouse BLE) e **BLE HID device** (celular como teclado BLE p/ PC — via BluetoothHidDevice API).
- ⛔ Sniffing do link BLE / captura de outros pares (precisa rádio em modo promíscuo).

### Wi-Fi
- ✅ **Scan de redes** (SSID/BSSID/canal/RSSI/segurança).
- ✅ **Wardriving** com GPS → CSV Wigle.
- 🟢 **Analisador de canais** (gráfico de ocupação 2.4/5 GHz).
- 🟢 **Hotspot local** (`startLocalOnlyHotspot`) → base de um **Evil/Captive Portal** com servidor HTTP (parcial: sem NAT/internet).
- 🟢 Info da conexão atual, força, IP, gateway, DNS.
- ⛔ **Deauth / beacon spam / evil twin / handshake capture** — exigem monitor mode + injeção (rádio bloqueia).

### Rede (camada IP) — já é forte no app
- ✅ **nmap** (via root, empacotado), ping, portscan, DNS, traceroute.
- 🟢 Sem root: portscan por sockets, mDNS/SSDP/UPnP discovery, banner grabbing, ARP via `/proc/net/arp`, HTTP recon, DNS lookups, WHOIS.
- 🟢 **Responder/LLMNR-poisoning leve**, servidor HTTP/phishing local, MITM em rede própria (proxy).
- 🟢 Wake-on-LAN, escaneamento de sub-rede, fingerprinting de dispositivos.

### Infravermelho (só aparelhos com blaster)
- ✅ **TX IR** (NEC) — telecomando universal + teclado numérico.
- 🟢 Mais protocolos (RC5/RC6/SIRC/Samsung) + **base de códigos** (Flipper-IRDB, formato `.ir`).
- ⛔ **RX/aprender IR** — quase nenhum celular tem receptor IR.

### Sensores / captura / utilidades
- 🟢 Câmera: ✅ **QR/barcode scan** (feito); OCR (ML Kit), detecção de câmeras ocultas por reflexo IR.
- 🟢 GPS/IMU/magnetômetro (detector de campo magnético, bússola).
- 🟢 Microfone: análise de espectro, detecção de ultrassom (beacons ultrassônicos).
- 🟢 Gerador de senhas (✅), cofre, TOTP/2FA, **U2F/FIDO2** (Credential Manager).
- 🟢 Leitor/decoder de formatos Flipper (`.nfc`, `.ir`, `.sub`) para interoperar.

---

## B) Precisa de ROOT (device rooteado)

- 🔴 **nmap / binários nativos** (já usado assim), tcpdump, aircrack-suite (offline), scapy via Termux.
- 🔴 **Monitor mode + injeção no chip interno via Nexmon** — só chips Broadcom específicos (Nexus 5/6P, alguns Galaxy antigos). Habilita **deauth/handshake reais** no rádio interno.
- 🔴 **BadUSB real (USB Gadget)** — configurar HID via `configfs` (`/dev/hidgX`) + DuckyScript. (A implementação atual do app usa USB *Host* e precisa ser reescrita.)
- 🔴 Firewall/VPN a nível de pacote, sniffing de tráfego local, spoofing de MAC, iptables/NAT (Evil Portal completo com internet).
- 🔴 Roteamento NFC/HCE arbitrário (relay sem restrição de AID).

---

## C) Precisa de HARDWARE EXTERNO (USB-OTG ou BLE) — o celular vira o "cérebro"

Este é o caminho para ter **paridade real com o Flipper Zero**: o celular controla um módulo.

- 🔌 **Adaptador Wi-Fi USB** (AR9271/RT3070/RTL8812AU) → **deauth, evil twin, handshake, injeção** reais (modelo Kali NetHunter).
- 🔌 **ESP32 / ESP8266** (Marauder, Evil-M5, DSTIKE) via USB-serial ou BLE → deauth, beacon spam, karma, evil portal, wardriving no ESP.
- 🔌 **Proxmark3** (USB-OTG ou BLE) → **RFID 125 kHz LF + HF completo**, clonagem, sniffing, ataques a chaves.
- 🔌 **RTL-SDR / HackRF / dongles Sub-GHz (CC1101)** via OTG → **receber (e com HackRF, transmitir) rádio**: Sub-GHz 315/433/868/915 MHz, ADS-B, POCSAG, etc. (apps: SDR Touch, SDR++).
- 🔌 **Arduino / microcontrolador** via ponte serial (Termux não tem driver serial OTG nativo — usa `usb-serial-for-android` no app ou ponte TCP).
- 🔌 **iButton/1-Wire, GPIO** → só com adaptador USB dedicado.

Bibliotecas-chave para o app: `usb-serial-for-android` (CDC/FTDI/CP210x/CH34x), `UsbManager` (host), BLE GATT.

---

## D) Inviável no smartphone (mesmo com root)

- ⛔ **Sub-GHz nativo** (sem rádio < 1 GHz) — só com SDR/CC1101 externo.
- ⛔ **RFID 125 kHz nativo** — só com Proxmark/leitor LF externo.
- ⛔ **iButton/1-Wire, GPIO** nativos — sem pinos.
- ⛔ **Sniffing de rádio BLE/Wi-Fi de terceiros** sem monitor mode.
- ⛔ **IMSI catcher / manipulação de banda base** — o modem é fechado; inviável e ilegal na maioria dos casos.

---

## Prioridades sugeridas para o FlipperDroid (do mais barato ao mais poderoso)

1. 🟢 **Wi-Fi analyzer** (gráfico de canais) e **decoders de formato Flipper** (`.nfc`/`.ir`) — nativo, barato.
2. 🟢 **IR code database** (Flipper-IRDB) + mais protocolos — transforma o IR em telecomando universal de verdade.
3. 🟢 **BLE advertiser/beacon** (iBeacon/Eddystone) e **BLE HID device** (teclado BLE p/ PC — um "BadKB" sem cabo).
4. 🔌 **Controlador ESP32/Evil-M5** (serial-OTG ou BLE) — desbloqueia deauth/beacon/evil-portal/wardriving reais.
5. 🔌 **Controlador Proxmark3** (BLE/OTG) — desbloqueia RFID LF/HF completo.
6. 🔌 **Ponte RTL-SDR/HackRF** — desbloqueia Sub-GHz (recepção; TX com HackRF).
7. 🔴 **BadUSB via USB Gadget + DuckyScript** (root) — BadUSB de verdade.

> **Regra de ouro:** tudo que envolve **rádio bruto** (Sub-GHz, LF RFID, injeção Wi-Fi, sniff BLE) exige **hardware externo ou chip/firmware especial**. Tudo que é **protocolo de alto nível** (NFC/HCE, BLE GATT, IP/rede, IR TX, câmera/sensores) o celular faz sozinho.
