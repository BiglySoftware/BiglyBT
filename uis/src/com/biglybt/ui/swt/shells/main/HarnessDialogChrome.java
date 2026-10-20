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
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Replaces the native caption of a small plugin dialog with Harness chrome.
 * The caller keeps the native window when install() returns false.
 */
public class HarnessDialogChrome
{
	private static final int	TITLE_H	= 76;

	private static final Color	BG		= new Color( Display.getDefault(), 21, 21, 23 );
	private static final Color	SURFACE	= new Color( Display.getDefault(), 31, 31, 33 );
	private static final Color	LINE	= new Color( Display.getDefault(), 42, 42, 45 );
	private static final Color	TEXT	= new Color( Display.getDefault(), 231, 231, 233 );
	private static final Color	MUTED	= new Color( Display.getDefault(), 139, 143, 148 );
	private static final Color	ACCENT	= new Color( Display.getDefault(), 77, 107, 254 );

	public static boolean
	install(
		Shell	shell,
		Button[]	buttons )
	{
		if ( shell == null || shell.isDisposed() || !com.biglybt.core.util.Constants.isWindows ){
			return( false );
		}

		long oldStyle = 0;
		boolean styleChanged = false;

		try{
			Class<?> OS = Class.forName( "org.eclipse.swt.internal.win32.OS" );

			long hwnd = ((Number)Shell.class.getField( "handle" ).get( shell )).longValue();

			oldStyle = ((Number)OS.getMethod( "GetWindowLong", long.class, int.class )
				.invoke( null, hwnd, -16 )).longValue();

			long style = oldStyle;

			// Remove only the caption; the dialog keeps its own client area.
			style &= ~0x00C00000L;

			OS.getMethod( "SetWindowLong", long.class, int.class, int.class )
				.invoke( null, hwnd, -16, (int)style );

			OS.getMethod( "SetWindowPos", long.class, long.class, int.class, int.class,
				int.class, int.class, int.class )
				.invoke( null, hwnd, 0L, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0004 | 0x0020 );

			styleChanged = true;

			Control content = null;

			for ( Control c : shell.getChildren()){
				if ( c instanceof Composite && c.getLayoutData() instanceof FormData ){
					content = c;
					break;
				}
			}

			if ( content == null && shell.getChildren().length > 0 ){
				content = shell.getChildren()[0];
			}

			if ( content == null ){
				throw new Exception( "no dialog content" );
			}

			final Composite bar = new Composite( shell, SWT.DOUBLE_BUFFERED );
			bar.setBackground( BG );

			FormData barData = new FormData();
			barData.left = new FormAttachment( 0, 0 );
			barData.right = new FormAttachment( 100, 0 );
			barData.top = new FormAttachment( 0, 0 );
			barData.height = TITLE_H;
			bar.setLayoutData( barData );

			if ( content.getLayoutData() instanceof FormData ){
				FormData fd = (FormData)content.getLayoutData();
				fd.top = new FormAttachment( 0, TITLE_H - 64 );
			}else{
				Rectangle r = content.getBounds();
				content.setBounds( r.x, TITLE_H, r.width, Math.max( 1, r.height - TITLE_H ));
			}

			buildTitle( shell, bar );
			installButtons( buttons );

			bar.moveAbove( content );

			shell.layout( true, true );

			return( true );
		}catch( Throwable e ){
			if ( styleChanged ){
				try{
					Class<?> OS = Class.forName( "org.eclipse.swt.internal.win32.OS" );
					long hwnd = ((Number)Shell.class.getField( "handle" ).get( shell )).longValue();
					OS.getMethod( "SetWindowLong", long.class, int.class, int.class )
						.invoke( null, hwnd, -16, (int)oldStyle );
					OS.getMethod( "SetWindowPos", long.class, long.class, int.class, int.class,
						int.class, int.class, int.class )
						.invoke( null, hwnd, 0L, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0004 | 0x0020 );
				}catch( Throwable e2 ){
				}
			}

			return( false );
		}
	}

	private static void
	buildTitle(
		final Shell		shell,
		Composite		bar )
	{
		bar.setLayout( new FormLayout());

		bar.addPaintListener(
			new PaintListener()
			{
				@Override
				public void
				paintControl(
					PaintEvent e )
				{
					Rectangle r = bar.getClientArea();

					e.gc.setForeground( LINE );
					e.gc.drawLine( 0, r.height - 1, r.width, r.height - 1 );
				}
			});

		Canvas brand = new Canvas( bar, SWT.DOUBLE_BUFFERED );
		brand.setBackground( BG );

		FormData bd = new FormData();
		bd.left = new FormAttachment( 0, 12 );
		bd.top = new FormAttachment( 0, 14 );
		bd.width = 18;
		bd.height = 18;
		brand.setLayoutData( bd );

		brand.addPaintListener(
			new PaintListener()
			{
				@Override
				public void
				paintControl(
					PaintEvent e )
				{
					GC gc = e.gc;
					gc.setAntialias( SWT.ON );
					gc.setBackground( ACCENT );
					gc.fillRoundRectangle( 0, 0, 18, 18, 5, 5 );
					gc.setForeground( TEXT );
					Font f = new Font( shell.getDisplay(), "Segoe UI", 9, SWT.BOLD );
					gc.setFont( f );
					gc.drawString( "B", 5, 2, true );
					f.dispose();
				}
			});

		Canvas title = new Canvas( bar, SWT.DOUBLE_BUFFERED );
		title.setBackground( BG );

		FormData td = new FormData();
		td.left = new FormAttachment( brand, 8 );
		td.right = new FormAttachment( 100, -50 );
		td.top = new FormAttachment( 0, 10 );
		td.height = 42;
		title.setLayoutData( td );

		title.addPaintListener(
			new PaintListener()
			{
				@Override
				public void
				paintControl(
					PaintEvent e )
				{
					GC gc = e.gc;
					gc.setForeground( TEXT );
					Font f = new Font( shell.getDisplay(), "Segoe UI", 10, SWT.BOLD );
					gc.setFont( f );
					gc.drawString( shell.getText(), 0, 0, true );
					f.dispose();

					gc.setForeground( MUTED );
					Font sf = new Font( shell.getDisplay(), "Segoe UI", 8, SWT.NONE );
					gc.setFont( sf );
					gc.drawString( "Measure your connection", 0, 22, true );
					sf.dispose();
				}
			});

		Canvas close = new Canvas( bar, SWT.DOUBLE_BUFFERED );
		close.setBackground( BG );

		FormData cd = new FormData();
		cd.right = new FormAttachment( 100, -6 );
		cd.top = new FormAttachment( 0, 8 );
		cd.width = 26;
		cd.height = 26;
		close.setLayoutData( cd );

		close.addPaintListener(
			new PaintListener()
			{
				@Override
				public void
				paintControl(
					PaintEvent e )
				{
					GC gc = e.gc;
					gc.setForeground( MUTED );
					gc.setLineWidth( 1 );
					gc.drawLine( 9, 9, 17, 17 );
					gc.drawLine( 17, 9, 9, 17 );
				}
			});

		close.addMouseListener(
			new MouseAdapter()
			{
				@Override
				public void
				mouseUp(
					MouseEvent e )
				{
					shell.close();
				}
			});

		final Point[] dragStart = new Point[1];
		final Point[] dragOrigin = new Point[1];

		MouseAdapter dragger = new MouseAdapter()
		{
			@Override
			public void
			mouseDown(
				MouseEvent e )
			{
				dragStart[0] = shell.getDisplay().getCursorLocation();
				dragOrigin[0] = shell.getLocation();
			}
		};

		bar.addMouseListener( dragger );
		title.addMouseListener( dragger );

		MouseMoveListener dragMove = new MouseMoveListener()
		{
			@Override
			public void
			mouseMove(
				MouseEvent e )
			{
				if ( dragStart[0] != null ){
					Point p = shell.getDisplay().getCursorLocation();
					shell.setLocation(
						dragOrigin[0].x + p.x - dragStart[0].x,
						dragOrigin[0].y + p.y - dragStart[0].y );
				}
			}
		};

		bar.addMouseMoveListener( dragMove );
		title.addMouseMoveListener( dragMove );

		bar.addMouseListener(
			new MouseAdapter()
			{
				@Override
				public void
				mouseUp(
					MouseEvent e )
				{
					dragStart[0] = null;
				}
			});
	}

	private static void
	installButtons(
		Button[]	buttons )
	{
		if ( buttons == null ){
			return;
		}

		for ( int i = 0; i < buttons.length; i++ ){
			final Button button = buttons[i];

			if ( button == null || button.isDisposed()){
				continue;
			}

			Composite parent = button.getParent();
			RowData row = button.getLayoutData() instanceof RowData
				? (RowData)button.getLayoutData() : new RowData( 90, 26 );

			final Canvas custom = new Canvas( parent, SWT.DOUBLE_BUFFERED );
			custom.setBackground( SURFACE );
			custom.setLayoutData( new RowData( Math.max( 75, row.width ), 26 ));
			custom.setCursor( parent.getDisplay().getSystemCursor( SWT.CURSOR_HAND ));

			final boolean primary = i == 0;

			custom.addPaintListener(
				new PaintListener()
				{
					@Override
					public void
					paintControl(
						PaintEvent e )
					{
						Rectangle r = custom.getClientArea();

						e.gc.setAntialias( SWT.ON );
						e.gc.setBackground( primary ? ACCENT : SURFACE );
						e.gc.fillRoundRectangle( 0, 0, r.width, r.height, 6, 6 );

						if ( !primary ){
							e.gc.setForeground( LINE );
							e.gc.drawRoundRectangle( 0, 0, r.width - 1, r.height - 1, 6, 6 );
						}

						e.gc.setForeground( TEXT );
						Font f = new Font( custom.getDisplay(), "Segoe UI", 9, SWT.NONE );
						e.gc.setFont( f );

						String text = button.getText() == null ? ""
							: button.getText().replace( "&", "" );

						Point size = e.gc.textExtent( text );
						e.gc.drawString( text,
							( r.width - size.x ) / 2, ( r.height - size.y ) / 2, true );

						f.dispose();
					}
				});

			custom.addMouseListener(
				new MouseAdapter()
				{
					@Override
					public void
					mouseUp(
						MouseEvent e )
					{
						if ( button.isEnabled()){
							button.notifyListeners( SWT.Selection, new org.eclipse.swt.widgets.Event());
						}
					}
				});

			button.setVisible( false );
		}

		if ( buttons.length > 0 && buttons[0] != null ){
			buttons[0].getParent().layout( true );
		}
	}
}
