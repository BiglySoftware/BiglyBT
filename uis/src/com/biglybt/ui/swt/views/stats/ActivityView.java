/*
 * File    : StatsView.java
 * Created : 15 d�c. 2003}
 * By      : Olivier
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.ui.swt.views.stats;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.components.graphics.SpeedGraphic;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.IViewRequiresPeriodicUpdates;
import com.biglybt.core.Core;

/**
 * @author Olivier
 *
 */
public class ActivityView
	implements ParameterListener, UISWTViewCoreEventListener, IViewRequiresPeriodicUpdates
{

  public static final String MSGID_PREFIX = "SpeedView";

	GlobalManager manager = null;
  GlobalManagerStats stats = null;

  Composite panel;

  Canvas downSpeedCanvas;
  SpeedGraphic downSpeedGraphic;

  Canvas upSpeedCanvas;
  SpeedGraphic upSpeedGraphic;

	public ActivityView() {
    downSpeedGraphic = SpeedGraphic.getInstance();
    upSpeedGraphic = SpeedGraphic.getInstance();
  	CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				manager = core.getGlobalManager();
				stats = manager.getStats();
			}
		});
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.views.stats.PeriodicViewUpdate#periodicUpdate()
   */
  public void periodicUpdate() {
  	if (manager == null || stats == null) {
  		return;
  	}

	int swarms_peer_speed = (int)stats.getTotalSwarmsPeerRate(true,false);

	int kinb = DisplayFormatters.getKinB();
	
    downSpeedGraphic.addIntsValue(
    	new int[]{ 	stats.getDataReceiveRate()+stats.getProtocolReceiveRate(),
    				stats.getProtocolReceiveRate(),
    				COConfigurationManager.getIntParameter("Max Download Speed KBs") * kinb,
    				swarms_peer_speed });

    upSpeedGraphic.addIntsValue(
    	new int[]{	stats.getDataSendRate()+stats.getProtocolSendRate(),
    				stats.getProtocolSendRate(),
    				COConfigurationManager.getIntParameter(TransferSpeedValidator.getActiveUploadParameter( manager )) * kinb,
    				swarms_peer_speed });
  }

  private void initialize(Composite composite) {
    panel = new Composite(composite,SWT.NULL);
    panel.setLayout(new GridLayout());
    GridData gridData;

    Group gDownSpeed = Utils.createSkinnedGroup(panel,SWT.NULL);
    Messages.setLanguageText(gDownSpeed,"SpeedView.downloadSpeed.title");
    gridData = new GridData(GridData.FILL_BOTH);
    gDownSpeed.setLayoutData(gridData);
    gDownSpeed.setLayout(new GridLayout());

    downSpeedCanvas = new Canvas(gDownSpeed,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    downSpeedCanvas.setLayoutData(gridData);
    downSpeedGraphic.initialize(downSpeedCanvas);
    Color[] colors = downSpeedGraphic.colors;

    Group gUpSpeed = Utils.createSkinnedGroup(panel,SWT.NULL);
    Messages.setLanguageText(gUpSpeed,"SpeedView.uploadSpeed.title");
    gridData = new GridData(GridData.FILL_BOTH);
    gUpSpeed.setLayoutData(gridData);
    gUpSpeed.setLayout(new GridLayout());

    upSpeedCanvas = new Canvas(gUpSpeed,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    upSpeedCanvas.setLayoutData(gridData);
    upSpeedGraphic.initialize(upSpeedCanvas);

    COConfigurationManager.addAndFireParameterListener("Stats Graph Dividers", this);

    upSpeedGraphic.setLineColors(colors);

	String[] colorConfigs = new String[] {
		"ActivityView.legend.peeraverage",
		"ActivityView.legend.achieved",
		"ActivityView.legend.overhead",
		"ActivityView.legend.limit",
		"ActivityView.legend.swarmaverage",
		"ActivityView.legend.trimmed"
	};

	Legend.createLegendComposite(panel, colors, colorConfigs);

	panel.addListener(
		SWT.Activate,
		new Listener()
		{
			@Override
			public void
			handleEvent(
				Event event )
			{
				refresh(true);
			}
		});
  }

  private void delete() {
    Utils.disposeComposite(panel);
    downSpeedGraphic.dispose();
    upSpeedGraphic.dispose();
    COConfigurationManager.removeParameterListener("Stats Graph Dividers", this);
  }

  private Composite getComposite() {
    return panel;
  }

  private void refresh(boolean force) {
    downSpeedGraphic.refresh(force);
    upSpeedGraphic.refresh(force);
  }

  @Override
  public void parameterChanged(String param_name) {
	  boolean update_dividers = COConfigurationManager.getBooleanParameter("Stats Graph Dividers");
	  int update_divider_width = update_dividers ? 60 : 0;
      downSpeedGraphic.setUpdateDividerWidth(update_divider_width);
      upSpeedGraphic.setUpdateDividerWidth(update_divider_width);
  }

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
	      UISWTView swtView = event.getView();
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
    	refresh(true);
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh(false);
        break;

      case StatsView.EVENT_PERIODIC_UPDATE:
      	periodicUpdate();
      	break;
    }

    return true;
  }
}
