# 자동 배포용 SSH 키는 이 스크립트만 실행할 수 있다(authorized_keys의 command=). 키가 새도 서버에서 다른 명령은 못 한다
set -e
cd "$(dirname "$0")/.."
git pull --ff-only
docker compose -f compose.prod.yaml up -d --build
docker image prune -f
