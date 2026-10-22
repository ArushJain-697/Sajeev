# #01. `make test` = unit tests (Maven) + the negative compile check.
.PHONY: test test-negative

test:
	mvn -B -q test
	$(MAKE) test-negative

# test_build_rejects_post_java11_api: the bad file must NOT compile with --release 11.
test-negative:
	@if javac --release 11 -d "$$(mktemp -d)" liveness-api/src/test-negative/java/io/sajeev/api/PostJava11Api.java 2>/dev/null; then \
		echo "FAIL: post-Java-11 API compiled with --release 11"; exit 1; \
	else \
		echo "PASS: post-Java-11 API rejected by --release 11"; \
	fi
