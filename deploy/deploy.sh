# 자동 배포용 SSH 키는 이 스크립트만 실행할 수 있다(authorized_keys의 command=). 키가 새도 서버에서 다른 명령은 못 한다
# GitHub Actions가 만든 app, web 이미지를 표준 입력(docker save | gzip)으로 받아 띄운다
set -eo pipefail
cd "$(dirname "$0")/.."
git pull --ff-only
gunzip | docker load
docker compose -f compose.prod.yaml up -d --no-build --remove-orphans
docker image prune -f
