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

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.components.DoubleBufferedLabel;

import java.util.regex.Pattern;


/**
 * @author Arbeiten
 */
public class Messages {

  private static final String RESOURCE_KEY = "com.biglybt.ui.swt.Messages:res";
  
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

    updateLanguageFromData(widget,null);	// OK, so we loose parameters on language change...
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
          updateLanguageFromData(columns[i], null);
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
        updateLanguageFromData(treeitems[i], null);
        updateLanguageForControl(treeitems[i]);
      }
    }

  }

  public static void setLanguageText(Widget widget, String key) {
    setLanguageText(widget, key, false);
  }

  public static void setLanguageText(Widget widget,  String key, String[]params) {
    setLanguageText(widget, key, params, false);
  }

  public static void setLanguageText(Widget widget,  String key, boolean setTooltipOnly) {
  	setLanguageText( widget, key, null, setTooltipOnly );
  }

  private static void
  setLanguageText(Widget widget,  String key, String[] params, boolean setTooltipOnly) {
  	widget.setData(RESOURCE_KEY,key);
  	if(!setTooltipOnly)
      updateLanguageFromData(widget, params);
  	widget.removeListener(SWT.MouseHover, hoverListener);
  	widget.addListener(SWT.MouseHover, hoverListener);
  }

	private static void updateToolTipFromData(Widget widget, boolean showKey) {
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


  private static void updateLanguageFromData(Widget widget,String[] params) {
  	if (widget == null || widget.isDisposed()) {
  		return;
  	}

      if (widget.getData(RESOURCE_KEY) != null) {
        String key = null;
        try {
          key = (String) widget.getData(RESOURCE_KEY);
        } catch(ClassCastException e) {
        }

        if(key == null) return;
        if(key.endsWith(".tooltip")) return;

        String	message;

        if ( params == null ){

        	message = MessageText.getString((String) widget.getData(RESOURCE_KEY));
        }else{

           	message = MessageText.getString((String) widget.getData(RESOURCE_KEY), params);
        }

        if (widget instanceof MenuItem) {
            final MenuItem menuItem = ((MenuItem) widget);
            boolean indent = (menuItem.getData("IndentItem") != null);

            if(Constants.isOSX)
                message = HIG_ELLIP_EXP.matcher(message).replaceAll("\u2026"); // hig style - ellipsis

            menuItem.setText(indent ? "  " + message : message);

            if(menuItem.getAccelerator() != 0) // opt-in only for now; remove this conditional check to allow accelerators for arbitrary MenuItem objects
                KeyBindings.setAccelerator(menuItem, (String)menuItem.getData(RESOURCE_KEY)); // update keybinding
        }
        else if (widget instanceof TableColumn) {
        	TableColumn tc = ((TableColumn) widget);
          tc.setText(message);
        } else if (widget instanceof Label)
        	// Disable Mnemonic when & is before a space.  Otherwise, it's most
        	// likely meant to be a Mnemonic
          ((Label) widget).setText(message.replaceAll("& ", "&& "));
        else if (widget instanceof CLabel)
          ((CLabel) widget).setText(message.replaceAll("& ", "&& "));
        else if (widget instanceof Group)
           ((Group) widget).setText(message);
        else if (widget instanceof Button)
           ((Button) widget).setText(message);
        else if (widget instanceof CTabItem)
           ((CTabItem) widget).setText(message);
        else if (widget instanceof TabItem)
           ((TabItem) widget).setText(message);
        else if (widget instanceof TreeItem)
          ((TreeItem) widget).setText(message);
        else if(widget instanceof Shell)
          ((Shell) widget).setText(message);
        else if(widget instanceof ToolItem)
            ((ToolItem) widget).setText(message);
        else if(widget instanceof Text)
          ((Text) widget).setText(message);
        else if(widget instanceof TreeColumn)
          ((TreeColumn) widget).setText(message);
        else if(widget instanceof DoubleBufferedLabel)
            ((DoubleBufferedLabel) widget).setText(message);
        else if(widget instanceof Canvas)
          ; // get a few of these
        else{
          Debug.out( "No cast for " + widget.getClass().getName());
        }
      }
  }

  public static void setLanguageTooltip(Widget widget, String key) {
  	if (widget == null || widget.isDisposed()) {
  		return;
  	}

  	widget.setData(RESOURCE_KEY,key);
    updateTooltipLanguageFromData(widget);
  }

  public static void updateTooltipLanguageFromData(Widget widget) {
  	if (widget == null || widget.isDisposed()) {
  		return;
  	}
		if (widget.getData(RESOURCE_KEY) != null) {
			String sToolTip = MessageText.getString((String) widget.getData(RESOURCE_KEY));
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
			else
				System.out.println("No cast for " + widget.getClass().getName());
		}
	}
}
