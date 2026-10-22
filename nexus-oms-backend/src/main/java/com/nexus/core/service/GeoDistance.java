package com.nexus.core.service;

import com.nexus.core.model.party.PostalAddress;

import java.math.BigDecimal;
import java.util.Optional;

/** Distances between points on the earth, for routing (nearest location) and delivery promises. */
public final class GeoDistance {

    static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {
    }

    /** A point by latitude and longitude, in degrees. */
    public record Point(double latitude, double longitude) {

        /** The point of a latitude and longitude that may be missing; empty when either is. */
        public static Optional<Point> of(BigDecimal latitude, BigDecimal longitude) {
            if (latitude == null || longitude == null) {
                return Optional.empty();
            }
            return Optional.of(new Point(latitude.doubleValue(), longitude.doubleValue()));
        }

        /** Where a postal address is, when it has been geocoded. */
        public static Optional<Point> of(PostalAddress address) {
            return address == null ? Optional.empty() : of(address.getLatitude(), address.getLongitude());
        }
    }

    /** Great-circle (haversine) distance in kilometres. */
    public static double km(double latitude1, double longitude1, double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double halfChordSquared = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2)) * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(halfChordSquared));
    }

    public static double km(Point from, Point to) {
        return km(from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }
}
