/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.browser.listener;

import java.util.*;
import java.net.URL;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.BrowserWrapper;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.progress.ProgressWindow;

import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.util.http.HTTPAuthHelper;
import com.biglybt.core.util.http.HTTPAuthHelperListener;
import com.biglybt.ui.swt.browser.CookiesListener;

public class ExternalLoginWindow {

	Display display;
	Shell shell;
	BrowserWrapper browser;

	ExternalLoginListener	listener;

	String originalLoginUrl;

	Map	cookies = new HashMap();

	Set	sniffer_cookies = new HashSet();
	Set js_cookies		= new HashSet();

	HTTPAuthHelper	sniffer;

	public
	ExternalLoginWindow(
		ExternalLoginListener _listener,
		String name,
		final String _loginUrl,
		boolean captureMode,
		String	authMode,
		boolean isMine )
	{
		listener			= _listener;
		originalLoginUrl 	= _loginUrl;

		shell = ShellFactory.createMainShell(SWT.TITLE | SWT.CLOSE);
		shell.setSize(800,600);
		Utils.centreWindow(shell);

		display = shell.getDisplay();
		shell.setText(MessageText.getString("externalLogin.title"));

		shell.setLayout(new FormLayout());

		shell.addDisposeListener(
			new DisposeListener()
			{
				@Override
				public void
				widgetDisposed(
					DisposeEvent arg0)
				{
					if ( sniffer != null ){

						sniffer.destroy();
					}
				}
			});


		Label explain = new Label(shell,SWT.WRAP);
		if(captureMode) {
			explain.setText(MessageText.getString("externalLogin.explanation.capture", new String[]{ name }));
		} else {
			explain.setText(MessageText.getString("externalLogin.explanation", new String[]{ name }));
		}

		browser = Utils.createSafeBrowser(shell, SWT.BORDER);
		if (browser == null) {
			shell.dispose();
			return;
		}
		final ExternalLoginCookieListener cookieListener = new ExternalLoginCookieListener(new CookiesListener() {
			@Override
			public void cookiesFound(String cookies){
				foundCookies( cookies, true );
			}
		},browser);

		cookieListener.hook();

		Label separator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);

		Button alt_method = null;

		if ( isMine ){

			alt_method = new Button(shell,SWT.CHECK);

			final Button f_alt_method = alt_method;

			alt_method.setText(MessageText.getString("externalLogin.auth_method_proxy"));

			alt_method.setSelection( authMode == WebEngine.AM_PROXY );

			alt_method.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event arg0) {

					setCaptureMethod( browser, !f_alt_method.getSelection(), true );
				}
			});
		}

		setCaptureMethod( browser, authMode == WebEngine.AM_TRANSPARENT, true );

		Button cancel = new Button(shell,SWT.PUSH);
		cancel.setText(MessageText.getString("Button.cancel"));

		Button done = new Button(shell,SWT.PUSH);
		done.setText(MessageText.getString("Button.done"));

		cancel.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				if(listener != null) {
					listener.canceled(ExternalLoginWindow.this);
				}
				shell.dispose();
			}
		});

		done.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				if(listener != null) {
					listener.done(ExternalLoginWindow.this,cookiesToString());
				}
				shell.dispose();
			}
		});

		FormData data;

		data =  new FormData();
		data.left = new FormAttachment(0,5);
		data.right = new FormAttachment(100,-5);
		data.top = new FormAttachment(0,5);
		explain.setLayoutData(data);

		data =  new FormData();
		data.left = new FormAttachment(0,5);
		data.right = new FormAttachment(100,-5);
		data.top = new FormAttachment(explain,5);
		data.bottom = new FormAttachment(separator,-5);
		browser.setLayoutData(data);

		data =  new FormData();
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.bottom = new FormAttachment(cancel,-5);
		separator.setLayoutData(data);

		if ( isMine ){

			data =  new FormData();
			data.width = 100;
			data.left = new FormAttachment(0,5);
			data.right = new FormAttachment(cancel,-5);
			data.bottom = new FormAttachment(100,-5);
			alt_method.setLayoutData(data);
		}

		data =  new FormData();
		data.width = 100;
		data.right = new FormAttachment(done,-5);
		data.bottom = new FormAttachment(100,-5);
		cancel.setLayoutData(data);

		data =  new FormData();
		data.width = 100;
		data.right = new FormAttachment(100,-5);
		data.bottom = new FormAttachment(100,-5);
		done.setLayoutData(data);

		shell.layout();
		shell.open();
	}

	protected void
	setCaptureMethod(
		final BrowserWrapper	browser,
		boolean					transparent,
		boolean					show_progress )
	{
		if ( sniffer != null ){

			sniffer.destroy();

			sniffer = null;
		}

		if ( show_progress ){

			final ProgressWindow prog_wind =
				new ProgressWindow( shell, "externalLogin.wait", SWT.DIALOG_TRIM, 500 );

			browser.addProgressListener(
				new ProgressListener()
				{
					@Override
					public void
					changed(
						ProgressEvent arg0 )
					{
					}

					@Override
					public void
					completed(
						ProgressEvent arg0 )
					{
						if (browser.isDisposed() || browser.getShell().isDisposed()) {
							return;
						}
						browser.removeProgressListener( this );

						prog_wind.destroy();
					}
				});
		}

		if ( transparent ){

			browser.setUrl( originalLoginUrl );

		}else{

			try{
				final HTTPAuthHelper this_sniffer = sniffer =
					new HTTPAuthHelper( new URL( originalLoginUrl ));

				this_sniffer.addListener(
					new HTTPAuthHelperListener()
					{
						@Override
						public void
						cookieFound(
							HTTPAuthHelper 	helper,
							String 			cookie_name,
							String 			cookie_value )
						{
							if ( helper == this_sniffer ){

								foundCookies( cookie_name + "=" + cookie_value, false );
							}
						}
					});

				this_sniffer.start();

				String str = originalLoginUrl.toString();

				int	pos = str.indexOf( "://" );

				str = str.substring( pos+3 );

				pos = str.indexOf( "/" );

				if ( pos != -1 ){

					str = str.substring( pos );
				}

				if ( !str.startsWith( "/" )){

					str  = "/" + str;
				}

				browser.setUrl( "http://localhost:" + sniffer.getPort() + str );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected void
	foundCookies(
		String		_cookies,
		boolean		_from_js )
	{
		String[]	x = _cookies.split( ";" );

		synchronized( cookies ){

			for (int i=0;i<x.length;i++){

				String	cookie = x[i];

				String[]	bits = cookie.split("=");

				if( bits.length == 2 ){

					String name 	= bits[0];
					String value	= bits[1];

					if ( _from_js ){

						js_cookies.add( name );

					}else{

						sniffer_cookies.add( name );
					}

					cookies.put(name,value);
				}
			}
		}

		if ( listener != null ){

			listener.cookiesFound(ExternalLoginWindow.this,cookiesToString());
		}
	}

	protected String
	cookiesToString()
	{
		synchronized( cookies ){

			String	res = "";

			Iterator it = cookies.entrySet().iterator();

			while( it.hasNext()){

				Map.Entry entry = (Map.Entry)it.next();

				res += (res.length()==0?"":";" ) + entry.getKey() + "=" + entry.getValue();
			}

			return( res );
		}
	}

	public boolean
	proxyCaptureModeRequired()
	{
			// if we sniffed more cookies that we grabbed through js then use proxy

		if ( sniffer_cookies.size() > js_cookies.size()){

			return( true );
		}

		return( sniffer != null && sniffer.wasHTTPOnlyCookieDetected());
	}

	public void close() {
		Utils.execSWTThread(new Runnable() {
			@Override
			public void run() {
				shell.close();
			}
		});
	}

	public static void main(String[] args) {
		Display display = new Display();
		ExternalLoginWindow slw =
			new ExternalLoginWindow(
				new ExternalLoginListener()
				{
					@Override
					public void
					cookiesFound(
						ExternalLoginWindow window,String cookies)
					{
						System.out.println( "Cookies found: " + cookies );
					}

					@Override
					public void
					canceled(
						ExternalLoginWindow window)
					{
						System.out.println( "Cancelled" );
					}

					@Override
					public void
					done(
						ExternalLoginWindow window,String cookies)
					{
						System.out.println( "Done" );
					}
				},
				"test",
				"http://www.sf.net/",
				false,
				WebEngine.AM_PROXY,
				true );

		while(!slw.shell.isDisposed()) {
			if(!display.readAndDispatch()) {
				display.sleep();
			}
		}

		System.out.println( "Found httponly cookies=" + slw.proxyCaptureModeRequired());
	}

}
