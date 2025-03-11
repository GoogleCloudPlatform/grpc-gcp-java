FROM eclipse-temurin
COPY build/install/continuous_load_testing /app
# Uncomment the line below to enable CloudPath instead of DirectPath
# ENV GOOGLE_CLOUD_DISABLE_DIRECT_PATH=true
CMD /app/bin/continuous_load_testing --methods=EmptyCall --concurrency=1
