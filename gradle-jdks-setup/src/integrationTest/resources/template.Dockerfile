ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ARG INSTALL_CURL=false
# Update package lists and conditionally install curl
RUN if [ "$INSTALL_CURL" = "true" ] ; then \
        apt-get update && \
        apt-get install -y curl ; \
    fi
COPY . /
RUN mkdir -p /etc/ssl/certs && cat /amazon.crt >> /etc/ssl/certs/ca-bundle.crt
RUN if [ -f /palantir.crt ]; then cat /palantir.crt >> /etc/ssl/certs/ca-bundle.crt; else echo "File does not exist."; fi
RUN chmod +x /testing-script.sh
ARG SCRIPT_SHELL
CMD $SCRIPT_SHELL /testing-script.sh
