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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Adds a compact Harness caption to skinned dialogs. Native MessageBoxShell
 * windows, which are not skin-managed, are deliberately left alone.
 */
public class HarnessGenericDialogChrome
{
	private static final int	TITLE_H	= 34;

	public static final String	DATA_KEY	= "harness.generic.chrome";

	private static final Color	BG		= new Color( Display.getDefault(), 21, 21, 23 );
	private static final Color	LINE	= new Color( Display.getDefault(), 42, 42, 45 );
	private static final Color	TEXT	= new Color( Display.getDefault(), 231, 231, 233 );
	private static final Color	MUTED	= new Color( Display.getDefault(), 139, 143, 148 );
	private static final Color	ACCENT	= new Color( Display.getDefault(), 77, 107, 254 );

	public static boolean
	install(
		Shell	shell )
	{
		if ( shell == null || shell.isDisposed() || shell.getData( DATA_KEY ) != null ){
			return( false );
		}

		if ( !com.biglybt.core.util.Constants.isWindows ){
			return( false );
		}

		Object shellClass = shell.getData( "class" );

		if ( !(shellClass instanceof com.biglybt.ui.swt.views.skin.SkinnedDialog)){
			return( false );
		}

		int style = shell.getStyle();

		if ( ( style & SWT.NO_TRIM ) != 0 || ( style & SWT.TOOL ) != 0 || ( style & SWT.ON_TOP ) != 0 ){
			return( false );
		}

		if ( shell.getMenuBar() != null || shell.getText() == null || shell.getText().length() == 0 ){
			return( false );
		}

		Rectangle bounds = shell.getBounds();

		if ( bounds.width < 260 || bounds.height < 140 ){
			return( false );
		}

		Control content = null;

		for ( Control c : shell.getChildren()){
			if ( c instanceof Composite && c.getLayoutData() instanceof FormData ){
				content = c;
				break;
			}
		}

		if ( content == null ){
			return( false );
		}

		long oldStyle = 0;
		boolean styleChanged = false;
		Composite bar = null;
		FormData contentData = (FormData)content.getLayoutData();
		org.eclipse.swt.layout.FormAttachment oldTop = contentData.top;

		try{
			Class<?> OS = Class.forName( "org.eclipse.swt.internal.win32.OS" );
			long hwnd = ((Number)Shell.class.getField( "handle" ).get( shell )).longValue();

			oldStyle = ((Number)OS.getMethod( "GetWindowLong", long.class, int.class )
				.invoke( null, hwnd, -16 )).longValue();

			long newStyle = oldStyle & ~0x00C00000L;	// remove WS_CAPTION

			OS.getMethod( "SetWindowLong", long.class, int.class, int.class )
				.invoke( null, hwnd, -16, (int)newStyle );

			OS.getMethod( "SetWindowPos", long.class, long.class, int.class, int.class,
				int.class, int.class, int.class )
				.invoke( null, hwnd, 0L, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0004 | 0x0020 );

			styleChanged = true;

			bar = new Composite( shell, SWT.DOUBLE_BUFFERED );
			bar.setBackground( BG );

			FormData barData = new FormData();
			barData.left = new FormAttachment( 0, 0 );
			barData.right = new FormAttachment( 100, 0 );
			barData.top = new FormAttachment( 0, 0 );
			barData.height = TITLE_H;
			bar.setLayoutData( barData );

			contentData.top = new FormAttachment( 0, TITLE_H );

			buildBar( shell, bar );

			bar.moveAbove( content );
			shell.layout( true, true );

			shell.setData( DATA_KEY, Boolean.TRUE );

			return( true );
		}catch( Throwable e ){
			contentData.top = oldTop;

			if ( bar != null && !bar.isDisposed()){
				bar.dispose();
			}

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
	buildBar(
		final Shell		shell,
		final Composite	bar )
	{
		bar.addPaintListener(
			new PaintListener()
			{
				@Override
				public void
				paintControl(
					PaintEvent e )
				{
					GC gc = e.gc;
					Rectangle r = bar.getClientArea();

					gc.setBackground( BG );
					gc.fillRectangle( 0, 0, r.width, r.height );

					gc.setAntialias( SWT.ON );
					gc.setBackground( ACCENT );
					gc.fillRoundRectangle( 10, 8, 18, 18, 5, 5 );

					gc.setForeground( TEXT );
					Font f = new Font( shell.getDisplay(), "Segoe UI", 9, SWT.BOLD );
					gc.setFont( f );
					gc.drawString( "B", 15, 10, true );
					gc.drawString( shell.getText(), 36, 10, true );
					f.dispose();

					gc.setForeground( MUTED );
					gc.drawLine( r.width - 22, 12, r.width - 14, 20 );
					gc.drawLine( r.width - 14, 12, r.width - 22, 20 );

					gc.setForeground( LINE );
					gc.drawLine( 0, r.height - 1, r.width, r.height - 1 );
				}
			});

		final Point[] dragStart = new Point[1];
		final Point[] dragOrigin = new Point[1];

		bar.addMouseListener(
			new MouseAdapter()
			{
				private boolean pressedClose;

				@Override
				public void
				mouseDown(
					MouseEvent e )
				{
					Rectangle r = bar.getClientArea();
					pressedClose = e.x >= r.width - 30;

					if ( !pressedClose ){
						dragStart[0] = shell.getDisplay().getCursorLocation();
						dragOrigin[0] = shell.getLocation();
					}
				}

				@Override
				public void
				mouseUp(
					MouseEvent e )
				{
					if ( pressedClose ){
						shell.close();
					}

					pressedClose = false;
					dragStart[0] = null;
				}
			});

		bar.addMouseMoveListener(
			new MouseMoveListener()
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
			});
	}
}
