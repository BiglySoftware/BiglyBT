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
import java.util.List;

import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo2;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoListener;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.ConfigurationParameterNotFoundException;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.toolbar.UIToolBarEnablerBase;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.pif.PluginUISWTSkinObject;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pif.UISWTViewEventListenerEx;
import com.biglybt.ui.swt.pifimpl.*;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx.CloneConstructor;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectListener;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.sidebar.SideBarEntrySWT;

public abstract class BaseMdiEntry
	extends UISWTViewImpl
	implements MdiEntrySWT, ViewTitleInfoListener, AEDiagnosticsEvidenceGenerator,
		ObfuscateImage
{
	protected final MultipleDocumentInterface mdi;

	protected String logID;

	private String skinRef;

	protected SWTSkin skin;

	private List<MdiCloseListener> listCloseListeners = null;

	private List<MdiChildCloseListener> listChildCloseListeners = null;

	private List<MdiEntryLogIdListener> listLogIDListeners = null;

	private List<MdiEntryOpenListener> listOpenListeners = null;

	private List<MdiEntryDropListener> listDropListeners = null;

	private List<MdiEntryDatasourceListener> listDatasourceListeners = null;

	private List<MdiSWTMenuHackListener> listMenuHackListners;

	protected ViewTitleInfo viewTitleInfo;

	/** Parent MDIEntry.  Doesn't mean that this view is embedded inside the parentID */
	private String parentEntryID;

	private boolean closeable;

	private Boolean isExpanded = null;

	private boolean added = false;

	private String imageLeftID;

	private Image imageLeft;

	private boolean collapseDisabled = false;

	private SWTSkinObject soMaster;

	private String preferredAfterID;

	private boolean hasBeenOpened;

	private BaseMdiEntry() {
		super(null, null, false);
		mdi = null;
		setDefaultExpanded(false);
		AEDiagnostics.addWeakEvidenceGenerator(this);
	}

	public BaseMdiEntry(MultipleDocumentInterface mdi, String id, String parentViewID) {
		super(id, parentViewID, true);
		this.mdi = mdi;
		AEDiagnostics.addWeakEvidenceGenerator(this);

		if (id == null) {
			logID = "null";
		} else {
			int i = id.indexOf('_');
			if (i > 0) {
				logID = id.substring(0, i);
			} else {
				logID = id;
			}
		}
		setDefaultExpanded(false);
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getId()
	 */
	@Override
	public String getId() {
		return id;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#addVitalityImage(java.lang.String)
	 */
	@Override
	public MdiEntryVitalityImage addVitalityImage(String imageID) {
		return null;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#close()
	 */
	@Override
	public boolean close(boolean forceClose) {
		if (!forceClose) {
			if (!requestClose()) {
				return false;
			}
		}

		setCloseable(closeable);
		
		setDisposed( true );

		baseDispose();

		return true;
	}

	private void baseDispose() {
		ViewTitleInfoManager.removeListener(this);
	}

	public Object getDatasourceCore() {
		return datasource;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getExportableDatasource()
	 */
	@Override
	public String getExportableDatasource() {
		if (viewTitleInfo != null) {
			Object ds = viewTitleInfo.getTitleInfoProperty(ViewTitleInfo2.TITLE_EXPORTABLE_DATASOURCE);
			if (ds != null) {
				return ds.toString();
			}
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
	 * @see MdiEntry#getLogID()
	 */
	@Override
	public String getLogID() {
		return logID;
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getMDI()
	 */
	@Override
	public MultipleDocumentInterface getMDI() {
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
	public void setParentID(String id) {
		if (id == null || "Tools".equals(id)) {
			id = MultipleDocumentInterface.SIDEBAR_HEADER_PLUGINS;
		}
		if (id.equals(getId())) {
			Debug.out("Setting Parent to same ID as child! " + id);
			return;
		}
		parentEntryID = id;
		// ensure parent gets created if it isn't there already
		if (mdi != null) {
			mdi.loadEntryByID(parentEntryID, false);
		}
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getVitalityImages()
	 */
	@Override
	public MdiEntryVitalityImage[] getVitalityImages() {
		return null;
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
		if (objectWithListeners instanceof MdiEntryLogIdListener) {
			addListener((MdiEntryLogIdListener) objectWithListeners);
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

	public void triggerCloseListeners(boolean user) {
		Object[] list = {};
		synchronized (this) {
			if (listCloseListeners != null) {
				list = listCloseListeners.toArray();
			}
		}
		for (int i = 0; i < list.length; i++) {
			MdiCloseListener l = (MdiCloseListener) list[i];
			try {
				l.mdiEntryClosed(this, user);
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
  		MdiEntry parentEntry = mdi.getEntry(parentEntryID);
  		if (parentEntry instanceof BaseMdiEntry) {
  			((BaseMdiEntry) parentEntry).triggerChildCloseListeners(this, user);
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
	public void addListener(MdiEntryLogIdListener l) {
		synchronized (this) {
			if (listLogIDListeners == null) {
				listLogIDListeners = new ArrayList<>(1);
			}
			listLogIDListeners.add(l);
		}
	}

	@Override
	public void removeListener(MdiEntryLogIdListener sideBarLogIdListener) {
		synchronized (this) {
			if (listLogIDListeners != null) {
				listLogIDListeners.remove(sideBarLogIdListener);
			}
		}
	}

	protected void triggerLogIDListeners(String oldID) {
		Object[] list;
		synchronized (this) {
			if (listLogIDListeners == null) {
				return;
			}

			list = listLogIDListeners.toArray();
		}

		for (int i = 0; i < list.length; i++) {
			MdiEntryLogIdListener l = (MdiEntryLogIdListener) list[i];
			l.mdiEntryLogIdChanged(this, oldID, logID);
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
	 * @see MdiEntry#setLogID(java.lang.String)
	 */
	@Override
	public void setLogID(String logID) {
		if (logID == null || logID.equals("" + this.logID)) {
			return;
		}
		String oldID = this.logID;
		this.logID = logID;
		triggerLogIDListeners(oldID);
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
				try {
					setEventListener((UISWTViewEventListener) viewTitleInfo, true);
				} catch (UISWTViewEventCancelledException e) {
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
			if (skinObject instanceof SWTSkinObject) {
				((SWTSkinObject) skinObject).triggerListeners(
						SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, initialDataSource);
			}
			triggerEvent(UISWTViewEvent.TYPE_DATASOURCE_CHANGED, initialDataSource);
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
				updateUI();
			//}

			SWTSkinObject skinObjectMaster = getSkinObjectMaster();
			if (skinObjectMaster != null) {
				skinObjectMaster.triggerListeners(SWTSkinObjectListener.EVENT_LANGUAGE_CHANGE);
			}
		}

	}


	public void show() {
		if (skinObject == null) {
			return;
		}

		SelectedContentManager.clearCurrentlySelectedContent();

		UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uif != null) {
			//uif.refreshIconBar(); // needed?
			uif.refreshTorrentMenu();
		}



		SWTSkinObject skinObject = getSkinObjectMaster();
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
			// In theory, c.setVisible() will trigger TYPE_FOCUSGAINED, but let's
			// call it anyway (it will be ignored if focus is already gained)
			triggerEvent(UISWTViewEvent.TYPE_FOCUSGAINED, null);
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
			triggerEvent(UISWTViewEvent.TYPE_FOCUSLOST, null);
		} catch (Exception e) {
			Debug.out(e);
		}
	}

	/* (non-Javadoc)
	 * @see MdiEntry#updateUI()
	 */
	@Override
	public void
	updateUI()
	{
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void
			runSupport()
			{
				if (!isDisposed()) {
					if (getEventListener() != null) {

						triggerEvent(UISWTViewEvent.TYPE_REFRESH, null);
					}
					refreshTitle();
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see MdiEntry#getAutoOpenInfo()
	 */
	@Override
	public Map<String, Object> getAutoOpenInfo() {
		Map<String, Object> autoOpenInfo = new LightHashMap<>();
		if (getParentID() != null) {
			autoOpenInfo.put("parentID", getParentID());
		}
		autoOpenInfo.put("title", getTitle());
		Object datasource = getDatasourceCore();
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

		String eds = getExportableDatasource();
		if (eds != null) {
			autoOpenInfo.put("datasource", eds.toString());
		}
		return autoOpenInfo;
	}

	public void setCloseable(boolean closeable) {
		this.closeable = closeable;

		if (mdi != null) {
  		if (closeable) {
  			mdi.informAutoOpenSet(this, getAutoOpenInfo());
  		} else {
  			mdi.removeEntryAutoOpen(id);
  		}
		}
	}

	// @see MdiEntry#setDefaultExpanded(boolean)
	@Override
	public void setDefaultExpanded(boolean defaultExpanded) {
		COConfigurationManager.setBooleanDefault("SideBar.Expanded." + id,
				defaultExpanded);
	}

	@Override
	public boolean isExpanded() {
		return isExpanded == null
				? COConfigurationManager.getBooleanParameter("SideBar.Expanded." + id)
				: isExpanded;
			}

	/* (non-Javadoc)
	 * @see MdiEntry#setExpanded(boolean)
	 */
	@Override
	public void setExpanded(boolean expanded) {
		isExpanded = expanded;
		boolean defExpanded = true;
		try {
			defExpanded = ConfigurationDefaults.getInstance().getBooleanParameter(
					"SideBar.Expanded." + id);
		} catch (ConfigurationParameterNotFoundException e) {
		}
		if (isExpanded == defExpanded) {
			COConfigurationManager.removeParameter("SideBar.Expanded." + id);
		} else {
			COConfigurationManager.setParameter("SideBar.Expanded." + id, isExpanded);
		}
	}

	/* (non-Javadoc)
	 * @see MdiEntry#isAdded()
	 */
	@Override
	public boolean isAdded() {
		return added;
	}

	public void setDisposed(boolean b) {
		super.setDisposed( b );
		added = !b;

		if (added) {
			if (getSkinObject() != null) {
				getSkinObject().triggerListeners(
						SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, datasource);
			}
		}

		if ( isDisposed()){
			baseDispose();
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
		if (isDisposed()) {
			return;
		}

		String imageID = (String) viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_IMAGEID);
		
			// don't overwrite any any existing (probably statically assigned) image id with a
			// ViewTitleInfo that doesn't bother returning anythign better
		
		if ( imageID != null ){
			if ( imageID.length() == 0 ){
				imageID = null;
			}
						
			setImageLeftID(  imageID);
		}
		
		String logID = (String) viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_LOGID);
		if (logID != null) {
			setLogID(logID);
		}
	}

	public void build() {
	}

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

	public boolean requestClose() {
		return triggerEventRaw(UISWTViewEvent.TYPE_CLOSE, null);
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
			writer.println("Added: " + added);
			writer.println("closeable: " + closeable);
			writer.println("Disposed: " + isDisposed());
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

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.mdi.UISWTViewImpl2#closeView()
	 */
	@Override
	public void closeView() {
		// This essentially calls mdi.closeEntry(id)
		//UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		//if (uiFunctions != null) {
		//	uiFunctions.closePluginView(this);
		//}
		if (mdi != null) {
			mdi.closeEntry(id);
		}

		super.closeView();
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pifimpl.UISWTViewImpl2#setEventListener(com.biglybt.ui.swt.pif.UISWTViewEventListener, boolean)
	 */
	@Override
	public void setEventListener(UISWTViewEventListener _eventListener,
			boolean doCreate)
	throws UISWTViewEventCancelledException {
		UISWTViewEventListener eventListener = getEventListener();
		if (eventListener instanceof UIToolBarEnablerBase) {
			removeToolbarEnabler((UIToolBarEnablerBase) eventListener);
		}
		if ((eventListener instanceof ViewTitleInfo) && viewTitleInfo == eventListener) {
			setViewTitleInfo(null);
		}

		if (_eventListener instanceof UISWTViewEventListenerHolder) {
			UISWTViewEventListenerHolder h = (UISWTViewEventListenerHolder) _eventListener;
			UISWTViewEventListener delegatedEventListener = h.getDelegatedEventListener(this);
			if (delegatedEventListener != null) {
				_eventListener = delegatedEventListener;
			}
		}

		if (_eventListener instanceof UIToolBarEnablerBase) {
			addToolbarEnabler((UIToolBarEnablerBase) _eventListener);
		}
		if ((_eventListener instanceof ViewTitleInfo) && viewTitleInfo == null) {
			setViewTitleInfo((ViewTitleInfo) _eventListener);
		}


		if (_eventListener instanceof BasicPluginViewImpl) {
			String existing_id = getImageLeftID();

			if (existing_id==null||"image.sidebar.plugin".equals(existing_id)) {
				setImageLeftID("image.sidebar.logview");
			}
		}


		super.setEventListener(_eventListener, doCreate);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.mdi.UISWTViewImpl2#setDatasource(java.lang.Object)
	 */
	@Override
	public void setDatasource(Object datasource) {
		super.setDatasource(datasource);

		triggerDatasourceListeners();
		if (isAdded()) {
			if (getSkinObject() != null) {
				getSkinObject().triggerListeners(
						SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, datasource);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.mdi.UISWTViewImpl2#setTitle(java.lang.String)
	 */
	@Override
	public void setTitle(String title) {
		if ( super.setTitleSupport(title)) {
		
			redraw();
		}
	}

	@Override
	public void setTitleID( String id ) {
		if ( super.setTitleIDSupport( id )) {
			
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

	
	
	
	public boolean
	canBuildStandAlone()
	{
		String skinRef = getSkinRef();

		if (skinRef != null){

			return( true );

		}else {

			UISWTViewEventListener event_listener = getEventListener();

			if ( event_listener instanceof UISWTViewCoreEventListenerEx && ((UISWTViewCoreEventListenerEx)event_listener).isCloneable()){

				return( true );
				
			}else if ( event_listener instanceof UISWTViewEventListenerEx ) {
				
				return( true );
			}
		}

		return( false );
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
		
		result.put( "skin_ref", getSkinRef());
		
		result.put( "skin_id", skin.getSkinID());
		
		result.put( "parent_id", getParentID());

		result.put( "id", id );
		
		Object data_source = getDatasourceCore();
		
		if ( data_source == null ) {
		
			data_source = getInitialDataSource();
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

		UISWTViewEventListener listener = getEventListener();
		
		if ( listener instanceof UISWTViewCoreEventListenerEx ){
		
			CloneConstructor cc = ((UISWTViewCoreEventListenerEx)listener).getCloneConstructor();
		
			String name = cc.getCloneClass().getName();
			
			Map<String,Object>	map = new HashMap<>();
			
			map.put( "name",  name );
			
			List<Object>	params = cc.getParameters();
			
			if ( params != null ){
				
				List	p_types	= new ArrayList<>();
				List	p_vals	= new ArrayList<>();
				
				map.put( "p_types", p_types );
				map.put( "p_vals", p_vals );
				
				for ( Object p: params ) {
					
					if ( p instanceof Boolean ) {
						
						p_types.add( "bool" );
						
						p_vals.add( new Long(((Boolean)p)?1:0));
						
					}else if ( p instanceof Long ) {

						p_types.add( "long" );

						p_vals.add( p );
						
					}else if ( p instanceof String ) {

						p_types.add( "string" );

						p_vals.add( p );
	
					}else {
						
						Debug.out( "Unsupported param type: " + p );
					}
				}
			}
			
			result.put( "event_listener", map );
			
		}else if ( listener instanceof UISWTViewEventListenerEx ){
			
			com.biglybt.ui.swt.pif.UISWTViewEventListenerEx.CloneConstructor cc = ((UISWTViewEventListenerEx)listener).getCloneConstructor();
		
			PluginInterface pi = cc.getPluginInterface();
						
			Map<String,Object>	map = new HashMap<>();
			
			map.put( "plugin_id",  pi.getPluginID() );
			
			map.put( "plugin_name", pi.getPluginName());
			
			map.put( "ipc_method", cc.getIPCMethod());
			
			List<Object>	params = cc.getIPCParameters();
			
			if ( params != null ){
				
				List	p_types	= new ArrayList<>();
				List	p_vals	= new ArrayList<>();
				
				map.put( "p_types", p_types );
				map.put( "p_vals", p_vals );
				
				for ( Object p: params ) {
					
					if ( p instanceof Boolean ) {
						
						p_types.add( "bool" );
						
						p_vals.add( new Long(((Boolean)p)?1:0));
						
					}else if ( p instanceof Long ) {

						p_types.add( "long" );

						p_vals.add( p );
						
					}else if ( p instanceof String ) {

						p_types.add( "string" );

						p_vals.add( p );
	
					}else {
						
						Debug.out( "Unsupported param type: " + p );
					}
				}
			}
			
			result.put( "event_listener", map );
		}
		
		return( result );
	}
	
	public abstract SWTSkinObjectContainer
	buildStandAlone(
		SWTSkinObjectContainer		soParent );
	
	private static Set<String>	installing_pids = new HashSet<>();
	
	public static void
	popoutStandAlone(
		String						title,
		Map<String,Object>			state,
		String						configPrefix )
	{
		SkinnedDialog skinnedDialog =
				new SkinnedDialog(
						"skin3_dlg_sidebar_popout",
						"shell",
						null,	// standalone
						SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

		SWTSkin skin = skinnedDialog.getSkin();

		SWTSkinObjectContainer cont = 
			BaseMdiEntry.importStandAlone(
				(SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), 
				state,
				null );

		if ( cont != null ){

			skinnedDialog.setTitle( title );

			skinnedDialog.open( configPrefix, true );

		}else{

			skinnedDialog.close();
		}
	}
	
	public static SWTSkinObjectContainer
	importStandAlone(
		SWTSkinObjectContainer		soParent,
		Map<String,Object>			map,
		Runnable					callback )
	{
		String	mdi_type = (String)map.get( "mdi" );
		
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
		
		UISWTViewEventListener	event_listener	= null;
		
		if ( el_map != null ){
		
			try {
				String class_name = (String)el_map.get( "name" );
				
				if ( class_name != null ){
					
					Class<? extends UISWTViewCoreEventListenerEx> cla = (Class<? extends UISWTViewCoreEventListenerEx>) Class.forName( class_name );
					
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
						
						event_listener = cla.getConstructor( types.toArray( new Class<?>[types.size()])).newInstance( args.toArray( new Object[args.size()]));
						
					}else {
					
						event_listener = cla.newInstance();
					}
				}else{
					
					String plugin_id = (String)el_map.get( "plugin_id" );
					
					PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( plugin_id );
					
					if ( pi != null ){
						
						String ipc_method = (String)el_map.get( "ipc_method" );
						
						List	p_types = (List)el_map.get( "p_types" );
						List	p_vals	= (List)el_map.get( "p_vals" );
						
						List<Object>	args = new ArrayList<>();

						if ( p_types != null && !p_types.isEmpty()){
							
							List<Class> 	types = new ArrayList<>();
							
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
						}
							
						event_listener = (UISWTViewEventListener)pi.getIPC().invoke( ipc_method, args.toArray( new Object[args.size()]));
						
					}else{
						
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
		
								String plugin_name = (String)el_map.get( "plugin_name" );
								
								String remember_id = "basemdi.import.view.install." + plugin_id;
								
								String	title	= MessageText.getString( "plugin.required" );
								String	text	= MessageText.getString( "plugin.required.info", new String[]{ plugin_name });
								
								UIFunctionsUserPrompter prompter = 
									uif.getUserPrompter(title, text, new String[] {
										MessageText.getString("Button.yes"),
										MessageText.getString("Button.no")
									}, 0);
		
								if ( remember_id != null ){
		
									prompter.setRemember(
										remember_id,
										false,
										MessageText.getString("MessageBoxWindow.nomoreprompting"));
								}
		
								prompter.setAutoCloseInMS(0);
		
								prompter.open(null);
		
								boolean	install = prompter.waitUntilClosed() == 0;
		
								if ( install ){
		
									went_async = true;
		
									uif.installPlugin(
										plugin_id,
										"plugin.generic.install",
										new UIFunctions.actionListener()
										{
											@Override
											public void
											actionComplete(
												Object		result )
											{
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
				}
				
			}catch( Throwable e ) {
				
				Debug.out( e );
			}
		}
		
		if ( mdi_type.equals( "sidebar" )){
			
			return( SideBarEntrySWT.buildStandAlone(
						soParent,
						skin_ref,
						skin,
						parent_id,
						id,
						data_source,
						control_type,
						null,
						event_listener,
						true ));
			
		}else {
			return(TabbedEntry.buildStandAlone(
					soParent,
					skin_ref,
					skin,
					parent_id,
					id,
					data_source,
					control_type,
					null,
					event_listener,
					true ));
			
		}
	}
}