import { Alert, Box, Text } from "@mantine/core";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { useState } from "react";
import { MapContainer, Marker, Polyline, TileLayer } from "react-leaflet";
import { routeColor } from "../../utils/colors.js";

const TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
const SINGLE_POINT_ZOOM = 7;

/** Numbered marker as a div icon (avoids Leaflet's default image icons, which break in bundles). */
function stopIcon(sequence, color) {
  return L.divIcon({
    className: "hw-map-marker",
    html: `<span style="background:${color}">${sequence}</span>`,
    iconSize: [26, 26],
    iconAnchor: [13, 13],
  });
}

const depotIcon = L.divIcon({
  className: "hw-map-marker hw-map-depot",
  html: "<span>D</span>",
  iconSize: [30, 30],
  iconAnchor: [15, 15],
});

function routePath(depot, route) {
  const path = [
    [depot.lat, depot.lon],
    ...route.stops.map((stop) => [stop.lat, stop.lon]),
  ];
  return route.returnLeg ? [...path, [depot.lat, depot.lon]] : path;
}

/**
 * Straight-line routes (D-08) of one variant on OSM tiles: depot marker, numbered stops and
 * a polyline per truck in its colour. `allStops` (both variants) fixes the bounds, so the
 * view does not jump when the variant changes. Without tiles (offline) the routes still draw.
 */
export function RouteMap({ depot, routes, allStops, label }) {
  const [tilesFailed, setTilesFailed] = useState(false);
  const points = [
    [depot.lat, depot.lon],
    ...allStops.map((stop) => [stop.lat, stop.lon]),
  ];
  const view =
    points.length > 1
      ? { bounds: points, boundsOptions: { padding: [24, 24] } }
      : { center: points[0], zoom: SINGLE_POINT_ZOOM };

  return (
    <Box role="region" aria-label={label}>
      {tilesFailed && (
        <Alert color="gray" variant="light" mb="xs">
          Map tiles unavailable (offline). The routes and the stop lists below
          are complete.
        </Alert>
      )}
      <MapContainer {...view} className="hw-map" scrollWheelZoom={false}>
        <TileLayer
          url={TILE_URL}
          attribution={TILE_ATTRIBUTION}
          eventHandlers={{ tileerror: () => setTilesFailed(true) }}
        />
        <Marker
          position={[depot.lat, depot.lon]}
          icon={depotIcon}
          title={`Depot: ${depot.name}`}
        />
        {routes.map((route) => (
          <Polyline
            key={`line-${route.truckId}`}
            positions={routePath(depot, route)}
            pathOptions={{
              color: routeColor(route.colorIndex),
              weight: 4,
              opacity: 0.85,
            }}
          />
        ))}
        {routes.flatMap((route) =>
          route.stops.map((stop) => (
            <Marker
              key={`${route.truckId}-${stop.sequence}`}
              position={[stop.lat, stop.lon]}
              icon={stopIcon(stop.sequence, routeColor(route.colorIndex))}
              title={`${route.plate}, stop ${stop.sequence}: ${stop.city}`}
            />
          )),
        )}
      </MapContainer>
      <Text size="xs" c="dimmed" mt={4}>
        Straight lines between stops. The truck cards below list every stop.
      </Text>
    </Box>
  );
}
