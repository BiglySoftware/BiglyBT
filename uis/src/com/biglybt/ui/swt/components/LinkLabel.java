/*
 * Created on 11-Nov-2005
 * Created by Paul Gardner
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

package com.biglybt.ui.swt.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;

public class
LinkLabel
{
	private final Label	linkLabel;
	public
	LinkLabel(
		Composite	composite,
		String		resource,
		String		link )
	{
		this( composite, new GridData(), resource, link );
	}

	public
	LinkLabel(
		Composite	composite,
		GridData	gridData,
		String		resource,
		String		link )
	{
	    linkLabel = new Label(composite, SWT.NULL);
	    Messages.setLanguageText(linkLabel,resource);
	    linkLabel.setLayoutData( gridData );
	    makeLinkedLabel(linkLabel, link);
	}

	public
	LinkLabel(
		Composite		composite,
		String			resource,
		Runnable		runnable )
	{
		this( composite, new GridData(), resource, runnable );
	}

	public
	LinkLabel(
		Composite		composite,
		GridData		gridData,
		String			resource,
		Runnable		runnable )
	{
	    linkLabel = new Label(composite, SWT.NULL);
	    Messages.setLanguageText(linkLabel,resource);
	    linkLabel.setLayoutData( gridData );
	    makeLinkedLabel(linkLabel, runnable);
	}
	
	public Label
	getlabel()
	{
		return( linkLabel );
	}

	/**
	 * Alters a given label to make it appear like a launchable
	 * link. This should preferably be done after all other changes
	 * have been performed on the label - especially the setting of
	 * the label's text.
	 */
	public static void makeLinkedLabel(Label label, String hyperlink) {
		label.setData(hyperlink);
		String tooltip = label.getToolTipText();

		// We only set a tooltip if one isn't set already and it isn't
		// identical to the label text.
		if (tooltip == null && !hyperlink.equals(label.getText())) {
			label.setToolTipText(hyperlink.replaceAll("&", "&&"));
		}
	    label.setCursor(label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
	    label.setForeground(Colors.blue);
	    label.addMouseListener(new MouseAdapter() {
	      @Override
	      public void mouseDoubleClick(MouseEvent arg0) {
	      	Utils.launch((String) ((Label) arg0.widget).getData());
	      }
	      @Override
	      public void mouseUp(MouseEvent arg0) {
	      	Utils.launch((String) ((Label) arg0.widget).getData());
	      }
	    });
	    ClipboardCopy.addCopyToClipMenu( label );
	}

	public static void updateLinkedLabel(Label label, String hyperlink) {
		label.setData(hyperlink);
		label.setToolTipText(hyperlink);
	}
	
	public static void makeLinkedLabel(Label label, Runnable runnable) {
		
	    label.setCursor(label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
	    label.setForeground(Colors.blue);
	    label.addMouseListener(new MouseAdapter() {
	      @Override
	      public void mouseDoubleClick(MouseEvent arg0) {
	      	runnable.run();
	      }
	      @Override
	      public void mouseUp(MouseEvent arg0) {
	    	  runnable.run();
	      }
	    });
	}
}
