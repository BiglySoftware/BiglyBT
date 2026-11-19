/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.biglybt.ui.swt.shells.main;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

/**
 * A dark, searchable-feeling command surface that replaces the native Windows
 * menu bar. It flattens nested menu items into sections so the whole tree stays
 * usable in one scrollable column.
 */
public class HarnessMenuPopup
{
	private static final int	WIDTH	= 390;
	private static final int	MAX_H	= 620;

	private final Shell		owner;
	private final Control	anchor;
	private final Menu		menuBar;
	private final MenuItem	onlySection;
	private final Display	display;

	private Shell			popup;

	private long			opened;

	public
	HarnessMenuPopup(
		Shell		_owner,
		Control		_anchor,
		Menu		_menuBar )
	{
		this( _owner, _anchor, _menuBar, null );
	}

	public
	HarnessMenuPopup(
		Shell		_owner,
		Control		_anchor,
		Menu		_menuBar,
		MenuItem	_onlySection )
	{
		owner		= _owner;
		anchor		= _anchor;
		menuBar		= _menuBar;
		onlySection	= _onlySection;
		display		= owner.getDisplay();
	}

	public void
	show()
	{
		if ( menuBar == null || menuBar.isDisposed()){
			return;
		}

		if ( popup != null && !popup.isDisposed()){
			popup.dispose();
		}

		popup = new Shell( owner, SWT.NO_TRIM | SWT.ON_TOP );

		final Color bg = new Color( display, 21, 21, 23 );
		final Color surface = new Color( display, 27, 27, 28 );
		final Color hover = new Color( display, 38, 38, 42 );
		final Color text = new Color( display, 231, 231, 233 );
		final Color muted = new Color( display, 113, 113, 119 );
		final Color line = new Color( display, 42, 42, 45 );
		final Font font = new Font( display, "Segoe UI", 9, SWT.NORMAL );
		final Font sectionFont = new Font( display, "Segoe UI", 8, SWT.BOLD );

		popup.setBackground( line );

		GridLayout popupLayout = new GridLayout( 1, false );
		popupLayout.marginWidth = 1;
		popupLayout.marginHeight = 1;
		popup.setLayout( popupLayout );

		ScrolledComposite scroller = new ScrolledComposite( popup, SWT.V_SCROLL );
		scroller.setBackground( surface );
		scroller.setExpandHorizontal( true );
		scroller.setExpandVertical( false );
		scroller.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, true ));

		final Composite list = new Composite( scroller, SWT.NONE );
		list.setBackground( surface );

		GridLayout listLayout = new GridLayout( 1, false );
		listLayout.marginWidth = 8;
		listLayout.marginTop = 8;
		listLayout.marginBottom = 8;
		listLayout.verticalSpacing = 1;
		list.setLayout( listLayout );

		if ( onlySection != null ){
			addTop( list, onlySection, 0, font, sectionFont, text, muted, hover, line );
		}else{
			for ( MenuItem top: menuBar.getItems()){
				addTop( list, top, 0, font, sectionFont, text, muted, hover, line );
			}
		}

		scroller.setContent( list );

		Point listSize = list.computeSize( WIDTH - 16, SWT.DEFAULT );

		list.setSize( listSize );
		scroller.setMinSize( listSize );

		int height = Math.min( MAX_H, listSize.y + 18 );
		popup.setSize( WIDTH, height );

		Point at = anchor.toDisplay( 0, anchor.getSize().y );
		Rectangle screen = display.getBounds();

		if ( at.x + WIDTH > screen.x + screen.width ){
			at.x = screen.x + screen.width - WIDTH - 8;
		}

		if ( at.y + height > screen.y + screen.height ){
			at.y = Math.max( screen.y + 8, at.y - height - anchor.getSize().y );
		}

		popup.setLocation( at );

		popup.addListener( SWT.Deactivate, e -> {
			if ( System.currentTimeMillis() - opened > 250 ){
				close();
			}
		});
		popup.addListener( SWT.KeyDown, e -> {
			if ( e.keyCode == SWT.ESC ){
				close();
			}
		});

		popup.addDisposeListener(
			new DisposeListener()
			{
				@Override
				public void
				widgetDisposed(
					DisposeEvent e )
				{
					bg.dispose();
					surface.dispose();
					hover.dispose();
					text.dispose();
					muted.dispose();
					line.dispose();
					font.dispose();
					sectionFont.dispose();
				}
			});

		opened = System.currentTimeMillis();

		popup.setVisible( true );
	}

	private void
	close()
	{
		if ( popup != null && !popup.isDisposed()){
			popup.dispose();
		}
	}

	private void
	addTop(
		Composite	list,
		MenuItem	item,
		int			depth,
		Font		font,
		Font		sectionFont,
		Color		text,
		Color		muted,
		Color		hover,
		Color		line )
	{
		if ( item.getStyle() == SWT.SEPARATOR ){
			return;
		}

		Label section = new Label( list, SWT.NONE );
		section.setBackground( list.getBackground());
		section.setForeground( muted );
		section.setFont( sectionFont );
		section.setText( item.getText().replace( "&", "" ).toUpperCase());

		GridData sd = new GridData( SWT.FILL, SWT.CENTER, true, false );
		sd.verticalIndent = 10;
		sd.horizontalIndent = 6;
		section.setLayoutData( sd );

		if ( item.getMenu() != null ){
			for ( MenuItem child: item.getMenu().getItems()){
				addItem( list, child, depth + 1, font, sectionFont, text, muted, hover, line );
			}
		}else{
			addRow( list, item, depth + 1, font, text, muted, hover );
		}
	}

	private void
	addItem(
		Composite	list,
		MenuItem	item,
		int			depth,
		Font		font,
		Font		sectionFont,
		Color		text,
		Color		muted,
		Color		hover,
		Color		line )
	{
		int style = item.getStyle();

		if ( style == SWT.SEPARATOR ){
			Label sep = new Label( list, SWT.SEPARATOR | SWT.HORIZONTAL );
			sep.setBackground( line );

			GridData sd = new GridData( SWT.FILL, SWT.CENTER, true, false );
			sd.heightHint = 1;
			sd.verticalIndent = 4;
			sep.setLayoutData( sd );

			return;
		}

		if ( item.getMenu() != null ){
			Label sub = new Label( list, SWT.NONE );
			sub.setBackground( list.getBackground());
			sub.setForeground( muted );
			sub.setFont( sectionFont );
			sub.setText( "  " + item.getText().replace( "&", "" ));

			GridData sd = new GridData( SWT.FILL, SWT.CENTER, true, false );
			sd.horizontalIndent = 10 + depth * 10;
			sd.verticalIndent = 6;
			sub.setLayoutData( sd );

			for ( MenuItem child: item.getMenu().getItems()){
				addItem( list, child, depth + 1, font, sectionFont, text, muted, hover, line );
			}

			return;
		}

		addRow( list, item, depth, font, text, muted, hover );
	}

	private void
	addRow(
		final Composite	list,
		final MenuItem	item,
		int depth, Font font, Color text, Color muted, Color hover )
	{
		final Label row = new Label( list, SWT.NONE );

		row.setBackground( list.getBackground());
		row.setForeground( item.getEnabled() ? text : muted );
		row.setFont( font );
		row.setText( ( item.getSelection() ? "\u2713  " : "" ) + item.getText().replace( "&", "" ));

		GridData gd = new GridData( SWT.FILL, SWT.CENTER, true, false );
		gd.heightHint = 27;
		gd.horizontalIndent = 12 + depth * 12;
		row.setLayoutData( gd );

		if ( !item.getEnabled()){
			return;
		}

		row.setCursor( list.getDisplay().getSystemCursor( SWT.CURSOR_HAND ));

		row.addMouseTrackListener(
			new MouseTrackAdapter()
			{
				@Override
				public void
				mouseEnter(
					MouseEvent e )
				{
					row.setBackground( hover );
				}

				@Override
				public void
				mouseExit(
					MouseEvent e )
				{
					row.setBackground( list.getBackground());
				}
			});

		row.addMouseListener(
			new MouseAdapter()
			{
				@Override
				public void
				mouseUp(
					MouseEvent e )
				{
					if ( e.button != 1 ){
						return;
					}

					close();

					item.notifyListeners( SWT.Selection, new Event());
				}
			});
	}
}


