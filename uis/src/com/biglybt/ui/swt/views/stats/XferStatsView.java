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
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.global.GlobalManagerStats;

public class XferStatsView
	implements UISWTViewCoreEventListener
{
	public static final String MSGID_PREFIX = "XferStatsView";

	Composite 				composite;
	
	XferStatsPanel 			local_stats;
	XferStatsPanel 			global_stats;
	
	private final boolean 	autoAlpha;

	private UISWTView swtView;

	public XferStatsView() {
		this(false);
	}

	public XferStatsView(boolean autoAlpha ) {
		this.autoAlpha = autoAlpha;
	}

	public void 
	initialize(
		Composite _composite) 
	{
		composite = _composite;
		
		CTabFolder tab_folder = new CTabFolder(composite, SWT.LEFT);

		CTabItem global_item = new CTabItem(tab_folder, SWT.NULL);

		global_item.setText( MessageText.getString( "label.global" ));

		Composite global_composite = new Composite( tab_folder, SWT.NULL );

		global_item.setControl( global_composite );
	
		global_composite.setLayout(new FillLayout());
		global_composite.setLayoutData( Utils.getFilledFormData());
					
		Composite global_panel = new Composite(global_composite,SWT.NULL);
		global_panel.setLayout(new FillLayout());
		
		global_stats = new XferStatsPanel( global_panel, true );
		global_stats.setAutoAlpha(autoAlpha);
		

		CTabItem local_item = new CTabItem(tab_folder, SWT.NULL);

		local_item.setText( MessageText.getString( "DHTView.db.local" ));

		Composite local_composite = new Composite( tab_folder, SWT.NULL );

		local_composite.setLayout(new FillLayout());
		local_composite.setLayoutData( Utils.getFilledFormData());

		local_item.setControl( local_composite );
		
		Composite local_panel = new Composite(local_composite,SWT.NULL);
		local_panel.setLayout(new FillLayout());

		local_stats = new XferStatsPanel( local_panel, false );
		local_stats.setAutoAlpha(autoAlpha);
		
		tab_folder.setSelection(  global_item );

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {

			@Override
			public void coreRunning(Core core) {
				
				Utils.execSWTThread(
					new Runnable(){
						
						@Override
						public void run(){
							GlobalManagerStats stats = core.getGlobalManager().getStats();
							
							global_stats.init( stats.getAggregateRemoteStats());
							local_stats.init( stats.getAggregateLocalStats());
						} 
					});
			}
		});
	}

	private String
	getTitleID()
	{
		return( MSGID_PREFIX + ".title.full" );
	}

	public
	void delete()
	{
		if (global_stats != null) {
			global_stats.delete();
			local_stats.delete();
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
			Messages.updateLanguageForControl(composite);
			if (swtView != null) {
				swtView.setTitle(MessageText.getString(getTitleID()));
			}
			break;

		case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
			break;

		case UISWTViewEvent.TYPE_FOCUSGAINED:
			if ( global_stats != null ){
				
				global_stats.requestRefresh();
				local_stats.requestRefresh();
			}
			break;

		case UISWTViewEvent.TYPE_REFRESH:
			if ( global_stats != null ){
				
				global_stats.refreshView();
				local_stats.refreshView();
			}
			break;
		}

		return true;
	}
}
