/*
 * Created on Oct 2, 2012
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.ui.swt;

import java.util.function.Consumer;

import org.eclipse.swt.browser.CloseWindowListener;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.Debug;


public abstract class
BrowserWrapper
{
	public static BrowserWrapper
	createBrowser(
		Composite		composite,
		int				style )
	{
			// dump checking is async and might decide to disable the real browser to give it a chance to
			// complete in case there's a race

		AEDiagnostics.waitForDumpChecks( 10*1000 );

		boolean use_fake = COConfigurationManager.getBooleanParameter( "browser.internal.disable" );

		if ( use_fake ){

			return( new BrowserWrapperFake( composite, style, null ));

		}else{

			return( BrowserWrapperSWTFactory.create( composite, style ));
		}
	}

	protected
	BrowserWrapper()
	{
	}

	public abstract boolean
	isFake();

	public abstract Composite
	getControl();

	public abstract  void
	setVisible(
		boolean		visible );

	public abstract boolean
	isVisible();

	public abstract  boolean
	isDisposed();

	public abstract void
	dispose();

	public abstract  boolean
	execute(
		String		str );

	public abstract boolean
	isBackEnabled();

	public abstract String
	getUrl();

	public abstract void
	setUrl(
		String		url );

	public abstract void
	setFakeUrl(
		String				url,
		Consumer<String>	click_listener );
	
	public abstract void
	setText(
		String		text );

	public abstract void
	setData(
		String		key,
		Object		value );

	public abstract Object
	getData(
		String	key );

	public abstract void
	back();

	public abstract void
	refresh();

	public abstract void
	update();

	public abstract Shell
	getShell();

	public abstract Display
	getDisplay();

	public abstract Composite
	getParent();

	public abstract Object
	getLayoutData();

	public abstract void
	setLayoutData(
		Object	data );

	public abstract void
	setFocus();

	public abstract void
	addListener(
		int			type,
		Listener	l );

	public abstract void
	addLocationListener(
		LocationListener		l );

	public abstract void
	removeLocationListener(
		LocationListener		l );

	public abstract void
	addTitleListener(
		TitleListener		l );

	public abstract void
	addProgressListener(
		ProgressListener		l );

	public abstract void
	removeProgressListener(
		ProgressListener		l );

	public abstract void
	addOpenWindowListener(
		OpenWindowListener		l );

	public abstract void
	setBrowser(
		WindowEvent		event );
	
	public abstract void
	addCloseWindowListener(
		CloseWindowListener		l );

	public abstract void
	addDisposeListener(
		DisposeListener		l );

	public abstract void
	removeDisposeListener(
		DisposeListener		l );

	public abstract void
	addStatusTextListener(
		StatusTextListener		l );

	public abstract void
	removeStatusTextListener(
		StatusTextListener		l );

	public abstract BrowserFunction
	addBrowserFunction(
		String				name,
		BrowserFunction		bf );

	public static abstract class
	BrowserFunction
	{
		private BrowserFunction		delegate;

		public void
		bind(
			BrowserFunction		_delegate )
		{
			delegate = _delegate;
		}

		public abstract Object
		function(
			Object[] arguments );

		public boolean
		isDisposed()
		{
			if ( delegate != null ){

				return( delegate.isDisposed());
			}

			Debug.out( "wrong" );

			return( false );
		}

		public void
		dispose()
		{
			if ( delegate != null ){

				delegate.dispose();
			}

			Debug.out( "wrong" );
		}
	}
	
	public interface
	OpenWindowListener
	{
		public void
		open(
			WindowEvent		event );
	}
	
	public interface
	WindowEvent
	{
		public void
		setRequired(
			boolean		required );
	}
}
