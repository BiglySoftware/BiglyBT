/*
 * Created on 21.07.2003
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
 *
 */
package com.biglybt.ui.swt;

import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.components.DoubleBufferedLabel;
import com.biglybt.util.StringCompareUtils;


/**
 * @author Arbeiten
 */
public class Messages {

	private static final boolean DARK_MODE = Utils.isDarkAppearanceNative();
	
	private static final String THEME_SUFFIX = DARK_MODE?"._dark":null;
	
	private static final String MESSAGE_KEY 		= "com.biglybt.ui.swt.Messages:msg";
	private static final String RESOURCE_KEY 		= "com.biglybt.ui.swt.Messages:res";
	private static final String RESOURCE_TT_KEY 	= "com.biglybt.ui.swt.Messages:ttres";

	private static final Pattern HIG_ELLIP_EXP = Pattern.compile("([\\.]{3})"); // rec. hig style on some platforms
	private static Listener hoverListener;

	static {
		hoverListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				updateToolTipFromData(event.widget, (event.stateMask & SWT.CONTROL) > 0);
			}
		};
	}
	/**
	 *
	 */
	private Messages() {
	}

	public static void updateLanguageForControl(Widget widget) {
		if (widget == null || widget.isDisposed())
			return;

		updateLanguageFromData(widget);	// OK, so we loose parameters on language change...
		widget.removeListener(SWT.MouseHover, hoverListener);
		widget.addListener(SWT.MouseHover, hoverListener);

		if (widget instanceof CTabFolder) {
			CTabFolder folder = (CTabFolder) widget;
			CTabItem[] items = folder.getItems();
			for (int i = 0; i < items.length; i++) {
				updateLanguageForControl(items[i]);
				updateLanguageForControl(items[i].getControl());
			}
		} else if (widget instanceof TabFolder) {
			TabFolder folder = (TabFolder) widget;
			TabItem[] items = folder.getItems();
			for (int i = 0; i < items.length; i++) {
				updateLanguageForControl(items[i]);
				updateLanguageForControl(items[i].getControl());
			}
		}
		else if(widget instanceof CoolBar) {
			CoolItem[] items = ((CoolBar)widget).getItems();
			for(int i = 0 ; i < items.length ; i++) {
				Control control = items[i].getControl();
				updateLanguageForControl(control);
			}
		}
		else if(widget instanceof ToolBar) {
			ToolItem[] items = ((ToolBar)widget).getItems();
			for(int i = 0 ; i < items.length ; i++) {
				updateLanguageForControl(items[i]);
			}
		}
		else if (widget instanceof Composite) {
			Composite group = (Composite) widget;
			Control[] controls = group.getChildren();
			for (int i = 0; i < controls.length; i++) {
				updateLanguageForControl(controls[i]);
			}
			if (widget instanceof Table) {
				Table table = (Table) widget;
				TableColumn[] columns = table.getColumns();
				for (int i = 0; i < columns.length; i++) {
					updateLanguageFromData(columns[i]);
				}
				updateLanguageForControl(table.getMenu());

				// XXX We could (should?) send this event for all widget types
				// XXX Would it better to have a custom event type?
				Event event = new Event();
				event.type = SWT.Settings;
				event.widget = widget;
				widget.notifyListeners(SWT.Settings, event);
			}
			else if (widget instanceof Tree) {
				Tree tree = (Tree) widget;
				TreeItem[] treeitems = tree.getItems();
				for (int i = 0; i < treeitems.length; i++) {
					updateLanguageForControl(treeitems[i]);
				}
			}

			group.layout();
		}
		else if (widget instanceof MenuItem) {
			MenuItem menuItem = (MenuItem) widget;
			updateLanguageForControl(menuItem.getMenu());
		}
		else if (widget instanceof Menu) {
			Menu menu = (Menu) widget;
			if (menu.getStyle() == SWT.POP_UP)
				System.out.println("POP_UP");

			MenuItem[] items = menu.getItems();
			for (int i = 0; i < items.length; i++) {
				updateLanguageForControl(items[i]);
			}
		}
		else if (widget instanceof TreeItem) {
			TreeItem treeitem = (TreeItem) widget;
			TreeItem[] treeitems = treeitem.getItems();
			for (int i = 0; i < treeitems.length; i++) {
				updateLanguageFromData(treeitems[i]);
				updateLanguageForControl(treeitems[i]);
			}
		}

	}

	// Needed only because old plugins use this signature
	public static void setLanguageText(Widget widget, String key) {
		setLanguageText(widget, key, false);
	}

	public static void setLanguageText(Widget widget, String key,
			String... params) {
		setLanguageText(widget, key, false, params);
	}

	/**
	 * Updates text only if they key is different
	 * 
	 * @return 
	 * false: Text not changed (same or disposed widget<br/>
	 * true: Text updated
	 */
	public static boolean updateLanguageKey(Widget widget, String key,
			String... params) {
		if (widget == null || widget.isDisposed()) {
			return false;
		}
		String oldKey = (String) widget.getData(RESOURCE_KEY);
		if (StringCompareUtils.equals(key, oldKey)) {
			return false;
		}
		widget.setData(RESOURCE_KEY, key);
		updateLanguageFromData(widget, params);
		return true;
	}

	public static void setLanguageText(Widget widget, String key,
			boolean setTooltipOnly, String... params) {
		widget.setData(RESOURCE_KEY,key);
		if(!setTooltipOnly)
			updateLanguageFromData(widget, params);
		widget.removeListener(SWT.MouseHover, hoverListener);
		widget.addListener(SWT.MouseHover, hoverListener);
	}

	private static void updateLanguageFromData(Widget widget, String... params) {
		if (widget == null || widget.isDisposed()) {
			return;
		}

		String key = null;
		try {
			key = (String) widget.getData(RESOURCE_KEY);
		} catch (ClassCastException e) {
		}

		if (key == null || key.endsWith(".tooltip")) {
			return;
		}

		String message = params == null ? MessageText.getString(key)
				: MessageText.getString(key, params);

		widget.setData(MESSAGE_KEY,message);
		
		if (widget instanceof Label) {
			// Disable Mnemonic when & is before a space.  Otherwise, it's most
			// likely meant to be a Mnemonic
			((Label) widget).setText(message.replaceAll("& ", "&& "));

		} else if (widget instanceof MenuItem) {
			final MenuItem menuItem = ((MenuItem) widget);
			boolean indent = (menuItem.getData("IndentItem") != null);

			if (Constants.isOSX) {
				message = HIG_ELLIP_EXP.matcher(message).replaceAll("\u2026"); // hig style - ellipsis
			}

			menuItem.setText(indent ? "  " + message : message);

			if (menuItem.getAccelerator() != 0) {
				// opt-in only for now; remove this conditional check to allow accelerators for arbitrary MenuItem objects
				KeyBindings.setAccelerator(menuItem,
						(String) menuItem.getData(RESOURCE_KEY)); // update keybinding
			}

		} else if (widget instanceof Item) {
			// Must be after MenuItem, since MenuItem is Item, but we extra logic
			((Item) widget).setText(message);

		} else if (widget instanceof CLabel) {
			((CLabel) widget).setText(message.replaceAll("& ", "&& "));

		} else if (widget instanceof Group) {
			((Group) widget).setText(message);

		} else if (widget instanceof Button) {
			((Button) widget).setText(message);

		} else if (widget instanceof Decorations) {
			((Decorations) widget).setText(message);

		} else if (widget instanceof Text) {
			((Text) widget).setText(message);

		} else if (widget instanceof Link) {
			((Link) widget).setText(message.replaceAll("& ", "&& "));

		} else if (widget instanceof DoubleBufferedLabel) {
			((DoubleBufferedLabel) widget).setText(message);

		} else if (widget instanceof Canvas) {
			; // get a few of these

		} else {
			Debug.out("No cast for " + widget.getClass().getName());
		}
	}

	public static String getLanguageForControl( Widget widget ){
		return((String)widget.getData(MESSAGE_KEY));
	}
	
	private static void updateToolTipFromData(Widget widget, boolean showKey) {
		
		String tt_key = (String)widget.getData(RESOURCE_TT_KEY);
		
		if ( tt_key != null ){
			
			updateTooltipLanguageFromData( widget );
			
			return;
		}
		
		String key = (String) widget.getData(RESOURCE_KEY);
		if (key == null) {
			return;
		}
		if (widget instanceof Control) {
			if (showKey) {
				Utils.setTT((Control) widget,key);
				return;
			}
			if (!key.endsWith(".tooltip")) {
				key += ".tooltip";
			}
			String toolTip = MessageText.getString(key);
			if (!toolTip.equals('!' + key + '!')) {
				Utils.setTT((Control) widget, toolTip);
			}
		} else if (widget instanceof ToolItem) {
			if (!key.endsWith(".tooltip")) {
				key += ".tooltip";
			}
			String toolTip = MessageText.getString(key);
			if (!toolTip.equals('!' + key + '!')) {
				Utils.setTT((ToolItem) widget,toolTip.replaceAll("Meta\\+",
						Constants.isOSX ? "Cmd+" : "Ctrl+"));
			}
		} else if (widget instanceof TableColumn) {
			if (!key.endsWith(".info")) {
				key += ".info";
			}
			String toolTip = MessageText.getString(key, (String) null);
			if (toolTip == null) {
				toolTip = MessageText.getString(key.substring(0, key.length() - 5),
						(String) null);
			}
			if (toolTip != null) {
				try {
					Utils.setTT((TableColumn) widget,toolTip);
				} catch (NoSuchMethodError e) {
					// Pre SWT 3.2
				}
			}
		}
	}


	public static String 
	getLanguageText( String key )
	{
		if ( THEME_SUFFIX != null ){
			
				// doesn't work with platform-specific strings yet...
			
			String result = MessageText.getString( key + THEME_SUFFIX );
			
			if ( result != null ){
				
				return( result );
			}
		}
		
		return( MessageText.getString( key ));
	}
	
	public static void setLanguageTooltip(Widget widget, String key) {
		if (widget == null || widget.isDisposed()) {
			return;
		}

		widget.setData(RESOURCE_TT_KEY,key);
		updateTooltipLanguageFromData(widget);
	}

	private static void updateTooltipLanguageFromData(Widget widget) {
		if (widget == null || widget.isDisposed()) {
			return;
		}
		
		String tt_key = (String)widget.getData(RESOURCE_TT_KEY);
		
		if ( tt_key != null ){
			String sToolTip = MessageText.getString( tt_key );
			if (widget instanceof CLabel)
				Utils.setTT((CLabel) widget,sToolTip);
			else if (widget instanceof Label)
				Utils.setTT((Label) widget,sToolTip);
			else if (widget instanceof Text)
				Utils.setTT((Text) widget,sToolTip);
			else if (widget instanceof Canvas)
				Utils.setTT((Canvas) widget,sToolTip);
			else if (widget instanceof Composite)
				Utils.setTT((Composite) widget,sToolTip);
			else if (widget instanceof Control)
				Utils.setTT((Control) widget,sToolTip);
			else if (widget instanceof ToolItem)
				Utils.setTT((ToolItem) widget,sToolTip);
			else
				System.out.println("No cast for " + widget.getClass().getName());
		}
	}
}
