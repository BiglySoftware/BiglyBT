/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.stats;


import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;

public class XferStatsView
	implements UISWTViewCoreEventListenerEx
{
	public static final String MSGID_PREFIX = "XferStatsView";

	Composite panel;
	XferStatsPanel drawPanel;
	private final boolean autoAlpha;

	private UISWTView swtView;

	public XferStatsView() {
		this(false);
	}

	public boolean
	isCloneable()
	{
		return( true );
	}

	public UISWTViewCoreEventListenerEx
	getClone()
	{
		return( new XferStatsView());
	}
	
	public CloneConstructor
	getCloneConstructor()
	{
		return(
			new CloneConstructor()
			{
				public Class<? extends UISWTViewCoreEventListenerEx>
				getCloneClass()
				{
					return( XferStatsView.class );
				}
			});
	}
	
	public XferStatsView(boolean autoAlpha ) {
		this.autoAlpha = autoAlpha;
	}

	public void initialize(Composite composite) {
		
		panel = new Composite(composite,SWT.NULL);
		panel.setLayout(new FillLayout());
		drawPanel = new XferStatsPanel(panel);
		drawPanel.setAutoAlpha(autoAlpha);
		
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {

			@Override
			public void coreRunning(Core core) {
				
				Utils.execSWTThread(
					new Runnable(){
						
						@Override
						public void run(){
							drawPanel.init( core.getGlobalManager().getStats());
						}
					});
			}
		});
	}

	private Composite getComposite() {
		return panel;
	}

	private String
	getTitleID()
	{
		return( MSGID_PREFIX + ".title.full" );
	}

	public
	void delete()
	{
		if (drawPanel != null) {
			drawPanel.delete();
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
		case UISWTViewEvent.TYPE_CREATE:
			swtView = (UISWTView)event.getData();
			swtView.setTitle(MessageText.getString(getTitleID()));
			break;

		case UISWTViewEvent.TYPE_DESTROY:
			delete();
			break;

		case UISWTViewEvent.TYPE_INITIALIZE:
			initialize((Composite)event.getData());
			break;

		case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
			Messages.updateLanguageForControl(getComposite());
			if (swtView != null) {
				swtView.setTitle(MessageText.getString(getTitleID()));
			}
			break;

		case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
			break;

		case UISWTViewEvent.TYPE_FOCUSGAINED:
			if ( drawPanel != null ){
				
				drawPanel.requestRefresh();
			}
			break;

		case UISWTViewEvent.TYPE_REFRESH:
			if ( drawPanel != null ){
				
				drawPanel.refreshView();
			}
			break;
		}

		return true;
	}
}
