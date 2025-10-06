ARG NUXEO_VERSION=2023.24

FROM docker-private.packages.nuxeo.com/nuxeo/nuxeo:${NUXEO_VERSION}
ARG NUXEO_CLID=e629623c-0267-434e-8f95-55b0d8482c9c.1924991940.D+QQaEFIB/Dxwey7ClSRDgHwjGOQR/Wl4SyRDA05xOdmlCTBLttopVtWMdLr3gTE66OoU0flPVkNlyF+hk+YMk2LrspFytpJ9wNjFWK8PPeJuJ/zTk0V03YtyWMlFb4cNRSvcgDkuAgBLJsadduXx144dOR5vi+9ldBZwEkprIvTNfEmgO/4pCa8C6BzLAaNzsJ5TGkYk4Aj9EWkufqFJBYDYEL1xMFNVZlW8wvWU6691VeewDDE6G5MPuEI/EOpfGkI8rJvQAvzOFsC9XBs6+BzfJgXDOJuiYCNoq1dIelb77gju51685yASbUY3WIo5qw/4ttmfRzaEahYf18HAQ==--baa9d908-61f3-403d-9ed7-52ebba14391d


RUN echo "JAVA_OPTS=$JAVA_OPTS -XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=50" >> /etc/nuxeo/conf.d/00-docker.conf
USER $NUXEO_USER

COPY --chown=900:0 maretha-cp-package/target/maretha-ai-package-1.0.24.zip $NUXEO_HOME/local-packages/maretha-ai-package-1.0.24.zip
COPY --chown=900:0 maretha-angular-webui-package-1.0.0.zip $NUXEO_HOME/local-packages/maretha-angular-webui-package-1.0.0.zip
COPY --chown=900:0  nuxeo-generative-ai-package-1.0.zip  $NUXEO_HOME/local-packages/nuxeo-generative-ai-package-1.0.zip
RUN echo ${CLID} $NUXEO_HOME/nxserver/data/instance.clid

# Install nuxeo-web-ui
RUN /install-packages.sh --clid ${NUXEO_CLID}  nuxeo-web-ui amazon-s3-online-storage nuxeo-csv
# Install a local package without its dependencies (`mp-install --nodeps`)
RUN /install-packages.sh --offline $NUXEO_HOME/local-packages/maretha-ai-package-1.0.24.zip
RUN /install-packages.sh --offline $NUXEO_HOME/local-packages/nuxeo-generative-ai-package-1.0.zip
RUN /install-packages.sh --offline $NUXEO_HOME/local-packages/maretha-angular-webui-package-1.0.0.zip
RUN rm -rf $NUXEO_HOME/local-packages
