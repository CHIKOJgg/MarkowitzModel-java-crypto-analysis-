.PHONY: build test run clean

# ---- Docker ----
build:
	docker compose build optimizer

test:
	docker compose run --rm test

run:
	docker compose up optimizer

# ---- Local (without Docker) ----
local-build:
	mvn package -DskipTests

local-test:
	mvn test

local-run:
	mvn javafx:run

clean:
	rm -rf target output/
	docker compose down 2>/dev/null || true
