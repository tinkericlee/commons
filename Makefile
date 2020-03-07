#
# Makefile for Toolbox
#

GIT_REVISION = $(shell git describe --match= --always --abbrev=7 --dirty)
GOOGLE_JAVA_FORMAT = 1.6
DATE = $(or $(shell printenv DATE), $(shell git log -1 --format=%cd --date=format:%Y%m%d%H%M%S HEAD))
TIMESTAMP = $(or $(shell printenv TIMESTAMP), $(shell date -u +'%Y-%m-%dT%H:%M:%SZ'))
VERSION = $(or $(shell printenv VERSION), $(shell xmllint --xpath "//*[local-name()='project']/*[local-name()='version']/text()" pom.xml))
BRANCH_NAME = $(or $(shell printenv BRANCH_NAME), $(shell git rev-parse --abbrev-ref HEAD))

.PHONY: all
all: google-java-format xml-format

.PHONY: test
test:
	mvn validate test

.PHONY: google-java-format
google-java-format: tools/google-java-format.jar
	java -jar tools/google-java-format.jar \
	  --replace \
	  --set-exit-if-changed \
	  $(shell find . -name \*.java)

tools/google-java-format.jar:
	mkdir -p tools && \
	wget \
	  https://github.com/google/google-java-format/releases/download/google-java-format-$(GOOGLE_JAVA_FORMAT)/google-java-format-$(GOOGLE_JAVA_FORMAT)-all-deps.jar \
	  -O tools/google-java-format.jar

# use $$ to escape $ in Makefile
.PHONY: xml-format
xml-format:
	notPretty=0; \
	for f in *.xml; do \
	xmllint -o $$f.xmllint --format $$f; \
	origin_file=$$(echo -n $$(base64 -i $$f)); \
	linted_file=$$(echo -n $$(base64 -i $$f.xmllint)); \
	if [ "$$origin_file" = "$$linted_file" ]; then \
	  mv $$f.xmllint $$f && rm -f $$f.xmllint; \
	else \
	  mv $$f.xmllint $$f && rm -f $$f.xmllint && notPretty=1; \
	fi \
	done; \
	exit $$notPretty;
