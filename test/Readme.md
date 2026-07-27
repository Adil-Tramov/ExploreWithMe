# Тестер
## Скачать: [tester-0.0.1.jar](.github/workflows/stuff/tester-0.0.1.jar)

## Пример запуска тестируем только коллектор, сохраняем отчёт в другой файл
`java -jar tester.jar --tester.execution.mode=COLLECTION --tester.execution.output.file-path=./report.txt`

# Настройки генерации данных  

### Случайное количество уникальных пользователей, которое будет сгенерировано в указанном диапазоне.
tester.generation.user-count: 5 - 15

### Случайное количество различных мероприятий, которые будут сгенерированы в указанном диапазоне.
tester.generation.event-count: 10 - 20

### Лимит суммарного количества действий с мероприятиями то есть верхний лимит генерации действий для выбранного, с помощью настроек выше, случайного количества пользователей и мероприятий
tester.generation.actions-limit: 50 - 100

### Параметры временных меток генерируемых данных

### Насколько «назад» во времени смещается первая метка (от текущего момента).
### Значение парсится в Duration так, что можно использовать разные обозначения
tester.generation.timestamp-settings.back: 3d

### Минимальный интервал между последовательными метками.
tester.generation.timestamp-settings.increment-start: 1m

### Максимальный интервал между последовательными метками.
tester.generation.timestamp-settings.increment-end: 10m


# Настройки выполнения тестов #

### Режим проверки:
###   - COLLECTION   – только collector
###   - AGGREGATION  – collector + aggregator
###   - ANALYZE      – collector + aggregator + analyzer
tester.execution.mode: ANALYZE

### Логировать сразу в консоль (т.е. помимо сохранения в отчет, который выводится только в конце работы)
tester.execution.immediate-logging.enabled: false

## Настройки вывода результатов

### Печатать сообщения уровня info (если false, то будут выводиться только ошибки).
tester.execution.output.info-enabled: true

### Печатать TRACE‑сообщения.
tester.execution.output.trace-enabled: true

### Выводить отчёт в консоль.
tester.execution.output.print: true

### Сохранять отчёт в файл.
tester.execution.output.file: true

### Путь к файлу отчёта.
tester.execution.output.file-path: "./execution-report.txt"