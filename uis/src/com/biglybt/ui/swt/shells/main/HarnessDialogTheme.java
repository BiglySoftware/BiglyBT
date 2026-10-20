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
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;

import com.biglybt.ui.swt.utils.ColorCache;

/**
 * Applies the Harness surface to shells created by plugins and legacy code.
 * The main window builds its own chrome, but every secondary dialog should
 * still feel like it belongs to the same product.
 */
public class HarnessDialogTheme
{
	private static final String	THEMED	= "harness.dialog.themed";
	private static final String	CSS		=
		"html,body{background:#151517!important;color:#E7E7E9!important;}" +
		"body *{border-color:#2A2A2D!important;}" +
		"a{color:#8FA3FF!important;}" +
		"input,select,textarea,button{background:#1F1F21!important;color:#E7E7E9!important;" +
		"border:1px solid #2A2A2D!important;border-radius:6px!important;}" +
		"table,td,th,div,section,article{background-color:transparent!important;color:#E7E7E9!important;}" +
		"h1,h2,h3,h4{color:#F2F2F4!important;}";

	private static Color		bg;
	private static Color		surface;
	private static Color		text;
	private static Color		muted;
	private static Color		border;

	private static Shell		mainShell;

	public static void
	install(
		Display		display,
		Shell		_mainShell )
	{
		mainShell = _mainShell;

		org.eclipse.swt.widgets.Listener listener = e -> {
			if ( e.widget instanceof Shell ){
				Shell shell = (Shell)e.widget;

				if ( shell != mainShell && !shell.isDisposed()){
					apply( shell );
				}
			}
		};

		display.addFilter( SWT.Activate, listener );
		display.addFilter( SWT.Show, listener );

		for ( Shell shell: display.getShells()){
			if ( shell != mainShell ){
				apply( shell );
			}
		}

		// Plugin dialogs are often created from background threads and do not
		// emit a reliable Show/Activate event. Sweep once a second instead.
		display.timerExec(
			750,
			new Runnable()
			{
				@Override
				public void
				run()
				{
					if ( display.isDisposed()){
						return;
					}

					for ( Shell shell: display.getShells()){
						if ( shell != mainShell && !shell.isDisposed()){
							apply( shell );
						}
					}

					display.timerExec( 750, this );
				}
			});
	}

	public static void
	reapply(
		Shell	shell )
	{
		if ( shell != null && !shell.isDisposed()){
			shell.setData( THEMED, null );
			apply( shell );
		}
	}

	public static void
	apply(
		Shell	shell )
	{
		if ( shell == null || shell.isDisposed() || shell.getData( THEMED ) != null ){
			return;
		}

		shell.setData( THEMED, Boolean.TRUE );

		Display display = shell.getDisplay();

		if ( bg == null || bg.isDisposed()){
			bg		= ColorCache.getColor( display, 21, 21, 23 );
			surface	= ColorCache.getColor( display, 31, 31, 33 );
			text	= ColorCache.getColor( display, 231, 231, 233 );
			muted	= ColorCache.getColor( display, 139, 143, 148 );
			border	= ColorCache.getColor( display, 42, 42, 45 );
		}

		shell.setBackground( bg );
		shell.setForeground( text );

		applyControl( shell );
	}

	private static void
	applyControl(
		Control	control )
	{
		if ( control == null || control.isDisposed()){
			return;
		}

		applyNativeTheme( control );

		try{
			if ( control instanceof Browser ){
				Browser browser = (Browser)control;

				browser.setBackground( bg );
				browser.setForeground( text );

				browser.addProgressListener(
					new ProgressAdapter()
					{
						@Override
						public void
						completed(
							ProgressEvent event )
						{
							inject( browser );
						}
					});

				inject( browser );
			}else if ( control instanceof CTabFolder ){
				CTabFolder tabs = (CTabFolder)control;

				tabs.setBackground( bg );
				tabs.setForeground( text );
				tabs.setSelectionBackground( surface );
				tabs.setSelectionForeground( text );
			}else if ( control instanceof Table ){
				Table table = (Table)control;

				table.setBackground( bg );
				table.setForeground( text );
				table.setHeaderBackground( surface );
				table.setHeaderForeground( text );
			}else if ( control instanceof Tree ){
				Tree tree = (Tree)control;

				tree.setBackground( bg );
				tree.setForeground( text );
				tree.setHeaderBackground( surface );
				tree.setHeaderForeground( text );
			}else if ( control instanceof List ){
				List list = (List)control;

				list.setBackground( bg );
				list.setForeground( text );
			}else if ( control instanceof CLabel ){
				CLabel label = (CLabel)control;

				label.setBackground( bg );
				label.setForeground( text );
			}else if ( control instanceof StyledText ){
				StyledText styled = (StyledText)control;

				styled.setBackground( surface );
				styled.setForeground( text );
			}else if ( control instanceof Text ){
				Text textControl = (Text)control;

				textControl.setBackground( surface );
				textControl.setForeground( text );
			}else if ( control instanceof Combo ){
				Combo combo = (Combo)control;

				combo.setBackground( surface );
				combo.setForeground( text );
			}else if ( control instanceof Spinner ){
				Spinner spinner = (Spinner)control;

				spinner.setBackground( surface );
				spinner.setForeground( text );
			}else if ( control instanceof Scale ){
				Scale scale = (Scale)control;

				scale.setBackground( bg );
				scale.setForeground( text );
			}else if ( control instanceof ProgressBar ){
				ProgressBar progress = (ProgressBar)control;

				progress.setBackground( bg );
				progress.setForeground( border );
			}else if ( control instanceof Button ){
				Button button = (Button)control;

				button.setBackground( surface );
				button.setForeground( text );
			}else if ( control instanceof Label ){
				Label label = (Label)control;

				label.setBackground( bg );
				label.setForeground( text );
			}else if ( control instanceof Group ){
				Group group = (Group)control;

				group.setBackground( bg );
				group.setForeground( text );
			}else if ( control instanceof Composite ){
				Composite composite = (Composite)control;

				composite.setBackground( bg );
				composite.setForeground( text );
			}
		}catch( Throwable e ){
			// Some native peer is in a transient state; the next activation
			// will retry.
		}

		if ( control instanceof Composite ){
			for ( Control child: ((Composite)control).getChildren()){
				applyControl( child );
			}
		}
	}

	private static void
	applyNativeTheme(
		Control	control )
	{
		try{
			Class<?> OS = Class.forName( "org.eclipse.swt.internal.win32.OS" );
			long hwnd = ((Number)Control.class.getField( "handle" ).get( control )).longValue();

			OS.getMethod( "AllowDarkModeForWindow", long.class, boolean.class ).invoke( null, hwnd, true );

			String theme = control instanceof Button ? "" : "DarkMode_Explorer";

			OS.getMethod( "SetWindowTheme", long.class, char[].class, char[].class ).invoke(
				null, hwnd, theme.toCharArray(), null );
		}catch( Throwable e ){
		}
	}

	private static void
	inject(
		Browser	browser )
	{
		if ( browser == null || browser.isDisposed()){
			return;
		}

		try{
			String script =
				"(function(){var s=document.createElement('style');" +
				"s.type='text/css';s.appendChild(document.createTextNode(" + quote( CSS ) + "));" +
				"(document.head||document.documentElement).appendChild(s);})();";

			browser.execute( script );
		}catch( Throwable e ){
		}
	}

	private static String
	quote(
		String	value )
	{
		return( "'" + value.replace( "\\", "\\\\" ).replace( "'", "\\'" )
			.replace( "\r", "" ).replace( "\n", "" ) + "'" );
	}
}
