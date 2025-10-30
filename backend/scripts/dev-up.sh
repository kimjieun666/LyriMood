#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${PROJECT_ROOT}"

if ! command -v docker >/dev/null 2>&1; then
  echo "❌ Docker가 설치되어 있지 않습니다. https://docs.docker.com/get-docker/ 를 참고해 설치해 주세요."
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "❌ Docker 데몬이 실행 중이 아닙니다. Docker Desktop을 먼저 켜 주세요."
  exit 1
fi

echo "▶︎ 환경 변수 파일(.env.local) 확인 중..."
if [ ! -f "${PROJECT_ROOT}/.env.local" ]; then
  echo "❌ .env.local 파일이 없습니다. .env.example을 참고해 만들어 주세요."
  exit 1
fi

echo "🚀 Docker Compose로 백엔드 컨테이너를 실행합니다."
docker compose up --build
