ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ARG SCRIPT_SHELL
ENV SCRIPT_SHELL $SCRIPT_SHELL
ARG ADD_JDK_DIR=false
ARG INSTALL_CURL=false
# Update package lists and conditionally install curl
RUN if [ "$INSTALL_CURL" = "true" ] ; then \
        apt-get update && \
        apt-get install -y curl; \
    fi
RUN if [ "$ADD_JDK_DIR" = "true" ] ; then \
    mkdir -p "/root/.gradle/gradle-jdks/amazon-corretto-11.0.21.9.1"; \
    fi
COPY . /
RUN $SCRIPT_SHELL /gradle/gradle-jdks-setup.sh
