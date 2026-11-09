# Without Docker, tests and the app use a throwaway Postgres on port 55434.
.PHONY: db-up db-down test run proof demo-reset

PORT ?= 8096

db-up:
	scripts/local-db.sh start

db-down:
	scripts/local-db.sh stop

# Unit, integration and architecture tests against the local cluster
test: db-up
	./mvnw -B -ntp verify

# Demo profile: seeds three tenants and prints a token for each. Database harbor_demo.
run: db-up
	PORT=$(PORT) HARBOR_DB_URL=jdbc:postgresql://127.0.0.1:55434/harbor_demo ./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=demo

# Proofs with real numbers, written to docs/proof/report.md
proof: db-up
	rm -rf target/proof && mkdir -p target/proof docs/proof
	./mvnw -B -ntp test -Dharbor.groups=proof -Dharbor.excluded.groups=none
	scripts/build-report.sh

# Recreates the demo database (cluster on port 55434, never 5432). Stop the app first.
demo-reset: db-up
	/Library/PostgreSQL/16/bin/psql -h 127.0.0.1 -p 55434 -U harbor -d postgres -tAc "select datname from pg_database where datname like 'harbor_silo_%'" | while read d; do /Library/PostgreSQL/16/bin/dropdb -h 127.0.0.1 -p 55434 -U harbor --force "$$d"; done
	/Library/PostgreSQL/16/bin/dropdb --if-exists --force -h 127.0.0.1 -p 55434 -U harbor harbor_demo
	/Library/PostgreSQL/16/bin/createdb -h 127.0.0.1 -p 55434 -U harbor harbor_demo
