/* *
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

package com.biglybt.ui.swt.views.skin;


import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.main.MainMDISetup;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.views.skin.SB_Dashboard.DashboardListener;

public class SBC_DashboardView
	extends SkinView
	implements UIUpdatable, DashboardListener
{
	private static final String UI_NAME = "Dashboard";

	private static boolean core_running;
	private static boolean core_running_listener_added;
	
	private Composite 			dashboard_composite;
	
	private final boolean	destroy_when_hidden = false;
	
	private boolean	hidden		= false;
	private boolean	is_built 	= false;
	
	@Override
	public void updateUI() {
	}

	@Override
	public String getUpdateUIName() {
		return UI_NAME;
	}

	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {

		SWTSkinObject so_area = getSkinObject("dashboard-area");

		dashboard_composite = (Composite)so_area.getControl();
		
		dashboard_composite.setLayout( new FormLayout());
		
		MainMDISetup.getSb_dashboard().addListener( this );
		
		return( null );
	}


	@Override
	public Object 
	skinObjectHidden(
		SWTSkinObject 	skinObject, 
		Object 			params) {

		hidden	= true;
		
		if ( destroy_when_hidden ){
		
			Utils.disposeComposite( dashboard_composite, false );
			
			is_built = false;
		}
		
		return super.skinObjectHidden(skinObject, params);
	}

	@Override
	public Object 
	skinObjectShown(
		SWTSkinObject 	skinObject, 
		Object 			params ) 
	{	
		hidden	= false;
		
		Object result = super.skinObjectShown(skinObject, params);
		
		if ( !is_built ) {
		
			Utils.disposeComposite( dashboard_composite, false );
			
			build();
		}
		
		return( result );
	}
	
	private void
	build()
	{
		synchronized( SBC_DashboardView.class ){
			
			if ( !core_running ){
		
				Core core = CoreFactory.getSingleton();
				
				if ( core.isStarted()){
					
					core_running = true;
					
				}else{
					
					if ( !core_running_listener_added ){
						
						core_running_listener_added = true;
						
						core.addLifecycleListener(
							new CoreLifecycleAdapter() 
							{
								@Override
								public boolean
								requiresPluginInitCompleteBeforeStartedEvent()
								{
									return( true );
								}
								
								@Override
								public void started(Core core){
		
									synchronized( SBC_DashboardView.class ){
										
										core_running = true;
									}
							
									Utils.execSWTThread(
											new Runnable()
											{
												public void
												run()
												{
													buildSupport();
												}
											});
								}
							});
					}
				
					return;
				}
			}
		}

		buildSupport();
	}
	
	private void
	buildSupport()
	{
		if ( !hidden ) {
		
			MainMDISetup.getSb_dashboard().build( dashboard_composite );
		
			is_built = true;
		}
	}

	@Override
	public void itemsChanged(){
		Utils.execSWTThread(
			new Runnable()
			{
				public void
				run()
				{
					if ( hidden ) {
						
						is_built = false;
					}
					
					build();
				}
			});		
	}
	
	@Override
	public Object 
	skinObjectDestroyed(
		SWTSkinObject 	skinObject, 
		Object 			params ) 
	{
		SB_Dashboard sbd = MainMDISetup.getSb_dashboard();
		
		if ( sbd != null ){
			
			sbd.removeListener( this );
		}
		
		return super.skinObjectDestroyed(skinObject, params);
	}
}
