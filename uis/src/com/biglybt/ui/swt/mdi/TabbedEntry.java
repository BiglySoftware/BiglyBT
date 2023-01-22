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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.debug.ObfuscateTab;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventCancelledException;
import com.biglybt.ui.swt.shells.PopOutManager;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.util.MapUtils;
import com.biglybt.ui.swt.widgets.TabFolderRenderer;
import com.biglybt.ui.swt.widgets.TabFolderRenderer.TabbedEntryVitalityImage;

/**
 * MDI Entry that is a {@link CTabItem} and belongs wo {@link TabbedMDI}
 */
public class TabbedEntry
	extends BaseMdiEntry
	implements TabFolderRenderer.TabbedEntry
{
	private CTabItem swtItem;

	private boolean showonSWTItemSet;

	private boolean buildonSWTItemSet;

	private MdiEntryVitalityImageSWT viPopout;

	private boolean userInitiatedClose;
	
	public 
	TabbedEntry(
		TabbedMDI mdi, SWTSkin skin, String id) 
	{
		super(mdi, id);
		this.skin = skin;
	}

	@Override
	public void build() {
		Utils.execSWTThread(this::swt_build);
	}

	/**
	 * @implNote SideBarEntrySWT is neary identical to this one.  Please keep them
	 *       in sync until commonalities are placed in BaseMdiEntry
	 */
	public boolean swt_build() {
		if (swtItem == null || skin == null) {
			buildonSWTItemSet = true;
			return true;
		}
		buildonSWTItemSet = false;

		Control control = swtItem.getControl();
		if (control != null && !control.isDisposed()) {
			return true;
		}

		Composite parent = swtItem.getParent();
		if (parent == null || parent.isDisposed()) {
			return false;
		}
		SWTSkinObject soParent = (SWTSkinObject) parent.getData("SkinObject");

		String skinRef = getSkinRef();
		if (skinRef != null) {
			Shell shell = parent.getShell();
			Cursor cursor = shell.getCursor();
			try {
				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

//					SWTSkinObjectContainer soContents = (SWTSkinObjectContainer) skin.createSkinObject(
//							"MdiContents." + uniqueNumber++, "mdi.content.item",
//							soParent, getSkinRefParams());
//					skin.addSkinObject(soContents);


				SWTSkinObject skinObject = skin.createSkinObject(id, skinRef,
						soParent, getDatasourceCore());

				control = skinObject.getControl();
				control.setLayoutData(Utils.getFilledFormData());
				control.getParent().layout(true);
				// swtItem.setControl will set the control's visibility based on
				// whether the control is selected.  To ensure it doesn't set
				// our control invisible, set selection now
				CTabItem oldSelection = swtItem.getParent().getSelection();
				swtItem.getParent().setSelection(swtItem);
				swtItem.setControl(control);
				if (oldSelection != null) {
					swtItem.getParent().setSelection(oldSelection);
				}
				setPluginSkinObject(skinObject);
				setSkinObjectMaster(skinObject);


				initialize((Composite) control);
			} finally {
				shell.setCursor(cursor);
			}
		} else {
			// XXX: This needs to be merged into BaseMDIEntry.initialize
			try {
				SWTSkinObjectContainer soContents = (SWTSkinObjectContainer) skin.createSkinObject(
						"MdiIView." + uniqueNumber++, SO_ID_ENTRY_WRAPPER,
						soParent);

				Composite viewComposite = soContents.getComposite();

				boolean doGridLayout = true;
				if (getControlType() == CONTROLTYPE_SKINOBJECT) {
					doGridLayout = false;
				}
				if (doGridLayout) {
				  GridLayout gridLayout = new GridLayout();
				  gridLayout.horizontalSpacing = gridLayout.verticalSpacing = gridLayout.marginHeight = gridLayout.marginWidth = 0;
				  viewComposite.setLayout(gridLayout);
				  viewComposite.setLayoutData(Utils.getFilledFormData());
				}

				setPluginSkinObject(soContents);

				initialize(viewComposite);

				Composite iviewComposite = getComposite();
				control = iviewComposite;
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

				CTabItem oldSelection = swtItem.getParent().getSelection();
				swtItem.getParent().setSelection(swtItem);
				swtItem.setControl(viewComposite);
				if (oldSelection != null) {
					swtItem.getParent().setSelection(oldSelection);
				}
				setSkinObjectMaster(soContents);
			} catch (Exception e) {
				Debug.out("Error creating sidebar content area for " + id, e);
				try {
					setEventListener(null, null, false);
				} catch (UISWTViewEventCancelledException ignore) {
				}
				closeView();
			}

		}

		if (control != null && !control.isDisposed()) {
			control.setData("BaseMDIEntry", this);
			/** XXX Removed this because we can dispose of the control and still
			 * want the tab (ie. destroy on focus lost, rebuild on focus gain)
			control.addDisposeListener(new DisposeListener() {
				public void widgetDisposed(DisposeEvent e) {
					close(true);
				}
			});
			*/
		} else {
			return false;
		}

		return true;
	}

	@Override
	public boolean
	isEntryDisposed()
	{
		return( swtItem == null || swtItem.isDisposed());
	}
	
	protected void
	setUserInitiatedClose()
	{
		userInitiatedClose = true;
	}
	
	/* (non-Javadoc)
	 * @see BaseMdiEntry#show()
	 */
	@Override
	public void show() {
		// ensure show order by user execThreadLater
		// fixes case where two showEntries are called, the first from a non
		// SWT thread, and the 2nd from a SWT thread.  The first one will run last
		// showing itself
		Utils.execSWTThreadLater(0, this::swt_show);
	}

	private void swt_show() {
		if (swtItem == null) {
			showonSWTItemSet = true;
			return;
		}
		showonSWTItemSet = false;
		if (!swt_build()) {
			return;
		}

		triggerOpenListeners();


		CTabFolder parent = swtItem.getParent();
		if (parent != null && parent.getSelection() != swtItem) {
			parent.setSelection(swtItem);
		}

		super.show();
	}

	@Override
	public MdiEntryVitalityImageSWT addVitalityImage(String imageID) {
		MdiEntryVitalityImageSWT mdiEntryVitalityImage = super.addVitalityImage(imageID);
		Utils.execSWTThreadLater(0, () -> getMDI().swt_refreshVitality());
		return mdiEntryVitalityImage;
	}

	@Override
	public boolean isCloseable() {
		return getMDI().isMainMDI || super.isCloseable();
	}

	/* (non-Javadoc)
	 * @see BaseMdiEntry#setCloseable(boolean)
	 */
	@Override
	public void setCloseable(boolean closeable) {
		// override.. we don't support non-closeable for main
		if (getMDI().isMainMDI) {
			closeable = true;
		}
		super.setCloseable(closeable);
		Utils.execSWTThread(() -> {
			if (swtItem == null || swtItem.isDisposed()) {
				return;
			}
			swtItem.setShowClose(isCloseable());
		});
	}

	@Override
	public void setEventListener(UISWTViewEventListener _eventListener,
			UISWTViewBuilderCore builder, boolean doCreate)
			throws UISWTViewEventCancelledException {
		super.setEventListener(_eventListener, builder, doCreate);
		buildCommonVitalityImages();
	}

	@Override
	public void setSkinRef(String configID, Object params) {
		super.setSkinRef(configID, params);
		buildCommonVitalityImages();
	}

	private void buildCommonVitalityImages() {
		boolean canBuildStandalone = canBuildStandAlone();

		if (canBuildStandalone && viPopout == null) {
			// clone-able/popout-able
			viPopout = addVitalityImage("popout_window");
			viPopout.setToolTip(MessageText.getString("label.pop.out"));
			viPopout.setShowOnlyOnSelection(true);
			viPopout.setAlwaysLast(true);

			viPopout.addListener((x, y) -> {
				// From TabbedMDI.addMenus, but there's also Sidebar.addGeneralMenus which doesn't set datasource
				PopOutManager.popOut( TabbedEntry.this );
			});

		} else if (viPopout != null) {
			viPopout.setVisible(canBuildStandalone);
		}
	}

	public void setSwtItem(CTabItem swtItem) {
		this.swtItem = swtItem;
		if (swtItem == null) {
			return;
		}

		swtItem.addDisposeListener(e -> closeView( userInitiatedClose ));
		String title = getTitle();
		if (title != null) {
			//swtItem.setText(Utils.escapeAccelerators(title));	// seems we don't need this anymore...
			swtItem.setText(title);
		}

		updateLeftImage();

		swtItem.setShowClose(isCloseable());

		if (buildonSWTItemSet) {
			build();
		}
		if (showonSWTItemSet) {
			show();
		}
	}

	@Override
	protected boolean setTitleSupport(String title) {
		boolean changed = super.setTitleSupport(title);
		if (changed && swtItem != null) {
			Utils.execSWTThread(() -> {
				if (swtItem == null || swtItem.isDisposed()) {
					return;
				}
				//swtItem.setText(Utils.escapeAccelerators(getTitle()));	// seems we don't need this anymore...
				swtItem.setText(getTitle());
			});
		}
		return changed;
	}

	@Override
	protected boolean setTitleIDSupport(String titleID) {
		boolean changed = super.setTitleIDSupport(titleID);
		if (changed && swtItem != null) {
			Utils.execSWTThread(() -> {
				if (swtItem == null || swtItem.isDisposed()) {
					return;
				}
				//swtItem.setText(Utils.escapeAccelerators(getTitle()));	// seems we don't need this anymore...
				swtItem.setText(getTitle());
			});
		}
		return changed;
	}


	@Override
	protected void destroyEntry( boolean userInitiated ) {
		if (Utils.runIfNotSWTThread(()->destroyEntry(userInitiated))) {
			return;
		}

		if (swtItem == null) {
			destroyEntryAlways();
			return;
		}

		// Must make a copy of swtItem because swtItem.dispose will end up in
		// this method again, with swtItem.isDisposed() still false.
		CTabItem item = swtItem;
		swtItem = null;

		super.destroyEntry( userInitiated );

		try {
			if (!item.isDisposed()) {
				item.dispose();
			}
		}catch( SWTException e ){
			// getting internal 'Widget it disposed' here, ignore
		}
	}

	@Override
	public void 
	redraw() 
	{
		getMDI().getRenderer().redraw( this );
	}

	// @see BaseMdiEntry#setImageLeftID(java.lang.String)
	@Override
	public void setImageLeftID(String id) {
		super.setImageLeftID(id);
		updateLeftImage();
	}

	// @see BaseMdiEntry#setImageLeft(org.eclipse.swt.graphics.Image)
	@Override
	public void setImageLeft(Image imageLeft) {
		super.setImageLeft(imageLeft);
		updateLeftImage();
	}

	private void updateLeftImage() {
		if (swtItem == null) {
			return;
		}
		Utils.execSWTThread(() -> {
			if (swtItem == null || swtItem.isDisposed()) {
				return;
			}
			Image image = getImageLeft(null);
			swtItem.setImage(image);
		});
	}

	@Override
	public void viewTitleInfoRefresh(ViewTitleInfo titleInfoToRefresh) {
		super.viewTitleInfoRefresh(titleInfoToRefresh);

		if (titleInfoToRefresh == null || this.viewTitleInfo != titleInfoToRefresh) {
			return;
		}
		if (isEntryDisposed()) {
			return;
		}

		boolean changed = false;
		String newText = (String) viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_TEXT);
		if (newText != null) {
			changed = setTitleSupport(newText);
		}
		if (!changed) {
			// Text didn't change, but something else probably did, so repaint
			redraw();
		}
	}

	// @see MdiEntry#isSelectable()
	@Override
	public boolean isSelectable() {
		return true;
	}

	// @see MdiEntry#setSelectable(boolean)
	@Override
	public void setSelectable(boolean selectable) {
	}

	// @see BaseMdiEntry#setParentID(java.lang.String)
	@Override
	public void setParentEntryID(String parentEntryID) {
		// Do not set
	}

	// @see BaseMdiEntry#getParentID()
	@Override
	public String getParentID() {
		return null;
	}

	// @see com.biglybt.ui.swt.debug.ObfuscateImage#obfuscatedImage(org.eclipse.swt.graphics.Image)
	@Override
	public Image obfuscatedImage(Image image) {
		Rectangle bounds = swtItem == null ? null : swtItem.getBounds();
		if ( bounds != null ){

			CTabFolder parent = swtItem.getParent();
			if (parent == null || parent.isDisposed()) {
				return image;
			}
			boolean isActive = parent.getSelection() == swtItem;
			boolean isHeaderVisible = swtItem.isShowing();

			Point location = Utils.getLocationRelativeToShell(parent);

			bounds.x += location.x;
			bounds.y += location.y;

			Map<String, Object> map = new HashMap<>();
			map.put("image", image);
			map.put("obfuscateTitle", false);
			if (isActive) {
				triggerEvent(UISWTViewEvent.TYPE_OBFUSCATE, map);

				if (viewTitleInfo instanceof ObfuscateImage) {
					((ObfuscateImage) viewTitleInfo).obfuscatedImage(image);
				}
			}

			if (isHeaderVisible) {
  			if (viewTitleInfo instanceof ObfuscateTab) {
  				String header = ((ObfuscateTab) viewTitleInfo).getObfuscatedHeader();
  				if (header != null) {
  					UIDebugGenerator.obfuscateArea(image, bounds, header);
  				}
  			}

  			if (MapUtils.getMapBoolean(map, "obfuscateTitle", false)) {
  				UIDebugGenerator.obfuscateArea(image, bounds);
  			}
			}
		}

		return image;
	}

	@Override
	public void redraw(Rectangle hitArea) {
		if (Utils.runIfNotSWTThread(() -> this.redraw(hitArea))) {
			return;
		}

		if (swtItem == null || swtItem.isDisposed()) {
			return;
		}
		CTabFolder parent = swtItem.getParent();
		if (parent == null) {
			return;
		}
		parent.redraw(hitArea.x, hitArea.y, hitArea.width, hitArea.height, true);
	}

	@Override
	public TabbedMDI getMDI() {
		return (TabbedMDI) super.getMDI();
	}
	
	@Override
	public CTabItem 
	getTabbedEntryItem()
	{
		return( swtItem );
	}
	
	@Override
	public List<TabbedEntryVitalityImage> 
	getTabbedEntryVitalityImages()
	{
		return( new ArrayList<>( getVitalityImages()));
	}
	
	public ViewTitleInfo 
	getTabbedEntryViewTitleInfo()
	{
		return( getViewTitleInfo());
	}
	
	public boolean
	isTabbedEntryActive()
	{
		return( isActive());
	}
}
