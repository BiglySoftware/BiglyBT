/*
 * File    : UISWTViewImpl.java
 * Created : Oct 14, 2005
 * By      : TuxPaper
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.pifimpl;

import java.awt.*;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.DataSourceResolver;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.LightHashMap;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.pif.*;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.views.IViewAlwaysInitialize;
import com.biglybt.ui.swt.views.IViewRequiresPeriodicUpdates;
import com.biglybt.ui.swt.views.stats.StatsView;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.UIRuntimeException;
import com.biglybt.pif.ui.toolbar.UIToolBarEnablerBase;

/**
 * This class creates an view that triggers {@link UISWTViewEventListener}
 * appropriately
 *
 * @author TuxPaper
 *
 */
public class UISWTViewImpl
	implements UISWTViewCore, UIPluginViewToolBarListener
{
	public static final String CFG_PREFIX = "Views.plugins.";
	
	protected static final String SO_ID_ENTRY_WRAPPER = "mdi.content.item";

	protected static long uniqueNumber = 0;

	private boolean delayInitializeToFirstActivate = true;

	private static final boolean DEBUG_TRIGGERS = false;

	// TODO: not protected
	protected PluginUISWTSkinObject skinObject;

	private Object initialDatasource;

	private UISWTView parentView;

	protected SWTSkin skin;
	
	/* Always Core */
	protected Object datasource = DataSourceResolver.DEFAULT_DATASOURCE;

	private boolean useCoreDataSource = false;

	private UISWTViewEventListener eventListener;

	// This is the same as TabbedEntry.swtItem.getControl and something in SideBarEntry
	// TODO: not protected
	protected Composite composite;

	protected final String id;

	private String titleID;

	private int iControlType = UISWTView.CONTROLTYPE_SWT;

	private Boolean isShown = null;

	private Map<Object, Object> user_data;

	private boolean haveSentInitialize = false;

	private boolean created = false;

	/**
	 * Whether to destroy view on deactivation (view becomes hidden).
	 * <p/>
	 * Note that views can be rebuilt after being destroyed (ie. when shown again)
	 */
	private boolean destroyOnDeactivate = true;

	private Composite masterComposite;
	private final Set<UIPluginViewToolBarListener> setToolBarEnablers = new HashSet<>();
	private UISWTViewBuilderCore eventListenerBuilder;

	public UISWTViewImpl(String id) {
		this.id = id;
	}

	public UISWTViewImpl(UISWTViewBuilderCore builder, boolean doCreate)
			throws UISWTViewEventCancelledException {
		this(builder.getViewID());
		this.eventListenerBuilder = builder;
		UISWTViewEventListener eventListener = builder.createEventListener(this);
		if (eventListener == null) {
			throw new UISWTViewEventCancelledException(
					new NullPointerException("Could not create " + id));
		}
		this.initialDatasource = builder.getInitialDataSource();
		this.datasource = initialDatasource;
		setEventListener(eventListener, builder, doCreate);
	}

	public void setEventListener(UISWTViewEventListener _eventListener,
			UISWTViewBuilderCore builder, boolean doCreate)
			throws UISWTViewEventCancelledException {

		this.eventListener = _eventListener;
		this.eventListenerBuilder = builder;

		if (eventListener == null) {
			return;
		}

		if (eventListener instanceof IViewAlwaysInitialize) {
			delayInitializeToFirstActivate = false;
		}

		if (eventListener instanceof UISWTViewCoreEventListener) {
			setUseCoreDataSource(true);
		}
		
		if (builder != null && titleID == null) {
			String initialTitle = builder.getInitialTitle();
			if (initialTitle != null) {
				setTitle(initialTitle);
			}
		}
		
		if (builder != null) {
			initialDatasource = builder.getInitialDataSource();
		}

		// >> from UISWTViewImpl
		// we could pass the parentid as the data for the create call but unfortunately
		// there's a bunch of crap out there that assumes that data is the view object :(
		if (doCreate && !triggerBooleanEvent(UISWTViewEvent.TYPE_CREATE, this)) {
			throw new UISWTViewEventCancelledException();
		}
		// <<
	}
	
	public void create() throws UISWTViewEventCancelledException {
		if (created) {
			throw new UISWTViewEventCancelledException(id + " already created");
		}
		// >> from UISWTViewImpl
		// we could pass the parentid as the data for the create call but unfortunately
		// there's a bunch of crap out there that assumes that data is the view object :(
		if (!triggerBooleanEvent(UISWTViewEvent.TYPE_CREATE, this)) {
			throw new UISWTViewEventCancelledException();
		}
		// <<
	}
	
	/* (non-Javadoc)
	 * @see MdiEntrySWT#getEventListener()
	 */
	@Override
	public UISWTViewEventListener getEventListener() {
		return eventListener;
	}

	@Override
	public UISWTViewBuilderCore getEventListenerBuilder() {
		return eventListenerBuilder;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTView#getInitialDataSource()
	 */
	@Override
	public Object getInitialDataSource() {
		return initialDatasource;
	}

	public void setDatasource(Object datasource) {
		triggerEvent(UISWTViewEvent.TYPE_DATASOURCE_CHANGED, datasource);
	}

	@Override
	public Object getDataSource() {
		return PluginCoreUtils.convert(datasource, useCoreDataSource());
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#setParentView(com.biglybt.ui.swt.pif.UISWTView)
	 */
	@Override
	public void setParentView(UISWTView parentView) {
		this.parentView = parentView;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTView#getParentView()
	 */
	@Override
	public UISWTView getParentView() {
		return parentView;
	}
	
	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginView#getViewID()
	 */
	@Override
	public String getViewID() {
		return id;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginView#closeView()
	 */
	@Override
	public void closeView() {
		// Trigger will dispose of composites
		triggerEvent(UISWTViewEvent.TYPE_DESTROY, null);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTView#setControlType(int)
	 */
	@Override
	public void setControlType(int iControlType) {
		if (iControlType == CONTROLTYPE_AWT || iControlType == CONTROLTYPE_SWT
				|| iControlType == CONTROLTYPE_SKINOBJECT) {
			this.iControlType = iControlType;
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTView#getControlType()
	 */
	@Override
	public int getControlType() {
		return iControlType;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTView#triggerEvent(int, java.lang.Object)
	 */
	@Override
	public void triggerEvent(int eventType, Object data) {
		// Destroy event requires SWT Thread
		if (eventType == UISWTViewEvent.TYPE_DESTROY) {
			if (Utils.isDisplayDisposed()) {
				return;
			}
			if (Utils.runIfNotSWTThread(() -> triggerEvent(eventType, data))) {
				return;
			}
		}

		try {
			triggerBooleanEvent(eventType, data);
		} catch (Exception e) {
			// TODO: Better error
			Debug.out(e);
		}
	}
	
	@Override
	public boolean isContentDisposed(){
		return masterComposite == null || masterComposite.isDisposed();
	}

	private static String padRight(String s, int n) {
    return String.format("%1$-" + n + "s", s);
	}

	private boolean triggerBooleanEvent(int eventType, Object data) {
		if (DEBUG_TRIGGERS) {
			if (eventListener == null || eventType != UISWTViewEvent.TYPE_REFRESH) {
				System.out.println(System.currentTimeMillis() + "." + padRight(id, 20)
						+ "] " + "trigger "
						+ padRight(UISWTViewEvent.getEventDebug(eventType), 6)
						+ ", " + (eventListener == null ? "null" : "nonn") + ";data="
						+ DataSourceUtils.toDebugString(data)
						+ "/ds="
						+ DataSourceUtils.toDebugString(datasource)
						+ ";" + titleID + ";" + Debug.getCompressedStackTrace());
			}
		}

		if (eventType == UISWTViewEvent.TYPE_LANGUAGEUPDATE) {
			refreshTitle();
			Messages.updateLanguageForControl(getComposite());
		}

		if (	eventListener == null &&
				eventType != UISWTViewEvent.TYPE_DATASOURCE_CHANGED ){

			return false;
		}

		if (eventType == UISWTViewEvent.TYPE_INITIALIZE) {
			if (haveSentInitialize) {
				if (DEBUG_TRIGGERS) {
					System.out.println("  -> already haveSentInitialize");
				}
				return false;
			}
			if (!created) {
				// create will set DS changed
				triggerBooleanEvent(UISWTViewEvent.TYPE_CREATE, this);
			} else if (datasource != null) {
				triggerBooleanEvent(UISWTViewEvent.TYPE_DATASOURCE_CHANGED, datasource);
			}
			haveSentInitialize = true;
		}

		if (eventType == UISWTViewEvent.TYPE_CREATE) {
			created = true;
		}

		if (delayInitializeToFirstActivate
				&& eventType == UISWTViewEvent.TYPE_SHOWN
				&& !haveSentInitialize) {
			swt_triggerInitialize();
		}
		// prevent double fire of shown/hidden
		if (eventType == UISWTViewEvent.TYPE_SHOWN && isShown != null
				&& isShown) {
			if (DEBUG_TRIGGERS) {
				System.out.println("  -> already isShown");
			}
			return true;
		}
		if (eventType == UISWTViewEvent.TYPE_HIDDEN && isShown != null
				&& !isShown) {
			if (DEBUG_TRIGGERS) {
				System.out.println("  -> already !isShown");
			}
			return true;
		}

		if (eventType == UISWTViewEvent.TYPE_DATASOURCE_CHANGED) {
			Object newDataSource = PluginCoreUtils.convert(data, true);
			if (DataSourceUtils.areSame(datasource, newDataSource)) {
				if ( eventListener == null || !eventListener.informOfDuplicates(UISWTViewEvent.TYPE_DATASOURCE_CHANGED )){
					if (DEBUG_TRIGGERS) {
						System.out.println("  -> same DS, skip");
					}
					return true;
				}
			}
			datasource = newDataSource;
			data = PluginCoreUtils.convert(datasource, useCoreDataSource);

			if (skinObject instanceof SWTSkinObject) {
				((SWTSkinObject) skinObject).triggerListeners(SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, data);
			}

			if (eventListener == null) {
				return true;
			}

		} else if (eventType == UISWTViewEvent.TYPE_OBFUSCATE
				&& (eventListener instanceof ObfuscateImage)) {
			if (data instanceof Map) {
				((ObfuscateImage) eventListener).obfuscatedImage(
						(Image) MapUtils.getMapObject((Map<?, ?>) data, "image", null,
								Image.class));
			}
		} else if (eventType == UISWTViewEvent.TYPE_SHOWN) {
			isShown = true;
			if (!haveSentInitialize) {
				swt_triggerInitialize();
			}
		} else if (eventType == UISWTViewEvent.TYPE_HIDDEN) {
			isShown = false;
			if (destroyOnDeactivate) {
				triggerEvent(UISWTViewEvent.TYPE_DESTROY, null);
			}
		} else if (eventType == UISWTViewEvent.TYPE_DESTROY) {
			if (isShown != null && isShown) {
				triggerEvent(UISWTViewEvent.TYPE_HIDDEN, null);
			}
			// hidden may have destroyed us already
			if (!created && !haveSentInitialize && getComposite() == null) {
				return true;
			}
		}

		boolean result = false;
		try {
			result = eventListener.eventOccurred(
					new UISWTViewEventImpl(this, eventType, data));
		} catch (Throwable t) {
			Debug.out("ViewID=" + id + "; EventID="
					+ UISWTViewEvent.getEventDebug(eventType) + "; data="
					+ DataSourceUtils.toDebugString(data), t);
			//throw (new UIRuntimeException("UISWTView.triggerEvent:: ViewID="
			//		+ sViewID + "; EventID=" + eventType + "; data=" + data, t));
		}

		if (eventType == UISWTViewEvent.TYPE_DESTROY) {
			if (masterComposite != null && !masterComposite.isDisposed()) {
				Composite parent = masterComposite.getParent();
				Utils.disposeComposite(masterComposite);
				Utils.relayoutUp(parent);
			}
			setMasterComposite(null);
			composite = null;
			haveSentInitialize = false;
			isShown = false;
			created = false;
			// Datasource is still valid even after view is destroyed, because
			// sidebar entry or tab entry still exists
		} else if (eventType == UISWTViewEvent.TYPE_CREATE) {
			if (DEBUG_TRIGGERS) {
				System.out.println(" -> raw DS Change");
			}

			data = PluginCoreUtils.convert(datasource, useCoreDataSource);
			triggerEventRaw(UISWTViewEvent.TYPE_DATASOURCE_CHANGED, data);
			if (skinObject instanceof SWTSkinObject) {
				((SWTSkinObject) skinObject).triggerListeners(SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, data);
			}
		}

		return result;
	}

	protected boolean triggerEventRaw(int eventType, Object data) {
		if (eventListener == null) {
			System.err.println(
					"null eventListener for " + UISWTViewEvent.getEventDebug(eventType) + " " + Debug.getCompressedStackTrace());
			return eventType == UISWTViewEvent.TYPE_CLOSE ? true : false;
		}
		try {
			return eventListener.eventOccurred(
					new UISWTViewEventImpl(this, eventType, data));
		} catch (Throwable t) {
			throw (new UIRuntimeException(
					"UISWTView.triggerEvent:: ViewID=" + id + "; EventID=" + eventType
							+ "; data=" + DataSourceUtils.toDebugString(data),
					t));
		}
	}

	@Override
	public void setTitle(String title) {
		setTitleSupport(title);
	}
	
	protected boolean setTitleSupport(String title) {
		if (title == null) {
			return( false );
		}

		String newTitleID;
		if (title.startsWith("{") && title.endsWith("}") && title.length() > 2) {
			newTitleID = title.substring(1, title.length() - 1);
		} else if (title.contains(".") && MessageText.keyExists(title)) {
			// hack which might not be needed anymore
			newTitleID = title;
		} else {
			newTitleID = "!" + title + "!";
		}
		return setTitleIDSupport(newTitleID);
	}

	// Can't change signature to return boolean because MdiEntry shares same
	// method, and it's used by plugins..
	protected void setTitleID(String titleID) {
		setTitleIDSupport(titleID);
	}

	protected boolean setTitleIDSupport(String titleID) {
		if (titleID == null) {
			return false;
		}
		if (titleID.equals(this.titleID)) {
			return false;
		}
		this.titleID = titleID;
		return true;
	}

	protected void
	refreshTitle()
	{
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTView#getPluginInterface()
	 */
	@Override
	public PluginInterface getPluginInterface() {
		return eventListenerBuilder == null ? null
				: eventListenerBuilder.getPluginInterface();
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#getComposite()
	 */
	@Override
	public Composite getComposite() {
		return composite;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#getTitleID()
	 */
	// XXX Might not be needed once StatsView, SBC_TDV, and TVSWT_TC are converted
	@Override
	public String getTitleID() {
		return titleID;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#getFullTitle()
	 */
	@Override
	public String getFullTitle() {
		return MessageText.getString(getTitleID());
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#initialize(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void initialize(Composite parent) {
		setMasterComposite(parent);
		if (iControlType == UISWTView.CONTROLTYPE_SWT) {
			GridData gridData;
			Layout parentLayout = parent.getLayout();
			if (parentLayout instanceof FormLayout) {
				composite = parent;
			} else {
				composite = new Composite(parent, SWT.NONE);
				GridLayout layout = new GridLayout(1, false);
				layout.marginHeight = 0;
				layout.marginWidth = 0;
				composite.setLayout(layout);
				gridData = new GridData(GridData.FILL_BOTH);
				composite.setLayoutData(gridData);
			}

			Listener showListener = event -> {
				if (composite == null || composite.isDisposed()) {
					return;
				}
				Composite parent1 = composite.getParent();
				if (parent1 instanceof CTabFolder) {
					CTabFolder tabFolder = (CTabFolder) parent1;
					CTabItem selection = tabFolder.getSelection();
					if (selection != null && selection.getControl() != composite) {
						return;
					}
				} else if (parent1 instanceof TabFolder) {
					TabFolder tabFolder = (TabFolder) parent1;
					TabItem[] selectedControl = tabFolder.getSelection();
					if (selectedControl != null && selectedControl.length == 1
							&& selectedControl[0].getControl() != composite) {
						return;
					}
				}
				// Delay trigger of TYPE_SHOWN a bit, so that parent is visible
				Utils.execSWTThreadLater(0,
						() -> triggerEvent(UISWTViewEvent.TYPE_SHOWN, null));
			};

			composite.addListener(SWT.Show, showListener);
			if (parent != composite) {
				parent.addListener(SWT.Show, showListener);
			}
			if (composite.isVisible()) {
				boolean visible = true;
				if (parent instanceof CTabFolder || (parent instanceof TabFolder)) {
					// can't be visible yet.. we just created it and
					// it hasn't been assigned to TabFolder yet
					visible = false;
				}
				if (visible) {
					triggerEvent(UISWTViewEvent.TYPE_SHOWN, null);
				}
			}
			if (delayInitializeToFirstActivate) {
				return;
			}
			swt_triggerInitialize();
		} else if (iControlType == UISWTView.CONTROLTYPE_AWT) {
			composite = new Composite(parent, SWT.EMBEDDED);
			FillLayout layout = new FillLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			composite.setLayout(layout);
			GridData gridData = new GridData(GridData.FILL_BOTH);
			composite.setLayoutData(gridData);

			Frame f = SWT_AWT.new_Frame(composite);

			Panel pan = new Panel();

			f.add(pan);

			triggerEvent(UISWTViewEvent.TYPE_INITIALIZE, pan);
		} else if (iControlType == UISWTViewCore.CONTROLTYPE_SKINOBJECT) {
			triggerEvent(UISWTViewEvent.TYPE_INITIALIZE, getPluginSkinObject());
		}
	}

	private void swt_triggerInitialize() {
		if (haveSentInitialize) {
			return;
		}

		if (!created) {
			triggerBooleanEvent(UISWTViewEvent.TYPE_CREATE, this);
		}

		if ( composite != null ){
			composite.setRedraw(false);
			composite.setLayoutDeferred(true);
			triggerEvent(UISWTViewEvent.TYPE_INITIALIZE, composite);

			if (composite.getLayout() instanceof GridLayout) {
				// Force children to have GridData layoutdata.
				Control[] children = composite.getChildren();
				for (int i = 0; i < children.length; i++) {
					Control control = children[i];
					Object layoutData = control.getLayoutData();
					if (layoutData == null || !(layoutData instanceof GridData)) {
						if (layoutData != null) {
							Logger.log(
									new LogEvent(LogIDs.PLUGIN, LogEvent.LT_WARNING,
											"Plugin View '" + id + "' tried to setLayoutData of "
													+ control + " to a "
													+ layoutData.getClass().getName()));
						}

						GridData gridData;
						if (children.length == 1) {
							gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
						} else {
							gridData = new GridData();
						}

						control.setLayoutData(gridData);
					}
				}
			}
			
			composite.layout();
			composite.setLayoutDeferred(false);
			
				// issues with scroll bars not sizing correctly in Library view - is fixed by doign this...
			
			Utils.relayoutUp( composite );
			
			composite.setRedraw(true);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#useCoreDataSource()
	 */
	@Override
	public boolean useCoreDataSource() {
		return useCoreDataSource;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#setUseCoreDataSource(boolean)
	 */
	@Override
	public void setUseCoreDataSource(boolean useCoreDataSource) {
		if (this.useCoreDataSource == useCoreDataSource) {
			return;
		}

		this.useCoreDataSource = useCoreDataSource;
		if (datasource != null) {
			setDatasource(datasource);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#getSkinObject()
	 */
	@Override
	public PluginUISWTSkinObject getPluginSkinObject() {
		return skinObject;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#setSkinObject(com.biglybt.ui.swt.pif.PluginUISWTSkinObject, org.eclipse.swt.widgets.Composite)
	 */
	// TODO: Combine this with the other setSkinObject..
	@Override
	public void setPluginSkinObject(PluginUISWTSkinObject so) {
		this.skinObject = so;
	}

	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		UIToolBarEnablerBase[] toolbarEnablers = getToolbarEnablers();
		for (UIToolBarEnablerBase tbEnablerBase : toolbarEnablers) {
			if (tbEnablerBase instanceof UIPluginViewToolBarListener) {
				UIPluginViewToolBarListener tbEnabler = (UIPluginViewToolBarListener) tbEnablerBase;
				if (tbEnabler.toolBarItemActivated(item, activationType, datasource)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		UIToolBarEnablerBase[] toolbarEnablers = getToolbarEnablers();
		for (UIToolBarEnablerBase tbEnablerBase : toolbarEnablers) {
			if (tbEnablerBase instanceof UIPluginViewToolBarListener) {
				UIPluginViewToolBarListener tbEnabler = (UIPluginViewToolBarListener) tbEnablerBase;
				tbEnabler.refreshToolBarItems(list);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginView#setToolBarListener(com.biglybt.pif.ui.UIPluginViewToolBarListener)
	 */
	@Override
	public void setToolBarListener(UIPluginViewToolBarListener l) {
		addToolbarEnabler(l);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginView#getToolBarListener()
	 */
	@Override
	public UIPluginViewToolBarListener getToolBarListener() {
		return setToolBarEnablers.size() == 0 ? null : setToolBarEnablers.iterator().next();
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getToolbarEnablers()
	 */
	public UIToolBarEnablerBase[] getToolbarEnablers() {
		// XXX What if eventListener is of UIPluginViewToolBarListener (as per UISWTViewImpl's check)
		return setToolBarEnablers.toArray(new UIToolBarEnablerBase[0]);
	}

	public boolean hasToolbarEnableers() {
		return setToolBarEnablers.size() > 0;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#addToolbarEnabler(com.biglybt.pif.ui.toolbar.UIToolBarEnablerBase)
	 */
	public void addToolbarEnabler(UIToolBarEnablerBase enabler) {
		if (setToolBarEnablers.contains(enabler)) {
			return;
		}
		setToolBarEnablers.add((UIPluginViewToolBarListener) enabler);
		setToolbarVisibility(setToolBarEnablers.size() > 0);
	}

	/* (non-Javadoc)
	 * @see MdiEntry#removeToolbarEnabler(com.biglybt.pif.ui.toolbar.UIToolBarEnablerBase)
	 */
	public void removeToolbarEnabler(UIToolBarEnablerBase enabler) {
		setToolBarEnablers.remove(enabler);
		setToolbarVisibility(setToolBarEnablers.size() > 0);
	}

	protected void setToolbarVisibility(boolean visible) {
	}

	@Override
	public void setUserData(Object key, Object data) {
		synchronized (this) {

			if (user_data == null) {

				user_data = new LightHashMap<>();
			}

			if (data == null) {

				user_data.remove(key);

				if (user_data.isEmpty()) {

					user_data = null;
				}
			} else {

				user_data.put(key, data);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewCore#getUserData(java.lang.Object)
	 */
	@Override
	public Object getUserData(Object key) {
		synchronized (this) {

			if (user_data == null) {

				return (null);
			}

			return (user_data.get(key));
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTView#setDestroyOnDeactivate(boolean)
	 */
	@Override
	public void setDestroyOnDeactivate(boolean b) {
		destroyOnDeactivate = b;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTView#isDestroyOnDeactivate()
	 */
	@Override
	public boolean isDestroyOnDeactivate() {
		return destroyOnDeactivate;
	}

	public boolean isDelayInitializeToFirstActivate() {
		return delayInitializeToFirstActivate;
	}

	public void setDelayInitializeToFirstActivate(
			boolean delayInitializeToFirstActivate) {
		this.delayInitializeToFirstActivate = delayInitializeToFirstActivate;
	}

	protected void setMasterComposite(Composite masterComposite) {
		this.masterComposite = masterComposite;
	}

	@Override
	public SWTSkinObjectContainer buildStandAlone(
			SWTSkinObjectContainer soParent) {
		SWTSkin skin = this.skin == null ? soParent.getSkin() : this.skin;
		return buildStandAlone(soParent, skin, id, getDataSource(),
				getControlType(), getEventListenerBuilder());
	}

	public static SWTSkinObjectContainer
	buildStandAlone(
		SWTSkinObjectContainer		soParent,
		SWTSkin skin,
		String						id,
		Object						datasource,
		int							controlType,
		UISWTViewBuilderCore original_builder )
	{
		Composite parent = soParent.getComposite();
		if (parent == null) {
			return null;
		}

		if (original_builder != null && original_builder.isListenerCloneable()){

			final UISWTViewImpl view;
			UISWTViewEventListener event_listener;

			try{
				UISWTViewBuilderCore builder;
				if (datasource == original_builder.getInitialDataSource()) {
					builder = original_builder;
				} else {
					// datasource has changed since creation, clone the builder and
					// make it's initial datasource the new one
					builder = original_builder.cloneBuilder();
					builder.setInitialDatasource(datasource);
				}
				view = new UISWTViewImpl(builder, false );
				event_listener = view.getEventListener();

			}catch( Throwable e ){
				// shouldn't happen as we aren't asking for 'create' to occur which means it can't fail
				Debug.out( e );
				return null;
			}

			try {
				SWTSkinObjectContainer soContents = (SWTSkinObjectContainer) skin.createSkinObject(
					"MdiIView." + uniqueNumber++, SO_ID_ENTRY_WRAPPER,
					soParent );

				final Composite viewComposite = soContents.getComposite();
				boolean doGridLayout = true;
				if ( controlType == CONTROLTYPE_SKINOBJECT) {
					doGridLayout = false;
				}
				//					viewComposite.setBackground(parent.getDisplay().getSystemColor(
				//							SWT.COLOR_WIDGET_BACKGROUND));
				//					viewComposite.setForeground(parent.getDisplay().getSystemColor(
				//							SWT.COLOR_WIDGET_FOREGROUND));
				if (doGridLayout) {
					GridLayout gridLayout = new GridLayout();
					gridLayout.horizontalSpacing = gridLayout.verticalSpacing = gridLayout.marginHeight = gridLayout.marginWidth = 0;
					viewComposite.setLayout(gridLayout);
					viewComposite.setLayoutData(Utils.getFilledFormData());
				}

				view.setPluginSkinObject(soContents);
				view.initialize(viewComposite);

				// without this some views get messed up layouts (chat view for example)

				viewComposite.setData( Utils.RELAYOUT_UP_STOP_HERE, true );
				viewComposite.setData( "UISWTView", view  );

				soContents.addListener((skinObject, eventType, params) -> {
					if ( eventType == SWTSkinObjectListener.EVENT_OBFUSCATE ){
						Map data = new HashMap();
						data.put( "image", (Image)params );
						data.put( "obfuscateTitle",false );

						view.triggerEvent(UISWTViewEvent.TYPE_OBFUSCATE, data);
					}
					return null;
				});

				Composite iviewComposite = view.getComposite();
				// force layout data of IView's composite to GridData, since we set
				// the parent to GridLayout (most plugins use grid, so we stick with
				// that instead of form)
				if (doGridLayout) {
					Object existingLayoutData = iviewComposite.getLayoutData();
					Object existingParentLayoutData = iviewComposite.getParent().getLayoutData();
					if (existingLayoutData == null
						|| !(existingLayoutData instanceof GridData)
						&& (existingParentLayoutData instanceof GridLayout)) {
						GridData gridData = new GridData(GridData.FILL_BOTH);
						iviewComposite.setLayoutData(gridData);
					}
				}

				parent.layout(true, true);

				// UISWTViewImpl doesn't have a refresh trigger, so we need to make one
				// Note: BaseMdiEntry refreshes one UIUpdater via BaseMDI
				final UIUpdater updater = UIUpdaterSWT.getInstance();
				if (updater != null) {
					updater.addUpdater(new UIUpdatable() {
						@Override
						public void updateUI() {
							if (viewComposite.isDisposed()) {
								updater.removeUpdater(this);
							} else {
								view.triggerEvent(UISWTViewEvent.TYPE_REFRESH, null);
							}
						}

						@Override
						public String getUpdateUIName() {
							return ("Popout-" + view.getViewID());
						}
					});

					if ( event_listener instanceof IViewRequiresPeriodicUpdates){

						updater.addPeriodicUpdater(
							new UIUpdatable() {

								@Override
								public void updateUI() {
									if (viewComposite.isDisposed()) {
										updater.removePeriodicUpdater(this);
									} else {
										// Need to test, but this line seems better:
										//view.triggerEvent(StatsView.EVENT_PERIODIC_UPDATE, null);
										event_listener.eventOccurred(new UISWTViewEventImpl(view, StatsView.EVENT_PERIODIC_UPDATE, null));
									}
								}

								@Override
								public String getUpdateUIName() {
									return ("Popout-" + view.getViewID());
								}
							});
					}
				}

				soContents.setVisible( true );

				// Normally, an MDIEntry can dispose of it's content composite, and
				// still have the entry available in the MDI.  For standalone UISWTViewImpl,
				// there is no MDI, so when the main composite goes, so should the
				// view.
				iviewComposite.addDisposeListener(arg0 -> view.closeView());

				return( soContents );

			} catch (Throwable e) {

				Debug.out(e);
			}
		} else {
			Debug.out("Can't buildStandAlone '" + id
				+ "'. Invalid skinref or builder (" + original_builder + ")");
		}

		return( null );
	}

	@Override
	public boolean 
	canBuildStandAlone() 
	{
		if (eventListenerBuilder != null
				&& eventListenerBuilder.isListenerCloneable()) {
			return true;
		}

		return( false );
	}
}
