/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.jobs;

import java.io.File;
import java.util.concurrent.Callable;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.omnetpp.common.util.Pair;
import org.omnetpp.common.util.StringUtils;
import org.omnetpp.scave.ScavePlugin;
import org.omnetpp.scave.engine.IDList;
import org.omnetpp.scave.engine.ResultFileManager;
import org.omnetpp.scave.engine.ResultItemFields;
import org.omnetpp.scave.engine.OldScaveExport;
import org.omnetpp.scave.engine.VectorResult;
import org.omnetpp.scave.engine.XYArray;
import org.omnetpp.scave.model.Dataset;
import org.omnetpp.scave.model.DatasetItem;
import org.omnetpp.scave.model2.DatasetManager;

/**
 * Job for exporting scalar/vector/histogram data in the background.
 *
 * @author tomi
 */
public class OldExportJob extends WorkspaceJob
{
    private OldScaveExport exporter;
    private ResultFileManager manager;
    private IDList scalars, vectors, histograms;
    private Dataset dataset;
    private DatasetItem datasetItem;
    private ResultItemFields scalarsGroupBy;

    public OldExportJob(OldScaveExport exporter,
            IDList scalars, IDList vectors, IDList histograms,
            Dataset dataset, DatasetItem datasetItem,
            ResultItemFields scalarsGroupBy, ResultFileManager manager) {
        super("Data Export");
        this.exporter = exporter;
        this.scalars = scalars;
        this.vectors = vectors;
        this.histograms = histograms;
        this.dataset = dataset;
        this.datasetItem = datasetItem;
        this.scalarsGroupBy = scalarsGroupBy;
        this.manager = manager;
    }

    @Override
    public IStatus runInWorkspace(final IProgressMonitor monitor)
            throws CoreException {

        if (exporter == null || manager == null)
            return Status.OK_STATUS;

        IStatus status = Status.CANCEL_STATUS;

        try {
            monitor.beginTask("Exporting", calculateTotalWork());

            status = ResultFileManager.callWithReadLock(manager, new Callable<IStatus>() {
                @Override
                public IStatus call() throws Exception {
                    IStatus status = exportVectors(exporter, monitor);
                    if (status.getSeverity() != IStatus.OK)
                        return status;

                    status = exportScalars(exporter, monitor);
                    if (status.getSeverity() != IStatus.OK)
                        return status;

                    status = exportHistograms(exporter, monitor);
                    if (status.getSeverity() != IStatus.OK)
                        return status;

                    return Status.OK_STATUS;
                }
            });

            return status;
        }
        catch (Exception e) {
            IStatus error = new Status(IStatus.ERROR, ScavePlugin.PLUGIN_ID, "Error occured during export", e);
            ScavePlugin.getDefault().getLog().log(error);
            return error;
        }
        finally {
            monitor.done();
            if (status.getSeverity() != IStatus.OK) {
                String fileName = exporter.getLastFileName();
                if (!StringUtils.isEmpty(fileName)) {
                    try {
                        File file = new File(fileName);
                        if (file.exists()) {
                            if (!file.delete())
                                ScavePlugin.logError("Cannot delete export file: "+fileName, null);
                        }
                    } catch (Exception e) {
                        ScavePlugin.logError("Cannot delete export file: "+fileName, e);
                    }
                }
            }
            exporter.delete();
        }
    }

    protected int calculateTotalWork() {
        int work = 0;
        if (scalars != null && scalars.size() > 0)
            ++work;
        if (vectors != null)
            work += 2 * vectors.size();
        if (histograms != null && histograms.size() > 0)
            ++work;
        return work;
    }

    protected IStatus exportScalars(OldScaveExport exporter, IProgressMonitor monitor) {
        if (scalars != null && scalars.size() > 0) {
            if (monitor.isCanceled())
                return Status.CANCEL_STATUS;
            exporter.saveScalars("scalars", "", scalars, scalarsGroupBy, manager);
            monitor.worked(1);
        }
        return Status.OK_STATUS;
    }

    protected IStatus exportVectors(OldScaveExport exporter, IProgressMonitor monitor) {
        XYArray[] data = null;
        IProgressMonitor subMonitor = new SubProgressMonitor(monitor, vectors.size());
        if (dataset != null) {
            Pair<IDList, XYArray[]> pair =
                DatasetManager.readAndComputeVectorData(dataset, datasetItem, manager, subMonitor);
            vectors = pair.first;
            data = pair.second;
        }
        else {
            data = DatasetManager.getDataOfVectors(manager, vectors, subMonitor);
        }

        if (monitor.isCanceled())
            return Status.CANCEL_STATUS;

        for (int i = 0; i < vectors.size(); ++i) {
            if (monitor.isCanceled())
                return Status.CANCEL_STATUS;
            long id = vectors.get(i);
            VectorResult vector = manager.getVector(id);
            exporter.saveVector(vector.getName(), "", id, false, data[i], manager);
            monitor.worked(1);
        }

        return Status.OK_STATUS;
    }

    protected IStatus exportHistograms(OldScaveExport exporter, IProgressMonitor monitor) {
        if (histograms != null && histograms.size() > 0) {
            if (monitor.isCanceled())
                return Status.CANCEL_STATUS;
            exporter.saveHistograms("histograms", "", histograms, manager);
            monitor.worked(1);
        }
        return Status.OK_STATUS;
    }

}