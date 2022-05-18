/*
 * Created on Jun 27, 2008
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pifimpl.*;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl.SWTViewListener;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectListener;
import com.biglybt.ui.swt.skin.SWTSkinUtils;
import com.biglybt.ui.swt.views.skin.SkinView;

/**
 * @author TuxPaper
 * @created Jun 27, 2008
 *
 */
public class RightBarView
	extends SkinView
{
	private SWTSkin skin;

	private Composite cPluginsArea;
	
	private SWTViewListener swtViewListener;

	private final List<UISWTViewImpl> pluginViews = new ArrayList<>();
	
	@Override
	public Object 
	skinObjectInitialShow(
		SWTSkinObject 	skinObject, 
		Object 			params ) 
	{
		this.skin = skinObject.getSkin();

		skin.addListener("rightbar-area-plugins", new SWTSkinObjectListener() {
			@Override
			public Object eventOccured(SWTSkinObject skinObject, int eventType,
			                           Object params) {
				if (eventType == SWTSkinObjectListener.EVENT_SHOW) {
					skin.removeListener("rightbar-area-plugins", this);
					
					CoreFactory.addCoreRunningListener(new CoreRunningListener() {
						@Override
						public void coreRunning(Core core) {
							Utils.execSWTThreadLater(0, new AERunnable() {
								@Override
								public void runSupport() {
									buildViews();
								}
							});
						}
					});
				}
				return null;
			}
		});
		
		skin.getSkinObject("rightbar-area-plugin").addListener(
				new SWTSkinObjectListener() {

					@Override
					public Object eventOccured(SWTSkinObject skinObject, int eventType,
					                           Object params) {
						if ( eventType == SWTSkinObjectListener.EVENT_SHOW ){

							
						}else if ( eventType == SWTSkinObjectListener.EVENT_HIDE ){

							
						}

						return( null );
					}
				});
		
		return( null );
	}
	
	@Override
	public Object 
	skinObjectDestroyed(
		SWTSkinObject 	skinObject, 
		Object 			params ) 
	{
		if (swtViewListener != null) {
			try {
				ViewManagerSWT.getInstance().removeSWTViewListener(swtViewListener);
			} catch (Throwable ignore) {

			}
		}

		return super.skinObjectDestroyed(skinObject, params);
	}
	
	private void
	buildViews()
	{
		SWTSkinObject rbObject = skin.getSkinObject( SkinConstants.VIEWID_RIGHTBAR );

		SWTSkinObject pluginObject = skin.getSkinObject( "rightbar-area-plugins" );
				
		cPluginsArea = (Composite)pluginObject.getControl();
		
		SWTSkinObject lineObject = skin.getSkinObject( "rightbar-line" );
			
		Listener l = new Listener() {
			private int mouseDownAt = -1;

			@Override
			public void handleEvent(Event event) {
				Control c = rbObject.getControl();
				if (event.type == SWT.MouseDown) {
					if (event.x <= 10) {
						mouseDownAt = event.x;
					}
				} else if (event.type == SWT.MouseUp && mouseDownAt >= 0) {
					int diff = mouseDownAt - event.x ;
					mouseDownAt = -1;
					FormData formData = (FormData) c.getLayoutData();
					formData.width += diff;
					if (formData.width < 50) {
						formData.width = 50;
					}
					COConfigurationManager.setParameter("v3.rightbar.width", formData.width);
					Utils.relayout(c);
				} else if (event.type == SWT.MouseMove) {
					c.setCursor(c.getDisplay().getSystemCursor(SWT.CURSOR_SIZEWE));
				} else if (event.type == SWT.MouseExit) {
					c.setCursor(null);
				}
			}
		};

		Control rbControl = rbObject.getControl();
		
		rbControl.addListener(SWT.MouseDown, l);
		rbControl.addListener(SWT.MouseUp, l);
		rbControl.addListener(SWT.MouseMove, l);
		rbControl.addListener(SWT.MouseExit, l);

		rbObject.addListener(new SWTSkinObjectListener() {
			@Override
			public Object eventOccured(SWTSkinObject skinObject, int eventType,
			                           Object params) {
				if (eventType == EVENT_SHOW) {
					int w = COConfigurationManager.getIntParameter("v3.rightbar.width");
					Control control = rbObject.getControl();
					FormData formData = (FormData) control.getLayoutData();
					formData.width = w;
					control.setLayoutData(formData);
					Utils.relayout(control);
				}
				return null;
			}
		});

		createSideBarPluginViews();

		rbControl.getParent().layout(true);
	}
	
	private void 
	createSideBarPluginViews() 
	{
		if (cPluginsArea == null) {
			return;
		}

		List<UISWTViewBuilderCore> pluginViewBuilders = ViewManagerSWT.getInstance().getBuilders(UISWTInstance.VIEW_RIGHTBAR_AREA);
		for (UISWTViewBuilderCore builder : pluginViewBuilders) {
			if (builder == null) {
				continue;
			}
			try {
				UISWTViewImpl view = new UISWTViewImpl(builder, false);
				view.setDestroyOnDeactivate(false);
				addSideBarView(view, cPluginsArea);
				cPluginsArea.getParent().getParent().layout(true, true);
			} catch (Exception e) {
				e.printStackTrace();
				// skip, plugin probably specifically asked to not be added
			}
		}

		swtViewListener = new SWTViewListener() {
			@Override
			public void setViewRegistered(Object forDSTypeOrViewID,
					UISWTViewBuilderCore builder) {
				if (!UISWTInstance.VIEW_RIGHTBAR_AREA.equals(forDSTypeOrViewID)) {
					return;
				}
				Utils.execSWTThread(() -> {
					try {
						UISWTViewImpl view = new UISWTViewImpl(builder, false);
						view.setDestroyOnDeactivate(false);
						addSideBarView(view, cPluginsArea);
					} catch (Exception e) {
						e.printStackTrace();
						// skip, plugin probably specifically asked to not be added
					}
				});
			}

			@Override
			public void setViewDeregistered(Object forDSTypeOrViewID,
					UISWTViewBuilderCore builder) {
				if (!UISWTInstance.VIEW_RIGHTBAR_AREA.equals(forDSTypeOrViewID)) {
					return;
				}
				Utils.execSWTThread(() -> {	
					
						List<UISWTViewImpl> views;
						
						synchronized( pluginViews ){
						
							views = new ArrayList<>( pluginViews );
						}
						
						for (UISWTViewImpl view : views){
							
							if (builder.equals(view.getEventListenerBuilder())) {
									
								removeSideBarView( view );
							}
						}
					});			
			}
		};
		
		ViewManagerSWT.getInstance().addSWTViewListener(swtViewListener);

		cPluginsArea.getParent().getParent().layout(true, true);
	}

	private void 
	addSideBarView(
		UISWTViewImpl view, 
		Composite cPluginsArea) 
	{	
		synchronized( pluginViews ){
			
			pluginViews.add(view);
			
			try {
				view.create();
				
			} catch (UISWTViewEventCancelledException e) {
				
				pluginViews.remove(view);
				
				return;
			}
	
			if ( pluginViews.size() > 0 ){
									
				SWTSkinUtils.setVisibility(skin, SkinConstants.VIEWID_RIGHTBAR + ".visible", SkinConstants.VIEWID_RIGHTBAR, true, true);
			}
		}

		Utils.disposeComposite( cPluginsArea, false );
		
		Composite parent = new Composite(cPluginsArea, SWT.NONE);
						
		parent.setLayoutData( Utils.getFilledFormData());

		parent.setLayout( new FormLayout());	// this works well with the initialize code
		
		view.initialize( parent );
		
		cPluginsArea.getParent().layout( true,  true );
	}

	private void 
	removeSideBarView(
		UISWTViewImpl view )
	{	
		try{
			view.closeView();
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
		
		synchronized( pluginViews ){
			
			pluginViews.remove(view);
		
			if ( pluginViews.isEmpty()){
				
				SWTSkinUtils.setVisibility(skin, SkinConstants.VIEWID_RIGHTBAR + ".visible", SkinConstants.VIEWID_RIGHTBAR, false, true);
			}
		}
	}
}
