# 배포 안내 (Oracle Cloud Always Free)

무료 서버 한 대(VM.Standard.E2.1.Micro, AMD, 메모리 1GB)에 컨테이너 세 개를 띄운다.

| 컨테이너 | 하는 일 |
| --- | --- |
| web (Caddy) | HTTPS 인증서 발급·갱신, 프론트 화면 제공, `/api`·`/ws`만 app으로 전달, 보안 헤더 |
| app (Spring Boot) | `prod` 프로필로 실행. Swagger는 꺼져 있고, 밖에서 직접 접속할 수 없다. 힙 256MB |
| mysql (MySQL 8.4) | 데이터는 도커 볼륨에 둔다. 포트를 밖에 열지 않는다. 버퍼를 줄여 150MB 안팎 |

서버는 메모리가 작아 빌드하지 않는다. main에 병합되어 CI가 통과하면 GitHub Actions가 이미지를 만들어 SSH로 보내고, 서버의 `deploy/deploy.sh`가 받아서 띄운다.

## 1. 인스턴스 만들기

1. Oracle Cloud 콘솔 → Compute → Instances → Create instance
2. Image: **Ubuntu 24.04**, Shape: **VM.Standard.E2.1.Micro** (상시 무료 적격)
3. SSH 키: 새로 만들기를 골라 개인 키를 내려받거나, 내 공개 키를 붙여 넣는다
4. 만든 뒤 **Public IP**를 적어 둔다

## 2. 80, 443 포트 열기

Oracle은 두 군데를 모두 열어야 한다.

**클라우드 방화벽:** 인스턴스 → Subnet → Security List → Add Ingress Rules

| Source CIDR | Protocol | Destination Port |
| --- | --- | --- |
| 0.0.0.0/0 | TCP | 80 |
| 0.0.0.0/0 | TCP | 443 |

**서버 안 방화벽:** Oracle의 Ubuntu 이미지는 iptables가 22번만 열어 둔다. 허용 규칙은 REJECT 규칙보다 **앞에** 있어야 한다. REJECT의 번호는 이미지마다 달라서(5번 또는 6번) 먼저 찾아서 그 자리에 넣는다.

```bash
ssh -i <내려받은 개인 키> ubuntu@<Public IP>
N=$(sudo iptables -L INPUT -n --line-numbers | awk '/REJECT/{print $1; exit}')
sudo iptables -I INPUT $N -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT $N -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo netfilter-persistent save
sudo iptables -L INPUT -n --line-numbers   # 80, 443이 REJECT보다 위에 있어야 한다
```

## 3. 스왑과 도커

메모리 1GB가 모자랄 때 디스크를 대신 쓰도록 스왑 2GB를 붙인다.

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2 git
sudo usermod -aG docker $USER
exit                         # 다시 접속해야 docker 그룹이 적용된다
```

다시 접속해 `free -h`에서 Swap 2.0Gi, `docker compose version`이 나오면 된다.

## 4. 도메인

- 도메인이 있으면 DNS에 A 레코드로 Public IP를 넣는다.
- 없으면 `IP의 점을 줄표로 바꾼 주소.sslip.io`를 쓴다. 예: IP가 `140.238.1.2`면 `140-238-1-2.sslip.io`. 따로 설정할 것은 없다.

## 5. 코드 받기와 설정 파일

서버에는 설정 파일(`compose.prod.yaml`, `deploy.sh`)과 `.env`만 있으면 된다.

```bash
git clone https://github.com/yejeeni/moyeobom.git ~/moyeobom
cd ~/moyeobom
cp .env.example .env
openssl rand -base64 24      # 비밀번호로 쓸 값. 두 번 실행해 DB_PASSWORD, DB_ROOT_PASSWORD에 각각 넣는다
nano .env                    # DOMAIN, DB_PASSWORD, DB_ROOT_PASSWORD 채우기 (DB_PORT는 운영에서 쓰지 않는다)
chmod 600 .env
```

저장소를 비공개로 바꾼 뒤라면, 서버용 읽기 전용 키(Deploy key)로 받는다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github -N ""
cat ~/.ssh/github.pub        # GitHub 저장소 → Settings → Deploy keys → Add (Allow write access는 끈다)
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/github
EOF
cd ~/moyeobom && git remote set-url origin git@github.com:yejeeni/moyeobom.git
```

## 6. 자동 배포 연결

1. 내 PC에서 배포 전용 키를 만든다.
   ```bash
   ssh-keygen -t ed25519 -f moyeobom-deploy -N ""
   ```
2. 서버의 `~/.ssh/authorized_keys` 끝에 아래처럼 한 줄 추가한다. 앞부분 때문에 이 키로는 배포 스크립트만 실행되고, 키가 새도 서버에서 다른 일은 할 수 없다.
   ```
   restrict,command="bash /home/ubuntu/moyeobom/deploy/deploy.sh" <moyeobom-deploy.pub 내용 전체>
   ```
3. 서버 지문을 받는다(내 PC에서).
   ```bash
   ssh-keyscan -t ed25519 <Public IP>
   ```
4. GitHub 저장소 → Settings → Secrets and variables → Actions → New repository secret으로 넣는다.

| 이름 | 값 |
| --- | --- |
| `DEPLOY_HOST` | Public IP |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_KEY` | `moyeobom-deploy` 파일(개인 키) 전체 |
| `DEPLOY_KNOWN_HOSTS` | 3번에서 나온 줄 |

내 PC의 키 파일 두 개는 넣은 뒤 지워도 된다.

## 7. 첫 배포

GitHub → Actions → Deploy → Run workflow(main). 5분 안팎 걸린다. 끝나면 `https://<DOMAIN>`으로 접속해 첫 화면이 뜨는지 본다.

```bash
cd ~/moyeobom
docker compose -f compose.prod.yaml ps              # 세 개가 running인지
docker compose -f compose.prod.yaml logs -f app     # "Started MoyeobomApplication"이 보이면 정상
docker compose -f compose.prod.yaml logs web        # 인증서 발급 실패 시 여기서 원인을 본다
```

이후로는 main에 병합하면 알아서 배포된다.

## 운영

```bash
cd ~/moyeobom
docker compose -f compose.prod.yaml restart app     # 앱만 다시 시작
docker compose -f compose.prod.yaml down            # 모두 멈춤 (데이터는 남는다)
docker compose -f compose.prod.yaml up -d --no-build  # 다시 띄우기
free -h                                             # 메모리와 스왑 사용량

# DB 백업
docker compose -f compose.prod.yaml exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" moyeobom' > backup-$(date +%F).sql
```

- `down -v`는 DB와 인증서 볼륨까지 지우니 쓰지 않는다.
- 열람실과 메이트는 서버 메모리에만 있어, 배포하면 사람들이 방에 다시 들어온다. 기록(스프린트, 할 일, 집중 시간)은 DB에 남는다.
- 메모리가 넉넉한 서버(A1 등)로 옮기면 서버에서 `docker compose -f compose.prod.yaml up -d --build`로 직접 빌드해도 된다. ARM 서버라면 `deploy.yml`의 빌드도 arm64로 바꿔야 한다.
