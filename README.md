# Weather

A personal real-time weather dashboard powered by Open-Meteo. The Java server serves the dashboard and provides a small REST API that fetches forecast and geocoding data from Open-Meteo.

## Run it

Requires Java 17 or later. From this folder:

```sh
./run.sh
```

Then open [http://localhost:8080](http://localhost:8080). To use another port, set `PORT` before running, for example `PORT=8090 ./run.sh`.

## Your API

- `GET /api/weather?latitude=12.9716&longitude=77.5946` — current conditions, hourly outlook and seven day forecast.
- `GET /api/locations?name=Tokyo` — location search results with coordinates.

Both endpoints return Open-Meteo JSON. Open-Meteo provides the weather and geocoding data; this Java application is your personal API layer. See [Open-Meteo](https://open-meteo.com/) for provider details and usage terms.

## Project files

- `WeatherServer.java` — Java HTTP server and REST endpoints.
- `index.html` — responsive weather dashboard.
- `run.sh` — compile and launch script.
