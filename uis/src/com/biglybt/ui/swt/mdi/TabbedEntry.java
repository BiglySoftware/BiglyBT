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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.debug.ObfuscateTab;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.mainwindow.SWTThread;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventCancelledException;
import com.biglybt.ui.swt.pifimpl.UISWTViewImpl;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.utils.SWTRunnable;
import com.biglybt.util.MapUtils;

/**
 * MDI Entry that is a {@link CTabItem} and belongs wo {@link TabbedMDI}
 * <p>
 * TODO: VitalityImages
 */
public class TabbedEntry
	extends BaseMdiEntry implements DisposeListener
{
	private static final String SO_ID_ENTRY_WRAPPER = "mdi.content.item";

	private CTabItem swtItem;

	private SWTSkin skin;

	private boolean showonSWTItemSet;

	private boolean buildonSWTItemSet;

	private static long uniqueNumber = 0;

	public TabbedEntry(TabbedMDI mdi, SWTSkin skin, String id, String parentViewID) {
		super(mdi, id, parentViewID);
		this.skin = skin;
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
			}
		}

		return( false );
	}

	public SWTSkinObjectContainer
	buildStandAlone(
		SWTSkinObjectContainer		soParent )
	{
		Control control = null;

		//SWTSkin skin = soParent.getSkin();

		Composite parent = soParent.getComposite();

		String skinRef = getSkinRef();

		if ( skinRef != null ){

			Shell shell = parent.getShell();
			Cursor cursor = shell.getCursor();
			try {
				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

				// wrap skinRef with a container that we control visibility of
				// (invisible by default)
				SWTSkinObjectContainer soContents = (SWTSkinObjectContainer) skin.createSkinObject(
						"MdiContents." + uniqueNumber++, SO_ID_ENTRY_WRAPPER,
						soParent, null);

				SWTSkinObject skinObject = skin.createSkinObject(id, skinRef,
						soContents, getDatasourceCore());

				control = skinObject.getControl();
				control.setLayoutData(Utils.getFilledFormData());
				control.getParent().layout(true, true);

				soContents.setVisible( true );

				return( soContents );

			} finally {
				shell.setCursor(cursor);
			}
		} else {
			// XXX: This needs to be merged into BaseMDIEntry.initialize

			UISWTViewEventListener event_listener = getEventListener();

			if ( event_listener instanceof UISWTViewCoreEventListenerEx && ((UISWTViewCoreEventListenerEx)event_listener).isCloneable()){

				final UISWTViewImpl view = new UISWTViewImpl( getParentID(), id, true );

				try{
					view.setEventListener(((UISWTViewCoreEventListenerEx)event_listener).getClone(),false);

				}catch( Throwable e ){
					// shouldn't happen as we aren't asking for 'create' to occur which means it can't fail
					Debug.out( e );
				}

				view.setDatasource( datasource );

				try {
					SWTSkinObjectContainer soContents = (SWTSkinObjectContainer) skin.createSkinObject(
							"MdiIView." + uniqueNumber++, SO_ID_ENTRY_WRAPPER,
							soParent );

					parent.setBackgroundMode(SWT.INHERIT_NONE);

					final Composite viewComposite = soContents.getComposite();
					boolean doGridLayout = true;
					if (getControlType() == CONTROLTYPE_SKINOBJECT) {
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

					//swtItem.setText(view.getFullTitle());

					Composite iviewComposite = view.getComposite();
					control = iviewComposite;
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

					final UIUpdater updater = UIUpdaterSWT.getInstance();
					if (updater != null) {
						updater.addUpdater(
								new UIUpdatable() {

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
										return ("popout");
									}
								});
					}

					soContents.setVisible( true );

					view.triggerEvent(UISWTViewEvent.TYPE_FOCUSGAINED, null);

					return( soContents );

				} catch (Throwable e) {

					Debug.out(e);
				}
			}
		}

		return( null );
	}


	/* (non-Javadoc)
	 * @note SideBarEntrySWT is neary identical to this one.  Please keep them
	 *       in sync until commonalities are placed in BaseMdiEntry
	 */
	@Override
	public void build() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_build();
				TabbedEntry.super.build();
			}
		});
	}

	public boolean swt_build() {
		if (swtItem == null) {
			buildonSWTItemSet = true;
			return true;
		}
		buildonSWTItemSet = false;

		Control control = swtItem.getControl();
		if (control == null || control.isDisposed()) {
			Composite parent = swtItem.getParent();
			if ( parent.isDisposed()) {
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
							"MdiIView." + uniqueNumber++, "mdi.content.item",
							soParent);

					parent.setBackgroundMode(SWT.INHERIT_NONE);

					Composite viewComposite = soContents.getComposite();
					//viewComposite.setBackground(Colors.getSystemColor(parent.getDisplay(), SWT.COLOR_WIDGET_BACKGROUND));
					//viewComposite.setForeground(Colors.getSystemColor(parent.getDisplay(), SWT.COLOR_WIDGET_FOREGROUND));

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
						setEventListener(null, false);
					} catch (UISWTViewEventCancelledException e1) {
					}
					close(true);
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
		}

		return true;
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
		Utils.execSWTThreadLater(0, new SWTRunnable() {
			@Override
			public void runWithDisplay(Display display) {
				swt_show();
			}
		});
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


		if (swtItem.getParent().getSelection() != swtItem) {
			swtItem.getParent().setSelection(swtItem);
		}

		super.show();
	}

	/**
	 * Tabs don't have Vitality Image support (yet)
	 */
	@Override
	public MdiEntryVitalityImage addVitalityImage(String imageID) {
		return null; // new SideBarVitalityImageSWT(this, imageID);
	}

	@Override
	public boolean isCloseable() {
		// override.. we don't support non-closeable
		return ((TabbedMDI) getMDI()).isMainMDI ? true : super.isCloseable();
	}

	/* (non-Javadoc)
	 * @see BaseMdiEntry#setCloseable(boolean)
	 */
	@Override
	public void setCloseable(boolean closeable) {
		// override.. we don't support non-closeable for main
		if (((TabbedMDI) getMDI()).isMainMDI) {
			closeable = true;
		}
		super.setCloseable(closeable);
		Utils.execSWTThread(new SWTRunnable() {
			@Override
			public void runWithDisplay(Display display) {
				if (swtItem == null || swtItem.isDisposed()) {
					return;
				}
				swtItem.setShowClose(isCloseable());
			}
		});
	}

	public void setSwtItem(CTabItem swtItem) {
		this.swtItem = swtItem;
		if (swtItem == null) {
			setDisposed(true);
			return;
		}
		setDisposed(false);

		swtItem.addDisposeListener(this);
		String title = getTitle();
		if (title != null) {
			swtItem.setText(escapeAccelerators(title));
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

	public Item getSwtItem() {
		return swtItem;
	}

	/* (non-Javadoc)
	 * @see BaseMdiEntry#setTitle(java.lang.String)
	 */
	@Override
	public void setTitle(String title) {
		super.setTitle(title);

		if (swtItem != null) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (swtItem == null || swtItem.isDisposed()) {
						return;
					}
					swtItem.setText(escapeAccelerators(getTitle()));
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see BaseMdiEntry#getVitalityImages()
	 */
	@Override
	public MdiEntryVitalityImage[] getVitalityImages() {
		return new MdiEntryVitalityImage[0];
	}

	/* (non-Javadoc)
	 * @see BaseMdiEntry#close()
	 */
	@Override
	public boolean close(boolean forceClose) {
    // triggerCloseListener
		if (!super.close(forceClose)) {
			return false;
		}

		Utils.execSWTThread(new SWTRunnable() {
			@Override
			public void runWithDisplay(Display display) {
				if (swtItem != null && !swtItem.isDisposed()) {
					// this will triggerCloseListeners
					try {
						swtItem.dispose();
					}catch( SWTException e ){
						// getting internal 'Widget it disposed' here, ignore
					}
					swtItem = null;
				}
			}
		});
		return true;
	}

	@Override
	public void redraw() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (swtItem == null || swtItem.isDisposed()) {
					return;
				}
				// recalculate the size of tab (in case indicator text changed)
				swtItem.getParent().notifyListeners(SWT.Resize, new Event());
				// redraw indicator text
				swtItem.getParent().redraw();
			}
		});
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
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (swtItem == null || swtItem.isDisposed()) {
					return;
				}
				Image image = getImageLeft(null);
				swtItem.setImage(image);
			}
		});
	}

	@Override
	public void widgetDisposed(DisposeEvent e) {
		setSwtItem(null);

		SWTThread instance = SWTThread.getInstance();
		triggerCloseListeners(instance != null && !instance.isTerminated());

		try {
			setEventListener(null, false);
		} catch (UISWTViewEventCancelledException e1) {
		}

		SWTSkinObject so = getSkinObject();
		if (so != null) {
			setSkinObjectMaster(null);
			so.getSkin().removeSkinObject(so);
		}

		// delay saving of removing of auto-open flag.  If after the delay, we are
		// still alive, it's assumed the user invoked the close, and we should
		// remove the auto-open flag
		Utils.execSWTThreadLater(0, new SWTRunnable() {
			@Override
			public void runWithDisplay(Display display) {
				// even though execThreadLater will not run on close of app because
				// the display is disposed, do a double check of tree disposal just
				// in case.  We don't want to trigger close listeners or
				// remove autoopen parameters if the user is closing the app (as
				// opposed to closing  the sidebar)

				mdi.removeItem(TabbedEntry.this);
				mdi.removeEntryAutoOpen(id);
			}
		});
	}

	private String escapeAccelerators(String str) {
		if (str == null) {
			return (str);
		}

		return str.replaceAll("&", "&&");
	}

	@Override
	public void expandTo() {
	}

	@Override
	public void viewTitleInfoRefresh(ViewTitleInfo titleInfoToRefresh) {
		super.viewTitleInfoRefresh(titleInfoToRefresh);

		if (titleInfoToRefresh == null || this.viewTitleInfo != titleInfoToRefresh) {
			return;
		}
		if (isDisposed()) {
			return;
		}

		String newText = (String) viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_TEXT);
		if (newText != null) {
			setTitle(newText);
		} else {
			String titleID = getTitleID();
			if (titleID != null) {
				setTitleID(titleID);
			}
		}
		redraw();
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

	// @see MdiEntrySWT#addListener(MdiSWTMenuHackListener)
	@Override
	public void addListener(MdiSWTMenuHackListener l) {
		// TODO Auto-generated method stub
	}

	// @see MdiEntrySWT#removeListener(MdiSWTMenuHackListener)
	@Override
	public void removeListener(MdiSWTMenuHackListener l) {
		// TODO Auto-generated method stub
	}

	// @see BaseMdiEntry#setParentID(java.lang.String)
	@Override
	public void setParentID(String id) {
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

			boolean isActive = swtItem.getParent().getSelection() == swtItem;
			boolean isHeaderVisible = swtItem.isShowing();

			Point location = Utils.getLocationRelativeToShell(swtItem.getParent());

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
}
