# Introduction

This is a TPM 2.0 provisioning service.

# Table of Contents

- **[Prerequisites](#prerequisites)**
- **[Endpoints](#endpoints)**
- **[Prepare Docker Image](#prepare-docker-image)**
- **[Device Agent](#device-agent)**
- **[References](#references)**
- **[License](#license)**

# Prerequisites

- Software: Docker, Maven, Intellij IDEA
- Raspberry Pi 4 Model B
- [Iridium 9670 TPM 2.0 board](https://www.infineon.com/cms/en/product/evaluation-boards/iridium9670-tpm2.0-linux/)

# Endpoints

Table below for summary or visit [here](doc/architecture.pptx) for graphic illustration.

| Microservice | Endpoint / Console | Info |
|---|---|---|
| TPM20 | Public:<ul><li>http://localhost:1014/api</li></ul>Management:<ul><li>http://localhost:1015/actuator</li><li>http://localhost:1015/actuator/openapi</li><li>http://localhost:1015/actuator/swaggerui</li></ul> | |

# Prepare Docker Image

Build from scratch:
```
$ git clone https://github.com/wxleong/tpm2-provision-service
$ cd ~/tpm2-provision-service
$ docker build -t tpm20:local -f "./tpm20/Dockerfile" .
$ docker run -d -p 1014:1014 -p 1015:1015 --rm -it tpm20:local
```

# Device Agent

An example using the tpm20 service to provision a TPM on Raspberry Pi.

Tested on:
- Raspberry Pi 4 Model B ([Raspberry Pi OS image](https://downloads.raspberrypi.org/raspios_armhf/images/raspios_armhf-2021-11-08/2021-10-30-raspios-bullseye-armhf.zip))
- [Iridium 9670 TPM 2.0 board](https://www.infineon.com/cms/en/product/evaluation-boards/iridium9670-tpm2.0-linux/)

Install Docker on Raspberry Pi:
```
$ curl -fsSL https://get.docker.com -o get-docker.sh
$ sudo sh get-docker.sh
$ sudo usermod -aG docker pi
$ docker version
```

Download tpm20 docker image:
```
$ docker pull --platform linux/arm/v7 ghcr.io/wxleong/tpm2-provision-service/tpm20:develop-genesis-v1.0
$ docker images
```

Address a known [issue](https://github.com/AdoptOpenJDK/openjdk-docker/issues/469) by updating libseccomp to version 2.4.2 or newer:
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

Launch the tpm20 image:
```
$ docker run -d -p 1014:1014 -p 1015:1015 --rm -it ghcr.io/wxleong/tpm2-provision-service/tpm20:develop-genesis-v1.0
```

Build device agent:
```
$ git clone https://github.com/wxleong/tpm2-provision-service ~/tpm2-provision-service
$ cd ~/tpm2-provision-service/device
$ gcc -Wall xfer.c -o xfer
```

Finally, run the get-random script. TPM commands and responses are exchanged between the TPM and tpm20 service, device's TSS library is not involved at this stage. Eventually, the service will obtain a random value from the TPM.
```
$ sudo chmod a+rw /dev/tpmrm0
$ chmod a+x provision-get-random.sh
$ ./provision-get-random.sh
```

Create RSA2048 endorsement key and persist it at handle `0x81010001`:
```
$ tpm2_clear -c p
$ sudo chmod a+rw /dev/tpmrm0
$ chmod a+x provision.sh
$ ./provision.sh create-ek-rsa2048
$ tpm2_readpublic -c 0x81010001
```

Customize your own script in [tpm20/src/main/java/com/infineon/tpm20/script](tpm20/src/main/java/com/infineon/tpm20/script).

# References

<a id="1">[1] https://www.infineon.com/cms/en/product/security-smart-card-solutions/optiga-embedded-security-solutions/optiga-tpm/</a> <br>
<a id="2">[2] https://start.spring.io/</a> <br>
<a id="3">[3] https://maven.apache.org/</a> <br>

# License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.