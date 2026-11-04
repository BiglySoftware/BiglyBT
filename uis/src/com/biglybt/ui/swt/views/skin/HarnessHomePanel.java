/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package com.biglybt.ui.swt.views.skin;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
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
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.utils.ColorCache;

/**
 * The Harness landing surface. It is intentionally quiet: a few numbers, a
 * couple of actions, and a status readout. No dashboard chrome, no web view.
 */
public class HarnessHomePanel
{
	private static final int	CONTENT_MAX_WIDTH	= 1000;
	private static final int	CARD_RADIUS			= 18;

	private final Composite	parent;
	private final Display	display;

	private final Color		bg;
	private final Color		card;
	private final Color		cardHover;
	private final Color		border;
	private final Color		borderHover;
	private final Color		text;
	private final Color		muted;
	private final Color		dim;
	private final Color		accent;
	private final Color		good;

	private final Font		titleFont;
	private final Font		subtitleFont;
	private final Font		cardTitleFont;
	private final Font		bodyFont;
	private final Font		smallFont;
	private final Font		valueFont;
	private final Font		monoFont;

	private Label			activeValue;
	private Label			downValue;
	private Label			upValue;
	private Label			libraryValue;
	private Label			networkValue;

	private TimerEventPeriodic	timer;

	public
	HarnessHomePanel(
		Composite	_parent )
	{
		parent	= _parent;
		display	= parent.getDisplay();

		bg			= rgb( 0x15, 0x15, 0x17 );
		card		= rgb( 0x1B, 0x1B, 0x1C );
		cardHover	= rgb( 0x21, 0x21, 0x24 );
		border		= rgb( 0x2A, 0x2A, 0x2D );
		borderHover	= rgb( 0x3A, 0x3A, 0x40 );
		text		= rgb( 0xE7, 0xE7, 0xE9 );
		muted		= rgb( 0x8B, 0x8F, 0x94 );
		dim			= rgb( 0x61, 0x61, 0x66 );
		accent		= rgb( 0x4D, 0x6B, 0xFE );
		good		= rgb( 0x67, 0xD8, 0x9A );

		titleFont		= new Font( display, "Segoe UI", 22, SWT.NORMAL );
		subtitleFont	= new Font( display, "Segoe UI", 10, SWT.NORMAL );
		cardTitleFont	= new Font( display, "Segoe UI", 9, SWT.NORMAL );
		bodyFont		= new Font( display, "Segoe UI", 10, SWT.NORMAL );
		smallFont		= new Font( display, "Segoe UI", 8, SWT.NORMAL );
		valueFont		= new Font( display, "Segoe UI", 20, SWT.NORMAL );
		monoFont		= new Font( display, "Consolas", 9, SWT.NORMAL );

		parent.setBackground( bg );

		build();

		timer = SimpleTimer.addPeriodicEvent(
			"harness.home.stats." + SystemTime.getCurrentTime(),
			1000,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event )
				{
					Utils.execSWTThread(
						new Runnable()
						{
							@Override
							public void
							run()
							{
								if ( !parent.isDisposed()){
									try{
										updateStats();
									}catch( Throwable e ){
									}
								}
							}
						});
				}
			});

		parent.addDisposeListener(
			new DisposeListener()
			{
				@Override
				public void
				widgetDisposed(
					DisposeEvent e )
				{
					if ( timer != null ){
						timer.cancel();
						timer = null;
					}

					Font[] fonts = { titleFont, subtitleFont, cardTitleFont, bodyFont, smallFont, valueFont, monoFont };

					for ( Font f: fonts ){
						if ( f != null && !f.isDisposed()){
							f.dispose();
						}
					}
				}
			});
	}

	private void
	build()
	{
		GridLayout shellLayout = new GridLayout( 1, false );
		shellLayout.marginWidth = 0;
		shellLayout.marginHeight = 0;
		parent.setLayout( shellLayout );

		ScrolledComposite scroller = new ScrolledComposite( parent, SWT.V_SCROLL );
		scroller.setBackground( bg );
		scroller.setExpandHorizontal( false );
		scroller.setExpandVertical( false );
		scroller.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, true ));

		Composite content = new Composite( scroller, SWT.NONE );
		content.setBackground( bg );

		GridLayout contentLayout = new GridLayout( 1, false );
		contentLayout.marginLeft = 52;
		contentLayout.marginRight = 52;
		contentLayout.marginTop = 42;
		contentLayout.marginBottom = 40;
		contentLayout.verticalSpacing = 18;
		content.setLayout( contentLayout );

		// ---- overline + live state -----------------------------------------

		Composite overline = new Composite( content, SWT.NONE );
		overline.setBackground( bg );
		GridLayout overlineLayout = new GridLayout( 2, false );
		overlineLayout.marginWidth = 0;
		overlineLayout.marginHeight = 0;
		overline.setLayout( overlineLayout );
		overline.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		Label kicker = label( overline, "TRANSFERS  /  HOME", smallFont, muted );
		kicker.setLayoutData( new GridData( SWT.LEFT, SWT.CENTER, true, false ));

		Composite live = new Composite( overline, SWT.NONE );
		live.setBackground( bg );
		RowLayout liveLayout = new RowLayout( SWT.HORIZONTAL );
		liveLayout.marginWidth = 0;
		liveLayout.marginHeight = 0;
		liveLayout.spacing = 7;
		live.setLayout( liveLayout );
		live.setLayoutData( new GridData( SWT.RIGHT, SWT.CENTER, false, false ));

		new StatusDot( live, good, bg ).setLayoutData( new org.eclipse.swt.layout.RowData( 8, 8 ));
		label( live, "READY", smallFont, muted );

		// ---- headline ------------------------------------------------------

		Label title = label( content, "A quieter torrent client.", titleFont, text );
		title.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		Label sub = label( content,
			"BiglyBT keeps the mechanics close and the noise out of the way. Pick up a transfer, or leave it running.",
			subtitleFont, muted );
		sub.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		addSpacer( content, 6 );

		// ---- numbers -------------------------------------------------------

		Composite stats = new Composite( content, SWT.NONE );
		stats.setBackground( bg );
		GridLayout statsLayout = new GridLayout( 3, true );
		statsLayout.marginWidth = 0;
		statsLayout.marginHeight = 0;
		statsLayout.horizontalSpacing = 12;
		stats.setLayout( statsLayout );
		stats.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		activeValue = statCard( stats, "ACTIVE TRANSFERS", "0", "in the swarm" );
		downValue = statCard( stats, "DOWNLOAD", "0 B/s", "this session" );
		upValue = statCard( stats, "UPLOAD", "0 B/s", "this session" );

		// ---- actions + system ---------------------------------------------

		Composite split = new Composite( content, SWT.NONE );
		split.setBackground( bg );
		GridLayout splitLayout = new GridLayout( 2, false );
		splitLayout.marginWidth = 0;
		splitLayout.marginHeight = 0;
		splitLayout.horizontalSpacing = 12;
		splitLayout.makeColumnsEqualWidth = false;
		split.setLayout( splitLayout );
		split.setLayoutData( new GridData( SWT.FILL, SWT.TOP, true, false ));

		Composite actions = new Composite( split, SWT.NONE );
		actions.setBackground( bg );
		GridLayout actionsLayout = new GridLayout( 1, false );
		actionsLayout.marginWidth = 0;
		actionsLayout.marginHeight = 0;
		actionsLayout.verticalSpacing = 8;
		actions.setLayout( actionsLayout );
		GridData actionsData = new GridData( SWT.FILL, SWT.TOP, true, false );
		actionsData.widthHint = 650;
		actions.setLayoutData( actionsData );

		sectionLabel( actions, "START A TRANSFER" );

		actionRow( actions, "O", "Open a .torrent file",
			"Choose a file from disk and add it to the library.", new Runnable()
			{
				@Override
				public void
				run()
				{
					UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentWindow();
				}
			});

		actionRow( actions, "M", "Add a magnet link",
			"Paste a magnet URI and let BiglyBT find the swarm.", new Runnable()
			{
				@Override
				public void
				run()
				{
					UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentWindow();
				}
			});

		actionRow( actions, "F", "Search the library",
			"Jump straight into the search field.", new Runnable()
			{
				@Override
				public void
				run()
				{
					UIFunctionsManagerSWT.getUIFunctionsSWT().promptForSearch();
				}
			});

		Composite system = new Composite( split, SWT.NONE );
		system.setBackground( bg );
		GridLayout systemLayout = new GridLayout( 1, false );
		systemLayout.marginWidth = 0;
		systemLayout.marginHeight = 0;
		systemLayout.verticalSpacing = 8;
		system.setLayout( systemLayout );
		GridData systemData = new GridData( SWT.FILL, SWT.TOP, false, false );
		systemData.widthHint = 360;
		system.setLayoutData( systemData );

		sectionLabel( system, "SYSTEM" );

		Composite systemCard = new Composite( system, SWT.NONE );
		systemCard.setBackground( bg );
		systemCard.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, false ));

		GridLayout bodyLayout = new GridLayout( 2, false );
		bodyLayout.marginWidth = 20;
		bodyLayout.marginHeight = 16;
		bodyLayout.verticalSpacing = 11;
		bodyLayout.horizontalSpacing = 14;
		systemCard.setLayout( bodyLayout );

		libraryValue = systemRow( systemCard, "library", "0 torrents" );
		networkValue = systemRow( systemCard, "network", "starting" );
		systemRow( systemCard, "theme", "harness dark" );
		systemRow( systemCard, "version", "BiglyBT " + Constants.getBaseVersion());
		systemRow( systemCard, "runtime", "Java " + System.getProperty( "java.version" ));

		// ---- recent --------------------------------------------------------

		Composite recent = new Composite( content, SWT.NONE );
		recent.setBackground( bg );
		GridLayout recentLayout = new GridLayout( 1, false );
		recentLayout.marginWidth = 0;
		recentLayout.marginHeight = 0;
		recentLayout.verticalSpacing = 8;
		recent.setLayout( recentLayout );
		recent.setLayoutData( new GridData( SWT.FILL, SWT.TOP, true, false ));

		sectionLabel( recent, "RECENT ACTIVITY" );

		Composite logCard = new Composite( recent, SWT.NONE );
		logCard.setBackground( bg );
		logCard.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, false ));

		GridLayout logLayout = new GridLayout( 1, false );
		logLayout.marginWidth = 20;
		logLayout.marginHeight = 16;
		logLayout.verticalSpacing = 6;
		logCard.setLayout( logLayout );

		Label prompt = label( logCard, "$ biglybt status", monoFont, muted );
		prompt.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		Label line1 = label( logCard, "  library      empty - nothing queued yet", monoFont, dim );
		line1.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		Label line2 = label( logCard, "  next         drop a .torrent anywhere in this window", monoFont, dim );
		line2.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		Label cursor = label( logCard, "> _", monoFont, accent );
		cursor.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		// ---- footer --------------------------------------------------------

		addSpacer( content, 2 );

		Label footer = label( content,
			"BiglyBT Harness Edition  /  GPLv2  /  built for Windows",
			smallFont, dim );
		footer.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		scroller.setContent( content );

		Runnable relayout = new Runnable()
		{
			@Override
			public void
			run()
			{
				int width = scroller.getClientArea().width;

				content.setLocation( 0, 0 );
				content.setSize( width, 800 );
				scroller.setMinSize( width, 800 );
				content.layout( true, true );
			}
		};

		scroller.addListener( SWT.Resize, e -> relayout.run());

		relayout.run();

		parent.redraw();

		try{
			updateStats();
		}catch( Throwable e ){
			// Core may still be wiring up its global manager on first launch.
		}
	}

	private void
	addSpacer(
		Composite	parent,
		int			height )
	{
		Composite spacer = new Composite( parent, SWT.NONE );
		spacer.setBackground( bg );
		GridData data = new GridData( SWT.FILL, SWT.CENTER, true, false );
		data.heightHint = height;
		spacer.setLayoutData( data );
	}

	private void
	sectionLabel(
		Composite	parent,
		String		value )
	{
		Label l = label( parent, value, smallFont, muted );
		GridData data = new GridData( SWT.FILL, SWT.CENTER, true, false );
		data.verticalIndent = 4;
		l.setLayoutData( data );
	}

	private Label
	statCard(
		Composite		parent,
		String			label,
		String			value,
		String			hint )
	{
		Composite c = new Composite( parent, SWT.NONE );
		c.setBackground( bg );
		c.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, false ));

		GridLayout layout = new GridLayout( 1, false );
		layout.marginWidth = 0;
		layout.marginTop = 0;
		layout.marginBottom = 8;
		layout.verticalSpacing = 2;
		c.setLayout( layout );

		label( c, label, smallFont, muted );
		Label valueLabel = label( c, value, valueFont, text );
		valueLabel.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));
		label( c, hint, smallFont, dim );

		return( valueLabel );
	}

	private void
	actionRow(
		Composite	parent,
		String		key,
		String		title,
		String		description,
		final Runnable action )
	{
		Composite c = new Composite( parent, SWT.NONE );
		c.setBackground( bg );
		c.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		GridLayout layout = new GridLayout( 2, false );
		layout.marginWidth = 10;
		layout.marginHeight = 7;
		layout.horizontalSpacing = 14;
		layout.verticalSpacing = 0;
		c.setLayout( layout );

		KeyCap cap = new KeyCap( c, key, bg, border, text );
		cap.setLayoutData( new GridData( SWT.CENTER, SWT.CENTER, false, false ));

		Composite copy = new Composite( c, SWT.NONE );
		copy.setData( "utils:skinned-bg-forced", Boolean.TRUE );
		copy.setBackground( bg );
		GridLayout copyLayout = new GridLayout( 1, false );
		copyLayout.marginWidth = 0;
		copyLayout.marginHeight = 0;
		copyLayout.verticalSpacing = 2;
		copy.setLayout( copyLayout );
		copy.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		Label t = label( copy, title, bodyFont, text );
		t.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));
		Label d = label( copy, description, smallFont, muted );
		d.setLayoutData( new GridData( SWT.FILL, SWT.CENTER, true, false ));

		MouseAdapter click = new MouseAdapter()
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
		};

		for ( Control control: new Control[]{ c, copy, t, d, cap }){
			control.setCursor( display.getSystemCursor( SWT.CURSOR_HAND ));
			control.addMouseListener( click );
		}
	}

	private Label
	systemRow(
		Composite	parent,
		String		key,
		String		value )
	{
		Label k = label( parent, key, monoFont, dim );
		k.setLayoutData( new GridData( SWT.LEFT, SWT.CENTER, false, false ));

		Label v = label( parent, value, monoFont, text );
		v.setLayoutData( new GridData( SWT.RIGHT, SWT.CENTER, true, false ));

		return( v );
	}

	private void
	addMouse(
		Control			hoverOwner,
		MouseAdapter	click,
		Control...		controls )
	{
		hoverOwner.addMouseTrackListener(
			new MouseTrackAdapter()
			{
				@Override
				public void
				mouseEnter(
					MouseEvent e )
				{
					((HarnessCard)hoverOwner).setHovered( true );
				}

				@Override
				public void
				mouseExit(
					MouseEvent e )
				{
					((HarnessCard)hoverOwner).setHovered( false );
				}
			});

		hoverOwner.setCursor( display.getSystemCursor( SWT.CURSOR_HAND ));

		for ( Control c: controls ){
			c.setCursor( display.getSystemCursor( SWT.CURSOR_HAND ));
			c.addMouseListener( click );

			if ( c != hoverOwner ){
				c.addMouseTrackListener(
					new MouseTrackAdapter()
					{
						@Override
						public void
						mouseEnter(
							MouseEvent e )
						{
							((HarnessCard)hoverOwner).setHovered( true );
						}

						@Override
						public void
						mouseExit(
							MouseEvent e )
						{
							if ( !hoverOwner.getBounds().contains( display.getCursorLocation())){
								((HarnessCard)hoverOwner).setHovered( false );
							}
						}
					});
			}
		}
	}

	private Label
	label(
		Composite	parent,
		String		value,
		Font		font,
		Color		color )
	{
		Label label = new Label( parent, SWT.NONE );
		label.setData( "utils:skinned-bg-forced", Boolean.TRUE );
		label.setData( "utils:skinned-fg-forced", Boolean.TRUE );
		label.setBackground( parent instanceof HarnessCard ? card : parent.getBackground());
		label.setForeground( color );
		label.setFont( font );
		label.setText( value );
		return( label );
	}

	private void
	updateStats()
	{
		if ( !CoreFactory.isCoreAvailable()){
			return;
		}

		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();

		if ( gm == null ){
			return;
		}

		List<DownloadManager> dms = gm.getDownloadManagers();

		int active = 0;

		for ( DownloadManager dm: dms ){
			int state = dm.getState();
			if ( state == DownloadManager.STATE_DOWNLOADING || state == DownloadManager.STATE_CHECKING ){
				active++;
			}
		}

		long down = gm.getStats().getDataReceiveRate();
		long up = gm.getStats().getDataSendRate();

		activeValue.setText( String.valueOf( active ));
		downValue.setText( DisplayFormatters.formatByteCountToKiBEtcPerSec( down ));
		upValue.setText( DisplayFormatters.formatByteCountToKiBEtcPerSec( up ));

		if ( libraryValue != null && !libraryValue.isDisposed()){
			libraryValue.setText( dms.size() + ( dms.size() == 1 ? " torrent" : " torrents" ));
		}

		if ( networkValue != null && !networkValue.isDisposed()){
			networkValue.setText( active > 0 ? "transferring" : "ready" );
		}
	}

	private Color
	rgb(
		int	r,
		int	g,
		int	b )
	{
		return( ColorCache.getColor( display, r, g, b ));
	}

	/**
	 * A surface with a one-pixel hairline. Child controls paint directly on the
	 * card colour, so it behaves like the flat command cards in Harness.
	 */
	private class
	HarnessCard
		extends Canvas
	{
		private final Color	fill;
		private final Color	stroke;
		private final Color	hoverFill;
		private boolean		hovered;

		private
		HarnessCard(
			Composite	parent,
			Color		fill,
			Color		stroke,
			Color		hoverFill )
		{
			super( parent, SWT.NONE );
			this.fill		= fill;
			this.stroke		= stroke;
			this.hoverFill	= hoverFill;

			setBackground( bg );

			GridLayout cardLayout = new GridLayout( 1, false );
			cardLayout.marginWidth = 0;
			cardLayout.marginHeight = 0;
			setLayout( cardLayout );

			addPaintListener(
				new PaintListener()
				{
					@Override
					public void
					paintControl(
						PaintEvent e )
					{
						GC gc = e.gc;
						gc.setAntialias( SWT.ON );

						Rectangle r = getClientArea();
						int w = r.width - 1;
						int h = r.height - 1;

						gc.setBackground( hovered && hoverFill != null ? hoverFill : fill );
						gc.fillRoundRectangle( 0, 0, w, h, CARD_RADIUS, CARD_RADIUS );

						gc.setForeground( hovered ? borderHover : stroke );
						gc.drawRoundRectangle( 0, 0, w, h, CARD_RADIUS, CARD_RADIUS );
					}
				});
		}

		private void
		setHovered(
			boolean	value )
		{
			if ( hovered != value ){
				hovered = value;
				redraw();
			}
		}

	}

	private class
	KeyCap
		extends Canvas
	{
		private final String	key;
		private final Color		fill;
		private final Color		stroke;

		private
		KeyCap(
			Composite	parent,
			String		key,
			Color		fill,
			Color		stroke,
			Color		textColor )
		{
			super( parent, SWT.NONE );
			this.key	= key;
			this.fill	= fill;
			this.stroke	= stroke;

			setBackground( card );

			addPaintListener(
				new PaintListener()
				{
					@Override
					public void
					paintControl(
						PaintEvent e )
					{
						GC gc = e.gc;
						gc.setAntialias( SWT.ON );
						Rectangle r = getClientArea();

						gc.setBackground( fill );
						gc.fillRoundRectangle( 0, 0, r.width - 1, r.height - 1, 10, 10 );
						gc.setForeground( stroke );
						gc.drawRoundRectangle( 0, 0, r.width - 1, r.height - 1, 10, 10 );

						gc.setForeground( textColor );
						gc.setFont( monoFont );
						org.eclipse.swt.graphics.Point extent = gc.textExtent( key );
						gc.drawText( key, ( r.width - extent.x ) / 2, ( r.height - extent.y ) / 2, true );
					}
				});
		}

		@Override
		public org.eclipse.swt.graphics.Point
		computeSize(
			int		wHint,
			int		hHint,
			boolean	changed )
		{
			return( new org.eclipse.swt.graphics.Point( 28, 28 ));
		}
	}

	private class
	StatusDot
		extends Canvas
	{
		private final Color	dot;
		private final Color	back;

		private
		StatusDot(
			Composite	parent,
			Color		dot,
			Color		back )
		{
			super( parent, SWT.NONE );
			this.dot	= dot;
			this.back	= back;
			setBackground( back );

			addPaintListener(
				new PaintListener()
				{
					@Override
					public void
					paintControl(
						PaintEvent e )
					{
						GC gc = e.gc;
						gc.setAntialias( SWT.ON );
						gc.setBackground( dot );
						gc.fillOval( 0, 0, 7, 7 );
					}
				});
		}

		@Override
		public org.eclipse.swt.graphics.Point
		computeSize(
			int		wHint,
			int		hHint,
			boolean	changed )
		{
			return( new org.eclipse.swt.graphics.Point( 8, 8 ));
		}
	}
}

