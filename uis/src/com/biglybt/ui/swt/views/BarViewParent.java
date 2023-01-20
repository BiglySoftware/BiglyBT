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

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
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
		
	Point dragPosition;
	
	protected void
	buildViews()
	{
		SWTSkinObject rbObject = skin.getSkinObject( view_id );

		SWTSkinObject pluginObject = skin.getSkinObject( so_area_plugins );
				
		cPluginsArea = (Composite)pluginObject.getControl();
			
		Control rbControl = rbObject.getControl();

		Listener l = new Listener() {
			private int mouseDownAt = -1;

			boolean hooked;
			
			@Override
			public void handleEvent(Event event) {
				Control c = rbObject.getControl();

				try{
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
						mouseDownAt = -1;
					}
				}finally{
					
					if ( mouseDownAt != -1 ){
															
						dragPosition = c.toDisplay(new Point( event.x, event.y ));

					}else{
						
						dragPosition = null;
					}
					
					if ( mouseDownAt == -1 && hooked ){
						
						hookControls( rbControl, false );
						
						hooked = false;
						
					}else if ( mouseDownAt != -1 && !hooked ){
						
						hookControls( rbControl, true );
						
						hooked = true;
					}
					
					if ( hooked ){
						
						updateHookedControls();
					}
				}
			}
		};
		
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
	
	List<Control>				hookedControls 		= new ArrayList<>();
	Map<Control,List<Integer>>	modifiedControls	= new HashMap<>();
	
	Point paintDragPosition = null;
	
	PaintListener dragPainter = 
		new PaintListener()
		{
		
			@Override
			public void 
			paintControl(
				PaintEvent e )
			{
				Control c = (Control)e.widget;
				
				Point controlPosition = c.toControl( paintDragPosition==null?dragPosition:paintDragPosition );
					
				paintDragPosition = null;
				
				int relPosition = Integer.MIN_VALUE;

				if ( is_vertical ){
					
					if ( controlPosition.x >= 0 && controlPosition.x <=  c.getSize().x ){
						
						relPosition = controlPosition.x;
					}
				}else{
					if ( controlPosition.y >= 0 && controlPosition.y <=  c.getSize().y ){
						
						relPosition = controlPosition.y;
					}
				}
				
				if ( relPosition != Integer.MIN_VALUE ){
					
					GC gc = e.gc;
					
					Color old = gc.getForeground();
					
					gc.setForeground( Colors.grey );
										
					if ( is_vertical ){
						
						gc.drawLine( relPosition, 0, relPosition, c.getBounds().height );
						
					}else{
						
						gc.drawLine( 0, relPosition, c.getBounds().width, relPosition );
					}
					
					gc.setForeground( old );

					List<Integer> list = modifiedControls.get(c);
					
					if ( list == null ){
						
						list = new ArrayList<>();
						
						modifiedControls.put( c, list );
					}
					
					list.add( relPosition );
				}
			}
		};
		
	boolean update_outstanding = false;
		
	private void
	updateHookedControls()
	{
		if ( dragPosition == null || update_outstanding ){
			
			return;
		}
		
		update_outstanding = true;
		
		Utils.execSWTThreadLater(0, ()->{
		
			update_outstanding = false;
			
			paintDragPosition = dragPosition;
					
			for ( Control c: hookedControls ){
				
				if ( c.isDisposed()){
					
					continue;
				}
				
				List<Integer> list = modifiedControls.remove(c);
				
				Point controlPosition = c.toControl( dragPosition );
				
				int relPosition = Integer.MIN_VALUE;
				
				if ( is_vertical ){
					
					if ( controlPosition.x >= 0 && controlPosition.x <=  c.getSize().x ){
												
						relPosition = controlPosition.x;	
					}
				}else{
				
					if ( controlPosition.y >= 0 && controlPosition.y <=  c.getSize().y ){
												
						relPosition = controlPosition.y;
					}
				}
				
				if ( relPosition != Integer.MIN_VALUE ){
					
					if ( list == null ){
						
						list = new ArrayList<>();
					}
					
					list.add( 0, relPosition );
				}
				
				if ( list != null ){
					
					for ( int pos: list ){

						if ( is_vertical ){
						
							c.redraw( pos, 0, pos, c.getBounds().height, false );
							
						}else{
							
							c.redraw(0, pos, c.getBounds().width, pos, false );
						}
					}
				}
			}
			
			modifiedControls.clear();
		});
	}
	
	private void
	hookControls(
		Control	control,
		boolean do_it )
	{
		if ( do_it ){
			
			LinkedList<Control[]> controls = new LinkedList<>();
			
			controls.add( new Control[]{ control.getShell() }); 
	
			while ( !controls.isEmpty()){
	
				Control[] kids = controls.removeFirst();
	
				for ( Control c: kids ){
					
					if ( c instanceof Composite){
					
						controls.add(((Composite)c).getChildren());
					}
	
					if ( c.isVisible() && !( c instanceof Shell )){
						
						c.addPaintListener( dragPainter );
						
						hookedControls.add( c );
						
						c.redraw();
					}
				}
			}
		}else{
			
			for ( Control c: hookedControls ){
				
				if ( !c.isDisposed()){
				
					c.removePaintListener(dragPainter);
				
					c.redraw();
				}
			}
			
			hookedControls.clear();
			
			modifiedControls.clear();
		}
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
