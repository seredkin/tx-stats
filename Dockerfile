FROM openjdk:alpine
COPY ./build/libs/ebay-tx-stats-0.1-all.jar .
RUN java -jar ebay-tx-stats-0.1-all.jar ebay.tx.stats.TxStatsApp

