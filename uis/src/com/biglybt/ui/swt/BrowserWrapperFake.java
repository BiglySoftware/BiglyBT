/*
 * Created on Apr 1, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.ui.swt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.CloseWindowListener;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;


public class
BrowserWrapperFake
	extends BrowserWrapper
{
	private Composite		browser;
	private Label			link_label;
	private Label			description_label;

	private String 			url;
	private String			description;

	private List<LocationListener>		location_listeners 	= new ArrayList<>();
	private List<ProgressListener>		progress_listeners 	= new ArrayList<>();
	private List<TitleListener>			title_listeners 	= new ArrayList<>();

	protected
	BrowserWrapperFake(
		Composite		_parent,
		int				style,
		SWTError		_failure )
	{
		Composite parent = _parent;

		browser = new Composite(parent, SWT.NULL );
		browser.setBackground( Colors.white );

		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		browser.setLayout(layout);

		if ( _failure == null ){

			Label label = new Label(browser, SWT.WRAP);
			Messages.setLanguageText(label, "browser.internal.disabled.info");
			GridData grid_data = new GridData( GridData.FILL_HORIZONTAL );
			grid_data.horizontalSpan = 3;
			label.setLayoutData(grid_data);
			label.setBackground( Colors.white );

			label = new Label(browser, SWT.NULL);
			Messages.setLanguageText(label, "browser.internal.disabled.reenable");

			final Button button = new Button( browser, SWT.NULL );
			Messages.setLanguageText(button, "label.enable");

			button.addSelectionListener(
				new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						button.setEnabled( false );
						COConfigurationManager.setParameter( "browser.internal.disable", false );
					}
				});

			label = new Label(browser, SWT.NULL);
			grid_data = new GridData( GridData.FILL_HORIZONTAL );
			label.setLayoutData(grid_data);

		}else{

			Label label = new Label(browser, SWT.WRAP);
			Messages.setLanguageText(label, "browser.internal.failed.info", new String[]{ Debug.getNestedExceptionMessage( _failure )});
			GridData grid_data = new GridData( GridData.FILL_HORIZONTAL );
			grid_data.horizontalSpan = 3;
			label.setLayoutData(grid_data);
			label.setBackground( Colors.white );
		}

			// details

		Composite details = new Composite( browser, SWT.BORDER );
		layout = new GridLayout();
		layout.numColumns = 2;
		details.setLayout(layout);
		GridData grid_data = new GridData( GridData.FILL_BOTH );
		grid_data.horizontalSpan = 3;
		details.setLayoutData(grid_data);
		details.setBackground( Colors.white );

			// url

		Label label = new Label(details, SWT.NULL );
		label.setText( "URL" );
		label.setLayoutData(new GridData());
		label.setBackground( Colors.white );


		link_label = new Label(details, SWT.WRAP);
		link_label.setText( MessageText.getString( "ConfigView.label.please.visit.here" ));

		link_label.setCursor(link_label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		link_label.setForeground( link_label.getDisplay().getSystemColor(SWT.COLOR_LINK_FOREGROUND));
		link_label.setBackground( Colors.white );

		link_label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				Utils.launch( url );
			}
			@Override
			public void mouseUp(MouseEvent e) {

				if ( e.button == 1 && e.stateMask != SWT.CONTROL){

					Utils.launch( url );
				}
			}
		});

		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.horizontalIndent = 10;
		link_label.setLayoutData(grid_data);

		ClipboardCopy.addCopyToClipMenu(
			link_label,
			new ClipboardCopy.copyToClipProvider() {

				@Override
				public String getText() {
					return( url );
				}
			});

			// desc

		label = new Label(details, SWT.NULL );
		Messages.setLanguageText(label, "label.description" );
		label.setLayoutData(new GridData());
		label.setBackground( Colors.white );

		description_label = new Label(details, SWT.WRAP );
		description_label.setText( "" );
		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.horizontalIndent = 10;
		description_label.setLayoutData(grid_data);
		description_label.setBackground( Colors.white );
	}

	@Override
	public boolean
	isFake()
	{
		return( true );
	}

	@Override
	public Composite
	getControl()
	{
		return( browser );
	}

	@Override
	public void
	setBrowser(
		WindowEvent		event )
	{
	}

	@Override
	public void
	setVisible(
		boolean		visible )
	{
		browser.setVisible( visible );
	}

	@Override
	public boolean
	isVisible()
	{
		return( browser.isVisible());
	}

	@Override
	public boolean
	isDisposed()
	{
		return( browser.isDisposed());
	}

	@Override
	public void
	dispose()
	{
		browser.dispose();
	}

	@Override
	public boolean
	execute(
		String		str )
	{
		return( false );
	}

	@Override
	public boolean
	isBackEnabled()
	{
		return( false );
	}

	@Override
	public String
	getUrl()
	{
		return( url );
	}

	@Override
	public void
	setUrl(
		final String		_url )
	{
		url		= _url;

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					String url_str = _url;

					int	pos = url_str.indexOf( '?' );

					if ( pos != -1 ){

						url_str = url_str.substring( 0, pos );
					}

					link_label.setText( url_str );

					browser.layout();

					for ( LocationListener l: location_listeners ){

						try{
							LocationEvent event = new LocationEvent( browser );

							event.top 		= true;
							event.location 	= _url;

							l.changed( event );

						}catch( Throwable e){

							Debug.out( e );
						}
					}

					for ( ProgressListener l: progress_listeners ){

						try{
							ProgressEvent event = new ProgressEvent( browser );

							l.completed( event );

						}catch( Throwable e){

							Debug.out( e );
						}
					}

					for ( TitleListener l: title_listeners ){

						try{
							TitleEvent event = new TitleEvent( browser );

							event.title = "Browser Disabled";

							l.changed( event );

						}catch( Throwable e){

							Debug.out( e );
						}
					}
				}
			});
	}

	@Override
	public void
	setText(
		String		text )
	{
		description	= text;

		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					description_label.setText( description );

					browser.layout();
				}
			});
	}

	@Override
	public void
	setData(
		String		key,
		Object		value )
	{
		browser.setData(key, value);
	}

	@Override
	public Object
	getData(
		String	key )
	{
		return( browser.getData( key ));
	}

	@Override
	public void
	back()
	{
	}

	@Override
	public void
	refresh()
	{
	}

	@Override
	public void
	update()
	{
		browser.update();
	}

	@Override
	public Shell
	getShell()
	{
		return( browser.getShell());
	}

	@Override
	public Display
	getDisplay()
	{
		return( browser.getDisplay());
	}

	@Override
	public Composite
	getParent()
	{
		return( browser.getParent());
	}

	@Override
	public Object
	getLayoutData()
	{
		return( browser.getLayoutData());
	}

	@Override
	public void
	setLayoutData(
		Object	data )
	{
		browser.setLayoutData( data );
	}

	@Override
	public void
	setFocus()
	{
		browser.setFocus();
	}

	@Override
	public void
	addListener(
		int			type,
		Listener	l )
	{
		browser.addListener( type, l );
	}

	@Override
	public void
	addLocationListener(
		LocationListener		l )
	{
		location_listeners.add( l );
	}

	@Override
	public void
	removeLocationListener(
		LocationListener		l )
	{
		location_listeners.remove( l );
	}

	@Override
	public void
	addTitleListener(
		TitleListener		l )
	{
		title_listeners.add( l );
	}

	@Override
	public void
	addProgressListener(
		ProgressListener		l )
	{
		progress_listeners.add( l );
	}

	@Override
	public void
	removeProgressListener(
		ProgressListener		l )
	{
		progress_listeners.remove( l );
	}

	@Override
	public void
	addOpenWindowListener(
		OpenWindowListener		l )
	{
	}

	@Override
	public void
	addCloseWindowListener(
		CloseWindowListener		l )
	{
	}

	@Override
	public void
	addDisposeListener(
		DisposeListener		l )
	{
		browser.addDisposeListener( l );
	}

	@Override
	public void
	removeDisposeListener(
		DisposeListener		l )
	{
		browser.removeDisposeListener( l );
	}

	@Override
	public void
	addStatusTextListener(
		StatusTextListener		l )
	{
	}

	@Override
	public void
	removeStatusTextListener(
		StatusTextListener		l )
	{
	}

	@Override
	public BrowserFunction
	addBrowserFunction(
		String						name,
		final BrowserFunction		bf )
	{
		return( new BrowserFunctionFake( bf ));
	}

	public static class
	BrowserFunctionFake
		extends BrowserFunction
	{
		private final BrowserFunction	bf;

		private boolean	disposed;

		private
		BrowserFunctionFake(
			BrowserFunction	_bf )
		{
			bf		= _bf;

			bf.bind( this );
		}

		@Override
		public Object
		function(
			Object[] arguments )
		{
			return( bf.function( arguments ));
		}

		@Override
		public boolean
		isDisposed()
		{
			return( disposed );
		}

		@Override
		public void
		dispose()
		{
			disposed	= true;
		}
	}
}
