FROM oracle/graalvm-ce:1.0.0-rc15 as graalvm
COPY . /home/app/ebay-tx-stats
WORKDIR /home/app/ebay-tx-stats
RUN native-image --no-server -cp build/libs/ebay-tx-stats-*-all.jar

FROM frolvlad/alpine-glibc
EXPOSE 8080
COPY --from=graalvm /home/app/ebay-tx-stats .
ENTRYPOINT ["./ebay-tx-stats"]
