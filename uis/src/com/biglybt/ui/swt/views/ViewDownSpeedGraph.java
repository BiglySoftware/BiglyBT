/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.graphics.SpeedGraphic;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.core.CoreRunningListener;

/**
 * @author TuxPaper
 * @created Apr 7, 2007
 *
 */
public class ViewDownSpeedGraph
	implements UISWTViewCoreEventListener
{

	GlobalManager manager = null;

	GlobalManagerStats stats = null;

	Canvas downSpeedCanvas;

	SpeedGraphic downSpeedGraphic;

	TimerEventPeriodic	timerEvent;

	private boolean everRefreshed = false;

	private UISWTView swtView;

	public ViewDownSpeedGraph() {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				manager = core.getGlobalManager();
				stats = manager.getStats();
			}
		});
	}

	private void periodicUpdate() {
		if (manager == null || stats == null) {
			return;
		}

		int swarms_peer_speed = (int) stats.getTotalSwarmsPeerRate(true, false);

		downSpeedGraphic.addIntsValue(new int[] {
			stats.getDataReceiveRate() + stats.getProtocolReceiveRate(),
			stats.getProtocolReceiveRate(),
			COConfigurationManager.getIntParameter("Max Download Speed KBs") * DisplayFormatters.getKinB(),
			swarms_peer_speed
		});
	}

	private void initialize(Composite composite) {
		GridData gridData;
		composite.setLayout( Utils.getSimpleGridLayout(1));
		downSpeedCanvas = new Canvas(composite, SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_BOTH);
		downSpeedCanvas.setLayoutData(gridData);
		downSpeedGraphic = SpeedGraphic.getInstance();
		downSpeedGraphic.initialize(downSpeedCanvas);
		//downSpeedGraphic.setAutoAlpha(true);
		/* this was for testing right?
		SWTSkinProperties skinProperties = SWTSkinFactory.getInstance().getSkinProperties();
		Color cBG1 = skinProperties.getColor("color.topbar.speed.bg1");
		Color cBG2 = skinProperties.getColor("color.topbar.speed.bg2");
		Color cBG3 = skinProperties.getColor("color.topbar.speed.bg3");
		downSpeedGraphic.setColors(cBG1, cBG2, cBG3);
		downSpeedGraphic.setLineColors(skinProperties.getColor("color.topbar.speed.average"),
				skinProperties.getColor("color.topbar.speed.value0"),
				skinProperties.getColor("color.topbar.speed.overhead"),
				skinProperties.getColor("color.topbar.speed.value1"),
				skinProperties.getColor("color.topbar.speed.value2plus"),
				skinProperties.getColor("color.topbar.speed.trimmed"));
		*/
	}

	private void delete() {
		Utils.disposeComposite(downSpeedCanvas);
		if ( downSpeedGraphic != null ){
			downSpeedGraphic.dispose();
		}
	}

	private String getFullTitle() {
		return( MessageText.getString("TableColumn.header.downspeed"));
	}



	private Composite getComposite() {
		return downSpeedCanvas;
	}

	private void refresh() {
		if ( downSpeedGraphic == null ){
			return;
		}
		
		if (!everRefreshed) {
			everRefreshed = true;
			timerEvent = SimpleTimer.addPeriodicEvent("TopBarSpeedGraphicView", 1000, new TimerEventPerformer() {
				@Override
				public void perform(TimerEvent event) {
					if ( downSpeedCanvas.isDisposed()){
						timerEvent.cancel();
					}else{
						periodicUpdate();
					}
				}
			});
		}
		downSpeedGraphic.refresh(false);
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = event.getView();
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;
    }

    return true;
  }
}
