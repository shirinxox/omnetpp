/*--------------------------------------------------------------*
  Copyright (C) 2006-2008 OpenSim Ltd.
  
  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.editors.treeproviders;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;

import org.omnetpp.common.util.StringUtils;
import org.omnetpp.scave.engine.ResultFile;
import org.omnetpp.scave.engine.ResultFileList;
import org.omnetpp.scave.engine.Run;
import org.omnetpp.scave.engine.RunList;
import org.omnetpp.scave.model2.RunAttributePayload;

public class Sorter {
	
	public static final Comparator<ResultFile> resultFileComparator = new ResultFileComparator();
	public static final Comparator<Run> runComparator = new RunComparator();
	public static final Comparator<RunAttributePayload> runAttributeComparator = new RunAttributeComparator();
	
	private static class ResultFileComparator implements Comparator<ResultFile>, Serializable
	{
		private static final long serialVersionUID = 1L;

		public int compare(ResultFile left, ResultFile right) {
			return StringUtils.dictionaryCompare(left.getFileName(), right.getFileName());
		}
	}
	
	private static class RunComparator implements Comparator<Run>, Serializable
	{
		private static final long serialVersionUID = 1L;

		public int compare(Run left, Run right) {
			return StringUtils.dictionaryCompare(left.getRunName(), right.getRunName());
		}
	}
	
	private static class RunAttributeComparator implements Comparator<RunAttributePayload>, Serializable
	{
		private static final long serialVersionUID = 1L;

		public int compare(RunAttributePayload left, RunAttributePayload right) {
			return StringUtils.dictionaryCompare(left.getValue(), right.getValue());
		}
	}

	public static ResultFile[] sort(ResultFileList files) {
		ResultFile[] result = files.toArray();
		Arrays.sort(result, resultFileComparator);
		return result;
	}
	
	public static Run[] sort(RunList runs) {
		Run[] result = runs.toArray();
		Arrays.sort(result, runComparator);
		return result;
	}
}