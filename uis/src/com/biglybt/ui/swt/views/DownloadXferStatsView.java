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

package com.biglybt.ui.swt.views;


import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.stats.XferStatsPanel;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManagerStats;

public class DownloadXferStatsView
	implements UISWTViewCoreEventListener
{
	public static final String MSGID_PREFIX = "DownloadXferStatsView";

	private Composite 				composite;
	
	private XferStatsPanel 			local_stats;
	
	private UISWTView swtView;

	private GlobalManagerStats		gm_stats;
	private DownloadManager			current_dm;
	
	public 
	DownloadXferStatsView() 
	{
	}

	public void 
	initialize(
		Composite _composite) 
	{
		composite = _composite;	

		Composite local_composite = new Composite( composite, SWT.NULL );

		local_composite.setLayout(new FillLayout());
		local_composite.setLayoutData( Utils.getFilledFormData());

		Composite local_panel = new Composite(local_composite,SWT.NULL);
		local_panel.setLayout(new FillLayout());

		local_stats = new XferStatsPanel( local_panel, false );
		
		CoreFactory.addCoreRunningListener((core)->{
				
				Utils.execSWTThread(()->{
						
					gm_stats = core.getGlobalManager().getStats();
					
					local_stats.init( current_dm==null?null:gm_stats.getAggregateLocalStats( current_dm ));
				});
		});
						
	}

	private String
	getTitleID()
	{
		return( MSGID_PREFIX + ".title.full" );
	}

	private void
	dataSourceChanged(
		Object		ds )
	{
		DownloadManager[] dms = DataSourceUtils.getDMs( ds );
		
		current_dm = dms.length==1?dms[0]:null;
		
		Utils.execSWTThread(()->{
			
			if ( gm_stats != null ){
			
				local_stats.init( current_dm==null?null:gm_stats.getAggregateLocalStats( current_dm ));
			}
		});
	}
	
	public
	void delete()
	{
		if (local_stats != null) {
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

		case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:{
			dataSourceChanged( event.getData());
			break;
		}
		case UISWTViewEvent.TYPE_SHOWN:
			if ( local_stats != null ){
				local_stats.requestRefresh();
			}
			break;

		case UISWTViewEvent.TYPE_REFRESH:
			if ( local_stats != null ){
				local_stats.refreshView();
			}
			break;
		}

		return true;
	}
}
