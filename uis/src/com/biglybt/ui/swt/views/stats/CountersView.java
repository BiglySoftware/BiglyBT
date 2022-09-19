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


import java.util.*;


import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.stats.CoreStats;

import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;

import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.IViewRequiresPeriodicUpdates;

/**
 *
 */
public class CountersView
	implements UISWTViewCoreEventListener, IViewRequiresPeriodicUpdates
{
	public static final String MSGID_PREFIX = "CountersView";

	private Composite panel;
	private ScrolledComposite counters_panel_sc;
	private Composite counters_panel;
	
	private Map<String,BufferedLabel>	label_map = new HashMap<>();
	
	public 
	CountersView() 
	{

	}

	private void 
	initialize(
		Composite composite ) 
	{
		panel = new Composite( composite,SWT.NULL );
		
		panel.setLayout(new GridLayout());

		counters_panel_sc = new ScrolledComposite(panel, SWT.V_SCROLL );
	    counters_panel_sc.setExpandHorizontal(true);
	    counters_panel_sc.setExpandVertical(true);
		GridLayout layout = new GridLayout();
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		counters_panel_sc.setLayout(layout);
		GridData gridData = new GridData(GridData.FILL_BOTH );
		counters_panel_sc.setLayoutData(gridData);

		counters_panel = new Composite( counters_panel_sc, SWT.NULL );
		counters_panel.setLayoutData( new GridData( GridData.FILL_BOTH));
		
		counters_panel.setLayout(new GridLayout(2,false));

		counters_panel_sc.setContent(counters_panel);
		counters_panel_sc.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				counters_panel_sc.setMinSize(counters_panel.computeSize(SWT.DEFAULT, SWT.DEFAULT ));
			}
		});
			
		build();
	}
	
	private void
	build()
	{
		if ( counters_panel == null || counters_panel.isDisposed()){

			return;
		}

		for ( Control c: counters_panel.getChildren()){

			c.dispose();
		}
		
		label_map.clear();

		Set<String>	types = new HashSet<>();

		types.add( CoreStats.ST_ALL );

		Map<String,Object>	reply = CoreStats.getStats( types );

		java.util.List<String> keys = new ArrayList<>( reply.keySet());
		
		Collections.sort( keys );
		
		for ( String key: keys ){
		
			Label label = new Label( counters_panel, SWT.NULL );
			
			//label.setLayoutData( new GridData( GridData.FILL_HORIZONTAL));
			
			label.setText( key );
			
			BufferedLabel bl = new BufferedLabel(counters_panel, SWT.NULL );
			
			bl.setLayoutData( new GridData( GridData.FILL_HORIZONTAL));
			
			label_map.put( key, bl );
		}
		
		counters_panel_sc.setMinSize(counters_panel.computeSize(SWT.DEFAULT, SWT.DEFAULT ));
		
		panel.layout( true, true );
	}

	public void 
	periodicUpdate() 
	{
	}

	private void 
	delete() 
	{
		Utils.disposeComposite(panel);
	}

	private Composite 
	getComposite() 
	{
		return( panel );
	}

	private void 
	refresh() 
	{
		Set<String>	types = new HashSet<>();

		types.add( CoreStats.ST_ALL );

		Map<String,Object>	reply = CoreStats.getStats( types );

		for ( Map.Entry<String,Object> entry: reply.entrySet()){
			
			BufferedLabel lab = label_map.get( entry.getKey());
			
			if ( lab != null ){
				
				Object val = entry.getValue();
				
				if ( val instanceof Number ){
					
					lab.setText( String.valueOf( val ));
				}
			}
		}
	}


	@Override
	public boolean 
	eventOccurred(
		UISWTViewEvent event) 
	{
		switch( event.getType()){
		
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
	
			case UISWTViewEvent.TYPE_SHOWN:
				break;
	
			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;
	
			case StatsView.EVENT_PERIODIC_UPDATE:
				periodicUpdate();
				break;
		}

		return( true );
	}
}


