#Makefile
TEST_JDK_HOME?=$(JAVA_HOME)
PLUGIN_NAME?=mailer

JENKINS_VERSION=2.150
JAXB_API_VERSION=2.3.0
JAXB_VERSION=2.3.0.1
JAF_VERSION=1.2.0

.PHONY: all
all: clean package docker

.PHONY: clean
clean:
	mvn clean

.PHONY: package
package:
	mvn package verify

.PHONY: docker
docker:
	docker build -t jenkins/pct .

tmp:
	mkdir tmp

tmp/jenkins-war-$(JENKINS_VERSION).war: tmp
	mvn dependency:copy -Dartifact=org.jenkins-ci.main:jenkins-war:$(JENKINS_VERSION):war -DoutputDirectory=tmp
	touch tmp/jenkins-war-$(JENKINS_VERSION).war

tmp/jaxb-api-$(JAXB_API_VERSION).jar: tmp
	mvn dependency:copy -Dartifact=javax.xml.bind:jaxb-api:$(JAXB_API_VERSION) -DoutputDirectory=tmp
	touch tmp/jaxb-api-$(JAXB_API_VERSION).jar

tmp/jaxb-core-$(JAXB_VERSION).jar: tmp
	mvn dependency:copy -Dartifact=com.sun.xml.bind:jaxb-core:$(JAXB_VERSION) -DoutputDirectory=tmp
	touch tmp/jaxb-api-$(JAXB_API_VERSION).jar

tmp/jaxb-impl-$(JAXB_VERSION).jar: tmp
	mvn dependency:copy -Dartifact=com.sun.xml.bind:jaxb-impl:$(JAXB_VERSION) -DoutputDirectory=tmp
	touch tmp/jaxb-impl-$(JAXB_API_VERSION).jar

tmp/javax.activation-$(JAX_VERSION).jar: tmp
	mvn dependency:copy -Dartifact=com.sun.activation:javax.activation:$(JAF_VERSION) -DoutputDirectory=tmp
	touch tmp/javax.activation-$(JAX_VERSION).jar

.PHONY: print-java-home
print-java-home:
	echo "Using JAVA_HOME for tests $(TEST_JDK_HOME)"

.PHONY: demo-jdk8
demo-jdk8: tmp/jenkins-war-$(JENKINS_VERSION).war print-java-home
	java -jar plugins-compat-tester-cli/target/plugins-compat-tester-cli-0.0.3-SNAPSHOT.jar \
	     -reportFile $(CURDIR)/out/pct-report.xml \
	     -workDirectory $(CURDIR)/work -skipTestCache true \
	     -mvn $(shell which mvn) -war tmp/jenkins-war-$(JENKINS_VERSION).war \
	     -testJDKHome $(TEST_JDK_HOME) \
	     -includePlugins $(PLUGIN_NAME)

.PHONY: demo-jdk11
demo-jdk11: tmp/javax.activation-$(JAX_VERSION).jar tmp/jaxb-api-$(JAXB_API_VERSION).jar tmp/jenkins-war-$(JENKINS_VERSION).war tmp/jaxb-impl-$(JAXB_VERSION).jar tmp/jaxb-core-$(JAXB_VERSION).jar print-java-home
	# TODO Cleanup when/if the JAXB bundling issue is resolved.
	# https://issues.jenkins-ci.org/browse/JENKINS-52186
	java -jar plugins-compat-tester-cli/target/plugins-compat-tester-cli-0.0.3-SNAPSHOT.jar \
	     -reportFile $(CURDIR)/out/pct-report.xml \
	     -workDirectory $(CURDIR)/work -skipTestCache true \
	     -mvn $(shell which mvn) -war tmp/jenkins-war-$(JENKINS_VERSION).war \
	     -testJDKHome $(TEST_JDK_HOME) \
	     -testJavaArgs "-p $(CURDIR)/tmp/jaxb-api-$(JAXB_API_VERSION).jar:$(CURDIR)/tmp/javax.activation-${JAF_VERSION}.jar --add-modules java.xml.bind,java.activation -cp $(CURDIR)/tmp/jaxb-impl-$(JAXB_VERSION).jar:$(CURDIR)/tmp/jaxb-core-$(JAXB_VERSION).jar" \
	     -includePlugins $(PLUGIN_NAME)
