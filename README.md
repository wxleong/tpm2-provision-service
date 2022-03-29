# Introduction

Provisioning server for TPM 2.0.

# Table of Contents

- **[Prerequisites](#prerequisites)**
- **[Endpoints](#endpoints)**
- **[Project tpm20](#project-tpm20)**
- **[Prepare Docker Image](#prepare-docker-image)**
- **[Device Agent](#device-agent)**
- **[References](#references)**
- **[License](#license)**

# Prerequisites

- Software: Docker, Maven, Intellij IDEA
- Raspberry Pi 4 Model B
- Iridium 9670 TPM 2.0 board [[5]](#5)

# Endpoints

| Microservice | Endpoint / Console | Info |
|---|---|---|
| TPM20 | Public:<ul><li>http://localhost:1014/api</li></ul>Management:<ul><li>http://localhost:1015/actuator</li><li>http://localhost:1015/actuator/openapi</li><li>http://localhost:1015/actuator/swaggerui</li></ul> | |

# Project tpm20

- You may modify the project [tpm20](tpm20) using any Java IDE that supports Maven project (e.g., IntelliJ)
- The project test suite can only run on Windows machine with built-in TPM 2.0

# Prepare Docker Image

You may launch the service on docker.

Build from scratch:
```
$ git clone https://github.com/wxleong/tpm2-provision-service
$ cd ~/tpm2-provision-service
$ docker build -t tpm20:local -f "./tpm20/Dockerfile" .
$ docker run -d -p 1014:1014 -p 1015:1015 --rm -it tpm20:local
```

# Device Agent

Using the tpm20 service to provision a TPM on Raspberry Pi.

Tested on:
- Raspberry Pi 4 Model B (Raspberry Pi OS image [[6]](#6))
- Iridium 9670 TPM 2.0 board [[5]](#5)

Setup your Raspberry Pi according to [[4]](#4).

Install Docker on Raspberry Pi:
```
$ curl -fsSL https://get.docker.com -o get-docker.sh
$ sudo sh get-docker.sh
$ sudo usermod -aG docker pi
$ docker version
```

Address a known issue [[7]](#7) by updating libseccomp to version 2.4.2 or newer:
<!--
```
$ sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 04EE7237B7D453EC 648ACFD622F3D138
$ echo 'deb http://httpredir.debian.org/debian buster-backports main contrib non-free' | sudo tee -a /etc/apt/sources.list.d/debian-backports.list
$ sudo apt update
$ sudo apt install libseccomp2 -t buster-backports
```
-->
```
$ sudo apt install gperf
$ wget https://github.com/seccomp/libseccomp/releases/download/v2.5.3/libseccomp-2.5.3.tar.gz ~/
$ cd ~
$ tar -xf libseccomp-2.5.3.tar.gz
$ cd libseccomp-2.5.3/
$ ./configure --prefix=/usr --disable-static
$ make -j$(nproc)
$ sudo make install
```

Download tpm20 docker image:
```
$ docker pull --platform linux/arm/v7 ghcr.io/wxleong/tpm2-provision-service/tpm20:develop-genesis-v2.0
$ docker images
```

Launch the image:
```
$ docker run -d -p 1014:1014 -p 1015:1015 --rm -it ghcr.io/wxleong/tpm2-provision-service/tpm20:develop-genesis-v2.0
```

Build device agent:
```
$ git clone https://github.com/wxleong/tpm2-provision-service ~/tpm2-provision-service
$ cd ~/tpm2-provision-service/device
$ gcc -Wall xfer.c -o xfer
```

List supported scripts:
```
$ curl http://localhost:1014/api/v1/scripts
```

Alternatively, develop your own script and drop it [here](tpm20/src/main/java/com/infineon/tpm20/script).

## Example 1

A script to get random value. TPM commands and responses are exchanged between the TPM and tpm20 service, device's TSS library is not involved at this stage. Eventually, the service will obtain a random value from the TPM.
```
$ sudo chmod a+rw /dev/tpm0
$ chmod a+x get-random.sh
$ ./get-random.sh
```

## Example 2

A script to create an RSA2048 Endorsement Key (EK) and persist it at handle `0x81010001`:
```
$ sudo chmod a+rw /dev/tpm0
$ chmod a+x provision.sh
$ ./provision.sh create-ek-rsa2048
$ tpm2_readpublic -c 0x81010001
```

You may find the associated EK certificate at:
```
$ tpm2_nvread 0x1c00002 -o rsa_ek.crt.der
$ openssl x509 -inform der -in rsa_ek.crt.der -text
```

## Example 3

A script to perform device authentication, capable of proving a device contains an authentic TPM:
```
$ sudo chmod a+rw /dev/tpm0
$ chmod a+x provision.sh
$ ./provision.sh ek-rsa2048-based-auth
```

# References

<a id="1">[1] https://www.infineon.com/cms/en/product/security-smart-card-solutions/optiga-embedded-security-solutions/optiga-tpm/</a> <br>
<a id="2">[2] https://start.spring.io/</a> <br>
<a id="3">[3] https://maven.apache.org/</a> <br>
<a id="4">[4] https://github.com/wxleong/tpm2-rpi4/</a> <br>
<a id="5">[5] https://www.infineon.com/cms/en/product/evaluation-boards/iridium9670-tpm2.0-linux/</a> <br>
<a id="6">[6] https://downloads.raspberrypi.org/raspios_armhf/images/raspios_armhf-2021-11-08/2021-10-30-raspios-bullseye-armhf.zip</a> <br>
<a id="7">[7] https://github.com/AdoptOpenJDK/openjdk-docker/issues/469</a> <br>

# License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
