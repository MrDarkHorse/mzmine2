/*
 * Copyright 2006-2009 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peakpicking.chromatogrambuilder.massconnection;

import net.sf.mzmine.data.MzPeak;
import net.sf.mzmine.data.RawDataFile;

public interface MassConnector {

	public void addScan(RawDataFile dataFile, int scanNumber, MzPeak[] mzValues);

	/**
	 * This method creates an array of chromatogram. This function must be
	 * called after the last scan of the DataFile.
	 * 
	 * @return Chromatogram[]
	 */
	public Chromatogram[] finishChromatograms();

}