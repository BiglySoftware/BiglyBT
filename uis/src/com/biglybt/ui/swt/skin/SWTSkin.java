/*
 * Created on May 29, 2006 4:01:04 PM
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
package com.biglybt.ui.swt.skin;

import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.biglybt.ui.IUIIntializer;
import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory.AEShell;

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.ui.skin.SkinProperties;
import com.biglybt.ui.swt.imageloader.ImageLoader;

/**
 * @author TuxPaper
 * @created May 29, 2006
 *
 */
public class SWTSkin
{
	public static final boolean DEBUG_VISIBILITIES = false;

	private static final SWTSkinObjectListener[] NOLISTENERS = new SWTSkinObjectListener[0];

	private static SWTSkin default_instance;

	protected static synchronized SWTSkin
	getDefaultInstance()
	{
		if ( default_instance == null ){

			default_instance = new SWTSkin();
		}

		return( default_instance );
	}

	public boolean DEBUGLAYOUT = System.getProperty("debuglayout") != null;

	private Map<SkinProperties, ImageLoader> mapImageLoaders = new ConcurrentHashMap<>();

	private final SWTSkinProperties skinProperties;
	private final boolean is_default;

	private static Listener handCursorListener;
	private static Cursor handCursor;

	//private Listener ontopPaintListener;

	// Key = Skin Object ID; Value = Array of SWTSkinObject
	private HashMap<String, SWTSkinObject[]> mapIDsToSOs = new HashMap<>();

	private AEMonitor mon_MapIDsToSOs = new AEMonitor("mapIDsToControls");

	// Key = TabSet ID; Value = SWTSkinTabSet
	private HashMap<String, SWTSkinTabSet> mapTabSetToControls = new HashMap<>();

	// Key = Widget ID; Value = Array of SWTSkinObject
	private HashMap<String, SWTSkinObject[]> mapPublicViewIDsToSOs = new HashMap<>();

	private AEMonitor mon_mapPublicViewIDsToSOs = new AEMonitor("mapPVIDsToSOs");

	private HashMap<String, ArrayList<SWTSkinObjectListener>> mapPublicViewIDsToListeners = new HashMap<>();

	private AEMonitor mapPublicViewIDsToListeners_mon = new AEMonitor(
			"mapPVIDsToListeners");

	private ArrayList<SWTSkinObjectBasic> ontopImages = new ArrayList<>();

	private Composite skinComposite;

	private boolean bLayoutComplete = false;

	private CopyOnWriteList<SWTSkinLayoutCompleteListener> listenersLayoutComplete = new CopyOnWriteList<>();

	private int currentSkinObjectcreationCount = 0;

	private String startID;

	private boolean autoSizeOnLayout 		= true;
	private boolean autoSizeOnLayoutForce	= false;
	
	/**
	 *
	 */
	protected SWTSkin() {
		this(new SWTSkinPropertiesImpl(), true );
	}

	protected SWTSkin(ClassLoader classLoader, String skinPath, String mainSkinFile) {
		this(new SWTSkinPropertiesImpl(classLoader, skinPath, mainSkinFile), false );
	}

	private SWTSkin(SWTSkinProperties skinProperties, boolean is_default) {
		
		this.skinProperties = skinProperties;
		this.is_default = is_default;
		// the default skin gets the default image loader.  We don't add it to
		// the mapImageLoaders because non-skin objects may use the image loader
		// mapImageLoaders is used to dispose of images when the skin is disposed
		if ( is_default ) {
			ImageLoader imageLoader = ImageLoader.getInstance();
			imageLoader.addSkinProperties(skinProperties);
		}

		/*
		ontopPaintListener = new Listener() {
			public void handleEvent(Event event) {
				for (Iterator iter = ontopImages.iterator(); iter.hasNext();) {
					SWTSkinObject skinObject = (SWTSkinObject) iter.next();

					Control control = skinObject.getControl();
					if (control == null) {
						continue;
					}
					Rectangle bounds = control.getBounds();
					Point point = control.toDisplay(0, 0);
					bounds.x = point.x;
					bounds.y = point.y;

					Rectangle eventBounds = event.getBounds();
					point = ((Control) event.widget).toDisplay(0, 0);
					eventBounds.x += point.x;
					eventBounds.y += point.y;

					//System.out.println(eventBounds + ";" + bounds);

					if (eventBounds.intersects(bounds)) {
						Point dst = new Point(bounds.x - point.x, bounds.y - point.y);

						//System.out.println("Painting on " + event.widget + " at " + dst);
						Image image = (Image) control.getData("image");
						// TODO: Clipping otherwise alpha will multiply
						//event.gc.setClipping(eventBounds);
						event.gc.drawImage(image, dst.x, dst.y);
					}
				}
			}
		};
		*/
	}

	public String
	getSkinID()
	{
		return( skinProperties.getSkinID());
	}
	
	public ImageLoader getImageLoader(SkinProperties properties) {
		if (is_default) {
			return ImageLoader.getInstance();
		}

		ImageLoader loader = mapImageLoaders.get(properties);

		if (loader != null) {
			return loader;
		}

		loader = new ImageLoader(Display.getDefault(), properties);
		mapImageLoaders.put(properties, loader);

		return loader;
	}

	public void addToControlMap(SWTSkinObject skinObject) {
		String sID = skinObject.getSkinObjectID();
		if (DEBUGLAYOUT) {
			System.out.println("addToControlMap: " + sID + " : " + skinObject);
		}
		addToSOArrayMap(mapIDsToSOs, mon_MapIDsToSOs, sID, skinObject);

		// For SWT layout -- add a reverse lookup
		Control control = skinObject.getControl();
		if (control != null) {
			control.setData("ConfigID", skinObject.getConfigID());
			control.setData("SkinID", sID);
		}
	}

	private void addToSOArrayMap(Map<String, SWTSkinObject[]> arrayMap,
			AEMonitor mon, String key, SWTSkinObject object) {
		if (mon != null) {
			mon.enter();
		}
		try {
		SWTSkinObject[] existingObjects = arrayMap.get(key);
  		if (existingObjects != null) {

  			boolean bAlreadyPresent = false;
  			for (int i = 0; i < existingObjects.length; i++) {
  				//System.out.println(".." + existingObjects[i]);
  				if (existingObjects[i] != null && existingObjects[i].equals(object)) {
  					bAlreadyPresent = true;
  					System.err.println("already present: " + key + "; " + object
  							+ "; existing: " + existingObjects[i] + " via "
  							+ Debug.getCompressedStackTrace());
  					break;
  				}
  			}

  			if (!bAlreadyPresent) {
  				int length = existingObjects.length;
  				SWTSkinObject[] newObjects = new SWTSkinObject[length + 1];
  				System.arraycopy(existingObjects, 0, newObjects, 0, length);
  				newObjects[length] = object;

  				arrayMap.put(key, newObjects);
  				//				System.out.println("addToArrayMap: " + key + " : " + object + " #"
  				//						+ (length + 1));
  			}
  		} else {
  			arrayMap.put(key, new SWTSkinObject[] {
  				object
  			});
  		}
		} finally {
			if (mon != null) {
				mon.exit();
			}
		}
	}

	private Object getFromSOArrayMap(Map<String, SWTSkinObject[]> arrayMap,
			Object key, SWTSkinObject parent) {
		if (parent == null) {
			return null;
		}

		SWTSkinObject[] objects = arrayMap.get(key);
		if (objects == null) {
			return null;
		}

		for (int i = 0; i < objects.length; i++) {
			SWTSkinObject object = objects[i];
			SWTSkinObject thisParent = object;
			while (thisParent != null) {
				if (thisParent.equals(parent)) {
					return object;
				}
				thisParent = thisParent.getParent();
			}
		}

		return null;
	}

	private void setSkinObjectViewID(SWTSkinObject skinObject, String sViewID) {
		addToSOArrayMap(mapPublicViewIDsToSOs, mon_mapPublicViewIDsToSOs, sViewID,
				skinObject);
	}

	/*
	public void dumpObjects() {
		System.out.println("=====");
		FormData formdata;
		for (Iterator iter = mapIDsToControls.keySet().iterator(); iter.hasNext();) {
			String sID = (String) iter.next();
			Control control = getSkinObjectByID(sID).getControl();

			formdata = (FormData) control.getLayoutData();

			System.out.println(sID);

			sID += ".attach.";

			if (formdata.left != null) {
				System.out.println(sID + "left=" + getAttachLine(formdata.left));
			}
			if (formdata.right != null) {
				System.out.println(sID + "right=" + getAttachLine(formdata.right));
			}
			if (formdata.top != null) {
				System.out.println(sID + "top=" + getAttachLine(formdata.top));
			}
			if (formdata.bottom != null) {
				System.out.println(sID + "bottom=" + getAttachLine(formdata.bottom));
			}
		}
	}
	*/

	public SWTSkinObject getSkinObjectByID(String sID) {
		SWTSkinObject[] objects = mapIDsToSOs.get(sID);
		if (objects == null || objects.length == 0) {
			return null;
		}

		return objects[0];
	}

	public SWTSkinObject getSkinObjectByID(String sID, SWTSkinObject parent) {
		if (parent == null) {
			// XXX Search for parent is shell directly
			return getSkinObjectByID(sID);
		}

		return (SWTSkinObject) getFromSOArrayMap(mapIDsToSOs, sID, parent);
	}

	public SWTSkinObject getSkinObject(String sViewID) {
		SWTSkinObject[] objects = mapPublicViewIDsToSOs.get(sViewID);
		if (objects == null || objects.length == 0) {
			return createUnattachedView(sViewID, null);
		}

		return objects[0];
	}

	/**
	 * @param viewID
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	private SWTSkinObject createUnattachedView(String viewID, SWTSkinObject parent) {
		String unattachedView = skinProperties.getStringValue("UnattachedView."
				+ viewID);
		if (unattachedView != null) {
			if (!Utils.isThisThreadSWT()) {
				Debug.out("View "
						+ viewID
						+ " does not exist.  Skipping unattach check because not in SWT thread");
				return null;
			}
			if (unattachedView.indexOf(',') > 0) {
				String[] split = RegExUtil.PAT_SPLIT_COMMA.split(unattachedView);
				String parentID = split[1];
				SWTSkinObject soParent = getSkinObjectByID(parentID, parent);
				if (soParent != null) {
					String configID = split[0];
					return createSkinObject(configID, configID, soParent);
				}
			}

			SWTSkinObjectListener[] listeners = getSkinObjectListeners(viewID);
			for (int i = 0; i < listeners.length; i++) {
				SWTSkinObjectListener l = listeners[i];
				Object o = l.eventOccured(null,
						SWTSkinObjectListener.EVENT_CREATE_REQUEST, new String[] {
							viewID,
							unattachedView
						});
				if (o instanceof SWTSkinObject) {
					return (SWTSkinObject) o;
				}
			}
		}
		return null;
	}

	public SWTSkinObject getSkinObject(String sViewID, SWTSkinObject parent) {
		if (parent == null) {
			// XXX Search for parent is shell directly
			return getSkinObject(sViewID);
		}

		String parentViewID = parent.getViewID();
		if (parentViewID != null && parentViewID.equals(sViewID)) {
			return parent;
		}

		SWTSkinObject so = (SWTSkinObject) getFromSOArrayMap(
				mapPublicViewIDsToSOs, sViewID, parent);
		if (so == null) {
			so = createUnattachedView(sViewID, parent);
		}

		return so;
	}

	public SWTSkinTabSet getTabSet(String sID) {
		return mapTabSetToControls.get(sID);
	}

	public SWTSkinObjectTab activateTab(SWTSkinObject skinObjectInTab) {
		if (skinObjectInTab == null) {
			return null;
		}

		if (skinObjectInTab instanceof SWTSkinObjectTab) {
			SWTSkinObjectTab tab = (SWTSkinObjectTab) skinObjectInTab;
			tab.getTabset().setActiveTab(tab);
			return tab;
		}

		for (Iterator<SWTSkinTabSet> iter = mapTabSetToControls.values().iterator(); iter.hasNext();) {
			SWTSkinTabSet tabset = iter.next();

			SWTSkinObjectTab[] tabs = tabset.getTabs();
			boolean bHasSkinObject = false;
			for (int i = 0; i < tabs.length; i++) {
				SWTSkinObjectTab tab = tabs[i];
				SWTSkinObject[] activeWidgets = tab.getActiveWidgets(true);
				for (int j = 0; j < activeWidgets.length; j++) {
					SWTSkinObject object = activeWidgets[j];
					//System.out.println("check " + tab + ";" + object + " for " + skinObjectInTab);
					if (hasSkinObject(object, skinObjectInTab)) {
						//System.out.println("FOUND");
						tabset.setActiveTab(tab);
						return tab;
					}
				}
			}
		}
		System.out.println("NOT FOUND" + skinObjectInTab);
		return null;
	}

	private boolean hasSkinObject(SWTSkinObject start, SWTSkinObject skinObject) {
		if (start instanceof SWTSkinObjectContainer) {
			SWTSkinObject[] children = ((SWTSkinObjectContainer) start).getChildren();
			for (int i = 0; i < children.length; i++) {
				SWTSkinObject object = children[i];
				//System.out.println("  check " + object + " in " + start + " for " + skinObject);
				if (hasSkinObject(object, skinObject)) {
					return true;
				}
			}
		}
		//System.out.println("  check " + start + " for " + skinObject);
		return skinObject.equals(start);
	}

	public SWTSkinTabSet getTabSet(SWTSkinObject skinObject) {
		String sTabSetID = skinObject.getProperties().getStringValue(
				skinObject.getConfigID() + ".tabset");
		return getTabSet(sTabSetID);
	}

	public boolean setActiveTab(String sTabSetID, String sTabViewID) {
		SWTSkinTabSet tabSet = getTabSet(sTabSetID);
		if (tabSet == null) {
			System.err.println(sTabSetID);
			return false;
		}

		return tabSet.setActiveTab(sTabViewID);
	}

	/**
	 * @since 3.0.5.3
	 */
	public void initialize(Composite skincomp, String startID) {
		initialize(skincomp, startID, null);
	}

	public void initialize(final Composite skincomp, String startID,
			IUIIntializer uiInitializer) {

		this.skinComposite = skincomp;
		this.startID = startID;
		FormLayout layout = new FormLayout();
		skinComposite.setLayout(layout);
		if (Constants.isOSX) { // not sure if we need this anymore
			skinComposite.setBackgroundMode(SWT.INHERIT_DEFAULT);
		}

		skinComposite.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				disposeSkin();
			}
		});

		Listener l = new Listener() {
			Control lastControl = null;

			@Override
			public void handleEvent(Event event) {
				if (skinComposite.isDisposed() && event.display != null) {
					event.display.removeFilter(SWT.MouseMove, this);
					event.display.removeFilter(SWT.MouseExit, this);
					return;
				}
				Control cursorControl = skinComposite.getDisplay().getCursorControl();
				//System.out.println("move from " + (lastControl == null ? null : lastControl.handle) + " to " + (cursorControl == null ? "null" : cursorControl.handle));
				if (cursorControl != lastControl) {
					Point cursorLocation = skinComposite.getDisplay().getCursorLocation();
					while (lastControl != null && !lastControl.isDisposed()) {
						Point cursorLocationInControl = lastControl.toControl(cursorLocation);
						Point size = lastControl.getSize();
						if (!new Rectangle(0, 0, size.x, size.y).contains(cursorLocationInControl)) {
  						SWTSkinObjectBasic so = (SWTSkinObjectBasic) lastControl.getData("SkinObject");
  						if (so != null) {
  							so.switchSuffix("", 3, false, false);
  						}
						}
						lastControl = lastControl.getParent();
					}
					lastControl = cursorControl;

					while (cursorControl != null) {
						SWTSkinObjectBasic so = (SWTSkinObjectBasic) cursorControl.getData("SkinObject");
						if (so != null) {
							so.switchSuffix("-over", 3, false, false);
						}

						cursorControl = cursorControl.getParent();
					}
				}
			}
		};
		Display display = skinComposite.getDisplay();
		display.addFilter(SWT.MouseMove, l);
		// can't just add a MouseExit listener to the shell, because it doesn't
		// get fired when a control is on the edge
		display.addFilter(SWT.MouseExit, l);
		skinComposite.addListener(SWT.Deactivate, l);
		skinComposite.addListener(SWT.Activate, l);

		/****** REPLACED BY MouseMove
		// When shell activates or deactivates, send a MouseEnter or MouseExit
		// This fixes the problem where we are hovering over a skinobject with
		// a "-over" state, we tab away, move the mouse, and tab back again.
		// Without this code, the skinobject would still be in "-over" state
		shell.addListener(SWT.Deactivate, new Listener() {
			public void handleEvent(Event event) {
				Control cursorControl = shell.getDisplay().getCursorControl();
				if (cursorControl != null) {
					while (cursorControl != null) {
						Event mouseExitEvent = new Event();
						mouseExitEvent.type = SWT.MouseExit;
						mouseExitEvent.widget = cursorControl;
						shell.getDisplay().post(mouseExitEvent);
						System.out.println(cursorControl.getData("SkinObject"));

						cursorControl = cursorControl.getParent();
					}
				}
			}
		});
		shell.addListener(SWT.Activate, new Listener() {
			public void handleEvent(Event event) {
				Control cursorControl = shell.getDisplay().getCursorControl();
				if (cursorControl != null) {
					while (cursorControl != null) {
						Event mouseExitEvent = new Event();
						mouseExitEvent.type = SWT.MouseEnter;
						mouseExitEvent.widget = cursorControl;
						shell.getDisplay().post(mouseExitEvent);

						cursorControl = cursorControl.getParent();
					}
				}
			}
		});
		*/

		Color bg = skinProperties.getColor(startID + ".color");
		if (bg != null) {
			skinComposite.setBackground(bg);
		}

		Color fg = skinProperties.getColor(startID + ".fgcolor");
		if (fg != null) {
			skinComposite.setForeground(fg);
		}


		int width = skinProperties.getPxValue(startID + ".width", -1);
		int height = skinProperties.getPxValue(startID + ".height", -1);
		if (width > 0 && height > 0) {
			skinComposite.setSize(width, height);
		}

		// We handle cases where width || height < 0 later in layout()

		if (skinComposite instanceof Shell) {
			Shell shell = (Shell) skinComposite;
			int minWidth = skinProperties.getPxValue(startID + ".minwidth", -1);
			int minHeight = skinProperties.getPxValue(startID + ".minheight", -1);
			if (minWidth > 0 || minHeight > 0) {
				Point minimumSize = shell.getMinimumSize();
				shell.setMinimumSize(minWidth > 0 ? minWidth : minimumSize.x,
						minHeight > 0 ? minHeight : minimumSize.y);
			}
			String title = skinProperties.getStringValue(startID + ".title",
					(String) null);
			if (title != null) {
				((Shell) skinComposite).setText(title);
			}
		}

		String[] sMainGroups = skinProperties.getStringArray(startID + ".widgets");
		if (sMainGroups == null) {
			System.out.println("NO " + startID + ".widgets!!");
			sMainGroups = new String[] {};
		}

		for (int i = 0; i < sMainGroups.length; i++) {
			String sID = sMainGroups[i];

			if (DEBUGLAYOUT) {
				System.out.println("Container: " + sID);
			}

			if (uiInitializer != null) {
				uiInitializer.increaseProgress();
			}

			linkIDtoParent(skinProperties, sID, sID, null, false, true, null);
		}
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	private void disposeSkin() {
		ImageLoader imageLoaderInstance = ImageLoader.getInstance();
		for (Iterator<ImageLoader> iter = mapImageLoaders.values().iterator(); iter.hasNext();) {
			ImageLoader loader = iter.next();
			if (loader == imageLoaderInstance) {
				loader.unLoadImages();
			} else {
				loader.dispose();
			}
		}
		mapImageLoaders.clear();
	}

	/**
	 *
	 *
	private void addPaintListenerToAll(Control control) {
		// XXX: Bug: When paint listener is set to shell, browser widget will flicker on OSX when resizing
		if (!(control instanceof Shell)) {
			control.addListener(SWT.Paint, ontopPaintListener);
		}

		if (control instanceof Composite) {
			Composite composite = (Composite) control;

			Control[] children = composite.getChildren();
			for (int i = 0; i < children.length; i++) {
				addPaintListenerToAll(children[i]);
			}
		}
	}
	*/

	public void layout(SWTSkinObject soStart) {
		if (soStart instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer soContainer = (SWTSkinObjectContainer) soStart;
			SWTSkinObject[] children = soContainer.getChildren();
			for (SWTSkinObject so : children) {
				layout(so);
			}
		}

		if (DEBUGLAYOUT) {
			System.out.println("attachControl " + soStart.toString());
		}
		attachControl(soStart);
	}

	public void layout() {
		if (DEBUGLAYOUT) {
			System.out.println("==== Start Apply Layout");
		}
		// Apply layout data from skin
		Object[] values = mapIDsToSOs.values().toArray();
		for (int i = 0; i < values.length; i++) {
			SWTSkinObject[] skinObjects = (SWTSkinObject[]) values[i];
			if (skinObjects != null) {
				for (int j = 0; j < skinObjects.length; j++) {
					SWTSkinObject skinObject = skinObjects[j];
					if (DEBUGLAYOUT) {
						System.out.println("Apply Layout for " + skinObject);
					}
					attachControl(skinObject);
				}
			}
		}

		// Disabled due to Browser flickering
		//addPaintListenerToAll(shell);

		if (DEBUGLAYOUT) {
			System.out.println("====  Applied Layout");
		}
		bLayoutComplete = true;

		int width = skinProperties.getPxValue(startID + ".width", -1);
		int height = skinProperties.getPxValue(startID + ".height", -1);

		if (autoSizeOnLayout) {
			if (width > 0 && height == -1) {
				Point computeSize = skinComposite.computeSize(width, SWT.DEFAULT);
				skinComposite.setSize(computeSize);
			} else if (height > 0 && width == -1) {
				Point computeSize = skinComposite.computeSize(SWT.DEFAULT, height);
				skinComposite.setSize(computeSize);
			} else if (height > 0 && width > 0) {
				skinComposite.setSize(width, height);

			}else{
				if ( autoSizeOnLayoutForce ){
					Point computeSize = skinComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT);
					skinComposite.setSize(computeSize);
				}
			}
		} else {
			try {
				skinComposite.requestLayout();
			}catch( Throwable e ) {
				// old swt no support
			}
		}

		for (SWTSkinLayoutCompleteListener l : listenersLayoutComplete) {
			l.skinLayoutCompleted();
		}
		listenersLayoutComplete.clear();

		if (DEBUGLAYOUT) {
			System.out.println("==== End Apply Layout");
		}

		skinProperties.clearCache();
	}

	void attachControl(SWTSkinObject skinObject) {
		if (skinObject == null) {
			return;
		}

		Control controlToLayout = skinObject.getControl();

		if (controlToLayout == null || controlToLayout.isDisposed()) {
			return;
		}

		if (controlToLayout.getData("skin.layedout") != null) {
			return;
		}

		String sConfigID = skinObject.getConfigID();
		SWTSkinProperties properties = skinObject.getProperties();

		final String[] sDirections = {
			"top",
			"bottom",
			"left",
			"right"
		};

		// Because layout data is cached, we can't just set the data's properties
		// We need to create a brand new FormData.

		Object data = controlToLayout.getLayoutData();
		if (data != null && !(data instanceof FormData)) {
			return;
		}
		FormData oldFormData = (FormData) controlToLayout.getLayoutData();
		if (oldFormData == null) {
			oldFormData = new FormData();
		}

		FormData newFormData = new FormData(oldFormData.width, oldFormData.height);

		String templateID = properties.getStringValue(sConfigID
				+ ".attach.template");
		if (templateID == null) {
			//templateID = skinObject.getSkinObjectID();
		}

		boolean debugControl = controlToLayout.getData("DEBUG") != null;

		for (int i = 0; i < sDirections.length; i++) {
			Control control = null;
			int offset = 0;
			int percent = 0;
			String sAlign = null;
			int align = SWT.DEFAULT;

			// grab any defaults from existing attachment
			FormAttachment attachment;
			switch (i) {
				case 0:
					attachment = oldFormData.top;
					break;

				case 1:
					attachment = oldFormData.bottom;
					break;

				case 2:
					attachment = oldFormData.left;
					break;

				case 3:
					attachment = oldFormData.right;
					break;

				default:
					attachment = null;
			}

			if (attachment != null) {
				control = attachment.control;
				offset = attachment.offset;
				align = attachment.alignment;
				// XXX Assumed: Denominator is 100
				percent = attachment.numerator;
			}

			// parse skin config

			String suffix = ".attach." + sDirections[i];
			String prefix = sConfigID;
			String[] sParams;

			sParams = properties.getStringArray(sConfigID + suffix);
			if (sParams == null && templateID != null) {
				sParams = properties.getStringArray(templateID + suffix);
				prefix = templateID;
			}

			if (sParams == null) {
				if (attachment != null) {
					if (control == null) {
						attachment = new FormAttachment(percent, offset);
					} else {
						attachment = new FormAttachment(control, offset, align);
					}
				}

			} else if (sParams.length == 0
					|| (sParams.length == 1 && sParams[0].length() == 0)) {
				attachment = null;
			} else {

				if (sParams[0].length() > 0 && Character.isDigit(sParams[0].charAt(0))) {
					// Percent, Offset
					try {
						percent = Integer.parseInt(sParams[0]);
					} catch (Exception e) {
					}

					if (sParams.length > 1) {
						try {
							String value = sParams[1];
							if (value.endsWith("rem")) {
								float em = Float.parseFloat(value.substring(0, value.length() - 3));

								offset = (int) (properties.getEmHeightPX() * em);
							} else {
								offset = Integer.parseInt(value);
							}
						} catch (Exception e) {
						}
					}

					attachment = new FormAttachment(percent, offset);

				} else {
					// Object, Offset, Alignment
					String sWidget = sParams[0];

					SWTSkinObject configSkinObject = getSkinObjectByID(sWidget,
							skinObject.getParent());
					int iNextPos;
					if (configSkinObject != null) {
						control = configSkinObject.getControl();

						iNextPos = 1;
					} else {
						iNextPos = 0;

						if (sWidget.length() != 0) {
							System.err.println("ERROR: Trying to attach " + sDirections[i]
									+ " of widget '" + skinObject + "' to non-existant widget '"
									+ sWidget + "'.  Attachment Parameters: "
									+ properties.getStringValue(prefix + suffix));
						}
					}

					for (int j = iNextPos; j < sParams.length; j++) {
						if (sParams[j].length() > 0) {
							char c = sParams[j].charAt(0);
							if (Character.isDigit(c) || c == '-') {
								try {
									String value = sParams[j];
									if (value.endsWith("rem")) {
										float em = Float.parseFloat(value.substring(0, value.length() - 3));

										offset = (int) (properties.getEmHeightPX() * em);
									} else {
										offset = Integer.parseInt(value);
									}
								} catch (Exception e) {
								}
							} else {
								sAlign = sParams[j];
							}
						}
					}

					if (sAlign != null) {
						align = SWTSkinUtils.getAlignment(sAlign, align);
					}

					attachment = new FormAttachment(control, offset, align);
				}
			}

			if (debugControl && attachment != null) {
				if (controlToLayout instanceof Group) {
					Group group = (Group) controlToLayout;
					String sValue = properties.getStringValue(prefix + suffix);
					String sText = group.getText() + "; "
							+ sDirections[i].substring(0, 1) + "="
							+ (sValue == null ? "(def)" : sValue);
					if (sText.length() < 20) {
						group.setText(sText);
					}
					Utils.setTT(group,sText);
				}
			}

			if (DEBUGLAYOUT) {
				System.out.println("Attach: " + sConfigID + suffix + ": "
						+ properties.getStringValue(prefix + suffix) + "/" + attachment);
			}

			// create new attachment
			switch (i) {
				case 0:
					newFormData.top = attachment;
					break;

				case 1:
					newFormData.bottom = attachment;
					break;

				case 2:
					newFormData.left = attachment;
					break;

				case 3:
					newFormData.right = attachment;
					break;
			}

		}

		if (!skinObject.getDefaultVisibility()) {
			if (controlToLayout.getData("oldSize") == null) {
    		controlToLayout.setData("oldSize", new Point(properties.getPxValue(sConfigID + ".width",
    				SWT.DEFAULT), properties.getPxValue(sConfigID + ".height",
    						SWT.DEFAULT)));
			}
//			if (newFormData.width != 0 && newFormData.height != 0) {
//				controlToLayout.setData("oldSize", new Point(newFormData.width,
//						newFormData.height));
//			}
			newFormData.width = 0;
			newFormData.height = 0;
		} else {
			int h = properties.getPxValue(sConfigID + ".height", -2);
			int w = properties.getPxValue(sConfigID + ".width", -2);
			if (h != -2) {
				newFormData.height = h;
			}
			if (w != -2) {
				newFormData.width = w;
			}
		}
		controlToLayout.setLayoutData(newFormData);
		controlToLayout.setData("skin.layedout", "");
		skinObject.layoutComplete();
	}

	private SWTSkinObject createContainer(final SWTSkinProperties properties,
			String sID, final String sConfigID, String[] sTypeParams, SWTSkinObject parentSkinObject,
			boolean bForceCreate, boolean bPropogate, SWTSkinObject intoSkinObject) {
		String[] sItems = properties.getStringArray(sConfigID + ".widgets");
		if (sItems == null && !bForceCreate) {
			return null;
		}

		if (DEBUGLAYOUT) {
			System.out.println("createContainer: " + sID + ";"
					+ properties.getStringValue(sConfigID + ".widgets") + " on " + parentSkinObject);
		}

		SWTSkinObject skinObject = getSkinObjectByID(sID, parentSkinObject);

		if (skinObject == null) {
			if (intoSkinObject == null) {
				skinObject = new SWTSkinObjectContainer(this, properties, sID,
						sConfigID, sTypeParams, parentSkinObject);
				addToControlMap(skinObject);
			} else {
				skinObject = intoSkinObject;
			}
		} else {
			if (!(skinObject instanceof SWTSkinObjectContainer)) {
				return skinObject;
			}
		}

		if (!bPropogate) {
			bPropogate = properties.getIntValue(sConfigID + ".propogate", 0) == 1;
		}

		if (!bPropogate && (parentSkinObject instanceof SWTSkinObjectContainer)) {
			bPropogate = ((SWTSkinObjectContainer) parentSkinObject).getPropogation();
		}
		if (bPropogate) {
			((SWTSkinObjectContainer) skinObject).setPropogation(true);
		}

		if (sItems != null) {
			addContainerChildren(skinObject, sItems, properties);
		}

		return skinObject;
	}

	private void addContainerChildren(SWTSkinObject skinObject, String[] sItems,
			SWTSkinProperties properties) {
		String[] paramValues = null;
		if (properties instanceof SWTSkinPropertiesParam) {
			paramValues = ((SWTSkinPropertiesParam) properties).getParamValues();
		}
		// Cloning is only for one level.  Children get the original properties
		// object
		if (properties instanceof SWTSkinPropertiesClone) {
			properties = ((SWTSkinPropertiesClone) properties).getOriginalProperties();
		}

		// Propogate any parameter values.
		// XXX This could get ugly, we should could the # of
		//     SWTSkinPropertiesParam to determine if this needs optimizing
		//     ie. if a top container has paramValues, every child will get a new
		//         object.  How would this affect memory/performance?
		if (paramValues != null) {
			properties = new SWTSkinPropertiesParamImpl(properties, paramValues);
		}

		SWTSkinObject[] soChildren = new SWTSkinObject[sItems.length];
		for (int i = 0; i < sItems.length; i++) {
			String sItemID = sItems[i];
			soChildren[i] = linkIDtoParent(properties, sItemID, sItemID, skinObject,
					false, true, null);
		}
		if (bLayoutComplete) {
			// attach only after all children are added
			for (SWTSkinObject so : soChildren) {
				if (so != null) {
					attachControl(so);
				}
			}
		}
	}

	private SWTSkinObject createSash(SWTSkinProperties properties, String sID,
			String sConfigID, SWTSkinObject parentSkinObject, final boolean bVertical) {
		int style = bVertical ? SWT.VERTICAL : SWT.HORIZONTAL;

		final String[] sItems = properties.getStringArray(sConfigID + ".widgets");

		SWTSkinObject skinObject;

		Composite createOn;
		if (parentSkinObject == null) {
			createOn = skinComposite;
		} else {
			createOn = (Composite) parentSkinObject.getControl();
		}

		if (sItems == null) {
			// No widgets, so it's a sash
			Sash sash = new Sash(createOn, style);
			skinObject = new SWTSkinObjectBasic(this, properties, sash, sID,
					sConfigID, "sash", parentSkinObject);
			addToControlMap(skinObject);

			sash.setBackground(Colors.getSystemColor(sash.getDisplay(), SWT.COLOR_RED));

			sash.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					Sash sash = (Sash) e.widget;
					final boolean FASTDRAG = true;

					if (FASTDRAG && e.detail == SWT.DRAG) {
						return;
					}

					Rectangle parentArea = sash.getParent().getClientArea();

					FormData formData = (FormData) sash.getLayoutData();
					formData.left = new FormAttachment(e.x * 100 / parentArea.width);
					sash.getParent().layout();
				}
			});
		} else {
			// Widgets exist, so use a SashForm to split them
			final SashForm sashForm = new SashForm(createOn, style);
			skinObject = new SWTSkinObjectContainer(this, properties, sashForm, sID,
					sConfigID, "sash", parentSkinObject);
			addToControlMap(skinObject);

			int iSashWidth = properties.getIntValue(sConfigID + ".sash.width", -1);

			if (iSashWidth > 0) {
				sashForm.SASH_WIDTH = iSashWidth;
			}

			for (int i = 0; i < sItems.length; i++) {
				String sChildID = sItems[i];
				linkIDtoParent(properties, sChildID, sChildID, skinObject, false, true, null);
			}
		}

		return skinObject;
	}

	private SWTSkinObject createMySash(SWTSkinProperties properties,
			final String sID, String sConfigID, String[] typeParams,
			SWTSkinObject parentSkinObject, final boolean bVertical) {
		SWTSkinObject skinObject = new SWTSkinObjectSash(this, properties, sID,
				sConfigID, typeParams, parentSkinObject, bVertical);
		addToControlMap(skinObject);

		return skinObject;
	}

	/**
	 * Create a tab using a template.
	 * <p>
	 * (objectid).view.template.(sTemplateKey)=(Reference to Template skin object)
	 *
	 * @param sID ID to give the new tab
	 * @param sTemplateKey Template Key to read to get the tab's template skin object
	 * @param tabHolder Where to read the template key from
	 *
	 * @return The new tab, or null if tab could not be created
	 */
	public SWTSkinObjectTab createTab(String sID, String sTemplateKey,
			SWTSkinObject tabHolder) {
		String sTemplateID = SWTSkinTabSet.getTemplateID(this, tabHolder,
				sTemplateKey);

		if (sTemplateID == null) {
			return null;
		}

		SWTSkinObject skinObject = linkIDtoParent(skinProperties, sID, sTemplateID,
				tabHolder, true, true, null);

		//		SWTSkinObjectTab skinObject = _createTab(skinProperties, sID, sTemplateID,
		//				tabHolder);
		if (bLayoutComplete && skinObject != null) {
			((Composite) skinObject.getControl()).getParent().layout(true);
		}
		if (skinObject instanceof SWTSkinObjectTab) {
			return (SWTSkinObjectTab) skinObject;
		}

		System.err.println(skinObject + " not a SWTSkinObjectTab! Template: "
				+ sTemplateID);
		return null;
	}

	private SWTSkinObjectTab _createTab(SWTSkinProperties properties, String sID,
			String sConfigID, SWTSkinObject parentSkinObject) {
		//System.out.println("createTab " + sID + ", " + sConfigID + ", " + sParentID);

		SWTSkinObjectTab skinObjectTab = new SWTSkinObjectTab(this, properties,
				sID, sConfigID, parentSkinObject);
		createContainer(properties, sID, sConfigID, null, parentSkinObject, true, true,
				skinObjectTab);

		addToControlMap(skinObjectTab);

		String sTabSet = properties.getStringValue(sConfigID + ".tabset", "default");

		SWTSkinTabSet tabset = mapTabSetToControls.get(sTabSet);
		if (tabset == null) {
			tabset = new SWTSkinTabSet(this, sTabSet);
			mapTabSetToControls.put(sTabSet, tabset);
			if (DEBUGLAYOUT) {
				System.out.println("New TabSet: " + sTabSet);
			}
		}
		tabset.addTab(skinObjectTab);
		if (DEBUGLAYOUT) {
			System.out.println("Tab " + sID + " added");
		}

		return skinObjectTab;
	}

	private SWTSkinObject createTextLabel(SWTSkinProperties properties,
			String sID, String sConfigID, String[] typeParams,
			SWTSkinObject parentSkinObject) {
		if (DEBUGLAYOUT) {
			System.out.println("createTextLabel: " + sID + " on " + parentSkinObject);
		}

		SWTSkinObject skinObject = new SWTSkinObjectText2(this, properties, sID,
				sConfigID, typeParams, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	private SWTSkinObject createSlider(SWTSkinProperties properties, String sID,
			String sConfigID, String[] typeParams, SWTSkinObject parentSkinObject) {
		SWTSkinObject skinObject = new SWTSkinObjectSlider(this, properties, sID,
				sConfigID, typeParams, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	public Composite getShell() {
		return skinComposite;
	}

	//	private void createTextWidget(final String sConfigID) {
	//		SWTSkinObject parent = getParent(sConfigID);
	//		if (parent == null) {
	//			return;
	//		}
	//
	//		SWTTextPaintListener listener = new SWTTextPaintListener(this, parent.getControl(),
	//				sConfigID);
	//		parent.getControl().addPaintListener(listener);
	//
	//		//addToControlMap(listener, sConfigID);
	//
	//		return;
	//	}

	/* Used for dumpObjectsOnly
	private String getAttachLine(FormAttachment attach) {
		String s = "";
		if (attach.control != null) {
			s += attach.control.getData("ConfigID");
			if (attach.offset != 0 || attach.alignment != SWT.DEFAULT) {
				s += "," + attach.offset;
			}
			if (attach.alignment != SWT.DEFAULT) {
				if (attach.alignment == SWT.LEFT) {
					s += ",left";
				} else if (attach.alignment == SWT.RIGHT) {
					s += ",right";
				} else if (attach.alignment == SWT.TOP) {
					s += ",top";
				} else if (attach.alignment == SWT.BOTTOM) {
					s += ",bottom";
				} else if (attach.alignment == SWT.CENTER) {
					s += ",center";
				}
			}
		} else {
			s += (int) (100.0 * attach.numerator / attach.denominator) + ","
					+ attach.offset;
		}
		return s;
	}
	*/

	protected static Listener getHandCursorListener(Display display) {
		if (handCursorListener == null) {
			handCursor = new Cursor(display, SWT.CURSOR_HAND);
			handCursorListener = new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (event.type == SWT.MouseEnter) {
						((Control) event.widget).setCursor(handCursor);
					}
					if (event.type == SWT.MouseExit) {
						((Control) event.widget).setCursor(null);
					}
				}
			};
		}

		return handCursorListener;
	}

	public SWTSkinObject createSkinObject(String sID, String sConfigID,
			SWTSkinObject parentSkinObject) {
		return createSkinObject(sID, sConfigID, parentSkinObject, null);
	}

	private int 	constructionDepth;
	private Cursor	constructionCursor;
	
	public void
	constructionStart()
	{
		if ( Constants.IS_CVS_VERSION && !Utils.isSWTThread()){
			
			Debug.out( "Must be SWT thread" );
		}
		
		constructionDepth++;
		
		if ( constructionDepth == 1 ){
			
			constructionCursor = skinComposite.getCursor();
			
			skinComposite.setCursor(skinComposite.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
		}
	}
	
	public void
	constructionEnd()
	{
		if ( Constants.IS_CVS_VERSION && !Utils.isSWTThread()){
			
			Debug.out( "Must be SWT thread" );
		}
		
		constructionDepth--;
		
		if ( constructionDepth == 0 ){
			
			skinComposite.setCursor( constructionCursor );
		}
	}
	
	/**
	 * Create a skin object based off an existing config "template"
	 *
	 * @param sID ID of new skin object
	 * @param sConfigID config id to use to create new skin object
	 * @param parentSkinObject location to place new skin object in
	 *
	 * @return new skin object
	 */
	public SWTSkinObject createSkinObject(String sID, String sConfigID,
			SWTSkinObject parentSkinObject, Object datasource) {
		SWTSkinObject skinObject = null;
		
		if ( constructionDepth == 0 ){
			Cursor cursor = skinComposite.getCursor();
			try {
				skinComposite.setCursor(skinComposite.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
	
				skinObject = linkIDtoParent(skinProperties, sID, sConfigID,
						parentSkinObject, true, true, datasource);
	
				if (bLayoutComplete) {
					layout(skinObject);
	    	}
			} catch (Exception e) {
				Debug.out("Trying to create " + sID + "." + sConfigID + " on "
						+ parentSkinObject, e);
			} finally {
				skinComposite.setCursor(cursor);
			}
		}else{
			try {
	
				skinObject = linkIDtoParent(skinProperties, sID, sConfigID,
						parentSkinObject, true, true, datasource);
	
				if (bLayoutComplete) {
					layout(skinObject);
	    	}
			} catch (Exception e) {
				Debug.out("Trying to create " + sID + "." + sConfigID + " on "
						+ parentSkinObject, e);
			}
		}
		
		return skinObject;
	}

	public void addSkinObject(SWTSkinObject skinObject) {
		String sViewID = skinObject.getViewID();
		if (sViewID != null) {
			setSkinObjectViewID(skinObject, sViewID);
		}

		attachControl(skinObject);
	}

	/**
	 * @param skinObject
	 *
	 * @since 3.0.1.3
	 */
	public void removeSkinObject(SWTSkinObject skinObject) {
		skinObject.triggerListeners(SWTSkinObjectListener.EVENT_DESTROY);

		String id = skinObject.getSkinObjectID();
		mon_MapIDsToSOs.enter();
		try {
  		SWTSkinObject[] objects = mapIDsToSOs.get(id);
  		if (objects != null) {
  			int x = 0;
  			for (int i = 0; i < objects.length; i++) {
  				if (objects[i] != skinObject) {
  					objects[x++] = objects[i];
  				}
  			}

  			if (x == 0) {
  				mapIDsToSOs.remove(id);
			  } else {
	        SWTSkinObject[] newObjects = new SWTSkinObject[x];
	        System.arraycopy(objects, 0, newObjects, 0, x);
	        mapIDsToSOs.put(id, newObjects);
			  }
  		}
		} finally {
			mon_MapIDsToSOs.exit();
		}

		mon_mapPublicViewIDsToSOs.enter();
		try {
  		id = skinObject.getViewID();
  		SWTSkinObject[] objects = mapPublicViewIDsToSOs.get(id);
  		if (objects != null) {
  			int x = 0;
  			for (int i = 0; i < objects.length; i++) {
  				if (objects[i] != skinObject) {
  					objects[x++] = objects[i];
  				}
  			}
  			if (x == 0) {
  				mapPublicViewIDsToSOs.remove(id);
			  } else {
	        SWTSkinObject[] newObjects = new SWTSkinObject[x];
	        System.arraycopy(objects, 0, newObjects, 0, x);
	        mapPublicViewIDsToSOs.put(id, newObjects);
			  }
  		}
		} finally {
			mon_mapPublicViewIDsToSOs.exit();
		}

		skinObject.dispose();
		if (mapIDsToSOs.size() == 0) {
			disposeSkin();
		}
	}

	private SWTSkinObject linkIDtoParent(SWTSkinProperties properties,
			String sID, String sConfigID, SWTSkinObject parentSkinObject,
			boolean bForceCreate, boolean bAddView, Object datasource) {
		currentSkinObjectcreationCount++;

		SWTSkinObject skinObject = null;
		try {
			if (sConfigID == null) {
				return null;
			}
			String[] sTypeParams = properties.getStringArray(sConfigID + ".type");
			String sType;
			if (sTypeParams != null && sTypeParams.length > 0) {
				sType = sTypeParams[0];
				bForceCreate = true;
			} else {
				// no type, use best guess
				sType = null;

				String sImageLoc = properties.getStringValue(sConfigID);
				if (sImageLoc != null) {
					sType = "image";
				} else {
					String sText = properties.getStringValue(sConfigID + ".text");
					if (sText != null) {
						sType = "text";
					} else {
						String sWidgets = properties.getStringValue(sConfigID + ".widgets");
						if (sWidgets != null || bForceCreate) {
							sType = "container";
						}
					}
				}

				if (sType == null) {
					if (DEBUGLAYOUT) {
						System.err.println("no type defined for " + sConfigID);
					}
					return null;
				}

				sTypeParams = new String[] {
					sType
				};
			}

			int iMinUserMode = properties.getIntValue(sConfigID + ".minUserMode", -1);
			if (iMinUserMode > 0) {
				int userMode = COConfigurationManager.getIntParameter("User Mode");
				if (userMode <= iMinUserMode) {
					return null;
				}
			}


			if (sType.equals("image")) {
				skinObject = createImageLabel(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("image2")) {
				skinObject = createImageLabel2(properties, sID, parentSkinObject);
			} else if (sType.equals("container2")) {
				skinObject = createContainer2(properties, sID, sConfigID,
						parentSkinObject, bForceCreate, false, null);
			} else if (sType.equals("container")) {
				skinObject = createContainer(properties, sID, sConfigID, sTypeParams,
						parentSkinObject, bForceCreate, false, null);
			} else if (sType.equals("text")) {
				skinObject = createTextLabel(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("tab")) {
				skinObject = _createTab(properties, sID, sConfigID, parentSkinObject);
			} else if (sType.equals("v-sash")) {
				skinObject = createSash(properties, sID, sConfigID, parentSkinObject,
						true);
			} else if (sType.equals("h-sash")) {
				skinObject = createSash(properties, sID, sConfigID, parentSkinObject,
						false);
			} else if (sType.equals("v-mysash")) {
				skinObject = createMySash(properties, sID, sConfigID, sTypeParams,
						parentSkinObject, true);
			} else if (sType.equals("h-mysash")) {
				skinObject = createMySash(properties, sID, sConfigID, sTypeParams,
						parentSkinObject, false);
			} else if (sType.equals("clone")) {
				skinObject = createClone(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("slider")) {
				skinObject = createSlider(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("hidden")) {
				return null;
			} else if (sType.equals("browser")) {
				skinObject = createBrowser(properties, sID, sConfigID, parentSkinObject);
			} else if (sType.equals("separator")) {
				skinObject = createSeparator(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("button")) {
				skinObject = createButton(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("checkbox")) {
				skinObject = createCheckbox(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("toggle")) {
				skinObject = createToggle(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("textbox")) {
				skinObject = createTextbox(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("combo")) {
				skinObject = createCombo(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("list")) {
				skinObject = createList(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("tabfolder")) {
				skinObject = createTabFolder(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("expandbar")) {
				skinObject = createExpandBar(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else if (sType.equals("expanditem")) {
				skinObject = createExpandItem(properties, sID, sConfigID, sTypeParams,
						parentSkinObject);
			} else {
				System.err.println(sConfigID + ": Invalid type of " + sType);
				return( null );
			}

			if ( skinObject != null ){ // can be null from createContainer2
				skinObject.setData("CreationParams", datasource);
				if (datasource != null) {
					skinObject.triggerListeners(
							SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, datasource);
				}

				if (bAddView) {
					String sViewID = skinObject.getViewID();
					if (sViewID != null) {
						setSkinObjectViewID(skinObject, sViewID);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			currentSkinObjectcreationCount--;
		}

		if (skinObject != null) {
			skinObject.triggerListeners(SWTSkinObjectListener.EVENT_CREATED);
		}

		return skinObject;
	}

	/**
	 * @param properties
	 * @param id
	 * @param configID
	 * @param typeParams
	 * @param parentSkinObject
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	private SWTSkinObject createButton(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {

		SWTSkinObject skinObject = new SWTSkinObjectButton(this, properties, id,
				configID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	private SWTSkinObject createCheckbox(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {

		SWTSkinObject skinObject = new SWTSkinObjectCheckbox(this, properties, id,
				configID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	private SWTSkinObject createToggle(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {

		SWTSkinObject skinObject = new SWTSkinObjectToggle(this, properties, id,
				configID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	private SWTSkinObject createExpandBar(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {
		String[] sItems = properties.getStringArray(configID + ".widgets");

		if (DEBUGLAYOUT) {
			System.out.println("createExpandBar: " + id + ";"
					+ properties.getStringValue(configID + ".widgets"));
		}

		SWTSkinObject skinObject = new SWTSkinObjectExpandBar(this, properties, id,
				configID, parentSkinObject);
		addToControlMap(skinObject);

		if (sItems != null) {
			addContainerChildren(skinObject, sItems, properties);
		}


		return skinObject;
	}

	private SWTSkinObject createExpandItem(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {
		String[] sItems = properties.getStringArray(configID + ".widgets");

		if (DEBUGLAYOUT) {
			System.out.println("createExpandItem: " + id + ";"
					+ properties.getStringValue(configID + ".widgets"));
		}

		SWTSkinObject skinObject = new SWTSkinObjectExpandItem(this, properties, id,
				configID, parentSkinObject);
		addToControlMap(skinObject);

		if (sItems != null) {
			addContainerChildren(skinObject, sItems, properties);
		}

		return skinObject;
	}


	private SWTSkinObject createTextbox(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {

		SWTSkinObject skinObject = new SWTSkinObjectTextbox(this, properties, id,
				configID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	private SWTSkinObject createCombo(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {

		SWTSkinObject skinObject = new SWTSkinObjectCombo(this, properties, id,
				configID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	private SWTSkinObject createList(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {

		SWTSkinObject skinObject = new SWTSkinObjectList(this, properties, id,
				configID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}
	
	private SWTSkinObject createTabFolder(SWTSkinProperties properties, String id,
			String configID, String[] typeParams, SWTSkinObject parentSkinObject) {
		String[] sItems = properties.getStringArray(configID + ".widgets");

		if (DEBUGLAYOUT) {
			System.out.println("createTabFolder: " + id + ";"
					+ properties.getStringValue(configID + ".widgets"));
		}

		SWTSkinObject skinObject = getSkinObjectByID(id, parentSkinObject);

		if (skinObject == null) {
			skinObject = new SWTSkinObjectTabFolder(this, properties, id,
					configID, parentSkinObject);
			addToControlMap(skinObject);
		} else {
			if (!(skinObject instanceof SWTSkinObjectContainer)) {
				return skinObject;
			}
		}

		if (sItems != null) {
			addContainerChildren(skinObject, sItems, properties);
		}

		return skinObject;
	}

	/**
	 * @param properties
	 * @param sID
	 * @param sConfigID
	 * @param parentSkinObject
	 * @return
	 */
	private SWTSkinObject createBrowser(SWTSkinProperties properties, String sID,
			String sConfigID, SWTSkinObject parentSkinObject) {

		SWTSkinObject skinObject = new SWTSkinObjectBrowser(this, properties, sID,
				sConfigID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	private SWTSkinObject createClone(SWTSkinProperties properties, String sID,
			String sConfigID, String[] typeParams, SWTSkinObject parentSkinObject) {
		//System.out.println("Create Clone " + sID + " == " + sConfigID + " for " + parentSkinObject);
		if (sConfigID.length() == 0) {
			System.err.println("XXXXXXXX " + sID + " has no config ID.."
					+ Debug.getStackTrace(false, false));
		}

		String[] sCloneParams;
		if (typeParams.length > 1) {
			int size = typeParams.length - 1;
			sCloneParams = new String[size];
			System.arraycopy(typeParams, 1, sCloneParams, 0, size);
		} else {
			sCloneParams = properties.getStringArray(sConfigID + ".clone");
			if (sCloneParams == null || sCloneParams.length < 1) {
				return null;
			}
		}

		if (properties instanceof SWTSkinPropertiesClone) {
			properties = ((SWTSkinPropertiesClone) properties).getOriginalProperties();
		}

		//System.out.println(sCloneParams[0]);
		SWTSkinPropertiesClone cloneProperties = new SWTSkinPropertiesClone(
				properties, sConfigID, sCloneParams);

		return linkIDtoParent(cloneProperties, sID, "", parentSkinObject, false,
				false, null);
	}

	private SWTSkinObject createImageLabel(SWTSkinProperties properties,
			String sID, String sConfigID, String[] typeParams,
			SWTSkinObject parentSkinObject) {
		if (typeParams.length > 1) {
			properties.addProperty(sConfigID + ".image", typeParams[1]);
		}
		SWTSkinObjectImage skinObject = new SWTSkinObjectImage(this, properties,
				sID, sConfigID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	private SWTSkinObject createContainer2(SWTSkinProperties properties,
			String sID, final String sConfigID, SWTSkinObject parentSkinObject,
			boolean bForceCreate, boolean bPropogate, SWTSkinObject intoSkinObject) {
		String[] sItems = properties.getStringArray(sConfigID + ".widgets");
		if (sItems == null && !bForceCreate) {
			return null;
		}

		if (DEBUGLAYOUT) {
			System.out.println("createContainer: " + sID + ";"
					+ properties.getStringValue(sConfigID + ".widgets"));
		}

		SWTSkinObject skinObject = getSkinObjectByID(sID, parentSkinObject);

		if (skinObject == null) {
			if (intoSkinObject == null) {
				//skinObject = new SWTSkinObjectImageContainer(this, properties, sID,
				//		sConfigID, parentSkinObject);
				//addToControlMap(skinObject);
			} else {
				skinObject = intoSkinObject;
			}
		} else {
			if (!(skinObject instanceof SWTSkinObjectContainer)) {
				return skinObject;
			}
		}

		if (!bPropogate) {
			bPropogate = properties.getIntValue(sConfigID + ".propogate", 0) == 1;
		}

		if (!bPropogate && (parentSkinObject instanceof SWTSkinObjectContainer)) {
			bPropogate = ((SWTSkinObjectContainer) parentSkinObject).getPropogation();
		}
		if (bPropogate) {
			if ( skinObject != null ){
				((SWTSkinObjectContainer) skinObject).setPropogation(true);
			}
		}

		if (sItems != null) {
			String[] paramValues = null;
			if (properties instanceof SWTSkinPropertiesParam) {
				paramValues = ((SWTSkinPropertiesParam) properties).getParamValues();
			}
			// Cloning is only for one level.  Children get the original properties
			// object
			if (properties instanceof SWTSkinPropertiesClone) {
				properties = ((SWTSkinPropertiesClone) properties).getOriginalProperties();
			}

			// Propogate any parameter values.
			// XXX This could get ugly, we should could the # of
			//     SWTSkinPropertiesParam to determine if this needs optimizing
			//     ie. if a top container has paramValues, every child will get a new
			//         object.  How would this affect memory/performance?
			if (paramValues != null) {
				properties = new SWTSkinPropertiesParamImpl(properties, paramValues);
			}

			for (int i = 0; i < sItems.length; i++) {
				String sItemID = sItems[i];
				linkIDtoParent(properties, sItemID, sItemID, skinObject, false, true, null);
			}
		}

		return skinObject;
	}

	private SWTSkinObject createImageLabel2(SWTSkinProperties properties,
			final String sConfigID, SWTSkinObject parentSkinObject) {
		Composite createOn;
		if (parentSkinObject == null) {
			createOn = skinComposite;
		} else {
			createOn = (Composite) parentSkinObject.getControl();
		}

		final Canvas drawable = new Canvas(createOn, SWT.NO_BACKGROUND);
		drawable.setVisible(false);

		final ImageLoader imageLoader = getImageLoader(properties);
		Image image = imageLoader.getImage(sConfigID);
		if (ImageLoader.isRealImage(image)) {
			imageLoader.releaseImage(sConfigID);
			image = imageLoader.getImage(sConfigID + ".image");
			drawable.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					imageLoader.releaseImage(sConfigID + ".image");
				}
			});
		} else {
			drawable.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					imageLoader.releaseImage(sConfigID);
				}
			});
		}
		drawable.setData("image", image);

		SWTSkinObjectBasic skinObject = new SWTSkinObjectBasic(this, properties,
				drawable, sConfigID, sConfigID, "image", parentSkinObject);
		addToControlMap(skinObject);

		ontopImages.add(skinObject);

		return skinObject;
	}

	private SWTSkinObject createSeparator(SWTSkinProperties properties,
			String sID, String sConfigID, String[] typeParams,
			SWTSkinObject parentSkinObject) {
		SWTSkinObject skinObject = new SWTSkinObjectSeparator(this, properties,
				sID, sConfigID, parentSkinObject);
		addToControlMap(skinObject);

		return skinObject;
	}

	public SWTSkinProperties getSkinProperties() {
		return skinProperties;
	}

	public void addListener(String viewID, SWTSkinObjectListener listener) {
		mapPublicViewIDsToListeners_mon.enter();
		try {
			ArrayList<SWTSkinObjectListener> list = mapPublicViewIDsToListeners.get(viewID);

			if (list != null) {
				list.add(listener);
			} else {
				list = new ArrayList<>();
				list.add(listener);
				mapPublicViewIDsToListeners.put(viewID, list);
			}
		} finally {
			mapPublicViewIDsToListeners_mon.exit();
		}
	}

	public void removeListener(String viewID, SWTSkinObjectListener listener) {
		mapPublicViewIDsToListeners_mon.enter();
		try {
			List<SWTSkinObjectListener> list = mapPublicViewIDsToListeners.get(viewID);

			if (list != null) {
				list.remove(listener);
			}
		} finally {
			mapPublicViewIDsToListeners_mon.exit();
		}
	}

	public SWTSkinObjectListener[] getSkinObjectListeners(String viewID) {
		if (viewID == null) {
			return NOLISTENERS;
		}

		mapPublicViewIDsToListeners_mon.enter();
		try {
			ArrayList<SWTSkinObjectListener> existing = mapPublicViewIDsToListeners.get(viewID);

			if (existing != null) {
				return existing.toArray(NOLISTENERS);
			}
			return NOLISTENERS;
		} finally {
			mapPublicViewIDsToListeners_mon.exit();
		}
	}

	public boolean isLayoutComplete() {
		return bLayoutComplete;
	}

	public void addListener(SWTSkinLayoutCompleteListener l) {
		if (!listenersLayoutComplete.contains(l)) {
			listenersLayoutComplete.add(l);
		}
	}

	public static void main(String[] args) {
		java.util.Date d = new java.util.Date();
		long t = d.getTime();

		t -= (1156 * 24 * 60 * 60 * 1000l);
		t -= (6 * 60 * 60 * 1000l);
		t -= (17 * 60 * 1000l);

		Date then = new Date(t);

		System.out.println(d + ";" + then);

	}

	public boolean isCreatingSO() {
		return currentSkinObjectcreationCount > 0;
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	public void triggerLanguageChange() {
		Object[] values = mapIDsToSOs.values().toArray();
		for (int i = 0; i < values.length; i++) {
			SWTSkinObject[] skinObjects = (SWTSkinObject[]) values[i];
			if (skinObjects != null) {
				for (int j = 0; j < skinObjects.length; j++) {
					SWTSkinObject so = skinObjects[j];
					so.triggerListeners(SWTSkinObjectListener.EVENT_LANGUAGE_CHANGE);
				}
			}
		}
	}

	public void setAutoSizeOnLayout(boolean autoSizeOnLayout, boolean force ) {
		this.autoSizeOnLayout 		= autoSizeOnLayout;
		this.autoSizeOnLayoutForce 	= force;
	}

	public boolean isAutoSizeOnLayout() {
		return autoSizeOnLayout;
	}

	public static void disposeDefault() {
		if (default_instance != null) {
			default_instance.disposeSkin();
			default_instance = null;
		}
		if (handCursor != null) {
			handCursor.dispose();;
			handCursor = null;
		}
		handCursorListener = null;
	}

}
