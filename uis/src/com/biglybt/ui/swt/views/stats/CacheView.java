/*
 * Created on Sep 13, 2004
 * Created by Olivier Chalouhi
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package com.biglybt.ui.swt.views.stats;


import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.disk.DiskManagerFactory;
import com.biglybt.core.diskmanager.access.DiskAccessControllerStats;
import com.biglybt.core.diskmanager.cache.CacheFileManagerFactory;
import com.biglybt.core.diskmanager.cache.CacheFileManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.graphics.SpeedGraphic;
import com.biglybt.ui.swt.components.graphics.ValueFormater;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.IViewRequiresPeriodicUpdates;

/**
 *
 */
public class CacheView
	implements UISWTViewCoreEventListener, IViewRequiresPeriodicUpdates
{

  public static final String MSGID_PREFIX = "CacheView";

  CacheFileManagerStats 		cfmStats;
  DiskAccessControllerStats		dacStats;
  
  Composite panel;

  BufferedLabel lblInUse,lblSize,lblPercentUsed;
  ProgressBar pbInUse;

  BufferedLabel lblReadsFromCache,lblNumberReadsFromCache,lblAvgSizeFromCache;
  BufferedLabel lblReadsFromFile, lblNumberReadsFromFile,lblAvgSizeFromFile;
  BufferedLabel lblPercentReads1;
  BufferedLabel lblPercentReads2;
  ProgressBar pbReads1;
  ProgressBar pbReads2;


  BufferedLabel lblWritesToCache,lblNumberWritesToCache,lblAvgSizeToCache;
  BufferedLabel lblWritesToFile, lblNumberWritesToFile,lblAvgSizeToFile;
  BufferedLabel lblPercentWrites;
  ProgressBar pbWrites;

  Canvas  readsFromFile,readsFromCache,writesToCache,writesToFile;

  SpeedGraphic rffGraph,rfcGraph,wtcGraph,wtfGraph;

  Canvas  diskReadsQueued, diskWritesQueued;
  
  SpeedGraphic	drqGraph, dwqGraph;
  
  public CacheView() {
    try {
      cfmStats = CacheFileManagerFactory.getSingleton().getStats();
      
      dacStats = DiskManagerFactory.getDiskAccessController().getStats();
      
      rfcGraph = SpeedGraphic.getInstance();
      wtcGraph = SpeedGraphic.getInstance();
      rffGraph = SpeedGraphic.getInstance();
      wtfGraph = SpeedGraphic.getInstance();
         
      drqGraph =
		  SpeedGraphic.getInstance(
			new ValueFormater()
			{
			    @Override
			    public String
			    format(int value)
			    {
			         return( DisplayFormatters.formatByteCountToKiBEtc(value*(long)DisplayFormatters.getKinB()));
			    }
			});
	  
      dwqGraph =
		  SpeedGraphic.getInstance(
			new ValueFormater()
			{
			    @Override
			    public String
			    format(int value)
			    {
			         return( DisplayFormatters.formatByteCountToKiBEtc(value*(long)DisplayFormatters.getKinB()));
			    }
			});
      
    } catch(Exception e) {
    	Debug.printStackTrace( e );
    }
  }

  private void initialize(Composite composite) {
    panel = new Composite(composite,SWT.NULL);
    panel.setLayout(new GridLayout());

    generateGeneralGroup();
    generateReadsGroup();
    generateWritesGroup();
    generateSpeedGroup();
  }

  /**
   *
   */
  private void generateGeneralGroup() {
    GridData gridData;

    Group gCacheGeneral = new Group(panel,SWT.NULL);
    Messages.setLanguageText(gCacheGeneral,"CacheView.general.title");
    gCacheGeneral.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    GridLayout layoutGeneral = new GridLayout();
    layoutGeneral.numColumns = 4;
    gCacheGeneral.setLayout(layoutGeneral);
    Label lbl;

    lbl = new Label(gCacheGeneral,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    lbl.setLayoutData(gridData);
    Messages.setLanguageText(lbl,"CacheView.general.inUse");

    lblInUse = new BufferedLabel(gCacheGeneral,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblInUse.setLayoutData(gridData);

    pbInUse =  new ProgressBar(gCacheGeneral,SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.verticalSpan = 2;
    pbInUse.setLayoutData(gridData);
    pbInUse.setMinimum(0);
    pbInUse.setMaximum(1000);
    if ( Constants.isWindows ){
    		// disable the annoying animation windows bestows on us
    	pbInUse.setState(SWT.PAUSED);
    }

    lblPercentUsed = new BufferedLabel(gCacheGeneral,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.verticalSpan = 2;
    gridData.widthHint = 120;
    lblPercentUsed.setLayoutData(gridData);

    lbl = new Label(gCacheGeneral,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    lbl.setLayoutData(gridData);
    Messages.setLanguageText(lbl,"CacheView.general.size");

    lblSize = new BufferedLabel(gCacheGeneral,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblSize.setLayoutData(gridData);
  }

  private void generateReadsGroup() {
    GridData gridData;

    Group gCacheReads = new Group(panel,SWT.NULL);
    Messages.setLanguageText(gCacheReads,"CacheView.reads.title");
    gCacheReads.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    GridLayout layoutGeneral = new GridLayout();
    layoutGeneral.numColumns = 6;
    gCacheReads.setLayout(layoutGeneral);
    Label lbl;

    lbl = new Label(gCacheReads,SWT.NULL);

    lbl = new Label(gCacheReads,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.#");

    lbl = new Label(gCacheReads,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.amount");

    lbl = new Label(gCacheReads,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.avgsize");

    lbl = new Label(gCacheReads,SWT.NULL);
    lbl = new Label(gCacheReads,SWT.NULL);


    lbl = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    lbl.setLayoutData(gridData);
    Messages.setLanguageText(lbl,"CacheView.reads.fromCache");

    lblNumberReadsFromCache = new BufferedLabel(gCacheReads,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblNumberReadsFromCache.setLayoutData(gridData);

    lblReadsFromCache = new BufferedLabel(gCacheReads,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblReadsFromCache.setLayoutData(gridData);

    lblAvgSizeFromCache = new BufferedLabel(gCacheReads,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblAvgSizeFromCache.setLayoutData(gridData);

    pbReads1 =  new ProgressBar(gCacheReads,SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pbReads1.setLayoutData(gridData);
    pbReads1.setMinimum(0);
    pbReads1.setMaximum(1000);
    if ( Constants.isWindows ){
    	pbReads1.setState(SWT.PAUSED);
    }
    
    lblPercentReads1 = new BufferedLabel(gCacheReads,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_VERTICAL);
     gridData.widthHint = 120;
    lblPercentReads1.setLayoutData(gridData);

    lbl = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    lbl.setLayoutData(gridData);
    Messages.setLanguageText(lbl,"CacheView.reads.fromFile");

    lblNumberReadsFromFile = new BufferedLabel(gCacheReads,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblNumberReadsFromFile.setLayoutData(gridData);

    lblReadsFromFile = new BufferedLabel(gCacheReads,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblReadsFromFile.setLayoutData(gridData);

    lblAvgSizeFromFile = new BufferedLabel(gCacheReads,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblAvgSizeFromFile.setLayoutData(gridData);
    
    pbReads2 =  new ProgressBar(gCacheReads,SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pbReads2.setLayoutData(gridData);
    pbReads2.setMinimum(0);
    pbReads2.setMaximum(1000);
    if ( Constants.isWindows ){
    	pbReads2.setState(SWT.PAUSED);
    }
    
    lblPercentReads2 = new BufferedLabel(gCacheReads,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_VERTICAL);
     gridData.widthHint = 120;
    lblPercentReads2.setLayoutData(gridData);
  }

  private void generateSpeedGroup() {
    GridData gridData;

    Group gCacheSpeeds = new Group(panel,SWT.NULL);
    Messages.setLanguageText(gCacheSpeeds,"CacheView.speeds.title");
    gCacheSpeeds.setLayoutData(new GridData(GridData.FILL_BOTH));

    GridLayout layoutGeneral = new GridLayout();
    layoutGeneral.numColumns = 3;
    gCacheSpeeds.setLayout(layoutGeneral);
    Label lbl;

    lbl = new Label(gCacheSpeeds,SWT.NULL);

    lbl = new Label(gCacheSpeeds,SWT.NULL);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
    lbl.setLayoutData(gridData);
    Messages.setLanguageText(lbl,"CacheView.speeds.reads");

    lbl = new Label(gCacheSpeeds,SWT.NULL);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
    lbl.setLayoutData(gridData);
    Messages.setLanguageText(lbl,"CacheView.speeds.writes");

	// cache from cache

    lbl = new Label(gCacheSpeeds,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.speeds.fromCache");
    
    readsFromCache = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    readsFromCache.setLayoutData(gridData);
    rfcGraph.initialize(readsFromCache);


    writesToCache = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    writesToCache.setLayoutData(gridData);
    wtcGraph.initialize(writesToCache);

    	// cache from file
    
    lbl = new Label(gCacheSpeeds,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.speeds.fromFile");

    readsFromFile = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    readsFromFile.setLayoutData(gridData);
    rffGraph.initialize(readsFromFile);

    writesToFile = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    writesToFile.setLayoutData(gridData);
    wtfGraph.initialize(writesToFile);
    
    	// disk read/write queues
    
    lbl = new Label(gCacheSpeeds,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.disk.queues");

    diskReadsQueued = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    diskReadsQueued.setLayoutData(gridData);
    drqGraph.initialize(diskReadsQueued);

    diskWritesQueued = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    diskWritesQueued.setLayoutData(gridData);
    dwqGraph.initialize(diskWritesQueued);  
  }
  
  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.views.stats.PeriodicViewUpdate#periodicUpdate()
   */
  public void periodicUpdate() {
    rfcGraph.addIntValue((int)cfmStats.getAverageBytesReadFromCache());
    rffGraph.addIntValue((int)cfmStats.getAverageBytesReadFromFile());
    wtcGraph.addIntValue((int)cfmStats.getAverageBytesWrittenToCache());
    wtfGraph.addIntValue((int)cfmStats.getAverageBytesWrittenToFile());
    
    drqGraph.addIntValue((int)(dacStats.getReadBytesQueued()/DisplayFormatters.getKinB()));
    dwqGraph.addIntValue((int)(dacStats.getWriteBytesQueued()/DisplayFormatters.getKinB()));
  }

  private void generateWritesGroup() {
    GridData gridData;

    Group gCacheWrites = new Group(panel,SWT.NULL);
    Messages.setLanguageText(gCacheWrites,"CacheView.writes.title");
    gCacheWrites.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    GridLayout layoutGeneral = new GridLayout();
    layoutGeneral.numColumns = 6;
    gCacheWrites.setLayout(layoutGeneral);
    Label lbl;

    lbl = new Label(gCacheWrites,SWT.NULL);

    lbl = new Label(gCacheWrites,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.#");

    lbl = new Label(gCacheWrites,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.amount");

    lbl = new Label(gCacheWrites,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.avgsize");

    lbl = new Label(gCacheWrites,SWT.NULL);
    lbl = new Label(gCacheWrites,SWT.NULL);


    lbl = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    lbl.setLayoutData(gridData);
    Messages.setLanguageText(lbl,"CacheView.writes.toCache");

    lblNumberWritesToCache = new BufferedLabel(gCacheWrites,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblNumberWritesToCache.setLayoutData(gridData);

    lblWritesToCache = new BufferedLabel(gCacheWrites,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblWritesToCache.setLayoutData(gridData);

    lblAvgSizeToCache = new BufferedLabel(gCacheWrites,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblAvgSizeToCache.setLayoutData(gridData);

    pbWrites =  new ProgressBar(gCacheWrites,SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.verticalSpan = 2;
    pbWrites.setLayoutData(gridData);
    pbWrites.setMinimum(0);
    pbWrites.setMaximum(1000);
    if ( Constants.isWindows ){
    	pbWrites.setState(SWT.PAUSED);
    }
    
    lblPercentWrites = new BufferedLabel(gCacheWrites,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.verticalSpan = 2;
    gridData.widthHint = 120;
    lblPercentWrites.setLayoutData(gridData);

    lbl = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    lbl.setLayoutData(gridData);
    Messages.setLanguageText(lbl,"CacheView.writes.toFile");

    lblNumberWritesToFile = new BufferedLabel(gCacheWrites,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblNumberWritesToFile.setLayoutData(gridData);

    lblWritesToFile = new BufferedLabel(gCacheWrites,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblWritesToFile.setLayoutData(gridData);

    lblAvgSizeToFile = new BufferedLabel(gCacheWrites,SWT.DOUBLE_BUFFERED);
    gridData = new GridData();
    gridData.widthHint = 100;
    lblAvgSizeToFile.setLayoutData(gridData);
  }

  private void delete() {
    Utils.disposeComposite(panel);
    rfcGraph.dispose();
    rffGraph.dispose();
    wtcGraph.dispose();
    wtfGraph.dispose();
    
    drqGraph.dispose();
    dwqGraph.dispose();
  }

  private Composite getComposite() {
    return panel;
  }

  private void refresh() {
    //General Part
	  
	if ( lblSize == null || lblSize.isDisposed()){
	
		return;
	}

    lblSize.setText(DisplayFormatters.formatByteCountToKiBEtc(cfmStats.getSize()));
    lblInUse.setText(DisplayFormatters.formatByteCountToKiBEtc(cfmStats.getUsedSize()));

    int perThousands = (int) ((1000 * cfmStats.getUsedSize()) / cfmStats.getSize());
    lblPercentUsed.setText(DisplayFormatters.formatPercentFromThousands(perThousands));
    pbInUse.setSelection(perThousands);

    //Reads
    refrehReads();

    //Writes
    refreshWrites();

    //Graphics
    rfcGraph.refresh(false);
    rffGraph.refresh(false);
    wtcGraph.refresh(false);
    wtfGraph.refresh(false);
    
    drqGraph.refresh(false);
    dwqGraph.refresh(false);
  }

  /**
   *
   */
  private void refrehReads() {
    int perThousands;
    long readsFromCache = cfmStats.getBytesReadFromCache();
    long readsFromFile = cfmStats.getBytesReadFromFile();
    long nbReadsFromCache = cfmStats.getCacheReadCount();
    long nbReadsFromFile = cfmStats.getFileReadCount();
    lblNumberReadsFromCache.setText("" + nbReadsFromCache);
    lblNumberReadsFromFile.setText("" + nbReadsFromFile);

    if(nbReadsFromCache != 0) {
      long avgReadFromCache = readsFromCache / nbReadsFromCache;
      lblAvgSizeFromCache.setText(DisplayFormatters.formatByteCountToKiBEtc(avgReadFromCache));
    } else {
      lblAvgSizeFromCache.setText("--");
    }

    if(nbReadsFromFile != 0) {
      long avgReadFromFile = readsFromFile / nbReadsFromFile;
      lblAvgSizeFromFile.setText(DisplayFormatters.formatByteCountToKiBEtc(avgReadFromFile));
    } else {
      lblAvgSizeFromFile.setText("--");
    }

    lblReadsFromCache.setText(DisplayFormatters.formatByteCountToKiBEtc(readsFromCache));
    lblReadsFromFile.setText(DisplayFormatters.formatByteCountToKiBEtc(readsFromFile));

     if(nbReadsFromFile > 0) {
      perThousands = (int) ((1000l * nbReadsFromCache ) / ( nbReadsFromCache + nbReadsFromFile));
      
      lblPercentReads1.setText(
    		  DisplayFormatters.formatPercentFromThousands( perThousands ) + "  " + MessageText.getString("label.hits"));
      
      pbReads1.setSelection(perThousands);
    }else{
        lblPercentReads1.setText( MessageText.getString("label.hit.ratio"));
    }
     
     if( readsFromFile > 0) {
    	 perThousands = (int) ((1000l * readsFromCache ) / readsFromFile);

    	 lblPercentReads2.setText(
    			 DisplayFormatters.formatPercentFromThousands(perThousands) + " " + MessageText.getString("label.efficiency"));

    	 pbReads2.setSelection(perThousands);
     }else{
       	 lblPercentReads2.setText(MessageText.getString("label.efficiency"));
     }
  }

  private void refreshWrites() {
    int perThousands;
    long writesToCache = cfmStats.getBytesWrittenToCache();
    long writesToFile = cfmStats.getBytesWrittenToFile();
    long nbWritesToCache = cfmStats.getCacheWriteCount();
    long nbWritesToFile = cfmStats.getFileWriteCount();
    lblNumberWritesToCache.setText("" + nbWritesToCache);
    lblNumberWritesToFile.setText("" + nbWritesToFile);

    if(nbWritesToCache != 0) {
      long avgReadToCache = writesToCache / nbWritesToCache;
      lblAvgSizeToCache.setText(DisplayFormatters.formatByteCountToKiBEtc(avgReadToCache));
    } else {
      lblAvgSizeToCache.setText("--");
    }

    if(nbWritesToFile != 0) {
      long avgReadToFile = writesToFile / nbWritesToFile;
      lblAvgSizeToFile.setText(DisplayFormatters.formatByteCountToKiBEtc(avgReadToFile));
    } else {
      lblAvgSizeToFile.setText("--");
    }

    lblWritesToCache.setText(DisplayFormatters.formatByteCountToKiBEtc(writesToCache));
    lblWritesToFile.setText(DisplayFormatters.formatByteCountToKiBEtc(writesToFile));

    long totalNbWrites = nbWritesToCache + nbWritesToFile;
    if(totalNbWrites > 0) {
      perThousands = (int) ((1000l * nbWritesToCache) / totalNbWrites);
      lblPercentWrites.setText(DisplayFormatters.formatPercentFromThousands(perThousands) + " " + MessageText.getString("CacheView.writes.hits"));
      pbWrites.setSelection(perThousands);
    }else{
        lblPercentWrites.setText( MessageText.getString("CacheView.writes.hits"));
    }
  }

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
        UISWTView swtView = (UISWTView) event.getData();
      	swtView.setTitle(MessageText.getString(MSGID_PREFIX + ".title.full"));
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;

      case StatsView.EVENT_PERIODIC_UPDATE:
      	periodicUpdate();
      	break;
    }

    return true;
  }
}


