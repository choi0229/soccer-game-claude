#!/usr/bin/env sh
# JDK 21 과 Maven 이 없는 환경에서도 Docker 로 백엔드 빌드·테스트를 돌린다.
# 사용: ./backend/test.sh [maven 인자...]   (기본: test)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ "$#" -eq 0 ]; then set -- -q test; fi
exec docker run --rm -v "$ROOT":/workspace -v soccer-game-m2:/root/.m2 -w /workspace/backend \
  maven:3.9-eclipse-temurin-21 mvn -B "$@"
