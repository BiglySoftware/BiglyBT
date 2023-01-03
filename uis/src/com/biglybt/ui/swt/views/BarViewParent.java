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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pifimpl.*;
import com.biglybt.ui.swt.pifimpl.UISWTInstanceImpl.SWTViewListener;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectListener;
import com.biglybt.ui.swt.skin.SWTSkinUtils;
import com.biglybt.ui.swt.views.skin.SkinView;


public class BarViewParent
	extends SkinView
{
	private final String view_id;
	private final String view_area;
	private final String so_area_plugins;
	private final String so_area_plugin;
	private final String line_config_id;
	private final boolean	is_vertical;
	
	private SWTSkin skin;

	private Composite cPluginsArea;
	
	private SWTViewListener swtViewListener;

	private final List<UISWTViewImpl> pluginViews = new ArrayList<>();
	
	protected
	BarViewParent(
		String		_view_id,
		String		_view_area,
		String		_so_area_plugins,
		String		_so_area_plugin,
		String		_line_config_id,
		boolean		_is_vertical )
	{
		view_id			= _view_id;
		view_area		= _view_area;
		so_area_plugins = _so_area_plugins;
		so_area_plugin	= _so_area_plugin;
		line_config_id	= _line_config_id;
		is_vertical		= _is_vertical;
	}
	
	@Override
	public Object 
	skinObjectInitialShow(
		SWTSkinObject 	skinObject, 
		Object 			params ) 
	{
		this.skin = skinObject.getSkin();

		skin.addListener(so_area_plugins, new SWTSkinObjectListener() {
			@Override
			public Object eventOccured(SWTSkinObject skinObject, int eventType,
			                           Object params) {
				if (eventType == SWTSkinObjectListener.EVENT_SHOW) {
					skin.removeListener(so_area_plugins, this);
					
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
		
			// seems we need this listener otherwise the view never gets built...
		
		skin.getSkinObject(so_area_plugin).addListener(
				new SWTSkinObjectListener() {

					@Override
					public Object 
					eventOccured(
						SWTSkinObject skinObject, 
						int eventType,
					    Object params) 
					{
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
	
	protected void
	buildViews()
	{
		SWTSkinObject rbObject = skin.getSkinObject( view_id );

		SWTSkinObject pluginObject = skin.getSkinObject( so_area_plugins );
				
		cPluginsArea = (Composite)pluginObject.getControl();
					
		Listener l = new Listener() {
			private int mouseDownAt = -1;

			@Override
			public void handleEvent(Event event) {
				Control c = rbObject.getControl();
				if (event.type == SWT.MouseDown){
					Rectangle bounds = c.getBounds();
					if ( is_vertical ){
						if (event.x <= 10) {
							mouseDownAt = event.x;
						}
					}else{
						if (event.y >= ( bounds.height - 10 )) {
							mouseDownAt = event.y;
						}
					}
				} else if (event.type == SWT.MouseUp && mouseDownAt >= 0) {
					if ( is_vertical ){
						int diff = mouseDownAt - event.x ;
						mouseDownAt = -1;
						FormData formData = (FormData) c.getLayoutData();
						formData.width += diff;
						if (formData.width < 50) {
							formData.width = 50;
						}
						COConfigurationManager.setParameter(line_config_id, formData.width);
					}else{
						int diff = mouseDownAt - event.y ;
						mouseDownAt = -1;
						FormData formData = (FormData) c.getLayoutData();
						formData.height -= diff;
						if (formData.height < 50) {
							formData.height = 50;
						}
						COConfigurationManager.setParameter(line_config_id, formData.height);
					}
					Utils.relayout(c);
				} else if (event.type == SWT.MouseMove) {
					c.setCursor(c.getDisplay().getSystemCursor(is_vertical?SWT.CURSOR_SIZEWE:SWT.CURSOR_SIZENS));
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
					int dim = COConfigurationManager.getIntParameter(line_config_id);
					Control control = rbObject.getControl();
					FormData formData = (FormData) control.getLayoutData();
					if ( is_vertical ){
						formData.width = dim;
					}else{
						formData.height = dim;
					}
					control.setLayoutData(formData);
					Utils.relayout(control);
				}
				return null;
			}
		});

		createBarPluginViews();

		rbControl.getParent().layout(true);
	}
	
	private void 
	createBarPluginViews() 
	{
		if (cPluginsArea == null) {
			return;
		}

		List<UISWTViewBuilderCore> pluginViewBuilders = ViewManagerSWT.getInstance().getBuilders( view_area );
		for (UISWTViewBuilderCore builder : pluginViewBuilders) {
			if (builder == null) {
				continue;
			}
			try {
				UISWTViewImpl view = new UISWTViewImpl(builder, false);
				view.setDestroyOnDeactivate(false);
				addBarView(view, cPluginsArea);
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
				if (!view_area.equals(forDSTypeOrViewID)) {
					return;
				}
				Utils.execSWTThread(() -> {
					try {
						UISWTViewImpl view = new UISWTViewImpl(builder, false);
						view.setDestroyOnDeactivate(false);
						addBarView(view, cPluginsArea);
					} catch (Exception e) {
						e.printStackTrace();
						// skip, plugin probably specifically asked to not be added
					}
				});
			}

			@Override
			public void setViewDeregistered(Object forDSTypeOrViewID,
					UISWTViewBuilderCore builder) {
				if (!view_area.equals(forDSTypeOrViewID)) {
					return;
				}
				Utils.execSWTThread(() -> {	
					
						List<UISWTViewImpl> views;
						
						synchronized( pluginViews ){
						
							views = new ArrayList<>( pluginViews );
						}
						
						for (UISWTViewImpl view : views){
							
							if (builder.equals(view.getEventListenerBuilder())) {
									
								removeBarView( view );
							}
						}
					});			
			}
		};
		
		ViewManagerSWT.getInstance().addSWTViewListener(swtViewListener);

		cPluginsArea.getParent().getParent().layout(true, true);
	}

	private void 
	addBarView(
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
									
				SWTSkinUtils.setVisibility(skin, view_id + ".visible", view_id, true, true);
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
	removeBarView(
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
				
				SWTSkinUtils.setVisibility(skin, view_id + ".visible", view_id, false, true);
			}
		}
	}
}
