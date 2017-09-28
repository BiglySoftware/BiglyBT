/*
 * Created on Oct 19, 2010
 * Created by Paul Gardner
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */


package com.biglybt.ui.swt.pifimpl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pif.UISWTInstance.UISWTViewEventListenerWrapper;

/**
 * Holds information to create a real {@link UISWTViewEventListener} from
 * its {@link Class}
 * <p>
 * Holds {@link PluginInterface} reference
 */
public class
UISWTViewEventListenerHolder
	implements UISWTViewEventListenerWrapper
{
	private final UISWTViewEventListener		listener;
	private final Reference<PluginInterface>	pi;
	private Object datasource;
	private final String viewID;

	// when there is no #listener, we create a new #cla for each TYPE_CREATE event
	Map<UISWTView, UISWTViewEventListener> mapSWTViewToEventListener;
	private Class<? extends UISWTViewEventListener> cla;

	public
	UISWTViewEventListenerHolder(
		String viewID,
		Class<? extends UISWTViewEventListener> _cla,
		Object datasource,
		PluginInterface					_pi )
	{
		this(viewID, (UISWTViewEventListener) null, _pi);
		cla = _cla;
		this.datasource = datasource;
	}


	protected
	UISWTViewEventListenerHolder(
		String viewID,
		UISWTViewEventListener			_listener,
		PluginInterface					_pi )
	{
		this.viewID = viewID;
		listener	= _listener;

		if ( _pi == null ){

			if ( listener instanceof BasicPluginViewImpl ){

				_pi = ((BasicPluginViewImpl)listener).getModel().getPluginInterface();
			}
		}

		if ( _pi != null ){

			pi = new WeakReference<>( _pi );

		}else{

			pi = null;
		}
	}

	public boolean
	isLogView()
	{
		return( listener instanceof BasicPluginViewImpl );
	}

	public PluginInterface
	getPluginInterface()
	{
		return( pi==null?null:pi.get());
	}
	
	public Object
	getInitialDataSource()
	{
		return( datasource );
	}

	@Override
	public boolean
	eventOccurred(
		UISWTViewEvent event )
	{
		if (listener == null) {
			UISWTViewEventListener eventListener = null;

			synchronized( this ){
				int type = event.getType();
				if (type == UISWTViewEvent.TYPE_CREATE) {
					try {
						eventListener = cla.newInstance();
						UISWTView view = event.getView();
						if (eventListener instanceof UISWTViewCoreEventListener) {
							if (view instanceof UISWTViewCore) {
								UISWTViewCore coreView = (UISWTViewCore) view;
								coreView.setUseCoreDataSource(true);
							}
						}
						if (mapSWTViewToEventListener == null) {
							mapSWTViewToEventListener = new HashMap<>();
						}
						mapSWTViewToEventListener.put(view, eventListener);

						if (datasource != null) {
							if (view instanceof UISWTViewImpl) {
								UISWTViewImpl swtView = (UISWTViewImpl) view;
								swtView.triggerEventRaw(
										UISWTViewEvent.TYPE_DATASOURCE_CHANGED,
										PluginCoreUtils.convert(datasource,
												((UISWTViewImpl) view).useCoreDataSource()));
							} else {
								view.triggerEvent(UISWTViewEvent.TYPE_DATASOURCE_CHANGED,
										datasource);
							}
						}
					} catch (Exception e) {
						Debug.out(e);
						return false;
					}
				} else if (type == UISWTViewEvent.TYPE_DATASOURCE_CHANGED) {
					datasource = event.getData();
				}

				if (mapSWTViewToEventListener != null) {
					if (type == UISWTViewEvent.TYPE_DESTROY) {
						eventListener = mapSWTViewToEventListener.remove(event.getView());
					} else {
						eventListener = mapSWTViewToEventListener.get(event.getView());
					}
				}
			}

			if (eventListener == null) {
				return false;
			}

			return eventListener.eventOccurred(event);
		} else if (event.getType() == UISWTViewEvent.TYPE_CREATE && (listener instanceof UISWTViewCoreEventListener)){
			if (event.getView() instanceof UISWTViewCore) {
				UISWTViewCore coreView = (UISWTViewCore) event.getView();
				coreView.setUseCoreDataSource(true);
			}
		}

		return( listener.eventOccurred( event ));
	}

	public UISWTViewEventListener getDelegatedEventListener(UISWTView view) {
		if (listener != null) {
			return listener;
		}
		synchronized( this ){
			if (mapSWTViewToEventListener == null) {
				return null;
			}
			return mapSWTViewToEventListener.get(view);
		}
	}


	@Override
	public String getViewID() {
		return viewID;
	}

	public int getNumViews() {
		return mapSWTViewToEventListener == null ? 0 : mapSWTViewToEventListener.size();
	}

	public UISWTViewEventListener getListener() {
		return listener;
	}

	public void dispose() {
		mapSWTViewToEventListener = null;
	}

	public Class getCla() {
		return cla;
	}
}
