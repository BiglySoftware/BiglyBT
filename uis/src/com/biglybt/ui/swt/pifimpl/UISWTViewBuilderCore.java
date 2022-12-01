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
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.*;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.ui.model.PluginViewModel;

/**
 * Holds information to create a real {@link UISWTViewEventListener}.
 * <p/>
 * Also holds things like {@link PluginInterface} reference, initial datasource, title, id.
 */
public class UISWTViewBuilderCore
	implements UISWTViewBuilder
{
	// Can't store listener as a WeakReference, because some plugins create an
	// anonymous class with method scope, which will dispose leaving us no
	// way of creating the listener
	private UISWTViewEventListener listener;

	private final Map<UISWTView, Reference<UISWTViewEventListener>> mapViewToListener = new WeakHashMap<>();

	private Reference<PluginInterface> pi_ref;

	private Object datasource;

	private final String viewID;

	private Reference<Class<? extends UISWTViewEventListener>> cla_ref;

	private String initialTitle;

	private String preferredAfterID;

	// Most instances are lambdas that would be GC'd if we don't keep a reference
	private UISWTViewEventListenerInstantiator listenerInstantiator;
	
	private String parentEntryID;
	
	private boolean defaultVisibility = true;

	public UISWTViewBuilderCore(String viewID, PluginInterface pi) {
		this.viewID = viewID;
		pi_ref = pi == null ? null : new WeakReference<>(pi);
		if (pi != null) {
			pi.addListener(new PluginListener() {
				@Override
				public void initializationComplete() {
					
				}

				@Override
				public void closedownInitiated() {
					if (Utils.isDisplayDisposed()) {
						return;
					}
					GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
					if (gm != null && gm.isStopping()) {
						return;
					}
					dispose();
				}

				@Override
				public void closedownComplete() {

				}
			});
		}
	}

	/**
	 * Convenience constructor for creating a builder with a simple class.
	 * <br/>
	 * Same as {@link UISWTViewBuilderCore#UISWTViewBuilderCore(String, PluginInterface)}.{@link #setListenerClass(Class)}
	 */
	public UISWTViewBuilderCore(String viewID, PluginInterface pi,
			Class<? extends UISWTViewEventListener> cla) {
		this(viewID, pi);
		setListenerClass(cla);
	}

	/**
	 * @deprecated Use {@link UISWTViewBuilder#setListenerInstantiator(boolean, UISWTViewEventListenerInstantiator)}
	 */
	public UISWTViewBuilderCore(String viewID, PluginInterface pi,
			UISWTViewEventListener listener) {
		this(viewID, pi);

		this.listener = listener;
	}

	public UISWTViewBuilderCore cloneBuilder() {
		UISWTViewBuilderCore builder = new UISWTViewBuilderCore(viewID,
				getPluginInterface());
		builder.cla_ref = cla_ref;
		builder.listener = listener;
		builder.datasource = datasource;
		builder.initialTitle = initialTitle;
		builder.listenerInstantiator = listenerInstantiator;
		builder.preferredAfterID = preferredAfterID;
		builder.parentEntryID = parentEntryID;
		return builder;
	}

	public boolean isListenerOfClass(Class ofClass) {
		if (ofClass.isInstance(getListener())) {
			return true;
		}
		Class<? extends UISWTViewEventListener> cla = getListenerClass();
		return cla != null && cla.isAssignableFrom(ofClass);
	}

	public PluginInterface getPluginInterface() {
		return (pi_ref == null ? null : pi_ref.get());
	}

	public Object getInitialDataSource() {
		return datasource;
	}

	@Override
	public UISWTViewBuilderCore setInitialDatasource(Object datasource) {
		this.datasource = datasource;
		return this;
	}

	public UISWTViewEventListener createEventListener(UISWTView view) {
		boolean isCloneable = isListenerCloneable();
		UISWTViewEventListener listener = getListener();
		synchronized (mapViewToListener) {
			if (!isCloneable) {
				if (!mapViewToListener.isEmpty()) {
					UISWTView existingView = mapViewToListener.keySet().iterator().next();
					if (existingView != null && existingView != view
							&& !existingView.isContentDisposed()) {
						Debug.out("createEventListener already called for '" + viewID
								+ "' for a different view. Switch from pre-created listener to class reference to safely create multiple views.");
					}
				}

				if (listener != null) {
					mapViewToListener.put(view, new WeakReference<>(listener));
					return listener;
				}
			} else {
				// First creation gets the already instantiated listener, the
				// rest make a clone.  DeviceManagerUI.deviceView uses this
				if (listener != null && mapViewToListener.isEmpty()) {
					mapViewToListener.put(view, new WeakReference<>(listener));
					return listener;
				}
			}
		}

		// We reach here only if cloneable and need to clone

		try {
			Class<? extends UISWTViewEventListener> aClass = getListenerClass();
			UISWTViewEventListener eventListener = null;
			if (listenerInstantiator != null) {
				eventListener = listenerInstantiator.createNewInstance(this, view);
			} else if (aClass != null) {
				eventListener = aClass.newInstance();
			}

			if (eventListener != null) {
				synchronized (mapViewToListener) {
					mapViewToListener.put(view, new WeakReference<>(eventListener));
				}
			} else {
				Debug.out("Could not create eventLister for '" + viewID
						+ "'.  No way found to instantiate.");
			}

			return eventListener;
		} catch (Exception e) {
			Debug.out(e);
			return null;
		}
	}

	public String getViewID() {
		return viewID;
	}

	public UISWTViewEventListener getListener() {
		return listener;
	}

	/**
	 * Closes all views. Clears listener and datasource
	 */
	public void dispose() {
		datasource = null;

		disposeViews();

		// null listener in case something still has a reference to this builder,
		// and is thus keeping the plugin from fully unloading
		listener = null;
		cla_ref = null;
		listenerInstantiator = null;
	}

	public void disposeViews() {
		synchronized (mapViewToListener) {
			for (UISWTView view : mapViewToListener.keySet()) {
				if (view != null) {
					view.closeView();
				}
			}
			mapViewToListener.clear();
		}
	}

	public Class<? extends UISWTViewEventListener> getListenerClass() {
		return cla_ref == null ? null : cla_ref.get();
	}

	@Override
	@SuppressWarnings("UnusedReturnValue")
	public UISWTViewBuilderCore setListenerClass(
			Class<? extends UISWTViewEventListener> cla) {
		cla_ref = new WeakReference<>(cla);
		if (cla != null) {
			int modifiers = cla.getModifiers();
			if (Modifier.isPrivate(modifiers)) {
				Debug.out("Won't be able to create view " + viewID
						+ " because listener class '" + cla.getName() + " is private");
			}
		}
		return this;
	}

	public String getInitialTitle() {
		if (initialTitle == null) {
			String name = null;
			//noinspection DuplicateStringLiteralInspection
			String sResourceID = UISWTViewImpl.CFG_PREFIX + viewID + ".title";
			boolean bResourceExists = MessageText.keyExists(sResourceID);
			if (!bResourceExists) {
				PluginInterface pi = getPluginInterface();
				if (pi != null) {
					name = pi.getPluginconfig().getPluginStringParameter(sResourceID,
							null);
				}
			}

			if (bResourceExists) {
				name = MessageText.getString(sResourceID);
			} else if (name == null) {
				// try plain resource
				if (MessageText.keyExists(viewID)) {
					name = MessageText.getString(viewID);
				} else if (listener != null && (listener instanceof PluginViewModel)) {
					name = ((PluginViewModel) listener).getName();
				} else {
					name = viewID.replace('.', ' '); // support old plugins
				}
			}

			return name;
		}
		return initialTitle;
	}

	@Override
	public UISWTViewBuilderCore setInitialTitle(String initialTitle) {
		this.initialTitle = initialTitle;
		return this;
	}

	@Override
	public UISWTViewBuilderCore setListenerInstantiator(
			UISWTViewEventListenerInstantiator listenerInstantiator) {
		this.listenerInstantiator = listenerInstantiator;
		return this;
	}

	public UISWTViewEventListenerInstantiator
	getListenerInstantiator()
	{
		return( listenerInstantiator );
	}
	/**
	 * @param preferredAfterID
	 *    If you prefix the 'preferedAfterID' string with '~' then the operation 
	 *    will actually switch to 'preferedBeforeID'
	 * @return
	 */
	public UISWTViewBuilderCore setPreferredAfterID(String preferredAfterID) {
		this.preferredAfterID = preferredAfterID;
		return this;
	}

	public String getPreferredAfterID() {
		return preferredAfterID;
	}


	public String getParentEntryID() {
		return parentEntryID == null
				? MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS : parentEntryID;
	}

	@Override
	public UISWTViewBuilderCore setParentEntryID(String parentEntryID) {
		if (UISWTInstance.VIEW_MYTORRENTS.equals(parentEntryID)) {
			this.parentEntryID = SideBar.SIDEBAR_HEADER_TRANSFERS;
		} else if (UISWTInstance.VIEW_MAIN.equals(parentEntryID)) {
			this.parentEntryID = MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS;
		} else {
			this.parentEntryID = parentEntryID;
		}

		return this;
	}

	public boolean
	getDefaultVisibility()
	{
		return( defaultVisibility );
	}
	
	public void
	setDefaultVisibility(
		boolean	b )
	{
		defaultVisibility = b;
	}
	
	/**
	 * Can we create multiple views using this builder?
	 */
	public boolean isListenerCloneable() {

		if ( listenerInstantiator != null ){
			
			return( listenerInstantiator.supportsMultipleViews());
		}
		
		Class<? extends UISWTViewEventListener> cla = getListenerClass();
		
		if ( cla != null ){
			
			return( true );
		}
		return( false );
	}

	public String toDebugString() {
		StringBuilder sb = new StringBuilder("{").append(viewID);
		UISWTViewEventListener listener = getListener();
		if (listener != null) {
			sb.append("; l=").append(listener);
		}
		Class<? extends UISWTViewBuilderCore> cla = getClass();
		if (cla != null) {
			sb.append("; cla=").append(cla.getSimpleName());
		}
		PluginInterface pi = getPluginInterface();
		if (pi != null) {
			sb.append("; pi=").append(pi.getPluginID());
		}
		sb.append("; ").append(mapViewToListener.size()).append(" open");

		sb.append('}');
		return sb.toString();
	}
}
