#!/bin/sh
docker build . -t ebay-tx-stats
echo
echo
echo "To run the docker container execute:"
echo "    $ docker run -p 8080:8080 ebay-tx-stats"
