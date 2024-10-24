ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ARG SCRIPT_SHELL
ENV SCRIPT_SHELL $SCRIPT_SHELL
ARG INSTALL_CURL=false
# Update package lists and conditionally install curl
RUN if [ "$INSTALL_CURL" = "true" ] ; then \
        apt-get update && \
        apt-get install -y curl; \
    fi
COPY . /
RUN $SCRIPT_SHELL /gradle/gradle-jdks-setup.sh
