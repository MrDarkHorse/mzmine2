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
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */
package net.sf.mzmine.project.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.DoubleBuffer;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;


import net.sf.mzmine.taskcontrol.Task;
import net.sf.mzmine.taskcontrol.TaskStatus;
import net.sf.mzmine.util.ExceptionUtils;

/**
 * Project opening task using XStream library
 */
public class ProjectOpeningTask implements Task {

	private Logger logger = Logger.getLogger(this.getClass().getName());
	private TaskStatus status = TaskStatus.WAITING;
	private String errorMessage;
	private File openFile;
	private ZipInputStream zipStream;
	ProjectSerializer projectSerializer;
	RawDataFileSerializer rawDataFileSerializer;
	private int currentStage,  rawDataCount;

	public ProjectOpeningTask(File openFile) {
		this.openFile = openFile;
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getTaskDescription()
	 */
	public String getTaskDescription() {
		String taskDescription = "Opening project ";
		switch (currentStage) {
			case 2:
				return taskDescription + "(raw data points) " + this.projectSerializer.getRawDataNames()[rawDataCount];
			case 3:
				return taskDescription + "(peak list objects)";
			default:
				return taskDescription + openFile;
		}

	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getFinishedPercentage()
	 */
	public double getFinishedPercentage() {
		switch (currentStage) {
			case 2:
				try {
					return (double) rawDataFileSerializer.getProgress();
				} catch (Exception e) {
					return 0f;
				}

			case 3:
				return 1f;
			default:
				return 0f;
		}
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getStatus()
	 */
	public TaskStatus getStatus() {
		return status;
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getErrorMessage()
	 */
	public String getErrorMessage() {
		return errorMessage;
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {

		try {

			logger.info("Started opening project " + openFile);
			status = TaskStatus.PROCESSING;


			// Get project ZIP stream
			FileInputStream fileStream = new FileInputStream(openFile);
			zipStream = new ZipInputStream(fileStream);


			// Stage 1 - load project description
			currentStage++;
			loadProjectInformation();

			// Stage 2 - load RawDataFile objects
			currentStage++;
			loadRawDataObjects();

			// Stage 3 - load PeakList objects
			currentStage++;
			loadPeakListObjects();


			// Finish and close the project ZIP file
			zipStream.close();

			// Final check for cancel
			if (status == TaskStatus.CANCELED) {
				return;
			}
			//	((MZmineProjectImpl) project).setProjectFile(openFile);

			logger.info("Finished opening project " + openFile);
			status = TaskStatus.FINISHED;

		} catch (Throwable e) {
			status = TaskStatus.ERROR;
			errorMessage = "Failed opening project: " + ExceptionUtils.exceptionToString(e);
		}
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#cancel()
	 */
	public void cancel() {
		logger.info("Canceling opening of project " + openFile);
		status = TaskStatus.CANCELED;
	}

	private void loadProjectInformation() {
		projectSerializer = new ProjectSerializer(this.zipStream);
		projectSerializer.openProjectDescription();
		projectSerializer.openConfiguration();
	}

	private void loadRawDataObjects() throws IOException,
			ClassNotFoundException {
		rawDataFileSerializer = new RawDataFileSerializer(this.zipStream);
		for (int i = 0; i < this.projectSerializer.getNumOfRawDataFiles(); i++, rawDataCount++) {
			rawDataFileSerializer.readRawDataFile();
		}
	}

	private void loadPeakListObjects() throws IOException,
			ClassNotFoundException {
		for (int i = 0; i < this.projectSerializer.getNumOfPeakLists(); i++) {
			PeakListSerializer peakListSerializer = new PeakListSerializer(zipStream);
			peakListSerializer.readPeakList();
		}
	}

	public Object[] getCreatedObjects() {
		throw new UnsupportedOperationException("Not supported yet.");
	}
}
