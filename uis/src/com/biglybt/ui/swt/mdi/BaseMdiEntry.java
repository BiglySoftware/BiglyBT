/*
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

package com.biglybt.ui.swt.mdi;

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.ConfigurationParameterNotFoundException;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.ui.UIManagerImpl;
import com.biglybt.plugin.net.buddy.swt.FriendsView;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;
import com.biglybt.ui.common.viewtitleinfo.*;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.pif.*;
import com.biglybt.ui.swt.pif.UISWTViewBuilder.UISWTViewEventListenerInstantiator;
import com.biglybt.ui.swt.pifimpl.*;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.views.ViewManagerSWT;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.sidebar.SideBarEntrySWT;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.ui.toolbar.UIToolBarEnablerBase;

public abstract class BaseMdiEntry
	extends UISWTViewImpl
	implements MdiEntrySWT, ViewTitleInfoListener, AEDiagnosticsEvidenceGenerator,
		ObfuscateImage
{
	protected static final String SO_ID_ENTRY_WRAPPER = "mdi.content.item";

	protected static long uniqueNumber = 0;

	protected final BaseMDI mdi;

	private String skinRef;

	private List<MdiCloseListener> listCloseListeners = null;

	private List<MdiChildCloseListener> listChildCloseListeners = null;

	private List<MdiEntryOpenListener> listOpenListeners = null;

	private List<MdiEntryDropListener> listDropListeners = null;

	private List<MdiEntryDatasourceListener> listDatasourceListeners = null;

	private List<MdiSWTMenuHackListener> listMenuHackListners;

	private List<MdiAcceleratorListener> listAcceleratorListeners = null;
	
	protected ViewTitleInfo viewTitleInfo;

	/** Parent MDIEntry.  Doesn't mean that this view is embedded inside the parentID */
	private String parentEntryID;

	private boolean closeable;

	private Boolean isExpanded = null;

	private String imageLeftID;

	private Image imageLeft;

	private boolean collapseDisabled = false;

	private SWTSkinObject soMaster;

	private String preferredAfterID;

	private boolean hasBeenOpened;

	@SuppressWarnings("unchecked")
	private List<MdiEntryVitalityImageSWT> listVitalityImages = Collections.EMPTY_LIST;
	
	public BaseMdiEntry(BaseMDI mdi, String id) {

		super(id);
		setDestroyOnDeactivate(true);

		this.mdi = mdi;
		AEDiagnostics.addWeakEvidenceGenerator(this);

		setDefaultExpanded(false);
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public MdiEntryVitalityImageSWT addVitalityImage(String imageID) {
		synchronized (this) {
			MdiEntryVitalityImageSWT vitalityImage = new MdiEntryVitalityImageSWT(this,
				imageID);
			int index = 0;
			if (listVitalityImages == Collections.EMPTY_LIST) {
				listVitalityImages = new ArrayList<>(1);
			} else {
				for (int i = listVitalityImages.size() - 1; i >= 0; i--) {
					if (!listVitalityImages.get(i).getAlwaysLast()) {
						index = i + 1;
						break;
					}
				}
			}
			listVitalityImages.add(index, vitalityImage);
			return vitalityImage;
		}
	}

	@Override
	public List<MdiEntryVitalityImageSWT> getVitalityImages() {
		synchronized (this) {
			return new ArrayList<>(listVitalityImages);
		}
	}

	@Override
	public boolean close(boolean forceClose) {
		closeView();
		return true;
	}

	@Override
	public void closeView() {
		closeView( false );
	}
	
	public void closeView( boolean userInitiated ) {
		// Some plugins force close the view on TYPE_DESTROY 
		try {
			boolean shuttingDown = Utils.isDisplayDisposed();
			if (!shuttingDown) {
				GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
				shuttingDown = gm != null && gm.isStopping();
			}
			if (shuttingDown) {
				// Send view one last hidden event in case they want to save anything
				triggerEvent(UISWTViewEvent.TYPE_HIDDEN, null);
				return;
			}
		} catch (Throwable ignore) {
		}

		if (mdi != null) {
			MdiEntry entry = mdi.getEntry(id);
			
			// it is possible the entry has been replaced in the meantime, if it
			// has the tidyup for the old one will already have been done and we 
			// don't want to go and remove the new replacement that is probably queued
			// for addition in the SWT tubes
			
			if (entry == this ) {
				mdi.closeEntryByID(id,userInitiated);
				return;
			}
		}

		destroyEntry( userInitiated );
	}
	
	protected void destroyEntry( boolean userInitiated ) {
		try{
			triggerCloseListeners( userInitiated );
	
			try {
				setEventListener(null, null, false);
			} catch (UISWTViewEventCancelledException ignore) {
			}
	
			SWTSkinObject so = getSkinObject();
			if (so != null ){
				
				SWTSkinObject master = getSkinObjectMaster();
				
				if ( master != null && master != so ){
					master.dispose();
				}
				setSkinObjectMaster(null);
				so.getSkin().removeSkinObject(so);
			}
	
			// Fires off destroy event and destroys SWT widgets
			super.closeView();
			
		}finally{
			destroyEntryAlways();
		}
	}

	protected void destroyEntryAlways()
	{
			// we gotta always do this regardless - might be called > once
		ViewTitleInfoManager.removeListener(this);		
	}
	
	public Object getDatasourceCore() {
		return datasource;
	}

	@Override
	public Object getExportableDatasource() {
		if (viewTitleInfo != null) {
			return viewTitleInfo.getTitleInfoProperty(
					ViewTitleInfo2.TITLE_EXPORTABLE_DATASOURCE);
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getDatasource()
	 */
	@Override
	public Object getDatasource() {
		return PluginCoreUtils.convert(datasource, false);
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getMDI()
	 */
	@Override
	public BaseMDI getMDI() {
		return mdi;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getParentID()
	 */
	@Override
	public String getParentID() {
		return parentEntryID;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#setParentID(java.lang.String)
	 */
	@Override
	public void setParentEntryID(String parentEntryID) {
		if (parentEntryID == null || "Tools".equals(parentEntryID)) {
			parentEntryID = MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS;
		}
		if (parentEntryID.equals(getViewID())) {
			Debug.out("Setting Parent to same ID as child! " + parentEntryID);
			return;
		}
		this.parentEntryID = parentEntryID;
		// ensure parent gets created if it isn't there already
		if (mdi != null) {
			mdi.loadEntryByID(this.parentEntryID, false);
		}
	}

	/* (non-Javadoc)
	 * @see MdiEntry#isCloseable()
	 */
	@Override
	public boolean isCloseable() {
		return closeable;
	}

	// @see MdiEntry#addListeners(java.lang.Object)
	@Override
	public void addListeners(Object objectWithListeners) {
		if (objectWithListeners instanceof MdiChildCloseListener) {
			addListener((MdiChildCloseListener) objectWithListeners);
		}
		if (objectWithListeners instanceof MdiCloseListener) {
			addListener((MdiCloseListener) objectWithListeners);
		}
		if (objectWithListeners instanceof MdiEntryDatasourceListener) {
			addListener((MdiEntryDatasourceListener) objectWithListeners);
		}
		if (objectWithListeners instanceof MdiEntryDropListener) {
			addListener((MdiEntryDropListener) objectWithListeners);
		}
		if (objectWithListeners instanceof MdiEntryOpenListener) {
			addListener((MdiEntryOpenListener) objectWithListeners);
		}

		if (objectWithListeners instanceof MdiSWTMenuHackListener) {
			addListener((MdiSWTMenuHackListener) objectWithListeners);
		}
	}

	@Override
	public void addListener(MdiCloseListener l) {
		synchronized (this) {
			if (listCloseListeners == null) {
				listCloseListeners = new ArrayList<>(1);
			}
			listCloseListeners.add(l);
		}
	}

	@Override
	public void removeListener(MdiCloseListener l) {
		synchronized (this) {
			if (listCloseListeners != null) {
				listCloseListeners.remove(l);
			}
		}
	}

	public void triggerCloseListeners( boolean userInitiated ) {
		MdiCloseListener[] list = {};
		synchronized (this) {
			if (listCloseListeners != null) {
				list = listCloseListeners.toArray(new MdiCloseListener[0]);
			}
		}		
		
		for (MdiCloseListener l : list) {
			try {
				l.mdiEntryClosed(this, userInitiated );
			} catch (Exception e) {
				Debug.out(e);
			}
		}
		synchronized (this) {
			if (listCloseListeners != null) {
				listCloseListeners.clear();
			}
		}

		if (parentEntryID != null && mdi != null) {
			MdiEntrySWT parentEntry = mdi.getEntry(parentEntryID);
			if (parentEntry instanceof BaseMdiEntry) {
				((BaseMdiEntry) parentEntry).triggerChildCloseListeners(this, userInitiated);
			}
		}

		triggerEvent(UISWTViewEvent.TYPE_DESTROY, null);
	}

	@Override
	public void addListener(MdiChildCloseListener l) {
		synchronized (this) {
			if (listChildCloseListeners == null) {
				listChildCloseListeners = new ArrayList<>(1);
			}
			listChildCloseListeners.add(l);
		}
	}

	@Override
	public void removeListener(MdiChildCloseListener l) {
		synchronized (this) {
			if (listChildCloseListeners != null) {
				listChildCloseListeners.remove(l);
			}
		}
	}

	public void triggerChildCloseListeners(MdiEntry child, boolean user) {
		Object[] list;
		synchronized (this) {
			if (listChildCloseListeners == null) {
				return;
			}
			list = listChildCloseListeners.toArray();
		}
		for (int i = 0; i < list.length; i++) {
			MdiChildCloseListener l = (MdiChildCloseListener) list[i];
			try {
				l.mdiChildEntryClosed(this, child, user);
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}

	@Override
	public void addListener(MdiEntryOpenListener l) {
		synchronized (this) {
			if (listOpenListeners == null) {
				listOpenListeners = new ArrayList<>(1);
			}
			listOpenListeners.add(l);
		}

		if (hasBeenOpened) {
			l.mdiEntryOpen(this);
		}
	}

	@Override
	public void removeListener(MdiEntryOpenListener l) {
		synchronized (this) {
			if (listOpenListeners != null) {
				listOpenListeners.remove(l);
			}
		}
	}

	public void triggerOpenListeners() {
		Object[] list;
		hasBeenOpened = true;
		synchronized (this) {
			if (listOpenListeners == null) {
				return;
			}

			list = listOpenListeners.toArray();
		}
		for (int i = 0; i < list.length; i++) {
			MdiEntryOpenListener l = (MdiEntryOpenListener) list[i];
			try {
				l.mdiEntryOpen(this);
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}


	@Override
	public void addListener(MdiEntryDatasourceListener l) {
		synchronized (this) {
			if (listDatasourceListeners == null) {
				listDatasourceListeners = new ArrayList<>(1);
			}
			listDatasourceListeners.add(l);
		}

		l.mdiEntryDatasourceChanged(this);
	}

	@Override
	public void removeListener(MdiEntryDatasourceListener l) {
		synchronized (this) {
			if (listDatasourceListeners != null) {
				listDatasourceListeners.remove(l);
			}
		}
	}

	public void triggerDatasourceListeners() {
		Object[] list;
		synchronized (this) {
			if (listDatasourceListeners == null) {
				return;
			}

			list = listDatasourceListeners.toArray();
		}
		for (int i = 0; i < list.length; i++) {
			MdiEntryDatasourceListener l = (MdiEntryDatasourceListener) list[i];
			try {
				l.mdiEntryDatasourceChanged(this);
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}

	@Override
	public void addListener(MdiEntryDropListener l) {
		synchronized (this) {
			if (listDropListeners == null) {
				listDropListeners = new ArrayList<>(1);
			}
			listDropListeners.add(l);
		}
	}

	@Override
	public void removeListener(MdiEntryDropListener l) {
		synchronized (this) {
			if (listDropListeners != null) {
				listDropListeners.remove(l);
			}
		}
	}

	public boolean hasDropListeners() {
		synchronized (this) {
			return listDropListeners != null && listDropListeners.size() > 0;
		}
	}

	/**
	 *
	 * @param o
	 * @return true: handled; false: not handled
	 */
	public boolean triggerDropListeners(Object o) {
		boolean handled = false;
		Object[] list;
		synchronized (this) {
			if (listDropListeners == null) {
				return handled;
			}

			list = listDropListeners.toArray();
		}
		for (int i = 0; i < list.length; i++) {
			MdiEntryDropListener l = (MdiEntryDropListener) list[i];
			handled = l.mdiEntryDrop(this, o);
			if (handled) {
				break;
			}
		}
		return handled;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getViewTitleInfo()
	 */
	@Override
	public ViewTitleInfo getViewTitleInfo() {
		return viewTitleInfo;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#setViewTitleInfo(ViewTitleInfo)
	 */
	@Override
	public void setViewTitleInfo(ViewTitleInfo viewTitleInfo) {
		if (this.viewTitleInfo == viewTitleInfo) {
			return;
		}
		this.viewTitleInfo = viewTitleInfo;
		// TODO: Need to listen for viewTitleInfo triggers so we can refresh items below
		if (viewTitleInfo != null) {
			if (viewTitleInfo instanceof ViewTitleInfo2) {
				ViewTitleInfo2 vti2 = (ViewTitleInfo2) viewTitleInfo;
				try {
					vti2.titleInfoLinked(mdi, this);
				} catch (Exception e) {
					Debug.out(e);
				}
			}

			String imageID = (String) viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_IMAGEID);
			if (imageID != null) {
				setImageLeftID(imageID.length() == 0 ? null : imageID);
			}

			ViewTitleInfoManager.addListener(this);

			if (getEventListener() == null && (viewTitleInfo instanceof UISWTViewEventListener)) {
				if (getSkinRef() == null) {
					// TODO Remove this debug
					System.out.println(
							"Setting event listener because viewTitleInfo instance of UISWTViewEventListener.  Might lose builder info. "
									+ getViewID() + " via " + Debug.getCompressedStackTrace());
				}
				try {
					setEventListener((UISWTViewEventListener) viewTitleInfo, null, true);
				} catch (UISWTViewEventCancelledException ignore) {
				}
			}
		}
		redraw();
	}


	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewImpl2#setPluginSkinObject(com.biglybt.ui.swt.pif.PluginUISWTSkinObject, org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void setPluginSkinObject(PluginUISWTSkinObject skinObject) {
		super.setPluginSkinObject(skinObject);
		Object initialDataSource = (datasource == null
				|| ((datasource instanceof Object[])
						&& ((Object[]) datasource).length == 0)) ? getInitialDataSource()
								: datasource;
		if (initialDataSource != null) {
			setDatasource(initialDataSource);
		}
	}

	public void setSkinObjectMaster(SWTSkinObject soMaster) {
		this.soMaster = soMaster;
	}


	public SWTSkinObject getSkinObject() {
		return (SWTSkinObject) getPluginSkinObject();
	}

	public SWTSkinObject getSkinObjectMaster() {
		if (soMaster == null) {
			return getSkinObject();
		}
		return soMaster;
	}

	public void setSkinRef(String configID, Object params) {
		skinRef = configID;
		if (params != null) {
			setDatasource(params);
		}
	}

	public String getSkinRef() {
		return skinRef;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getTitle()
	 */
	@Override
	public String getTitle() {
		if (viewTitleInfo != null) {
			String viewTitle = (String) viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_TEXT);
			if (viewTitle != null && viewTitle.length() > 0) {
				return viewTitle;
			}
		}
		return super.getFullTitle();
	}

	public void updateLanguage() {
		triggerEvent(UISWTViewEvent.TYPE_LANGUAGEUPDATE, null);
	}


	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewImpl2#triggerEvent(int, java.lang.Object)
	 */
	@Override
	public void triggerEvent(int eventType, Object data) {
		super.triggerEvent(eventType, data);

		if (eventType == UISWTViewEvent.TYPE_LANGUAGEUPDATE) {
			//if (getTitleID() != null) {
			//	setTitleID(getTitleID());
			//} else {
				if (viewTitleInfo != null) {
					viewTitleInfoRefresh(viewTitleInfo);
				}
				updateUI( true );
			//}

			SWTSkinObject skinObjectMaster = getSkinObjectMaster();
			if (skinObjectMaster != null) {
				skinObjectMaster.triggerListeners(SWTSkinObjectListener.EVENT_LANGUAGE_CHANGE);
			}
		}

	}


	public void show() {
		SelectedContentManager.clearCurrentlySelectedContent();

		UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uif != null) {
			//uif.refreshIconBar(); // needed?
			uif.refreshTorrentMenu();
		}



		SWTSkinObject skinObject = getSkinObjectMaster();
		if (skinObject == null) {
			return;
		}
		skinObject.setVisible(true);
		if (skinObject instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer container = (SWTSkinObjectContainer) skinObject;
			Composite composite = container.getComposite();
			if (composite != null && !composite.isDisposed()) {
				composite.setVisible(true);
				composite.moveAbove(null);
				//composite.setFocus();
				//container.getParent().relayout();
				composite.getParent().layout();
			}
			// This causes double show because createSkinObject already calls show
			//container.triggerListeners(SWTSkinObjectListener.EVENT_SHOW);
		}

		Composite c = getComposite();
		if (c != null && !c.isDisposed()) {
			c.setData("BaseMDIEntry", this);
			c.setVisible(true);
			c.getParent().layout();
		}

		try {
			// In theory, c.setVisible() will trigger TYPE_SHOWN, but let's
			// call it anyway (it will be ignored if focus is already gained)
			triggerEvent(UISWTViewEvent.TYPE_SHOWN, null);
		} catch (Exception e) {
			Debug.out(e);
		}
		setToolbarVisibility(hasToolbarEnableers());
	}

	/* (non-Javadoc)
	 * @see MdiEntry#hide()
	 */
	@Override
	public void hide() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_hide();
			}
		});
		setToolbarVisibility(false);
	}

	/* (non-Javadoc)
	 * @see MdiEntry#requestAttention()
	 */
	@Override
	public void
	requestAttention()
	{
	}

	protected void swt_hide() {
		SWTSkinObject skinObjectMaster = getSkinObjectMaster();
		if (skinObjectMaster instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer container = (SWTSkinObjectContainer) skinObjectMaster;
			Control oldComposite = container.getControl();

			container.setVisible(false);
			if (oldComposite != null && !oldComposite.isDisposed()) {
				oldComposite.getShell().update();
			}
		}

		Composite oldComposite = getComposite();
		if (oldComposite != null && !oldComposite.isDisposed()) {

			oldComposite.setVisible(false);
			oldComposite.getShell().update();
		}

		try {
			triggerEvent(UISWTViewEvent.TYPE_HIDDEN, null);
		} catch (Exception e) {
			Debug.out(e);
		}
	}

	private long lastUpdateUI;
	
	@Override
	public void
	updateUI( boolean force )
	{
		Utils.execSWTThread(() -> {
			
				// unfortunately we can end up coming through here > once during a periodic update cycle 
				// e.g. via sidebar->updateUI and tabbedmdi->updateUI. This messes up things that count updates to
				// apply graphic-update-ticks and generally uses extra CPU. Thought about adding an 'updateTickCount' to updateUI
				// but bit complicated due to dependencies and the fact that there are currently two separate periodic mehanisms
				// for normal + stand-alone components. Also the updateUI goes via a 'refresh' event in some cases so the 
				// updateTickCount would need transporting. Meh
			
			long now = SystemTime.getMonotonousTime();

			if ( now - lastUpdateUI < 100 ){
				
				if ( !force ){
					
					return;
				}
			}
			
			lastUpdateUI = now;
			  
			if (getEventListener() != null) {
				triggerEvent(UISWTViewEvent.TYPE_REFRESH, null);
			}
			refreshTitle();
		});
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getAutoOpenInfo()
	 */
	@Override
	public Map<String, Object> getAutoOpenInfo() {
		Map<String, Object> autoOpenInfo = new LightHashMap<>();
		if (getParentID() != null) {
			autoOpenInfo.put(BaseMDI.AUTOOPENINFO_PARENTID, getParentID());
		}
		autoOpenInfo.put(BaseMDI.AUTOOPENINFO_TITLE, getTitle());
		Object datasource = getDatasourceCore();

		// There's also DataSourceResolver that might be useful

		if ( datasource instanceof List || datasource instanceof Object[]){
			DownloadManager[] dms = DataSourceUtils.getDMs(datasource);
			
			if ( dms.length > 0 ){
				datasource = dms;
			}
		}
		if (datasource instanceof DownloadManager) {
			try {
				autoOpenInfo.put(
						"dm",
						((DownloadManager) datasource).getTorrent().getHashWrapper().toBase32String());
			} catch (Throwable t) {
			}
		} else if (datasource instanceof DownloadManager[]) {
			DownloadManager[] dms = (DownloadManager[]) datasource;
			List<String> list = new ArrayList<>();
			for (DownloadManager dm : dms) {
				try {
					list.add(dm.getTorrent().getHashWrapper().toBase32String());
				} catch (Throwable e) {
				}
			}
			autoOpenInfo.put("dms", list);
		}

		Object eds = getExportableDatasource();
		if (eds != null) {
			autoOpenInfo.put(BaseMDI.AUTOOPENINFO_DS, eds);
		}
		return autoOpenInfo;
	}

	public void setCloseable(boolean closeable) {
		this.closeable = closeable;
	}

	// @see MdiEntry#setDefaultExpanded(boolean)
	@Override
	public void 
	setDefaultExpanded(
		boolean defaultExpanded) 
	{
		String configID = "SideBar.Expanded2." + Base32.encode( id.getBytes( Constants.UTF_8 ));
		
		COConfigurationManager.setBooleanDefault( configID, defaultExpanded );
	}

	@Override
	public boolean 
	isExpanded() 
	{
		String configID = "SideBar.Expanded2." + Base32.encode( id.getBytes( Constants.UTF_8 ));

		return isExpanded == null
				? COConfigurationManager.getBooleanParameter(configID)
				: isExpanded;
			}

	/* (non-Javadoc)
	 * @see MdiEntry#setExpanded(boolean)
	 */
	@Override
	public void 
	setExpanded(boolean expanded) 
	{
		String configID = "SideBar.Expanded2." + Base32.encode( id.getBytes( Constants.UTF_8 ));

		isExpanded = expanded;
		
		boolean defExpanded = true;
		
		try{
			defExpanded = ConfigurationDefaults.getInstance().getBooleanParameter(
					configID);
		} catch (ConfigurationParameterNotFoundException e) {
		}
		if (isExpanded == defExpanded) {
			COConfigurationManager.removeParameter(configID);
		} else {
			COConfigurationManager.setParameter(configID, isExpanded);
		}
	}


	@Override
	protected void setMasterComposite(Composite masterComposite) {
		super.setMasterComposite(masterComposite);

		if (isContentDisposed()) {
			setDatasource(datasource);
		}

	}

	@Override
	public void setImageLeftID(String id) {
		boolean changed = id != imageLeftID && ( id == null || imageLeftID == null || !id.equals( imageLeftID ));
		
		imageLeftID = id;
		imageLeft = null;
		
		if ( changed ){
			redraw();
		}
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getImageLeftID()
	 */
	@Override
	public String getImageLeftID() {
		return imageLeftID;
	}

	/**
	 * @param imageLeft the imageLeft to set
	 */
	@Override
	public void setImageLeft(Image imageLeft) {
		this.imageLeft = imageLeft;
		imageLeftID = null;
		redraw();
	}

	/**
	 * Don't forget to {@link #releaseImageLeft(String)}
	 */
	public Image getImageLeft(String suffix) {
		if (imageLeft != null) {
			return imageLeft;
		}
		if (imageLeftID == null) {
			return null;
		}
		Image img = null;
		if (suffix == null) {
			img = ImageLoader.getInstance().getImage(imageLeftID);
		} else {
			img = ImageLoader.getInstance().getImage(imageLeftID + suffix);
		}
		if (ImageLoader.isRealImage(img)) {
//			System.out.println("real" + getTitle() + "/" + img.getBounds() + Debug.getCompressedStackTrace());
			return img;
		}
		return null;
	}

	public void releaseImageLeft(String suffix) {
		// Still potential case where we could getImageLeft, setImageLeft/ID, then releaseImageLeft, resulting in no/wrong release
		if (imageLeftID != null) {
			ImageLoader.getInstance().releaseImage(
					imageLeftID + (suffix == null ? "" : suffix));
		}
	}

	/* (non-Javadoc)
	 * @see ViewTitleInfoListener#viewTitleInfoRefresh(ViewTitleInfo)
	 */
	@Override
	public void viewTitleInfoRefresh(ViewTitleInfo titleInfoToRefresh) {
		if (titleInfoToRefresh == null || this.viewTitleInfo != titleInfoToRefresh) {
			return;
		}
		if (isEntryDisposed()) {
			return;
		}

		String imageID = (String) viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_IMAGEID);
		
			// don't overwrite any any existing (probably statically assigned) image id with a
			// ViewTitleInfo that doesn't bother returning anything better
		
		if ( imageID != null ){
			if ( imageID.length() == 0 ){
				imageID = null;
			}
						
			setImageLeftID(  imageID);
		}
	}

	public abstract void build();
	
	/* (non-Javadoc)
	 * @see MdiEntry#setPreferredAfterID(java.lang.String)
	 */
	@Override
	public void setPreferredAfterID(String preferredAfterID) {
		this.preferredAfterID = preferredAfterID;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getPreferredAfterID()
	 */
	@Override
	public String getPreferredAfterID() {
		return preferredAfterID;
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.util.AEDiagnosticsEvidenceGenerator#generate(com.biglybt.core.util.IndentWriter)
	 */
	@Override
	public void generate(IndentWriter writer) {
		writer.println("View: " + id + ": " + getTitle());

		try {
			writer.indent();

			writer.println("Parent: " + getParentID());
			//writer.println("Created: " + created);
			writer.println("closeable: " + closeable);
			writer.println("isEntryDisposed: " + isEntryDisposed());
			writer.println("isContentDisposed: " + isContentDisposed());
			writer.println("hasBeenOpened: " + hasBeenOpened);
			//writer.println("hasFocus: " + hasFocus);
			//writer.println("haveSentInitialize: " + haveSentInitialize);
			writer.println("control type: " + getControlType());
			writer.println("hasEventListener: " + (getEventListener() != null));
			writer.println("hasViewTitleInfo: " + (viewTitleInfo != null));
			writer.println("skinRef: " + skinRef);
		} catch (Exception e) {

		} finally {

			writer.exdent();
		}

		if (getEventListener() instanceof AEDiagnosticsEvidenceGenerator) {

			try {
				writer.indent();

				((AEDiagnosticsEvidenceGenerator) getEventListener()).generate(writer);
			} catch (Exception e) {

			} finally {

				writer.exdent();
			}
		}
	}

	@Override
	public void setEventListener(UISWTViewEventListener newEventListener,
			UISWTViewBuilderCore builder, boolean doCreate)
			throws UISWTViewEventCancelledException {
		UISWTViewEventListener oldEventListener = getEventListener();
		if (oldEventListener instanceof UIToolBarEnablerBase) {
			removeToolbarEnabler((UIToolBarEnablerBase) oldEventListener);
		}
		if ((oldEventListener instanceof ViewTitleInfo) && viewTitleInfo == oldEventListener) {
			setViewTitleInfo(null);
		}

		if (newEventListener instanceof BasicPluginViewImpl) {
			String existing_id = getImageLeftID();

			if (existing_id==null||"image.sidebar.plugin".equals(existing_id)) {
				setImageLeftID("image.sidebar.logview");
			}
		}

		super.setEventListener(newEventListener, builder, doCreate);

		if (newEventListener instanceof UIToolBarEnablerBase) {
			addToolbarEnabler((UIToolBarEnablerBase) newEventListener);
		}
		if ((newEventListener instanceof ViewTitleInfo) && viewTitleInfo == null) {
			setViewTitleInfo((ViewTitleInfo) newEventListener);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.mdi.UISWTViewImpl2#setDatasource(java.lang.Object)
	 */
	@Override
	public void setDatasource(Object datasource) {
		super.setDatasource(datasource);

		triggerDatasourceListeners();
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.mdi.UISWTViewImpl2#setTitle(java.lang.String)
	 */
	@Override
	public void setTitle(String title) {
		if ( setTitleSupport(title)) {
		
			redraw();
		}
	}

	@Override
	public void setTitleID( String id ) {
		if ( setTitleIDSupport( id )) {
			
			redraw();
		}
	}
	
	@Override
	public void addListener(MdiSWTMenuHackListener l) {
		synchronized (this) {
			if (listMenuHackListners == null) {
				listMenuHackListners = new ArrayList<>(1);
			}
			if (!listMenuHackListners.contains(l)) {
				listMenuHackListners.add(l);
			}
		}
	}

	@Override
	public void removeListener(MdiSWTMenuHackListener l) {
		synchronized (this) {
			if (listMenuHackListners == null) {
				listMenuHackListners = new ArrayList<>(1);
			}
			listMenuHackListners.remove(l);
		}
	}

	public MdiSWTMenuHackListener[] getMenuHackListeners() {
		synchronized (this) {
			if (listMenuHackListners == null) {
				return new MdiSWTMenuHackListener[0];
			}
			return listMenuHackListners.toArray(new MdiSWTMenuHackListener[0]);
		}
	}

	@Override
	public void addAcceleratorListener(MdiAcceleratorListener l) {
		synchronized (this) {
			if (listAcceleratorListeners == null) {
				listAcceleratorListeners = new ArrayList<>(1);
			}
			if (!listAcceleratorListeners.contains(l)) {
				listAcceleratorListeners.add(l);
			}
		}
	}

	@Override
	public void removeAcceleratorListener(MdiAcceleratorListener l) {
		synchronized (this) {
			if (listAcceleratorListeners == null) {
				listAcceleratorListeners = new ArrayList<>(1);
			}
			listAcceleratorListeners.remove(l);
		}
	}

	@Override
	public boolean processAccelerator( char c, int mask) {
		MdiAcceleratorListener[] listeners;
		
		synchronized (this) {
			if (listAcceleratorListeners == null) {
				return( false );
			}
			listeners = listAcceleratorListeners.toArray(new MdiAcceleratorListener[0]);
		}
		
		for ( MdiAcceleratorListener l: listeners ){
			try{
				if ( l.process(c, mask)){
					
					return( true );
				}
			}catch( Throwable e ){
				Debug.out( e );
			}
		}
		
		return( false );
	}
	
	@Override
	public boolean
	canBuildStandAlone()
	{
		String skinRef = getSkinRef();

		if (skinRef != null){

			return( true );

		}

		return super.canBuildStandAlone();
	}

	public Map<String,Object>
	exportStandAlone()
	{
		Map<String,Object>	result = new HashMap<>();
		
		result.put( "mdi", ( this instanceof SideBarEntrySWT )?"sidebar":"tabbed" );
		
		String title = null;
		
		ViewTitleInfo vti = getViewTitleInfo();
		
		if ( vti != null ) {
			
			title = (String)vti.getTitleInfoProperty( ViewTitleInfo.TITLE_TEXT );
		}
		
		if ( title == null || title.length() == 0 ) {
			
			title = getFullTitle();
		}
		
		result.put( "title", title );
		
		String title_id = getTitleID();
		
		if ( title_id != null && !title_id.isEmpty()){

			boolean ok = false;
			
			if ( title_id.startsWith( "!" ) && title_id.endsWith( "!" )){
			
				String id = getId();

				if ( MessageText.keyExists( id )){
					
					title_id = id;
					
					ok = true;
					
				}else{
				
					String test = id + ".title.full";
	
					if ( MessageText.keyExists( test )){
						
						title_id = test;
						
						ok = true;
					}
				}
			}
			
			if ( ok || MessageText.keyExists( title_id )){
				
				result.put( "title_id", title_id );
			}
		}
		
		if ( !result.containsKey( "title_id" )){
			
			if ( vti != null ){
				
				title_id = (String)vti.getTitleInfoProperty( ViewTitleInfo.TITLE_TEXT_ID );
				
				if ( title_id != null && MessageText.keyExists( title_id )){
				
					result.put( "title_id", title_id );
				}
			}
		}
		
		result.put( "skin_ref", getSkinRef());
		
		result.put( "skin_id", skin.getSkinID());
		
		result.put( "parent_id", getParentID());

		result.put( "id", id );
		
		Object data_source = getDatasourceCore();
		
		if ( data_source == null ) {
		
			data_source = getInitialDataSource();
		}
		
		if ( data_source == null ){
			
			data_source = getUserData( UD_STANDALONE_DATA_SOURCE );
		}
		
		if ( data_source != null ) {
		
			if ( data_source instanceof String ) {
			
				result.put( "data_source", data_source );
				
			}else if ( data_source instanceof Integer ) {
				
				List	l = new ArrayList();
				
				l.add( "i" );
				l.add(((Integer)data_source).longValue());
				
				result.put( "data_source", l );
				
			}else {
			
				result.put( "data_source", DataSourceResolver.exportDataSource( data_source ));
			}
		}
		
		result.put( "control_type", getControlType());

		UISWTViewBuilderCore builder = getEventListenerBuilder();
		
		if ( builder != null){
			
			Map map = new HashMap();
			
			Class<? extends UISWTViewEventListener> cla = builder.getListenerClass();
			
			if (cla != null) {
				map.put("name", cla.getName());
			}
			
			UISWTViewEventListenerInstantiator instantiator = builder.getListenerInstantiator();

			if ( instantiator != null ){			
				map.put( "instantiator", instantiator.getUID());
			}
			
			PluginInterface pi = builder.getPluginInterface();
			if (pi != null) {
				map.put( "plugin_id",  pi.getPluginID() );
				map.put( "plugin_name", pi.getPluginName());
			}
			if (map.size() > 0) {
				result.put( "event_listener", map );
			}
		}

		UISWTViewEventListener listener = getEventListener();

		return( result );
	}

	@Override
	public SWTSkinObjectContainer
	buildStandAlone(
		SWTSkinObjectContainer		soParent )
	{
		Object data_source = getDatasourceCore();

		if ( data_source == null ){

			data_source = getUserData( UD_STANDALONE_DATA_SOURCE );
		}
		
		return(
			buildStandAlone(
				soParent,
				getSkinRef(),
				skin,
				id,
				data_source,
				getControlType(),
				getEventListenerBuilder() ));
	}
	
	private static final Set<String>	installing_pids = new HashSet<>();
	
	private static Map<String,List<Runnable>>	builder_waiters = new HashMap<>();
	
	public static SWTSkinObjectContainer
	importStandAlone(
		SWTSkinObjectContainer		soParent,
		Map<String,Object>			map,
		Runnable					callback )
	{
		//String	mdi_type = (String)map.get( "mdi" );
		
		String		skin_ref = (String)map.get( "skin_ref" );
		
		String		skin_id	= (String)map.get( "skin_id" );
		
		SWTSkin	skin = SWTSkinFactory.lookupSkin( skin_id );
		
		String		parent_id	= (String)map.get( "parent_id" );
		
		String		id			= (String)map.get( "id" );

		Object		data_source =  map.get( "data_source" );
		
		if ( data_source != null ) {
			
			if ( data_source instanceof Map ) {
		
				Map<String,Object>		ds_map  = (Map<String,Object>)data_source;
		
				if ( ds_map != null ) {
				
					ds_map = new HashMap<String, Object>( ds_map );
				
					ds_map.put( "callback", callback );
				}
				
				data_source = ds_map==null?null:DataSourceResolver.importDataSource( ds_map );
				
			}else if ( data_source instanceof List ) {
				
				List l = (List)data_source;
				
				String 	type 	= (String)l.get(0);
				Long	value 	= (Long)l.get(1);
				
				if ( type.equals( "i" )) {
					
					data_source = value.intValue();
				}
			}
		}
		
		int			control_type = ((Number)map.get( "control_type")).intValue();
		
		Map<String,Object>	el_map = (Map<String,Object>)map.get( "event_listener" );
		
		UISWTViewBuilderCore builder = null;
		
		if ( el_map != null ){
		
			try {
				String class_name = (String)el_map.get( "name" );

				String try_install_plugin_id = null;
				
				PluginManager pluginManager = CoreFactory.getSingleton().getPluginManager();
				
					// Legacy didn't have plugin_id

				String plugin_id = (String)el_map.get( "plugin_id" );
				
				PluginInterface pi = plugin_id==null?null:pluginManager.getPluginInterfaceByID( plugin_id );

				if ( class_name != null ){
					
					ClassLoader cl;
					
					if ( pi != null ){
						
						cl = pi.getPluginClassLoader();
						
					}else{
						
						cl = BaseMdiEntry.class.getClassLoader();
					}
					
					Class<? extends UISWTViewCoreEventListener> cla = null;
					
					try{
						cla = (Class<? extends UISWTViewCoreEventListener>) Class.forName( class_name, true, cl );
						
					}catch(Throwable e ) {
					}
	
					// legacy had p_type and p_values, but we use datasource, so we
					// need to parse and convert
					List	p_types = (List)el_map.get( "p_types" );
					List	p_vals	= (List)el_map.get( "p_vals" );
					
					if ( p_types != null && !p_types.isEmpty()){
						
						List<Class> 	types = new ArrayList<>();
						List<Object>	args = new ArrayList<>();
						
						for ( int i=0;i<p_types.size();i++) {
							
							String type = (String)p_types.get(i);
							Object val	= p_vals.get(i);
							
							if ( type.equals( "bool" )) {
								
								types.add( boolean.class );
								
								args.add(((Long)val)!=0);
								
							}else if ( type.equals( "long" )) {
								
								types.add( long.class );
								
								args.add((Long)val );
								
							}else if ( type.equals( "string" )) {
								
								types.add( String.class );
								
								args.add((String)val );
								
							}else {
								
								Debug.out( "Unsupported type: " + type );
							}
						}

						// Note: Legacy had constructor params, which have been migrated to
						//       datasource or removed if they weren't needed
						// FilesView                   | boolean | removed
						// PeersGeneralView            | long tagUUID | Moved to datasource
						// DeviceManagerUI.deviceView  | String parent_key, String device_id | Moved to datasource
						// BuddyPluginView             | String VIEW_ID | Not needed; class moved 
						// BasicPluginViewImpl         | String (pluginID + "/" + model.getName()) | derp

						switch (class_name) {
							case "com.biglybt.ui.swt.views.PeersGeneralView":
								data_source = args.get(0);
								break;
							case "com.biglybt.ui.swt.devices.DeviceManagerUI.deviceView":
								data_source = new String[]{(String) args.get(0), (String) args.get(1)};
								break;
							case "com.biglybt.plugin.net.buddy.swt.BuddyPluginView":
								cla = FriendsView.class;
								pi = pluginManager.getPluginInterfaceByID("azbuddy");
								break;
							case "com.biglybt.ui.swt.pifimpl.BasicPluginViewImpl":
								String key = (String) args.get(0);
								// Key is {@link UIManagerImpl#getBasicPluginViewModelKey()}
								BasicPluginViewModel model = UIManagerImpl.getBasicPluginViewModel(key);
								if (pi == null && model != null) {
									pi = model.getPluginInterface();
								}
								data_source = model;
								break;
						}
						
					}
					
					if (	plugin_id != null && 
							!plugin_id.equals( PluginInitializer.INTERNAL_PLUGIN_ID ) && 
							pi == null ){
						
						try_install_plugin_id = plugin_id;
						
					}else{
						
						if ( cla == null ){
							
							throw(new Exception( "Failed to load class '" +class_name + "'"));
						}
						
						builder = new UISWTViewBuilderCore( id, pi, cla ).setInitialDatasource(data_source);
					}
				}else{
					String instantiator_uid = (String)el_map.get( "instantiator" );

					if ( instantiator_uid != null ){
						
						List<UISWTViewBuilderCore> builders = ViewManagerSWT.getInstance().getBuildersForInstantiatorUID( instantiator_uid );
						
						if ( !builders.isEmpty()){
							
							builder = builders.get(0);
							
						}else{
							
							if ( callback != null ){
								
								synchronized( builder_waiters ){
									
										// empty list denotes tried before and gave up
									
									List<Runnable> list = builder_waiters.get( instantiator_uid );
									
									if ( list == null ){
									
										list = new ArrayList<>();
										
										builder_waiters.put( instantiator_uid, list );
										
										list.add( callback );
										
										TimerEventPeriodic[] timer = { null };
										
										synchronized( timer ){
											
											timer[0] = 
												SimpleTimer.addPeriodicEvent(
													"BuilderWaiter",
													500,
													new TimerEventPerformer(){
														
														long start = SystemTime.getMonotonousTime();
														
														@Override
														public void 
														perform(
															TimerEvent event)
														{
															boolean done = false;
															
															try{
																List<UISWTViewBuilderCore> builders = ViewManagerSWT.getInstance().getBuildersForInstantiatorUID( instantiator_uid );
																
																done = !builders.isEmpty();
																	
															}finally{
											
																if ( done || SystemTime.getMonotonousTime() - start > 30*1000 ){
																
																	synchronized( timer ){
																	
																		timer[0].cancel();
																	}
																	
																	List<Runnable> todo;
																	
																	synchronized( builder_waiters ){
																		
																		List<Runnable> list = builder_waiters.get( instantiator_uid );
																		
																		todo = new ArrayList<>( list );
																		
																		list.clear();
																	}
																	
																	for ( Runnable r: todo ){
																		
																		try{
																			r.run();
																			
																		}catch( Throwable e ){
																			
																			Debug.out( e );
																		}
																	}
																}
															}
														}
													});
											}
									
										return( null );
										
									}else if ( !list.isEmpty()){
										
										list.add( callback );
										
										return( null );
									}
								}
							}
							
							if (	plugin_id != null && 
									!plugin_id.equals( PluginInitializer.INTERNAL_PLUGIN_ID ) && 
									pi == null ){
								
								try_install_plugin_id = plugin_id;
								
							}else{
								
								throw(new Exception( "No builders found for '" + instantiator_uid + "'" ));
							}
						}
					}
				}

				if ( try_install_plugin_id != null ){
					
					tryInstallPlugin(
						try_install_plugin_id, 
						MapUtils.getMapString(el_map, "plugin_name", try_install_plugin_id), 
						callback );
				}
			}catch( Throwable e ) {
				
				Debug.out( e );
				
				return null;
			}
		}
		
		return( buildStandAlone(
					soParent,
					skin_ref,
					skin,
					id,
					data_source,
					control_type,
					builder ));
		
	}

	private static void tryInstallPlugin(String plugin_id, String plugin_name,
			Runnable callback) {
		boolean	try_install = false;

		synchronized( installing_pids ) {

			if ( !installing_pids.contains( plugin_id )) {

				installing_pids.add( plugin_id );

				try_install = true;
			}
		}

		if ( try_install ){

			boolean	went_async = false;

			try {
				UIFunctions uif = UIFunctionsManager.getUIFunctions();
				if (uif == null) {
					return;
				}

				String remember_id = "basemdi.import.view.install." + plugin_id;

				String	title	= MessageText.getString( "plugin.required" );
				String	text	= MessageText.getString( "plugin.required.info", new String[]{ plugin_name });

				UIFunctionsUserPrompter prompter =
					uif.getUserPrompter(title, text, new String[] {
						MessageText.getString("Button.yes"),
						MessageText.getString("Button.no")
					}, 0);

				prompter.setRemember(
					remember_id,
					false,
					MessageText.getString("MessageBoxWindow.nomoreprompting"));

				prompter.setAutoCloseInMS(0);

				prompter.open(null);

				boolean	install = prompter.waitUntilClosed() == 0;

				if ( install ){

					went_async = true;

					uif.installPlugin(
						plugin_id,
						"plugin.generic.install",
						result -> {
							try{
								if ( callback != null ){

									if ( result instanceof Boolean ){

										if ( (Boolean)result ) {

											callback.run();
										}
									}
								}
							}finally{

								synchronized( installing_pids ) {

									installing_pids.remove( plugin_id );
								}
							}
						});
				}
			}finally {

				if ( !went_async ) {

					synchronized( installing_pids ) {

						installing_pids.remove( plugin_id );
					}
				}
			}
		}
	}

	/**
	 * Either skinRef or original_builder must be non-null
	 */
	public static SWTSkinObjectContainer
	buildStandAlone(
		SWTSkinObjectContainer		soParent,
		String						skinRef,
		SWTSkin						skin,
		String						id,
		Object						datasource,
		int							controlType,
		UISWTViewBuilderCore originalBuilder )
	{
		Composite parent = soParent.getComposite();
		if (parent == null) {
			return null;
		}

		if (skinRef != null){

			Shell shell = parent.getShell();
			Cursor cursor = shell.getCursor();
			try {
				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

				// wrap skinRef with a container that we control visibility of
				// (invisible by default)
				SWTSkinObjectContainer soContents = (SWTSkinObjectContainer) skin.createSkinObject(
					"MdiContents." + uniqueNumber++, SO_ID_ENTRY_WRAPPER,
					soParent, null);

				SWTSkinObject skinObject = skin.createSkinObject( id, skinRef, soContents, datasource );

				Control control = skinObject.getControl();
				control.setLayoutData(Utils.getFilledFormData());
				control.getParent().layout(true, true);

				soContents.setVisible( true );

				return( soContents );

			}finally{
				shell.setCursor(cursor);
			}
		}

		return UISWTViewImpl.buildStandAlone(soParent, skin, id, datasource, controlType, originalBuilder);
	}
}
