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

import java.util.function.Consumer;

import org.eclipse.swt.browser.Browser;
//import org.eclipse.swt.chromium.Browser;

import org.eclipse.swt.browser.CloseWindowListener;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.OpenWindowListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.browser.WindowEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.util.Debug;

public class
BrowserWrapperSWT
	extends BrowserWrapper
{
	private Browser		browser;

	protected
	BrowserWrapperSWT(
		Composite		composite,
		int				style )
	{
		browser = new Browser( composite, style );
		
		browser.setData( BrowserWrapperSWTFactory.BROWSER_KEY, true );
	}

	@Override
	public boolean
	isFake()
	{
		return( false );
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
		((WindowEventImpl)event).setBrowser( browser );
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
		//System.out.println( "execute: " + str );

		return( browser.execute( str ));
	}

	@Override
	public boolean
	isBackEnabled()
	{
		return( browser.isBackEnabled());
	}

	@Override
	public String
	getUrl()
	{
		return( browser.getUrl());
	}

	@Override
	public void
	setFakeUrl(
		String				url,
		Consumer<String>	listener )
	{
		Debug.out( "Should be called on fake browser..." );
		
		setUrl(url);
	}
	
	@Override
	public void
	setUrl(
		String		url )
	{
		browser.setUrl( url );
	}

	@Override
	public void
	setText(
		String		text )
	{
		browser.setText( text );
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
		browser.back();
	}

	@Override
	public void
	refresh()
	{
		browser.refresh();
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
		browser.addLocationListener( l );
	}

	@Override
	public void
	removeLocationListener(
		LocationListener		l )
	{
		browser.removeLocationListener( l );
	}

	@Override
	public void
	addTitleListener(
		TitleListener		l )
	{
		browser.addTitleListener( l );
	}

	@Override
	public void
	addProgressListener(
		ProgressListener		l )
	{
		browser.addProgressListener( l );
	}

	@Override
	public void
	removeProgressListener(
		ProgressListener		l )
	{
		browser.removeProgressListener( l );
	}

	@Override
	public void
	addCloseWindowListener(
		CloseWindowListener		l )
	{
		browser.addCloseWindowListener( l );
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
		browser.addStatusTextListener( l );
	}

	@Override
	public void
	removeStatusTextListener(
		StatusTextListener		l )
	{
		browser.removeStatusTextListener( l );
	}
	
	
	@Override
	public void
	addOpenWindowListener(
		OpenWindowListener		l )
	{
		browser.addOpenWindowListener( 
				new org.eclipse.swt.browser.OpenWindowListener(){
					
					@Override
					public void open(org.eclipse.swt.browser.WindowEvent event){
						l.open( new WindowEventImpl( event ));
					}
				});
	}
	
	@Override
	public BrowserFunction
	addBrowserFunction(
		String						name,
		final BrowserFunction		bf )
	{
		org.eclipse.swt.browser.BrowserFunction swt_bf =
			new org.eclipse.swt.browser.BrowserFunction(
				browser,
				name )
			{
				@Override
				public Object
				function(
					Object[] arguments )
				{
					return( bf.function(arguments));
				}
			};

		return( new BrowserFunctionSWT( bf, swt_bf ));
	}

	public static class
	BrowserFunctionSWT
		extends BrowserFunction
	{
		private final BrowserFunction							bf;
		private final org.eclipse.swt.browser.BrowserFunction	swt_bf;

		private
		BrowserFunctionSWT(
			BrowserFunction								_bf,
			org.eclipse.swt.browser.BrowserFunction	_swt_bf )
		{
			bf		= _bf;
			swt_bf 	= _swt_bf;

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
			return( swt_bf.isDisposed());
		}

		@Override
		public void
		dispose()
		{
			swt_bf.dispose();
		}
	}
	
	private class
	WindowEventImpl
		implements WindowEvent
	{
		private org.eclipse.swt.browser.WindowEvent	event;

		private
		WindowEventImpl(
			org.eclipse.swt.browser.WindowEvent	_event )
		{
			event	= _event;
		}
		
		@Override
		public void setRequired(boolean required){
			event.required = required;
		}
		
		private void
		setBrowser(
			org.eclipse.swt.browser.Browser	browser )
		{
			event.browser = browser;
		}
	}

	
	/*
	@Override
	public void
	addOpenWindowListener(
		OpenWindowListener		l )
	{
		browser.addOpenWindowListener( 
			new org.eclipse.swt.chromium.OpenWindowListener(){
				
				@Override
				public void open(org.eclipse.swt.chromium.WindowEvent event){
					l.open( new WindowEventImpl( event ));
				}
			});
	}
	
	@Override
	public BrowserFunction
	addBrowserFunction(
		String						name,
		final BrowserFunction		bf )
	{
		org.eclipse.swt.chromium.BrowserFunction swt_bf =
			new org.eclipse.swt.chromium.BrowserFunction(
				browser,
				name )
			{
				@Override
				public Object
				function(
					Object[] arguments )
				{
					return( bf.function(arguments));
				}
			};

		return( new BrowserFunctionSWTChromium( bf, swt_bf ));
	}
	
	public static class
	BrowserFunctionSWT
		extends BrowserFunction
	{
		private final BrowserFunction							bf;
		private final org.eclipse.swt.chromium.BrowserFunction	swt_bf;

		private
		BrowserFunctionSWT(
			BrowserFunction								_bf,
			org.eclipse.swt.chromium.BrowserFunction	_swt_bf )
		{
			bf		= _bf;
			swt_bf 	= _swt_bf;

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
			return( swt_bf.isDisposed());
		}

		@Override
		public void
		dispose()
		{
			swt_bf.dispose();
		}
	}
	
	private class
	WindowEventImpl
		implements WindowEvent
	{
		private org.eclipse.swt.chromium.WindowEvent	event;

		private
		WindowEventImpl(
			org.eclipse.swt.chromium.WindowEvent	_event )
		{
			event	= _event;
		}
		
		@Override
		public void setRequired(boolean required){
			event.required = required;
		}
		
		private void
		setBrowser(
			org.eclipse.swt.chromium.Browser	browser )
		{
			event.browser = browser;
		}
	}
	*/
}
