/*
 * Copyright 2006-2008 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peakpicking.threestep.massdetection.exactmass;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Logger;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.modules.peakpicking.threestep.massdetection.MassDetector;
import net.sf.mzmine.modules.peakpicking.threestep.massdetection.MzPeak;
import net.sf.mzmine.util.Range;

public class ExactMassDetector implements MassDetector {

	// parameter values
	private float noiseLevel;
	private int resolution;
	private String peakModelname;

	private Logger logger = Logger.getLogger(this.getClass().getName());

	public ExactMassDetector(ExactMassDetectorParameters parameters) {
		noiseLevel = (Float) parameters
				.getParameterValue(ExactMassDetectorParameters.noiseLevel);
		resolution = (Integer) parameters
				.getParameterValue(ExactMassDetectorParameters.resolution);
		peakModelname = (String) parameters
				.getParameterValue(ExactMassDetectorParameters.peakModel);

	}

	/**
	 * @see net.sf.mzmine.modules.peakpicking.threestep.massdetection.MassDetector#getMassValues(net.sf.mzmine.data.Scan)
	 */
	public MzPeak[] getMassValues(Scan scan) {

		// Create a tree set of detected mzPeaks sorted by MZ in ascending order
		TreeSet<MzPeak> mzPeaks = new TreeSet<MzPeak>(new MzPeaksSorter(true,
				true));

		// Create a tree set of candidate mzPeaks sorted by intensity in
		// descending order.
		TreeSet<MzPeak> candidatePeaks = new TreeSet<MzPeak>(new MzPeaksSorter(
				false, false));

		// First get all candidate peaks (local maximum)
		getLocalMaxima(scan, candidatePeaks);

		// We calculate the exact mass for each peak and remove lateral peaks,
		// starting with biggest intensity peak and so on
		while (candidatePeaks.size() > 0) {

			// Always take the biggest (intensity) peak
			MzPeak currentCandidate = candidatePeaks.first();

			// Calculate the exact mass and update value in current candidate
			// (MzPeak)
			float exactMz = calculateExactMass(currentCandidate);
			currentCandidate.setMZ(exactMz);

			// Add this candidate to the final tree set sorted by MZ and remove
			// from tree set sorted by intensity
			mzPeaks.add(currentCandidate);
			candidatePeaks.remove(currentCandidate);

			// Remove from tree set sorted by intensity all FTMS shoulder peaks,
			// taking as a main peak the current candidate
			removeLateralPeaks(currentCandidate, candidatePeaks);

		}

		// Return an array of detected MzPeaks sorted by MZ
		return mzPeaks.toArray(new MzPeak[0]);

	}

	/**
	 * This method gets all possible MzPeaks using local maximum criteria from
	 * the current scan and return a tree set of MzPeaks sorted by intensity in
	 * descending order.
	 * 
	 * @param scan
	 * @return
	 */
	private void getLocalMaxima(Scan scan, TreeSet<MzPeak> candidatePeaks) {

		DataPoint[] scanDataPoints = scan.getDataPoints();
		DataPoint localMaximum = scanDataPoints[0];
		Vector<DataPoint> rangeDataPoints = new Vector<DataPoint>();

		boolean ascending = true;

		// Iterate through all data points
		for (int i = 0; i < scanDataPoints.length - 1; i++) {

			boolean nextIsBigger = scanDataPoints[i + 1].getIntensity() > scanDataPoints[i]
					.getIntensity();
			boolean nextIsZero = scanDataPoints[i + 1].getIntensity() == 0;
			boolean currentIsZero = scanDataPoints[i].getIntensity() == 0;

			// Ignore zero intensity regions
			if (currentIsZero) {
				continue;
			}

			// Add current (non-zero) data point to the current m/z peak
			rangeDataPoints.add(scanDataPoints[i]);

			// Check for local maximum
			if (ascending && (!nextIsBigger)) {
				localMaximum = scanDataPoints[i];
				rangeDataPoints.remove(scanDataPoints[i]);
				ascending = false;
				continue;
			}

			// Check for the end of the peak
			if ((!ascending) && (nextIsBigger || nextIsZero)) {

				// Add the m/z peak if it is above the noise level
				if (localMaximum.getIntensity() > noiseLevel) {
					DataPoint[] rawDataPoints = rangeDataPoints
							.toArray(new DataPoint[0]);
					candidatePeaks.add(new MzPeak(localMaximum, rawDataPoints));
				}

				// Reset and start with new peak
				ascending = true;
				rangeDataPoints.clear();
			}

		}

	}

	/**
	 * This method calculates the exact mass of a peak using the FWHM concept
	 * and linear equation (y = mx + b).
	 * 
	 * @param MzPeak
	 * @return float
	 */
	private float calculateExactMass(MzPeak currentCandidate) {

		/*
		 * According with the FWHM concept, the exact mass of this peak is the
		 * half point of FWHM. In order to get the points in the curve that
		 * define the FWHM, we use the linear equation.
		 * 
		 * First we look for, in left side of the peak, 2 data points together
		 * that have an intensity less (first data point) and bigger (second
		 * data point) than half of total intensity. Then we calculate the slope
		 * of the line defined by this two data points. At least, we calculate
		 * the point in this line that has an intensity equal to the half of
		 * total intensity
		 * 
		 * We repeat the same process in the right side.
		 */

		float xRight = -1, xLeft = -1;
		DataPoint[] rangeDataPoints = currentCandidate.getRawDataPoints();

		for (int i = 0; i < rangeDataPoints.length - 1; i++) {

			// Left side of the curve
			if ((rangeDataPoints[i].getIntensity() <= currentCandidate
					.getIntensity() / 2)
					&& (rangeDataPoints[i].getMZ() < currentCandidate.getMZ())
					&& (rangeDataPoints[i + 1].getIntensity() >= currentCandidate
							.getIntensity() / 2)) {

				// First point with intensity just less than half of total
				// intensity
				float leftY1 = rangeDataPoints[i].getIntensity();
				float leftX1 = rangeDataPoints[i].getMZ();

				// Second point with intensity just bigger than half of total
				// intensity
				float leftY2 = rangeDataPoints[i + 1].getIntensity();
				float leftX2 = rangeDataPoints[i + 1].getMZ();

				// We calculate the slope with formula m = Y1 - Y2 / X1 - X2
				float mLeft = (leftY1 - leftY2) / (leftX1 - leftX2);

				// We calculate the desired point (at half intensity) with the
				// linear equation
				// X = X1 + [(Y - Y1) / m ], where Y = half of total intensity
				xLeft = leftX1
						+ (((currentCandidate.getIntensity() / 2) - leftY1) / mLeft);
				continue;
			}

			// Right side of the curve
			if ((rangeDataPoints[i].getIntensity() >= currentCandidate
					.getIntensity() / 2)
					&& (rangeDataPoints[i].getMZ() > currentCandidate.getMZ())
					&& (rangeDataPoints[i + 1].getIntensity() <= currentCandidate
							.getIntensity() / 2)) {

				// First point with intensity just less than half of total
				// intensity
				float rightY1 = rangeDataPoints[i].getIntensity();
				float rightX1 = rangeDataPoints[i].getMZ();

				// Second point with intensity just bigger than half of total
				// intensity
				float rightY2 = rangeDataPoints[i + 1].getIntensity();
				float rightX2 = rangeDataPoints[i + 1].getMZ();

				// We calculate the slope with formula m = Y1 - Y2 / X1 - X2
				float mRight = (rightY1 - rightY2) / (rightX1 - rightX2);

				// We calculate the desired point (at half intensity) with the
				// linear equation
				// X = X1 + [(Y - Y1) / m ], where Y = half of total intensity
				xRight = rightX1
						+ (((currentCandidate.getIntensity() / 2) - rightY1) / mRight);
				break;
			}
		}

		// We verify the values to confirm we find the desired points. If not we
		// return the same mass value.
		if ((xRight == -1) || (xLeft == -1))
			return currentCandidate.getMZ();

		// FWHM is defined by the points in the left and right side of the curve
		// with intensity equal to the half of total intensity.
		float FWHM = xRight - xLeft;

		// The center of FWHM is the exact mass of our peak.
		float exactMass = xLeft + FWHM / 2;

		return exactMass;
	}

	/**
	 * This function remove peaks encountered in the lateral of a main peak
	 * (currentCandidate) that are considered as garbage, for example FTMS
	 * shoulder peaks. 
	 * 
	 * First calculates a peak model (Gauss, Lorenzian, etc)
	 * defined by peakModelName parameter, with the same position (m/z) and
	 * height (intensity) of the currentCandidate, and the defined resolution
	 * (resolution parameter). Second search and remove all the lateral peaks
	 * that are under the curve of the modeled peak.
	 * 
	 * @param mzPeaks
	 * @param percentageHeight
	 * @param percentageResolution
	 */
	private void removeLateralPeaks(MzPeak currentCandidate,
			TreeSet<MzPeak> candidates) {

		PeakModel peakModel;

		String peakModelClassName = null;

		// Peak Model used to remove FTMS shoulder peaks
		for (int peakModelindex = 0; peakModelindex < ExactMassDetectorParameters.peakModelNames.length; peakModelindex++) {
			if (ExactMassDetectorParameters.peakModelNames[peakModelindex]
					.equals(peakModelname))
				peakModelClassName = ExactMassDetectorParameters.peakModelClasses[peakModelindex];
		}

		if (peakModelClassName == null) {
			logger.warning("Error trying to make an instance of peak model "
					+ peakModelname);
			return;
		}

		try {
			Class peakModelClass = Class.forName(peakModelClassName);
			peakModel = (PeakModel) peakModelClass.newInstance();

		} catch (Exception e) {
			logger.warning("Error trying to make an instance of peak model "
					+ peakModelClassName);
			return;
		}

		// We set our peak model with same position(m/z), height(intensity) and
		// resolution of the current peak
		peakModel.setParameters(currentCandidate.getMZ(), currentCandidate
				.getIntensity(), resolution);

		// We use the width of the modeled peak at noise level to set the range
		// of search for lateral peaks.
		Range rangePeak = peakModel.getWidth(noiseLevel);

		// We search over all peak candidates and remove all of them that are
		// under the curve defined by our peak model
		Iterator<MzPeak> candidatesIterator = candidates.iterator();
		while (candidatesIterator.hasNext()) {

			MzPeak lateralCandidate = candidatesIterator.next();

			// Condition in x domain (m/z)
			if ((lateralCandidate.getMZ() >= rangePeak.getMin())
					&& (lateralCandidate.getMZ() <= rangePeak.getMax())
					// Condition in y domain (intensity)
					&& (lateralCandidate.getIntensity() < peakModel
							.getIntensity(lateralCandidate.getMZ()))) {
				candidatesIterator.remove();
			}
		}

	}
}