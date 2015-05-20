package mil.nga.giat.geowave.adapter.vector.utils;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collections;

import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.geotools.ows.bindings.UnitBinding;
import org.geotools.referencing.GeodeticCalculator;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

public class GeometryUtils
{

	private static Logger LOGGER = Logger.getLogger(GeometryUtils.class);

	/**
	 * Build a buffer around a geometry for the pro
	 * 
	 * @param crs
	 * @param geometry
	 * @param distanceUnits
	 * @param distance
	 * @return
	 * @throws TransformException
	 */
	public static final Pair<Geometry, Double> buffer(
			final CoordinateReferenceSystem crs,
			final Geometry geometry,
			final String distanceUnits,
			final double distance )
			throws TransformException {
		Unit<?> unit;
		try {
			unit = (Unit<?>) new UnitBinding().parse(
					null,
					distanceUnits);
		}
		catch (final Exception e) {
			unit = SI.METER;
			LOGGER.warn(
					"Cannot lookup unit of measure " + distanceUnits,
					e);
		}
		final double meterDistance = unit.getConverterTo(
				SI.METER).convert(
				distance);
		final double degrees = distanceToDegrees(
				crs,
				geometry,
				meterDistance);
		// buffer does not respect the CRS; it uses simple cartesian math.
		return Pair.of(
				adjustGeo(
						crs,
						geometry.buffer(degrees)),
				degrees);

	}

	/**
	 * Fix the polygon so that long/lat fits into the bounds.
	 * 
	 * @param geometry
	 * @return
	 */
	public static Geometry adjustGeo(
			final CoordinateReferenceSystem crs,
			final Geometry geometry ) {

		final Coordinate[] geoCoords = geometry.getCoordinates();
		final Coordinate[] newCoords = new Coordinate[geoCoords.length];

		int i = 0;
		for (Coordinate geoCoord : geoCoords) {
			newCoords[i++] = clipRange(
					crs,
					geoCoord);
		}
		if (geometry instanceof Point) {
			return geometry.getFactory().createPoint(
					newCoords[0]);
		}
		else if (geometry instanceof LineString) {
			return geometry.getFactory().createLineString(
					newCoords);
		}
		return geometry.getFactory().createPolygon(
				newCoords);
	}

	public static Coordinate clipRange(
			final CoordinateReferenceSystem crs,
			final Coordinate coord ) {
		return new Coordinate(
				clipRange(
						coord.x,
						crs,
						0),
				clipRange(
						coord.y,
						crs,
						1),
				clipRange(
						coord.z,
						crs,
						2));
	}

	/**
	 * This is perhaps a brain dead approach to do this, but it does handle wrap
	 * around cases
	 * 
	 * @param val
	 * @param bound
	 * @return
	 */
	private static double clipRange(
			final double val,
			final CoordinateReferenceSystem crs,
			int dimension ) {
		final CoordinateSystem coordinateSystem = crs.getCoordinateSystem();
		if (coordinateSystem.getDimension() > dimension) {
			double lowerBound = coordinateSystem.getAxis(
					dimension).getMinimumValue();
			double bound = coordinateSystem.getAxis(
					dimension).getMaximumValue() - lowerBound;
			double sign = sign(val);
			// re-scale to 0 to n, then determine how many times to 'loop
			// around'
			double mult = Math.floor(Math.abs((val + sign * (-1.0 * lowerBound)) / bound));
			return val + mult * bound * sign * (-1.0);
		}
		return val;
	}

	/**
	 * Convert meters to decimal degrees based on widest point
	 * 
	 * @throws TransformException
	 */
	private static double distanceToDegrees(
			final CoordinateReferenceSystem crs,
			final Geometry geometry,
			final double meters )
			throws TransformException {
		final GeometryFactory factory = geometry.getFactory();
		return (geometry instanceof Point) ? geometry.distance(farthestPoint(
				crs,
				(Point) geometry,
				meters)) : distanceToDegrees(
				crs,
				geometry.getEnvelopeInternal(),
				factory == null ? new GeometryFactory() : factory,
				meters);
	}

	private static double distanceToDegrees(
			final CoordinateReferenceSystem crs,
			final Envelope env,
			final GeometryFactory factory,
			final double meters )
			throws TransformException {
		return Collections.max(Arrays.asList(
				distanceToDegrees(
						crs,
						factory.createPoint(new Coordinate(
								env.getMaxX(),
								env.getMaxY())),
						meters),
				distanceToDegrees(
						crs,
						factory.createPoint(new Coordinate(
								env.getMaxX(),
								env.getMinY())),
						meters),
				distanceToDegrees(
						crs,
						factory.createPoint(new Coordinate(
								env.getMinX(),
								env.getMinY())),
						meters),
				distanceToDegrees(
						crs,
						factory.createPoint(new Coordinate(
								env.getMinX(),
								env.getMaxY())),
						meters)));
	}

	/** farther point in longitudinal axis given a latitude */

	private static Point farthestPoint(
			final CoordinateReferenceSystem crs,
			final Point point,
			final double meters ) {
		final GeodeticCalculator calc = new GeodeticCalculator(
				crs);
		calc.setStartingGeographicPoint(
				point.getX(),
				point.getY());
		calc.setDirection(
				90,
				meters);
		Point2D dest2D = calc.getDestinationGeographicPoint();
		// if this flips over the date line then try the other direction
		if (sign(dest2D.getX()) != sign(point.getX())) {
			calc.setDirection(
					-90,
					meters);
			dest2D = calc.getDestinationGeographicPoint();
		}
		return point.getFactory().createPoint(
				new Coordinate(
						dest2D.getX(),
						dest2D.getY()));
	}

	private static double sign(
			double val ) {
		return val < 0 ? -1 : 1;
	}
}
