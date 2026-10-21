# 배포 안내 (Oracle Cloud Always Free)

서버 한 대에 컨테이너 세 개를 띄운다.

| 컨테이너 | 하는 일 |
| --- | --- |
| web (Caddy) | HTTPS 인증서 발급·갱신, 프론트 화면 제공, `/api`·`/ws`만 app으로 전달, 보안 헤더 |
| app (Spring Boot) | `prod` 프로필로 실행. Swagger는 꺼져 있고, 밖에서 직접 접속할 수 없다 |
| mysql (MySQL 8.4) | 데이터는 도커 볼륨에 둔다. 포트를 밖에 열지 않는다 |

## 1. 인스턴스 만들기

1. Oracle Cloud 콘솔 → Compute → Instances → Create instance
2. Image: **Ubuntu 24.04**, Shape: **VM.Standard.A1.Flex (Ampere, ARM)**, 2 OCPU / 12GB면 충분하다(무료는 합계 4 OCPU / 24GB까지)
   - "Out of capacity"가 나오면 다른 Availability Domain을 고르거나 시간을 두고 다시 시도한다
3. SSH 키: 새로 만들기를 골라 개인 키를 내려받거나, 내 공개 키를 붙여 넣는다
4. 만든 뒤 **Public IP**를 적어 둔다

## 2. 80, 443 포트 열기

Oracle은 두 군데를 모두 열어야 한다.

**클라우드 방화벽:** 인스턴스 → Subnet → Security List → Add Ingress Rules

| Source CIDR | Protocol | Destination Port |
| --- | --- | --- |
| 0.0.0.0/0 | TCP | 80 |
| 0.0.0.0/0 | TCP | 443 |

**서버 안 방화벽:** Oracle의 Ubuntu 이미지는 iptables가 22번만 열어 둔다.

```bash
ssh ubuntu@<Public IP>
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 3. 도커 설치

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2 git
sudo usermod -aG docker $USER
exit                         # 다시 접속해야 docker 그룹이 적용된다
```

## 4. 도메인

- 도메인이 있으면 DNS에 A 레코드로 Public IP를 넣는다.
- 없으면 `IP의 점을 줄표로 바꾼 주소.sslip.io`를 쓴다. 예: IP가 `140.238.1.2`면 `140-238-1-2.sslip.io`. 따로 설정할 것은 없다.

## 5. 코드 받기

저장소가 공개면 그냥 받는다.

```bash
git clone https://github.com/yejeeni/moyeobom.git ~/moyeobom
```

비공개면 서버용 읽기 전용 키(Deploy key)를 만들어 받는다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github -N ""
cat ~/.ssh/github.pub        # GitHub 저장소 → Settings → Deploy keys → Add (Allow write access는 끈다)
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/github
EOF
git clone git@github.com:yejeeni/moyeobom.git ~/moyeobom
```

## 6. 설정 파일과 실행

```bash
cd ~/moyeobom
cp .env.example .env
openssl rand -base64 24      # 비밀번호로 쓸 값. 두 번 실행해 DB_PASSWORD, DB_ROOT_PASSWORD에 각각 넣는다
nano .env                    # DOMAIN, DB_PASSWORD, DB_ROOT_PASSWORD 채우기 (DB_PORT는 운영에서 쓰지 않는다)
chmod 600 .env
docker compose -f compose.prod.yaml up -d --build
```

처음 빌드는 몇 분 걸린다. `https://<DOMAIN>`으로 접속해 첫 화면이 뜨면 끝이다.

```bash
docker compose -f compose.prod.yaml ps              # 세 개가 running인지
docker compose -f compose.prod.yaml logs -f app     # "Started MoyeobomApplication"이 보이면 정상
docker compose -f compose.prod.yaml logs web        # 인증서 발급 실패 시 여기서 원인을 본다
```

## 7. 자동 배포 연결

main에 병합되고 CI가 통과하면 GitHub Actions(`deploy.yml`)가 서버에 접속해 `git pull`과 `up -d --build`를 한다. 서버를 연결하기 전에는 건너뛴다.

1. 내 PC에서 배포 전용 키를 만든다.
   ```bash
   ssh-keygen -t ed25519 -f moyeobom-deploy -N ""
   ```
2. 서버의 `~/.ssh/authorized_keys` 끝에 아래처럼 한 줄 추가한다. 앞부분 때문에 이 키로는 배포 스크립트만 실행되고, 키가 새도 서버에서 다른 일은 할 수 없다.
   ```
   restrict,command="bash /home/ubuntu/moyeobom/deploy/deploy.sh" <moyeobom-deploy.pub 내용 전체>
   ```
3. 서버 지문을 받는다.
   ```bash
   ssh-keyscan -t ed25519 <Public IP>
   ```
4. GitHub 저장소 → Settings → Secrets and variables → Actions에 넣는다.

| 이름 | 값 |
| --- | --- |
| `DEPLOY_HOST` | Public IP |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_KEY` | `moyeobom-deploy` 파일(개인 키) 전체 |
| `DEPLOY_KNOWN_HOSTS` | 3번에서 나온 줄 |

넣은 뒤 Actions → Deploy → Run workflow로 한 번 돌려 확인한다. 내 PC의 키 파일 두 개는 넣은 뒤 지워도 된다.

## 운영

```bash
cd ~/moyeobom
docker compose -f compose.prod.yaml restart app     # 앱만 다시 시작
docker compose -f compose.prod.yaml down            # 모두 멈춤 (데이터는 남는다)

# DB 백업
docker compose -f compose.prod.yaml exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" moyeobom' > backup-$(date +%F).sql
```

- `down -v`는 DB와 인증서 볼륨까지 지우니 쓰지 않는다.
- 열람실과 메이트는 서버 메모리에만 있어, 배포하면 사람들이 방에 다시 들어온다. 기록(스프린트, 할 일, 집중 시간)은 DB에 남는다.
- DB를 Oracle HeatWave로 옮기려면 `compose.prod.yaml`의 `DB_URL`을 HeatWave 주소로 바꾸고 mysql 서비스를 뺀다.
