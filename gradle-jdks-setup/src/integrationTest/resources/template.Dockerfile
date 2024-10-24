ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ARG SCRIPT_SHELL
ENV SCRIPT_SHELL $SCRIPT_SHELL
ARG INSTALL_CURL=false
# Update package lists and conditionally install curl
RUN if [ "$INSTALL_CURL" = "true" ] ; then \
        apt-get update && \
        apt-get install -y curl ; \
    fi
COPY . /
RUN mkdir -p /etc/ssl/certs && cat /example.com.crt >> /etc/ssl/certs/ca-bundle.crt && update-ca-certificates
RUN if [ -f /palantir.crt ]; then cat /palantir.crt >> /etc/ssl/certs/ca-bundle.crt; else echo "File does not exist."; fi
RUN $SCRIPT_SHELL /gradle/gradle-jdks-setup.sh
