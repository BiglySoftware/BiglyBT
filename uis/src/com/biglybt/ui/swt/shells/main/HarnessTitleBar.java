/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.biglybt.ui.swt.shells.main;

import java.io.InputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

/**
 * A borderless, Harness-styled window chrome. It owns the title, menu access
 * and window controls; the native frame is removed by MainWindowImpl.
 */
public class HarnessTitleBar
	extends Composite
{
	private static final int	HEIGHT		= 38;
	private static final int	BUTTON_W	= 44;

	private final Shell		shell;
	private final Display	display;

	private final Color		bg;
	private final Color		hover;
	private final Color		closeHover;
	private final Color		text;
	private final Color		muted;
	private final Color		line;
	private final Color		accent;

	private final Font		titleFont;
	private final Font		badgeFont;
	private final Font		iconFont;

	private final Image		logo;
	private final Menu		menuBar;

	private boolean			dragging;
	private Point			dragOffset;

	private final org.eclipse.swt.widgets.Listener	mouseMoveFilter;
	private final org.eclipse.swt.widgets.Listener	mouseUpFilter;

	public
	HarnessTitleBar(
		Shell		_shell )
	{
		super( _shell, SWT.DOUBLE_BUFFERED );

		shell	= _shell;
		display	= getDisplay();

		bg			= new Color( display, 21, 21, 23 );
		hover		= new Color( display, 38, 38, 42 );
		closeHover	= new Color( display, 196, 43, 28 );
		text		= new Color( display, 231, 231, 233 );
		muted		= new Color( display, 113, 113, 119 );
		line		= new Color( display, 42, 42, 45 );
		accent		= new Color( display, 77, 107, 254 );

		titleFont	= new Font( display, "Segoe UI", 10, SWT.BOLD );
		badgeFont	= new Font( display, "Consolas", 8, SWT.NORMAL );
		iconFont	= new Font( display, "Segoe UI", 9, SWT.NORMAL );

		logo	= loadLogo();
		menuBar	= shell.getMenuBar();

		setBackground( bg );
		setLayout( new FormLayout());

		build();

		// The menu bar is replaced by the compact menu button.
		shell.setMenuBar( null );
		detachNativeMenu();

		mouseMoveFilter = new org.eclipse.swt.widgets.Listener()
		{
			@Override
			public void
			handleEvent(
				Event e )
			{
				if ( dragging && !shell.isDisposed()){
					Point p = display.getCursorLocation();

					shell.setLocation( p.x - dragOffset.x, p.y - dragOffset.y );
				}
			}
		};

		mouseUpFilter = new org.eclipse.swt.widgets.Listener()
		{
			@Override
			public void
			handleEvent(
				Event e )
			{
				dragging = false;
			}
		};

		display.addFilter( SWT.MouseMove, mouseMoveFilter );
		display.addFilter( SWT.MouseUp, mouseUpFilter );

		addDisposeListener(
			new DisposeListener()
			{
				@Override
				public void
				widgetDisposed(
					DisposeEvent e )
				{
					display.removeFilter( SWT.MouseMove, mouseMoveFilter );
					display.removeFilter( SWT.MouseUp, mouseUpFilter );

					bg.dispose();
					hover.dispose();
					closeHover.dispose();
					text.dispose();
					muted.dispose();
					line.dispose();
					accent.dispose();

					titleFont.dispose();
					badgeFont.dispose();
					iconFont.dispose();

					if ( logo != null && !logo.isDisposed()){
						logo.dispose();
					}
				}
			});

		addPaintListener(
			new PaintListener()
			{
				@Override
				public void
				paintControl(
					PaintEvent e )
				{
					GC gc = e.gc;
					gc.setForeground( line );
					gc.drawLine( 0, HEIGHT - 1, e.width, HEIGHT - 1 );
					gc.setForeground( accent );
					gc.drawLine( 0, 0, 0, HEIGHT - 1 );
				}
			});
	}

	private void
	build()
	{
		Composite brand = new Composite( this, SWT.NONE );
		brand.setBackground( bg );

		RowLayout row = new RowLayout( SWT.HORIZONTAL );
		row.marginWidth = 0;
		row.marginHeight = 0;
		row.spacing = 9;
		row.center = true;
		brand.setLayout( row );

		Label logoLabel = new Label( brand, SWT.NONE );
		logoLabel.setBackground( bg );
		logoLabel.setImage( logo );

		Label name = new Label( brand, SWT.NONE );
		name.setBackground( bg );
		name.setForeground( text );
		name.setFont( titleFont );
		name.setText( "BiglyBT" );

		Label slash = new Label( brand, SWT.NONE );
		slash.setBackground( bg );
		slash.setForeground( line );
		slash.setFont( titleFont );
		slash.setText( "/" );

		Label badge = new Label( brand, SWT.NONE );
		badge.setBackground( bg );
		badge.setForeground( muted );
		badge.setFont( badgeFont );
		badge.setText( "HARNESS" );

		if ( menuBar != null && !menuBar.isDisposed()){
			for ( final MenuItem top: menuBar.getItems()){
				if ( top.getStyle() == SWT.SEPARATOR ){
					continue;
				}

				final Label item = new Label( brand, SWT.NONE );
				item.setBackground( bg );
				item.setForeground( muted );
				item.setFont( iconFont );
				item.setText( top.getText().replace( "&", "" ));
				item.setCursor( display.getSystemCursor( SWT.CURSOR_HAND ));

				item.addMouseTrackListener(
					new MouseTrackAdapter()
					{
						@Override
						public void
						mouseEnter(
							MouseEvent e )
						{
							item.setForeground( text );
						}

						@Override
						public void
						mouseExit(
							MouseEvent e )
						{
							item.setForeground( muted );
						}
					});

				item.addMouseListener(
					new MouseAdapter()
					{
						@Override
						public void
						mouseUp(
							MouseEvent e )
						{
							if ( e.button == 1 ){
								new HarnessMenuPopup( shell, item, menuBar, top ).show();
							}
						}
					});
			}
		}

		FormData fd = new FormData();
		fd.left = new FormAttachment( 0, 6 );
		fd.top = new FormAttachment( 0, 0 );
		fd.height = HEIGHT;
		fd.right = new FormAttachment( 100, -3 * BUTTON_W - 8 );
		brand.setLayoutData( fd );

		installDrag( this );
		installDrag( brand );
		installDrag( logoLabel );
		installDrag( name );
		installDrag( slash );
		installDrag( badge );

		Control close = createWindowButton( "close", new Runnable()
		{
			@Override
			public void
			run()
			{
				shell.close();
			}
		});

		fd = new FormData();
		fd.right = new FormAttachment( 100, 0 );
		fd.top = new FormAttachment( 0, 0 );
		fd.width = BUTTON_W;
		fd.height = HEIGHT;
		close.setLayoutData( fd );

		Control max = createWindowButton( "max", new Runnable()
		{
			@Override
			public void
			run()
			{
				shell.setMaximized( !shell.getMaximized());
				shell.layout( true, true );
			}
		});

		fd = new FormData();
		fd.right = new FormAttachment( close, 0 );
		fd.top = new FormAttachment( 0, 0 );
		fd.width = BUTTON_W;
		fd.height = HEIGHT;
		max.setLayoutData( fd );

		Control min = createWindowButton( "min", new Runnable()
		{
			@Override
			public void
			run()
			{
				shell.setMinimized( true );
			}
		});

		fd = new FormData();
		fd.right = new FormAttachment( max, 0 );
		fd.top = new FormAttachment( 0, 0 );
		fd.width = BUTTON_W;
		fd.height = HEIGHT;
		min.setLayoutData( fd );

		shell.addListener( SWT.Resize, e -> max.redraw());
	}

	private Control
	createIconButton(
		final String	type,
		final Runnable	action )
	{
		final Canvas button = new Canvas( this, SWT.DOUBLE_BUFFERED );

		button.setBackground( bg );
		button.setCursor( display.getSystemCursor( SWT.CURSOR_HAND ));

		button.addPaintListener(
			new PaintListener()
			{
				private boolean over;

				@Override
				public void
				paintControl(
					PaintEvent e )
				{
					GC gc = e.gc;
					gc.setAntialias( SWT.ON );

					if ( over ){
						gc.setBackground( hover );
						gc.fillRoundRectangle( 0, 3, button.getSize().x - 1, HEIGHT - 7, 8, 8 );
					}

					gc.setForeground( text );
					gc.setLineWidth( 1 );

					if ( type.equals( "menu" )){
						int x = 12;
						for ( int y: new int[]{ 13, 18, 23 }){
							gc.drawLine( x, y, x + 10, y );
						}
					}
				}

				{
					button.addMouseTrackListener(
						new MouseTrackAdapter()
						{
							@Override
							public void
							mouseEnter(
								MouseEvent e )
							{
								over = true;
								button.redraw();
							}

							@Override
							public void
							mouseExit(
								MouseEvent e )
							{
								over = false;
								button.redraw();
							}
						});
				}
			});

		button.addMouseListener(
			new MouseAdapter()
			{
				@Override
				public void
				mouseUp(
					MouseEvent e )
				{
					if ( e.button == 1 ){
						action.run();
					}
				}
			});

		return( button );
	}

	private Control
	createWindowButton(
		final String	type,
		final Runnable	action )
	{
		final Canvas button = new Canvas( this, SWT.DOUBLE_BUFFERED );

		button.setBackground( bg );
		button.setCursor( display.getSystemCursor( SWT.CURSOR_HAND ));

		final boolean[] over = { false };

		button.addMouseTrackListener(
			new MouseTrackAdapter()
			{
				@Override
				public void
				mouseEnter(
					MouseEvent e )
				{
					over[0] = true;
					button.redraw();
				}

				@Override
				public void
				mouseExit(
					MouseEvent e )
				{
					over[0] = false;
					button.redraw();
				}
			});

		button.addMouseListener(
			new MouseAdapter()
			{
				@Override
				public void
				mouseUp(
					MouseEvent e )
				{
					if ( e.button == 1 ){
						action.run();
					}
				}
			});

		button.addPaintListener(
			new PaintListener()
			{
				@Override
				public void
				paintControl(
					PaintEvent e )
				{
					GC gc = e.gc;
					gc.setAntialias( SWT.ON );

					boolean close = type.equals( "close" );

					if ( over[0] ){
						gc.setBackground( close ? closeHover : hover );
						gc.fillRectangle( 0, 0, button.getSize().x, HEIGHT );
					}

					gc.setForeground( text );
					gc.setLineWidth( 1 );

					int cx = button.getSize().x / 2;
					int cy = HEIGHT / 2;

					if ( type.equals( "min" )){
						gc.drawLine( cx - 5, cy + 4, cx + 5, cy + 4 );
					}else if ( type.equals( "max" )){
						if ( shell.getMaximized()){
							gc.drawRectangle( cx - 4, cy - 5, 8, 8 );
							gc.drawLine( cx - 6, cy - 3, cx - 6, cy + 5 );
							gc.drawLine( cx - 6, cy + 5, cx + 2, cy + 5 );
						}else{
							gc.drawRectangle( cx - 5, cy - 5, 10, 10 );
						}
					}else{
						gc.drawLine( cx - 4, cy - 4, cx + 4, cy + 4 );
						gc.drawLine( cx + 4, cy - 4, cx - 4, cy + 4 );
					}
				}
			});

		return( button );
	}

	private void
	installDrag(
		Control	control )
	{
		control.addMouseListener(
			new MouseAdapter()
			{
				@Override
				public void
				mouseDown(
					MouseEvent e )
				{
					if ( e.button != 1 || shell.getMaximized()){
						return;
					}

					dragging = true;

					Point cursor = display.getCursorLocation();
					Point loc = shell.getLocation();

					dragOffset = new Point( cursor.x - loc.x, cursor.y - loc.y );
				}

				@Override
				public void
				mouseDoubleClick(
					MouseEvent e )
				{
					if ( e.button == 1 ){
						shell.setMaximized( !shell.getMaximized());
						shell.layout( true, true );
					}
				}
			});
	}

	private void
	detachNativeMenu()
	{
		try{
			Class<?> OS = Class.forName( "org.eclipse.swt.internal.win32.OS" );
			long hwnd = ((Number)Shell.class.getField( "handle" ).get( shell )).longValue();

			OS.getMethod( "SetMenu", long.class, long.class ).invoke( null, hwnd, 0L );
			OS.getMethod( "DrawMenuBar", long.class ).invoke( null, hwnd );
		}catch( Throwable e ){
			// Non-Windows or an older SWT; the normal menu bar is harmless.
		}
	}

	private Image
	loadLogo()
	{
		InputStream is = HarnessTitleBar.class.getResourceAsStream(
			"/com/biglybt/ui/images/harness/brand.png" );

		if ( is == null ){
			return( null );
		}

		try{
			return( new Image( display, is ));
		}catch( Throwable e ){
			return( null );
		}finally{
			try{
				is.close();
			}catch( Throwable ignore ){
			}
		}
	}

	private void
	showMenu()
	{
		if ( menuBar == null || menuBar.isDisposed()){
			return;
		}

		new HarnessMenuPopup( shell, this, menuBar ).show();
	}

	@Override
	public Point
	computeSize(
		int		wHint,
		int		hHint,
		boolean	changed )
	{
		return( new Point( wHint == SWT.DEFAULT ? 600 : wHint, HEIGHT ));
	}
}
