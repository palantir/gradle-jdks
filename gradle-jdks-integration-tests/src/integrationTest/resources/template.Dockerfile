ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ARG INSTALL_CURL=false
# Update package lists and conditionally install curl
RUN if [ "$INSTALL_CURL" = "true" ] ; then \
        apt-get update && \
        apt-get install -y curl ; \
    fi
COPY ./gradle /gradle
RUN chmod +x /gradle/testing-script.sh
ARG SCRIPT_SHELL
CMD $SCRIPT_SHELL /gradle/testing-script.sh
