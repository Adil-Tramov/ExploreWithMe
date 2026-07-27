#!/bin/bash

# Проверка переменных окружения и установка значений по умолчанию
if [ -z "$BRANCH_NAME" ]; then
  BRANCH_NAME=${GITHUB_HEAD_REF:-${GITHUB_REF##*/}}
fi

if [ -z "$BRANCH_NAME" ]; then
  echo "❌ Ошибка: Не удалось определить ветку."
  exit 1
fi

echo "Текущая ветка: $BRANCH_NAME"

if ! command -v jq &> /dev/null; then
  echo "❌ Ошибка: Утилита jq не установлена."
  exit 1
fi

WAIT_FOR_IT="$(dirname "$0")/wait-for-it.sh"

# Создаем директорию логов, если ее нет
LOG_DIR="${LOG_DIR:-./logs}"
mkdir -p "$LOG_DIR"
ROOT_DIR="${ROOT_DIR:-./}"

# Для сервисов работающих с бд
POSTGRES_USER=${POSTGRES_USER:-postgres}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-password}
POSTGRES_PORT=${POSTGRES_PORT:-5432}

EUREKA_PORT=${EUREKA_PORT:-8761}
EUREKA_URL="http://localhost:${EUREKA_PORT}/eureka"

# Функция поиска правильного JAR'ника
find_jar() {
  local service=$1
  local jar_path

  # Ищем *-boot.jar сначала
  jar_path=$(find ./ -name "${service}-*-boot.jar" | head -n 1)
  if [ -z "$jar_path" ]; then
    # Если нет boot-версии, ищем обычный JAR
    jar_path=$(find ./ -name "${service}-*.jar" | head -n 1)
  fi

  echo "$jar_path"
}

check_grpc_service() {
  local service_name=$1
  echo "⏳ Поиск grpc порта для сервиса $service_name в Eureka..."

  for i in {1..10}; do
    local metadata=$(curl -s -H "Accept: application/json" "${EUREKA_URL}/apps/$service_name")

    if [[ -z "$metadata" ]]; then
      echo "⚠️ Ответ от Eureka пустой или невалидный."
      sleep 3
      continue
    fi

    local GRPC_PORT=$(echo "$metadata" | jq -r '.application.instance | if type=="array" then .[0] else . end | .metadata["gRPC_port"] // empty')

    if [[ -n "$GRPC_PORT" ]]; then
      echo "✅ Найден grpc порт: $GRPC_PORT"
      $WAIT_FOR_IT localhost:$GRPC_PORT --timeout=10 --strict -- echo "✅ Service $service_name is up on gRPC port $GRPC_PORT"
      return
    fi

    echo "⏳ grpcPort для сервиса $service_name не найден... ($i/10)"
    sleep 3
  done

    echo "❌ Не удалось найти grpcPort для сервиса $service_name в Eureka."
    echo "📋 Список приложений, зарегистрированных в Eureka:"
    curl -s -H "Accept: application/json" "${EUREKA_URL}/apps" | jq .
    exit 1
}

check_http_service() {
  local service_name=$1
  echo "⏳ Проверка доступности HTTP-сервиса $service_name в Eureka..."

  for i in {1..10}; do
    local metadata=$(curl -s -H "Accept: application/json" "${EUREKA_URL}/apps/$service_name")

    if [[ -z "$metadata" ]]; then
      echo "⏳ Ждём регистрации $service_name в Eureka... ($i/10)"
      sleep 3
      continue
    fi

    local SERVICE_URL=$(echo "$metadata" | jq -r '.application.instance | if type=="array" then .[0] else . end | .homePageUrl // empty')

    if [[ -n "$SERVICE_URL" ]]; then
      echo "✅ Найден адрес $service_name: $SERVICE_URL"

      # 1. Попробуем проверить /actuator/health
      if curl -s --connect-timeout 5 "${SERVICE_URL%/}/actuator/health" | jq -e '.status == "UP"' > /dev/null; then
        echo "✅ HTTP-сервис $service_name доступен и здоров (по actuator/health)."
        return
      fi

      # 2. Если не удалось — просто проверим, что сервис отвечает
      if curl -s --connect-timeout 5 "$SERVICE_URL" > /dev/null; then
        echo "⚠️  Сервис $service_name отвечает, но не отдает статус 'UP' (возможно, нет actuator/health)"
        return
      else
        echo "❌ Сервис $service_name пока не отвечает... ($i/10)"
      fi
    else
      echo "⏳ Ждём регистрации $service_name в Eureka... ($i/10)"
    fi

    sleep 3
  done

  echo "❌ Не удалось проверить доступность HTTP-сервиса $service_name."
  echo "📋 Список приложений, зарегистрированных в Eureka:"
  curl -s -H "Accept: application/json" "${EUREKA_URL}/apps" | jq .
  exit 1
}

start_service() {
  local service_name=$1
  local extra_args=$2
  local registration_check=${3:-http}
  local optional=${4:false}

  local jar_path="$(find_jar $service_name)"
  local log_file="${LOG_DIR}/${service_name}.log"

  if [ -z "$jar_path" ]; then
    if [[ "$optional" == "true" ]]; then
      return
    fi
    echo "❌ Ошибка: JAR-файл для сервиса $service_name не найден."
    exit 1
  fi

  echo "⏳ Запуск сервиса $service_name..."
  nohup java -jar "$jar_path" $extra_args \
        --eureka.client.serviceUrl.defaultZone=${EUREKA_URL}/ \
        --logging.file.name="$log_file" \
        > "$log_file" 2>&1 &

  sleep 15
  if ! pgrep -f "$jar_path" > /dev/null; then
    echo "❌ Ошибка: Сервис $service_name не запустился. Проверьте логи: $log_file"
    cat "$log_file"
    exit 1
  fi

  if [[ "$registration_check" == "grpc" ]]; then
    check_grpc_service "$service_name"
  elif [[ "$registration_check" == "http" ]]; then
    check_http_service "$service_name"
  fi

  echo "✅ Сервис $service_name успешно запущен и готов к работе."
}

start_stateful_service() {
  local service_name=$1
  local db_name=$2
  local extra_args=$3
  local registration_check=${4:-http}

  extra_args+=" --spring.datasource.url=jdbc:postgresql://localhost:${POSTGRES_PORT}/$db_name"
  extra_args+=" --spring.datasource.username=${POSTGRES_USER}"
  extra_args+=" --spring.datasource.password=${POSTGRES_PASSWORD}"

  start_service $service_name "$extra_args" "$registration_check"
}

start_optional_service() {
  local service_name=$1
  local db_name=$2
  local extra_args=$3
  local registration_check=${4:-http}

  extra_args+=" --spring.datasource.url=jdbc:postgresql://localhost:${POSTGRES_PORT}/$db_name"
  extra_args+=" --spring.datasource.username=${POSTGRES_USER}"
  extra_args+=" --spring.datasource.password=${POSTGRES_PASSWORD}"

  start_service $service_name "$extra_args" "$registration_check" "true"
}

start_platform_core() {
  start_service "discovery-server" "--server.port=${EUREKA_PORT}" "none"
  $WAIT_FOR_IT localhost:${EUREKA_PORT} --timeout=20 --strict -- echo "✅ Eureka is up"

  start_service "config-server"

  sleep 10
}

print_gateway_routes() {
  local service_name="gateway-server"

  echo "📡 Пробуем получить маршруты из $service_name через /actuator/gateway/routes..."

  local metadata=$(curl -s -H "Accept: application/json" "${EUREKA_URL}/apps/$service_name")
  local SERVICE_URL=$(echo "$metadata" | jq -r '.application.instance | if type=="array" then .[0] else . end | .homePageUrl // empty')

  if [[ -z "$SERVICE_URL" ]]; then
    echo "⚠️ Не удалось получить URL сервиса gateway-server из Eureka. Пробуем использовать http://localhost:8080"
    SERVICE_URL="http://localhost:8080"
  fi

  local routes_response=$(curl -s --connect-timeout 5 "${SERVICE_URL%/}/actuator/gateway/routes")
  if [[ -n "$routes_response" ]]; then
    echo "✅ Получены маршруты gateway:"
    echo "$routes_response" | jq .
  else
    echo "⚠️ Не удалось получить маршруты gateway. Возможно, actuator отключен."
  fi
}

echo "Проверка наличия JAR-файлов и запуск нужных сервисов..."

# Логика запуска в зависимости от ветки
case "$BRANCH_NAME" in
  "spring-cloud")
    start_platform_core

    sleep 2

    start_stateful_service "main-service" "ewm_main_db" "--spring.application.name=main-service"
    start_stateful_service "stats-server" "ewm_stats_db" "--spring.application.name=stats-server"

    start_service "gateway-server" "--spring.application.name=gateway-server --management.endpoints.web.exposure.include=gateway"
    print_gateway_routes

    sleep 2
    ;;

  "microservices")
    start_platform_core

    sleep 2

    start_optional_service "category-service" "ewm_category" "--spring.application.name=category-service"
    start_stateful_service "user-service" "ewm_user" "--spring.application.name=user-service"
    start_stateful_service "event-service" "ewm_event" "--spring.application.name=event-service"
    start_stateful_service "request-service" "ewm_request" "--spring.application.name=request-service"
    start_stateful_service "stats-server" "ewm_stats_db" "--spring.application.name=stats-server"
    start_optional_service "compilation-service" "ewm_compilation" "--spring.application.name=compilation-service"
    start_optional_service "comment-service" "ewm_comment" "--spring.application.name=comment-service"
    start_optional_service "subscription-service" "ewm_subscription" "--spring.application.name=subscription-service"
    start_optional_service "rating-service" "ewm_rating" "--spring.application.name=rating-service"
    start_optional_service "location-service" "ewm_location" "--spring.application.name=location-service"

    start_service "gateway-server" "--spring.application.name=gateway-server --management.endpoints.web.exposure.include=gateway"
    print_gateway_routes

    sleep 2
    ;;

  "recommendations")
    start_platform_core

    sleep 2

    start_optional_service "category-service" "ewm_category" "--spring.application.name=category-service"
    start_stateful_service "user-service" "ewm_user" "--spring.application.name=user-service"
    start_stateful_service "event-service" "ewm_event" "--spring.application.name=event-service"
    start_stateful_service "request-service" "ewm_request" "--spring.application.name=request-service"
    start_optional_service "compilation-service" "ewm_compilation" "--spring.application.name=compilation-service"
    start_optional_service "comment-service" "ewm_comment" "--spring.application.name=comment-service"
    start_optional_service "subscription-service" "ewm_subscription" "--spring.application.name=subscription-service"
    start_optional_service "rating-service" "ewm_rating" "--spring.application.name=rating-service"
    start_optional_service "location-service" "ewm_location" "--spring.application.name=location-service"

    start_service "gateway-server" "--spring.application.name=gateway-server --management.endpoints.web.exposure.include=gateway"
    print_gateway_routes

    start_service "collector" "--spring.application.name=collector" "grpc"
    start_service "aggregator" "--spring.application.name=aggregator" "none"
    start_stateful_service "analyzer" "ewm_analyzer" "--spring.application.name=analyzer" "grpc"

    sleep 2
    ;;
  *)
    echo "❌ Ошибка: Ветка $BRANCH_NAME не поддерживается этим workflow."
    exit 1
    ;;
esac